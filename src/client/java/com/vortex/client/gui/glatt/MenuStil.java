package com.vortex.client.gui.glatt;

import com.vortex.client.gui.Theme;
import com.vortex.client.gui.VortexStyle;
import com.vortex.client.gui.glatt.Glatt.Schrift;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;

/**
 * Neues Aussehen fuer Hauptmenue, Einzelspieler, Mehrspieler und Pausenmenue
 * (seit Client 4.17, abschaltbar: Client Settings -> "Modern Menus").
 *
 *   - Knoepfe: dunkles Glas mit runden, glatten Ecken, feinem Rahmen und der
 *     Schrift "Inter"; beim Ueberfahren blendet der Akzent weich ein
 *   - Hauptmenue: Vortex-Logo mit Schriftzug statt des Minecraft-Logos,
 *     ruhiger Verlauf ueber dem Panorama
 *
 * Gilt nur in diesen Bildschirmen -- andere Mods und Menues bleiben, wie sie
 * sind. Knoepfe anderer Mods in diesen Menues (z. B. Mod Menu) bekommen das
 * Aussehen automatisch mit.
 */
public final class MenuStil {

    private MenuStil() {}

    private static final Map<Object, float[]> HOVER = new WeakHashMap<>();
    private static final long START = System.nanoTime();

    /** Gilt der neue Stil im gerade offenen Bildschirm? */
    public static boolean aktiv() {
        try {
            if (!com.vortex.client.core.ClientSettings.INSTANCE.modernMenus.get()) return false;
            Screen s = Minecraft.getInstance().gui.screen();
            return s instanceof TitleScreen || s instanceof SelectWorldScreen || s instanceof JoinMultiplayerScreen
                    || s instanceof PauseScreen || s instanceof net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
        } catch (Throwable e) {
            return false;
        }
    }

    public static boolean titel() {
        try {
            return com.vortex.client.core.ClientSettings.INSTANCE.modernMenus.get()
                    && Minecraft.getInstance().gui.screen() instanceof TitleScreen;
        } catch (Throwable e) {
            return false;
        }
    }

    /**
     * Text in "Inter" umstellen? Nur Haupt-, Einzelspieler- und
     * Mehrspielermenue (dort laeuft kein HUD mit, das sich mitveraendern wuerde)
     * und nur im Render-Thread.
     */
    public static boolean textAktiv() {
        try {
            if (inListe > 0) {
                // Falls eine Liste mit Fehler abbricht, haengt der Zaehler nicht ewig
                if (System.nanoTime() - listeSeit < 250_000_000L) return false;
                inListe = 0;
            }
            if (!com.vortex.client.core.ClientSettings.INSTANCE.modernMenus.get()) return false;
            Minecraft mc = Minecraft.getInstance();
            if (!mc.isSameThread()) return false;
            Screen s = mc.gui.screen();
            return s instanceof TitleScreen || s instanceof SelectWorldScreen || s instanceof JoinMultiplayerScreen
                    || s instanceof net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
        } catch (Throwable e) {
            return false;
        }
    }

    /**
     * Wird gerade eine Liste (Welten, Server) gezeichnet? Deren Eintraege --
     * Weltname, Servername, MOTD -- bleiben in Minecrafts eigener Schrift,
     * genau wie die Server sie gestaltet haben (seit 4.18.1).
     */
    private static int inListe = 0;
    private static long listeSeit = 0;

    public static void listeBeginn() { inListe++; listeSeit = System.nanoTime(); }
    public static void listeEnde() { if (inListe > 0) inListe--; }

    /** Zeichen in Minecrafts Standardschrift auf "Inter" umstellen. */
    public static net.minecraft.util.FormattedCharSequence umschreiben(net.minecraft.util.FormattedCharSequence seq) {
        if (seq == null) return null;
        final net.minecraft.network.chat.FontDescription f = Glatt.Schrift.NORMAL.font(Glatt.skala());
        return sink -> seq.accept((i, st, cp) ->
                sink.accept(i, st.getFont().equals(net.minecraft.network.chat.FontDescription.DEFAULT) ? st.withFont(f) : st, cp));
    }

    /** Weicher Hover-Wert 0..1 je Knopf. */
    public static float hover(Object knopf, boolean ueber) {
        float[] z = HOVER.computeIfAbsent(knopf, k -> new float[]{0f, 0f});
        float jetzt = (System.nanoTime() - START) / 1e9f;
        float dt = z[1] == 0f ? 0.016f : Math.max(0f, Math.min(0.1f, jetzt - z[1]));
        z[1] = jetzt;
        float f = 1f - (float) Math.exp(-16f * dt);
        z[0] += ((ueber ? 1f : 0f) - z[0]) * f;
        return z[0];
    }

    private static int akzent() {
        return Theme.INSTANCE.accent.get() | 0xFF000000;
    }

    /** Hintergrund eines Knopfs (ohne Text). */
    public static float grund(GuiGraphicsExtractor ctx, AbstractWidget w) {
        int x = w.getX(), y = w.getY(), bw = w.getWidth(), bh = w.getHeight();
        boolean an = w.active;
        float hv = hover(w, an && w.isHoveredOrFocused());
        float alpha = Masken.klemme(widgetAlpha(w));
        int akz = akzent();
        int hell = Glatt.mix(akz, 0xFFFFFFFF, 0.35f);
        float r = Math.min(8f, bh / 2f);
        int flaeche = an ? Glatt.mix(0xCC120F1B, Glatt.mix(0xE0120F1B, akz, 0.28f), hv) : 0x80100D16;
        Glatt.rund(ctx, x, y, bw, bh, r, Glatt.alpha(flaeche, alpha));
        int kante = an ? Glatt.mix(0x2EFFFFFF, Glatt.alpha(hell, 0.85f), hv) : 0x14FFFFFF;
        Glatt.rahmen(ctx, x, y, bw, bh, r, 1, Glatt.alpha(kante, alpha));
        if (an && bw > 2 * r + 4) {
            // Glanzkante oben innen -- wirkt wie Glas statt flacher Flaeche
            Glatt.linieW(ctx, x + r, x + bw - r, y + 1, Glatt.alpha(0x16FFFFFF, alpha * (1f - 0.5f * hv)));
        }
        if (hv > 0.01f) {
            // feiner Lichtschein oben
            Glatt.licht(ctx, x + bw / 2f, y, Math.max(bw, 40) * 0.45f, Glatt.alpha(hell, alpha * 0.10f * hv));
        }
        return hv;
    }

    /** Ganzer Knopf: Grund + Text in Inter. */
    public static void knopf(GuiGraphicsExtractor ctx, AbstractWidget w) {
        float hv = grund(ctx, w);
        boolean an = w.active;
        float alpha = Masken.klemme(widgetAlpha(w));
        String text = w.getMessage().getString();
        int platz = w.getWidth() - 12;
        text = Glatt.kuerzen(text, platz, Schrift.FETT);
        int farbe = an ? Glatt.mix(0xFFE9E6F2, 0xFFFFFFFF, hv) : 0xFF7A748C;
        Glatt.textMitte(ctx, text, w.getX() + w.getWidth() / 2f, w.getY() + (w.getHeight() - 9) / 2f + 0.5f,
                Glatt.alpha(farbe, alpha), Schrift.FETT);
        if (an && hv > 0.01f && w.getHeight() >= 18) {
            // Feine Akzentlinie unter dem Text, waechst aus der Mitte
            float lw = (Glatt.breite(text, Schrift.FETT) + 10) * hv;
            float cx = w.getX() + w.getWidth() / 2f;
            Glatt.rund(ctx, cx - lw / 2f, w.getY() + w.getHeight() - 4.5f, lw, 1.5f, 0.75f,
                    Glatt.alpha(Glatt.mix(akzent(), 0xFFFFFFFF, 0.35f), alpha * hv));
        }
    }

    /** Ausgewaehlter Eintrag einer Liste (Welten, Server). */
    public static void auswahl(GuiGraphicsExtractor ctx, int x, int y, int w, int h) {
        int hell = Glatt.mix(akzent(), 0xFFFFFFFF, 0.35f);
        Glatt.rund(ctx, x, y, w, h, 8, Glatt.alpha(Glatt.mix(0xFF141019, akzent(), 0.35f), 0.55f));
        Glatt.rahmen(ctx, x, y, w, h, 8, 1, Glatt.alpha(hell, 0.75f));
    }

    /** Haarfeine Trennlinien ueber und unter einer Liste. */
    public static void trenner(GuiGraphicsExtractor ctx, int x, int oben, int w, int unten) {
        Glatt.linieW(ctx, x, x + w, oben, 0x22FFFFFF);
        Glatt.linieW(ctx, x, x + w, unten, 0x22FFFFFF);
    }

    /** Eingabefeld-Hintergrund. */
    public static void eingabe(GuiGraphicsExtractor ctx, AbstractWidget w, int x, int y, int bw, int bh) {
        boolean fokus = w.isFocused();
        float hv = hover(w, fokus || w.isHovered());
        int hell = Glatt.mix(akzent(), 0xFFFFFFFF, 0.35f);
        float r = Math.min(7f, bh / 2f);
        Glatt.rund(ctx, x, y, bw, bh, r, w.active ? 0xD80A0810 : 0x900A0810);
        int kante = fokus ? Glatt.alpha(hell, 0.9f) : Glatt.mix(0x2EFFFFFF, 0x55FFFFFF, hv);
        Glatt.rahmen(ctx, x, y, bw, bh, r, 1, kante);
    }

    /**
     * Symbol-Knopf: bekannte Minecraft-Symbole durch glatte ersetzen.
     * @return true, wenn gezeichnet (sonst bleibt Minecrafts Bildchen)
     */
    public static boolean symbolKnopf(GuiGraphicsExtractor ctx, AbstractWidget w, String pfad, int x, int y, int sw, int sh) {
        Symbole.Symbol s;
        if (pfad.contains("language")) s = Symbole.Symbol.GLOBUS;
        else if (pfad.contains("accessibility")) s = Symbole.Symbol.PERSON;
        else return false;
        float[] z = HOVER.get(w);
        float hv = z == null ? 0f : z[0];
        int farbe = w.active ? Glatt.mix(0xFFD6D1E6, 0xFFFFFFFF, hv) : 0xFF6F6982;
        float g = Math.min(sw, sh) + 2;
        Glatt.symbol(ctx, s, x + (sw - g) / 2f, y + (sh - g) / 2f, g, Glatt.alpha(farbe, Masken.klemme(widgetAlpha(w))));
        return true;
    }

    private static float widgetAlpha(AbstractWidget w) {
        try {
            return w.getAlpha();
        } catch (Throwable e) {
            return 1f;
        }
    }

    // ------------------------------------------------------------------
    // Hauptmenue
    // ------------------------------------------------------------------

    /**
     * Ruhiger Verlauf ueber dem Panorama, damit Logo und Knoepfe gut lesbar
     * sind; seit 4.30.2 zusaetzlich zu den Seiten hin abgedunkelt (Vignette),
     * damit die Mitte, die Figur und das Dock nicht im hellen Himmel stehen.
     */
    public static void titelGrund(GuiGraphicsExtractor ctx, int breite, int hoehe) {
        ctx.fillGradient(0, 0, breite, hoehe / 2, 0x90050309, 0x38050309);
        ctx.fillGradient(0, hoehe / 2, breite, hoehe, 0x38050309, 0xC0050309);
        seitenVerlauf(ctx, breite, hoehe, 0x78);
        int akz = akzent();
        Glatt.licht(ctx, breite * 0.5f, hoehe * 0.12f, Math.max(breite, hoehe) * 0.45f,
                Glatt.alpha(Glatt.mix(akz, VortexStyle.VIOLETT, 0.5f), 0.16f));
        titelBewegung(ctx, breite, hoehe);
    }

    /**
     * Waagerechter Verlauf zu beiden Raendern hin (fillGradient kann nur
     * senkrecht): schmale Streifen mit weich auslaufender Deckkraft.
     */
    private static void seitenVerlauf(GuiGraphicsExtractor ctx, int breite, int hoehe, int maxAlpha) {
        int weite = Math.max(1, Math.round(breite * 0.34f));
        int schritte = Math.min(48, weite);
        for (int i = 0; i < schritte; i++) {
            int x0 = weite * i / schritte, x1 = weite * (i + 1) / schritte;
            if (x1 <= x0) continue;
            float t = 1f - (i + 0.5f) / schritte;
            int a = Math.round(maxAlpha * t * t);
            if (a <= 0) continue;
            int farbe = (a << 24) | 0x050309;
            ctx.fill(x0, 0, x1, hoehe, farbe);
            ctx.fill(breite - x1, 0, breite - x0, hoehe, farbe);
        }
    }

    private static final long TITEL_START = System.nanoTime();

    /**
     * Ruhige Bewegung im Hauptmenue: zwei Lichter in Vortex-Violett und -Blau
     * wandern langsam, kleine Funken steigen auf. Alles aus der Zeit
     * berechnet -- kein Zustand, nichts zu aktualisieren.
     */
    private static void titelBewegung(GuiGraphicsExtractor ctx, int breite, int hoehe) {
        float t = (System.nanoTime() - TITEL_START) / 1_000_000_000f;
        float r = Math.max(breite, hoehe) * 0.32f;
        Glatt.licht(ctx, breite * (0.22f + 0.08f * (float) Math.sin(t * 0.13f)), hoehe * (0.7f + 0.1f * (float) Math.cos(t * 0.11f)),
                r, Glatt.alpha(VortexStyle.VIOLETT, 0.10f));
        Glatt.licht(ctx, breite * (0.78f + 0.08f * (float) Math.cos(t * 0.09f)), hoehe * (0.35f + 0.12f * (float) Math.sin(t * 0.12f)),
                r, Glatt.alpha(VortexStyle.BLAU, 0.09f));
        for (int i = 0; i < 26; i++) {
            // Feste "Zufallswerte" je Funke, damit nichts flackert
            float a = (float) ((Math.sin(i * 12.9898) * 43758.5453) % 1 + 1) % 1;
            float b = (float) ((Math.sin(i * 78.233) * 12543.1234) % 1 + 1) % 1;
            float tempo = 0.025f + b * 0.04f;
            float phase = (t * tempo + a) % 1f;
            float x = breite * a + (float) Math.sin(t * 0.7f + i) * 14f;
            float y = hoehe * (1.05f - phase * 1.15f);
            float sicht = (float) Math.sin(phase * Math.PI);
            int farbe = i % 3 == 0 ? VortexStyle.BLAU : VortexStyle.VIOLETT;
            Glatt.kreis(ctx, x, y, 1.6f + b * 1.6f, Glatt.alpha(Glatt.mix(farbe, 0xFFFFFFFF, 0.35f), 0.55f * sicht));
        }
    }

    /**
     * Vortex-Logo mit Schriftzug, mittig, Oberkante y. Darunter (seit 4.30.2)
     * zwei Plaketten: Client-Version in der Akzentfarbe und die
     * Minecraft-Version.
     */
    public static void titelLogo(GuiGraphicsExtractor ctx, int breite, float y, float alpha) {
        if (alpha <= 0.01f) return;
        String name = "Vortex Client";
        float lg = 46;
        float tw = Glatt.breite(name, Schrift.GROSS);
        float gesamt = lg + 12 + tw;
        float x = (breite - gesamt) / 2f;
        Glatt.licht(ctx, x + lg / 2f, y + lg / 2f, lg * 1.5f, Glatt.alpha(VortexStyle.VIOLETT, alpha * 0.38f));
        // Beim Einblenden des Hauptmenues falten sich die Klingen zum V
        Glatt.logo(ctx, x, y, lg, alpha, alpha);
        float tx = x + lg + 11;
        float ty = y + (lg - 38) / 2f;
        Glatt.text(ctx, name, tx, ty, Glatt.alpha(0xFFFFFFFF, alpha), Schrift.GROSS);

        float py = ty + 25;
        String client = clientVersion();
        if (!client.isEmpty()) {
            tx = plakette(ctx, "v" + client, tx, py, Glatt.mix(akzent(), VortexStyle.VIOLETT, 0.4f), 0xFFFFFFFF, alpha) + 4;
        }
        String mc = mcVersion();
        if (!mc.isEmpty()) plakette(ctx, "Minecraft " + mc, tx, py, 0xFF2A2440, 0xFFC9C3DC, alpha);
    }

    /** Kleine runde Plakette mit Text; gibt den rechten Rand zurueck. */
    private static float plakette(GuiGraphicsExtractor ctx, String text, float x, float y, int grund, int schrift, float alpha) {
        float w = Glatt.breite(text, Schrift.FETT) + 12, h = 13;
        Glatt.rund(ctx, x, y, w, h, h / 2f, Glatt.alpha(grund, 0.78f * alpha));
        Glatt.rahmen(ctx, x, y, w, h, h / 2f, 1, Glatt.alpha(0x30FFFFFF, alpha));
        Glatt.text(ctx, text, x + 6, y + (h - 9) / 2f + 0.5f, Glatt.alpha(schrift, alpha), Schrift.FETT);
        return x + w;
    }

    private static String clientVersionCache;

    private static String clientVersion() {
        if (clientVersionCache == null) {
            try {
                clientVersionCache = net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer("vortexclient")
                        .map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("").replaceAll("\\+.*$", "");
            } catch (Throwable e) {
                clientVersionCache = "";
            }
        }
        return clientVersionCache;
    }

    private static String mcVersion() {
        try {
            return net.minecraft.SharedConstants.getCurrentVersion().name();
        } catch (Throwable e) {
            return "";
        }
    }
}
