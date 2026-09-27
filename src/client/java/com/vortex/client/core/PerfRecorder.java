package com.vortex.client.core;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.Locale;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

/**
 * Merkt sich die FPS einer Runde fuer den Leistungs-Check im Launcher.
 *
 * Einmal pro Sekunde in der Welt (ohne offenes Menue -- dort sind die FPS
 * gedrosselt und wuerden das Bild verfaelschen). Beim Verlassen der Welt oder
 * Beenden des Spiels landet das Ergebnis in config/vortex-perf.json:
 *   { avgFps, lowFps, maxFps, minutes, renderDistance, date }
 * lowFps = die schlechtesten 1 % der Sekunden, also die spuerbaren Ruckler.
 *
 * Nichts davon verlaesst den PC -- der Launcher liest die Datei nur lokal.
 */
public final class PerfRecorder {

    private PerfRecorder() {}

    private static int[] werte = new int[1024];
    private static int anzahl = 0;
    private static int tick = 0;

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            try {
                if (mc.level == null || mc.player == null || mc.isPaused() || mc.gui.screen() != null) return;
                if (++tick % 20 != 0) return;
                if (anzahl == werte.length) werte = Arrays.copyOf(werte, werte.length * 2);
                if (anzahl < 6 * 3600) werte[anzahl++] = mc.getFps();      // hoechstens 6 Stunden
            } catch (Throwable ignored) { }
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> speichern(client));
        ClientLifecycleEvents.CLIENT_STOPPING.register(PerfRecorder::speichern);
    }

    private static void speichern(Minecraft mc) {
        try {
            if (anzahl < 30) { anzahl = 0; return; }                // unter 30 Sekunden: nicht aussagekraeftig
            int[] s = Arrays.copyOf(werte, anzahl);
            anzahl = 0;
            Arrays.sort(s);
            long summe = 0;
            for (int v : s) summe += v;
            double schnitt = summe / (double) s.length;
            int wenig = s[Math.max(0, (int) Math.floor(s.length * 0.01))];
            int sicht = 0;
            try { sicht = mc.options.renderDistance().get(); } catch (Throwable ignored) { }
            String json = String.format(Locale.ROOT,
                    "{\"avgFps\":%.1f,\"lowFps\":%d,\"maxFps\":%d,\"minutes\":%.1f,\"renderDistance\":%d,\"date\":\"%s\"}",
                    schnitt, wenig, s[s.length - 1], s.length / 60.0, sicht, Instant.now().toString());
            Path datei = FabricLoader.getInstance().getConfigDir().resolve("vortex-perf.json");
            Files.writeString(datei, json, StandardCharsets.UTF_8);
        } catch (Throwable pvpErr) {
            Errors.report("PerfRecorder", pvpErr);
        }
    }
}
