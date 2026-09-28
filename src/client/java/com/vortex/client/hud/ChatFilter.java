package com.vortex.client.hud;

import com.vortex.client.core.Friends;
import com.vortex.client.core.ListFile;
import com.vortex.client.module.ModuleManager;
import com.vortex.client.module.modules.ChatFilterModule;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.sounds.SoundEvents;

/**
 * Chat Highlight & Filter (siehe ChatFilterModule).
 *
 * Ausblenden laeuft ueber die Fabric-Ereignisse fuer eingehende Nachrichten
 * (die Nachricht kommt gar nicht erst im Chat an). Hervorheben und
 * Zusammenfassen passieren im ChatHudMixin, direkt bevor die Zeile in den
 * Chat kommt -- dort, wo auch der Zeitstempel davorgesetzt wird.
 */
public final class ChatFilter {

    private ChatFilter() {}

    public static final ListFile FILTER = new ListFile("chat-filter.txt");
    public static final ListFile HIGHLIGHT = new ListFile("chat-highlight.txt");

    private static String letzte = null;
    private static int anzahl = 1;
    private static long letzteZeit = 0;

    public static void register() {
        ClientReceiveMessageEvents.ALLOW_GAME.register((msg, overlay) -> overlay || !versteckt(msg));
        ClientReceiveMessageEvents.ALLOW_CHAT.register((msg, signed, sender, params, zeit) -> !versteckt(msg));
    }

    private static ChatFilterModule an() {
        ChatFilterModule m = ModuleManager.INSTANCE.get(ChatFilterModule.class);
        return m != null && m.isEnabled() ? m : null;
    }

    /** Soll diese Nachricht gar nicht erst angezeigt werden? */
    static boolean versteckt(Component msg) {
        try {
            ChatFilterModule m = an();
            if (m == null || msg == null) return false;
            if (m.hideJoinLeave.get() && msg.getContents() instanceof TranslatableContents tc) {
                String k = tc.getKey();
                if (k.startsWith("multiplayer.player.joined") || k.equals("multiplayer.player.left")) return true;
            }
            if (m.filterWords.get()) {
                String s = msg.getString().toLowerCase(Locale.ROOT);
                for (String w : FILTER.alle()) {
                    if (!w.isEmpty() && s.contains(w.toLowerCase(Locale.ROOT))) return true;
                }
            }
        } catch (Throwable e) {
            com.vortex.client.core.Errors.report("ChatFilter.hide", e);
        }
        return false;
    }

    /** Aus dem ChatHudMixin: Zusammenfassen und Hervorheben. */
    public static Component verarbeiten(ChatComponent chat, Component msg) {
        ChatFilterModule m = an();
        if (m == null || msg == null) {
            letzte = null;
            return msg;
        }
        try {
            String roh = msg.getString();
            Component out = hervorheben(m, msg);
            if (m.stackDuplicates.get() && roh.equals(letzte) && System.currentTimeMillis() - letzteZeit < 5 * 60_000) {
                if (entferneLetzte(chat, roh)) {
                    anzahl++;
                    out = out.copy().append(Component.literal(" (x" + anzahl + ")").setStyle(Style.EMPTY.withColor(0x8A8A96)));
                } else {
                    anzahl = 1;
                }
            } else {
                anzahl = 1;
            }
            letzte = roh;
            letzteZeit = System.currentTimeMillis();
            return out;
        } catch (Throwable e) {
            com.vortex.client.core.Errors.report("ChatFilter", e);
            return msg;
        }
    }

    // ------------------------------------------------------------------
    // Zusammenfassen: die vorige, gleiche Zeile aus dem Chat nehmen

    private static Field alleFeld, zeilenFeld;
    private static boolean reflFehler = false;

    @SuppressWarnings("unchecked")
    private static boolean entferneLetzte(ChatComponent chat, String roh) {
        if (reflFehler) return false;
        try {
            if (alleFeld == null) {
                alleFeld = feld("allMessages");
                zeilenFeld = feld("trimmedMessages");
            }
            List<GuiMessage> alle = (List<GuiMessage>) alleFeld.get(chat);
            List<GuiMessage.Line> zeilen = (List<GuiMessage.Line>) zeilenFeld.get(chat);
            if (alle.isEmpty()) return false;
            GuiMessage vorige = alle.get(0);
            if (!vorige.content().getString().contains(roh)) return false;
            alle.remove(0);
            while (!zeilen.isEmpty() && zeilen.get(0).parent() == vorige) zeilen.remove(0);
            return true;
        } catch (Throwable e) {
            reflFehler = true;
            com.vortex.client.core.Errors.report("ChatFilter.stack", e);
            return false;
        }
    }

    private static Field feld(String name) throws NoSuchFieldException {
        Field f = ChatComponent.class.getDeclaredField(name);
        f.setAccessible(true);
        return f;
    }

    // ------------------------------------------------------------------
    // Hervorheben

    private record Treffer(String text, int farbe, boolean name) {}
    private record Stueck(String text, Style style) {}

    private static Component hervorheben(ChatFilterModule m, Component msg) {
        Minecraft mc = Minecraft.getInstance();
        List<Treffer> suche = new ArrayList<>();
        String ich = mc.player != null ? mc.player.getName().getString() : mc.getUser().getName();
        if (m.highlightName.get() && ich != null && ich.length() >= 3) suche.add(new Treffer(ich, m.nameColor.get() & 0xFFFFFF, true));
        if (m.highlightFriends.get()) {
            for (String f : Friends.alle()) if (f.length() >= 3) suche.add(new Treffer(f, Friends.farbe() & 0xFFFFFF, false));
        }
        if (m.highlightWords.get()) {
            for (String w : HIGHLIGHT.alle()) if (w.length() >= 2) suche.add(new Treffer(w, m.wordColor.get() & 0xFFFFFF, false));
        }
        if (suche.isEmpty()) return msg;

        List<Stueck> stuecke = new ArrayList<>();
        StringBuilder ganz = new StringBuilder();
        msg.visit((style, text) -> {
            stuecke.add(new Stueck(text, style));
            ganz.append(text);
            return Optional.empty();
        }, Style.EMPTY);
        String voll = ganz.toString();
        String klein = voll.toLowerCase(Locale.ROOT);
        int[] farbe = new int[voll.length()];
        java.util.Arrays.fill(farbe, -1);
        boolean erwaehnt = false, gefunden = false;
        for (Treffer t : suche) {
            String n = t.text().toLowerCase(Locale.ROOT);
            int i = 0;
            while ((i = klein.indexOf(n, i)) >= 0) {
                int e = i + n.length();
                boolean grenze = (i == 0 || !wortZeichen(klein.charAt(i - 1))) && (e >= klein.length() || !wortZeichen(klein.charAt(e)));
                if (grenze && !(t.name() && istAbsender(voll, i, e))) {
                    for (int k = i; k < e; k++) farbe[k] = t.farbe();
                    gefunden = true;
                    if (t.name()) erwaehnt = true;
                }
                i = e;
            }
        }
        if (!gefunden) return msg;
        if (erwaehnt && m.mentionSound.get()) {
            try {
                mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_PLING.value(), 1.6f, 0.8f));
            } catch (Throwable ignored) { }
        }
        MutableComponent out = Component.empty();
        int pos = 0;
        for (Stueck s : stuecke) {
            String text = s.text();
            int a = 0;
            while (a < text.length()) {
                int f = farbe[pos + a];
                int b = a + 1;
                while (b < text.length() && farbe[pos + b] == f) b++;
                Style st = f >= 0 ? s.style().withColor(f).withBold(true) : s.style();
                out.append(Component.literal(text.substring(a, b)).setStyle(st));
                a = b;
            }
            pos += text.length();
        }
        return out;
    }

    private static boolean wortZeichen(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    /** Ist der Name hier der Absender ("<Name> ...", "Name: ...", "Name » ...")? */
    private static boolean istAbsender(String voll, int anfang, int ende) {
        if (anfang > 40) return false;
        int i = ende;
        while (i < voll.length() && voll.charAt(i) == ' ') i++;
        if (i >= voll.length()) return false;
        char c = voll.charAt(i);
        return c == '>' || c == ':' || c == '»' || c == '|' || c == ']' || c == '→';
    }
}
