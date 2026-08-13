package com.example.pvpclient.hud;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Zaehlt Klicks pro Sekunde (CPS).
 *
 * Prinzip: Jeder Klick legt den aktuellen Zeitstempel (ms) in eine Queue.
 * Beim Auslesen werfen wir alle Zeitstempel raus, die aelter als 1 Sekunde
 * sind. Was uebrig bleibt = Klicks in der letzten Sekunde = CPS.
 *
 * Das ist bewusst simpel gehalten. Es ist deine Lern-Vorlage: jedes
 * weitere HUD-Modul (ArmorHUD, FPS, Ping, Keystrokes) folgt demselben
 * Muster -- eine Logik-Klasse + ein Mixin, das die Daten einspeist
 * oder rendert.
 */
public final class CpsCounter {

    // Linke und rechte Maustaste getrennt zaehlen.
    public static final CpsCounter LEFT = new CpsCounter();
    public static final CpsCounter RIGHT = new CpsCounter();

    private final Deque<Long> clicks = new ArrayDeque<>();

    private static final long WINDOW_MS = 1000L;

    /** Wird vom Mixin bei jedem Klick aufgerufen. */
    public void onClick() {
        clicks.addLast(System.currentTimeMillis());
    }

    /** Aktuelle CPS = Anzahl Klicks innerhalb des letzten Sekundenfensters. */
    public int getCps() {
        long now = System.currentTimeMillis();
        // Veraltete Zeitstempel vorne entfernen.
        while (!clicks.isEmpty() && now - clicks.peekFirst() > WINDOW_MS) {
            clicks.removeFirst();
        }
        return clicks.size();
    }
}
