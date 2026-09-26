package com.vortex.client.hud;

import com.vortex.client.module.ModuleManager;
import com.vortex.client.module.modules.StreamerModeModule;
import java.util.Locale;
import net.minecraft.client.Minecraft;

/**
 * Ersetzt Name und Serveradresse in jedem gezeichneten Text.
 *
 * Wird fuer JEDEN Text aufgerufen, den Minecraft zeichnet -- also sehr oft.
 * Deshalb: ausgeschaltet sofort zurueck, und die gesuchten Woerter werden nur
 * neu bestimmt, wenn sich Name oder Server aendern, nicht bei jedem Aufruf.
 */
public final class StreamerMode {

    private StreamerMode() {}

    private static String name = null;
    private static String host = null;       // klein geschrieben, ohne Port
    private static String ersatzName = "You";
    private static long geprueft = 0;

    public static String ersetze(String text) {
        if (text == null || text.isEmpty()) return text;
        StreamerModeModule m = ModuleManager.INSTANCE.get(StreamerModeModule.class);
        if (m == null || !m.isEnabled()) return text;
        aktualisiere(m);

        String aus = text;
        if (m.hideName.get() && name != null && name.length() > 1 && aus.contains(name)) {
            aus = aus.replace(name, ersatzName);
        }
        if (m.hideServer.get() && host != null && host.length() > 3) {
            String klein = aus.toLowerCase(Locale.ROOT);
            int i = klein.indexOf(host);
            while (i >= 0) {
                aus = aus.substring(0, i) + "server.ip" + aus.substring(i + host.length());
                klein = aus.toLowerCase(Locale.ROOT);
                i = klein.indexOf(host, i + 9);
            }
        }
        return aus;
    }

    private static void aktualisiere(StreamerModeModule m) {
        long jetzt = System.currentTimeMillis();
        if (jetzt - geprueft < 1000) return;
        geprueft = jetzt;
        Minecraft mc = Minecraft.getInstance();
        try {
            name = mc.getUser() != null ? mc.getUser().getName() : null;
        } catch (Throwable t) {
            name = null;
        }
        ersatzName = m.alias.get();
        try {
            var server = mc.getCurrentServer();
            String ip = server != null ? server.ip : null;
            if (ip != null) {
                ip = ip.toLowerCase(Locale.ROOT).trim();
                int port = ip.lastIndexOf(':');
                if (port > 0 && ip.indexOf(':') == port) ip = ip.substring(0, port);
                // "mc.beispiel.net" -> auch "beispiel.net" treffen, das steht
                // oft im Scoreboard
                if (ip.startsWith("mc.") || ip.startsWith("play.")) {
                    ip = ip.substring(ip.indexOf('.') + 1);
                }
            }
            host = ip;
        } catch (Throwable t) {
            host = null;
        }
    }
}
