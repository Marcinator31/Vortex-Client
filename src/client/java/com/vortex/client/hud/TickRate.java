package com.vortex.client.hud;

import com.vortex.client.core.PacketHooks;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;

/**
 * Schaetzt die Ticks pro Sekunde des Servers.
 *
 * Der Server schickt jede Sekunde (alle 20 Ticks) die Weltzeit. Kommt das
 * Paket nach 2 statt nach 1 Sekunde, lief der Server nur mit halber
 * Geschwindigkeit -- 10 TPS. Gemittelt ueber die letzten 20 Pakete, damit ein
 * einzelnes verspaetetes Paket (Netzwerk, nicht Server) die Anzeige nicht
 * springen laesst.
 *
 * Dazu die Zeit seit dem letzten Paket: Bleibt es laenger als ein paar
 * Sekunden aus, haengt der Server oder die Verbindung -- das ist die Frage
 * "laggt der Server oder ich?".
 */
public final class TickRate {

    private TickRate() {}

    private static final float[] WERTE = new float[20];
    private static int naechster = 0;
    private static int anzahl = 0;
    private static volatile long letztes = -1;
    private static volatile long beitritt = 0;

    public static void register() {
        PacketHooks.onReceive(p -> {
            if (p instanceof ClientboundSetTimePacket) paket();
            return false;
        });
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.JOIN
                .register((h, s, c) -> zuruecksetzen());
    }

    private static synchronized void paket() {
        long jetzt = System.currentTimeMillis();
        if (letztes > 0) {
            float sek = (jetzt - letztes) / 1000f;
            if (sek > 0.01f) {
                WERTE[naechster] = Math.max(0f, Math.min(20f, 20f / sek));
                naechster = (naechster + 1) % WERTE.length;
                if (anzahl < WERTE.length) anzahl++;
            }
        }
        letztes = jetzt;
    }

    private static synchronized void zuruecksetzen() {
        anzahl = 0;
        naechster = 0;
        letztes = -1;
        beitritt = System.currentTimeMillis();
    }

    /** Geschaetzte TPS, 20 in den ersten Sekunden nach dem Beitreten. */
    public static synchronized float tps() {
        if (anzahl == 0 || System.currentTimeMillis() - beitritt < 4000) return 20f;
        float summe = 0;
        for (int i = 0; i < anzahl; i++) summe += WERTE[i];
        return summe / anzahl;
    }

    /** Sekunden seit dem letzten Zeitpaket. */
    public static float seitLetztem() {
        if (letztes < 0) return 0f;
        return (System.currentTimeMillis() - letztes) / 1000f;
    }
}
