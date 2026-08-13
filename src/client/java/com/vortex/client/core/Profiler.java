package com.vortex.client.core;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Misst, wie viel Zeit einzelne Teile des Clients pro Tick bzw. Bild
 * verbrauchen.
 *
 * WARUM: Ruckler lassen sich nicht erraten. Statt weiter Vermutungen zu
 * aendern, wird hier gemessen -- danach zeigt {@code /lag}, welcher Teil
 * tatsaechlich Zeit frisst.
 *
 * Bewusst sehr sparsam: nur ein Zeitstempel je Abschnitt und ein paar
 * Additionen. Der Aufwand der Messung selbst faellt gegenueber allem, was
 * gemessen wird, nicht ins Gewicht.
 */
public final class Profiler {

    /** Sammelwerte je Abschnitt. */
    private static final class Stat {
        long calls;
        long totalNanos;
        long maxNanos;
        long windowStart = System.nanoTime();
        long windowMax;      // Hoechstwert im laufenden Fenster
        long lastMax;        // Hoechstwert des letzten abgeschlossenen Fensters
    }

    private static final Map<String, Stat> STATS = new ConcurrentHashMap<>();
    private static final ThreadLocal<Long> START = new ThreadLocal<>();

    /** Fensterlaenge fuer den Spitzenwert: 5 Sekunden. */
    private static final long WINDOW_NANOS = 5_000_000_000L;

    private static volatile boolean enabled = true;

    private Profiler() {}

    public static void setEnabled(boolean on) {
        enabled = on;
    }

    public static boolean isEnabled() {
        return enabled;
    }

    /** Beginn eines Abschnitts. Muss von {@link #end(String)} gefolgt werden. */
    public static void begin(String name) {
        if (!enabled) return;
        START.set(System.nanoTime());
    }

    /** Ende eines Abschnitts -- rechnet die verbrauchte Zeit an. */
    public static void end(String name) {
        if (!enabled) return;
        Long s = START.get();
        if (s == null) return;
        long dur = System.nanoTime() - s;
        record(name, dur);
    }

    /** Direkt eine gemessene Dauer anrechnen. */
    public static void record(String name, long nanos) {
        if (!enabled) return;
        Stat st = STATS.computeIfAbsent(name, k -> new Stat());
        synchronized (st) {
            st.calls++;
            st.totalNanos += nanos;
            if (nanos > st.maxNanos) st.maxNanos = nanos;
            if (nanos > st.windowMax) st.windowMax = nanos;

            long now = System.nanoTime();
            if (now - st.windowStart > WINDOW_NANOS) {
                st.lastMax = st.windowMax;
                st.windowMax = 0;
                st.windowStart = now;
            }
        }
    }

    public static void reset() {
        STATS.clear();
    }

    /**
     * Uebersicht fuer den Befehl /lag.
     *
     * Wichtig ist vor allem die Spalte "Spitze": ein hoher Durchschnitt bedeutet
     * dauerhafte Last, eine hohe Spitze bedeutet Ruckler.
     */
    public static String summary() {
        if (STATS.isEmpty()) {
            return "Noch keine Messwerte. Kurz spielen und erneut versuchen.";
        }
        StringBuilder sb = new StringBuilder("Zeitverbrauch (Durchschnitt / Spitze):");
        STATS.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue().maxNanos, a.getValue().maxNanos))
                .limit(14)
                .forEach(e -> {
                    Stat st = e.getValue();
                    double avgMs = st.calls == 0 ? 0 : (st.totalNanos / (double) st.calls) / 1.0e6;
                    double maxMs = st.maxNanos / 1.0e6;
                    double recentMs = st.lastMax / 1.0e6;
                    sb.append(String.format(java.util.Locale.ROOT,
                            "\n  %-18s %5.2f ms / %6.2f ms   (zuletzt %.2f ms, %d x)",
                            e.getKey(), avgMs, maxMs, recentMs, st.calls));
                });
        sb.append("\n  Faustregel: ein Tick hat 50 ms. Alles ueber 5 ms faellt auf.");
        return sb.toString();
    }
}
