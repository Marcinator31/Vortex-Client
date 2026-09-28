package com.vortex.client.hud;

import com.vortex.client.core.ListFile;
import com.vortex.client.module.ModuleManager;
import com.vortex.client.module.modules.AutoGGModule;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Auto GG (siehe AutoGGModule).
 */
public final class AutoGG {

    private AutoGG() {}

    public static final ListFile NACHRICHTEN = new ListFile("autogg.txt");

    /** Typische Siegesmeldungen (klein geschrieben). */
    private static final String[] RUNDENENDE = {
            "you won", "you win", "victory!", "winner:", "winners:", "1st killer", "#1 victory",
            "won the game", "has won the game", "game over!", "you are the last one standing"
    };

    private record Treffer(long zeit, String name) {}
    private static final Map<Integer, Treffer> TREFFER = new HashMap<>();
    private static long letzteNachricht = 0;
    private static long senden = -1;
    private static String zuSenden = null;
    private static Object letzteWelt = null;

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(AutoGG::tick);
        ClientReceiveMessageEvents.GAME.register((msg, overlay) -> {
            try {
                AutoGGModule m = an();
                if (m == null || !m.onGameEnd.get() || msg == null) return;
                String s = msg.getString().toLowerCase(Locale.ROOT);
                for (String w : RUNDENENDE) {
                    if (s.contains(w)) { ausloesen(m, ""); return; }
                }
            } catch (Throwable e) {
                com.vortex.client.core.Errors.report("AutoGG.chat", e);
            }
        });
    }

    private static AutoGGModule an() {
        AutoGGModule m = ModuleManager.INSTANCE.get(AutoGGModule.class);
        return m != null && m.isEnabled() ? m : null;
    }

    /** Aus CombatFx.schlag: jemand wurde von mir geschlagen. */
    public static void schlag(Entity ziel) {
        if (!(ziel instanceof LivingEntity le) || an() == null) return;
        TREFFER.put(le.getId(), new Treffer(System.currentTimeMillis(), le.getName().getString()));
    }

    private static void tick(Minecraft mc) {
        if (mc.level == null || mc.player == null) { TREFFER.clear(); zuSenden = null; return; }
        if (mc.level != letzteWelt) { letzteWelt = mc.level; TREFFER.clear(); }
        AutoGGModule m = an();
        if (m == null) { TREFFER.clear(); zuSenden = null; return; }
        long jetzt = System.currentTimeMillis();
        for (Iterator<Map.Entry<Integer, Treffer>> it = TREFFER.entrySet().iterator(); it.hasNext();) {
            var e = it.next();
            if (jetzt - e.getValue().zeit() > 4000) { it.remove(); continue; }
            Entity ent = mc.level.getEntity(e.getKey());
            if (!(ent instanceof LivingEntity le)) continue;
            if (m.onlyPlayers.get() && !(le instanceof Player)) { it.remove(); continue; }
            if (le.isDeadOrDying() || le.getHealth() <= 0f) {
                it.remove();
                if (m.onKill.get()) ausloesen(m, e.getValue().name());
            }
        }
        if (zuSenden != null && jetzt >= senden) {
            String text = zuSenden;
            zuSenden = null;
            var net = mc.getConnection();
            if (net != null) {
                if (text.startsWith("/")) net.sendCommand(text.substring(1));
                else net.sendChat(text);
            }
        }
    }

    private static void ausloesen(AutoGGModule m, String name) {
        long jetzt = System.currentTimeMillis();
        if (zuSenden != null || jetzt - letzteNachricht < m.cooldown.get() * 1000) return;
        letzteNachricht = jetzt;
        String text = nachricht().replace("{name}", name == null ? "" : name).strip();
        if (text.isEmpty()) return;
        if (text.length() > 256) text = text.substring(0, 256);
        zuSenden = text;
        senden = jetzt + Math.round(m.delay.get());
    }

    /** Schluessel fuer den aktuellen Server (Adresse, klein). */
    public static String server() {
        Minecraft mc = Minecraft.getInstance();
        var sd = mc.getCurrentServer();
        if (sd == null) return "singleplayer";
        String ip = sd.ip == null ? "" : sd.ip.toLowerCase(Locale.ROOT).strip();
        if (ip.endsWith(":25565")) ip = ip.substring(0, ip.length() - 6);
        return ip.isEmpty() ? "unknown" : ip;
    }

    public static String nachricht() {
        Map<String, String> t = NACHRICHTEN.tabelle();
        String s = t.get(server());
        if (s == null) s = t.get("default");
        return s == null ? "gg" : s;
    }
}
