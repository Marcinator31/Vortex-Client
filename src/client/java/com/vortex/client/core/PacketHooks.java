package com.vortex.client.core;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.network.protocol.Packet;

/**
 * Zentrale Stelle fuer ein- und ausgehende Netzwerkpakete.
 *
 * Mehrere Module brauchen dieselben Pakete: die TPS-Anzeige und die
 * Zeit-Aenderung das Zeitpaket, Anti-Knockback das Bewegungspaket, Anti
 * Hunger das Sprintpaket. Statt fuer jedes Modul einen eigenen Eingriff in
 * die Netzwerkschicht zu bauen, gibt es EINEN (ConnectionMixin), und die
 * Module melden sich hier an. Das Addon benutzt dieselbe Stelle.
 *
 * ACHTUNG, THREAD: Empfangene Pakete kommen auf dem Netzwerk-Thread an, nicht
 * auf dem Spiel-Thread. Ein Empfaenger darf dort lesen und das Paket
 * verwerfen, aber nichts an der Welt veraendern -- das gehoert in den
 * naechsten Tick.
 */
public final class PacketHooks {

    private PacketHooks() {}

    /** Gibt true zurueck, wenn das Paket verworfen werden soll. */
    @FunctionalInterface
    public interface Listener {
        boolean handle(Packet<?> packet);
    }

    private static final List<Listener> EMPFANG = new CopyOnWriteArrayList<>();
    private static final List<Listener> SENDEN = new CopyOnWriteArrayList<>();

    /** Empfaenger fuer ankommende Pakete (Netzwerk-Thread!). */
    public static void onReceive(Listener l) {
        EMPFANG.add(l);
    }

    /** Empfaenger fuer abgehende Pakete (meist Spiel-Thread). */
    public static void onSend(Listener l) {
        SENDEN.add(l);
    }

    /** Vom Mixin aufgerufen. true = Paket verwerfen. */
    public static boolean empfangen(Packet<?> packet) {
        boolean weg = false;
        for (Listener l : EMPFANG) {
            try {
                if (l.handle(packet)) weg = true;
            } catch (Throwable pvpErr) {
                Errors.report("PacketHooks.empfangen", pvpErr);
            }
        }
        return weg;
    }

    /** Vom Mixin aufgerufen. true = Paket nicht senden. */
    public static boolean senden(Packet<?> packet) {
        boolean weg = false;
        for (Listener l : SENDEN) {
            try {
                if (l.handle(packet)) weg = true;
            } catch (Throwable pvpErr) {
                Errors.report("PacketHooks.senden", pvpErr);
            }
        }
        return weg;
    }
}
