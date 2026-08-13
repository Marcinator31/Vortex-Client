package com.vortex.client.core;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Zentrale Stelle fuer Fehler, die den Betrieb nicht abbrechen sollen.
 *
 * HINTERGRUND: An vielen Stellen im Client steht ein Auffangblock, damit eine
 * einzelne kaputte Anzeige nicht das ganze Spiel mitreisst. Bisher wurden diese
 * Fehler aber restlos verschluckt -- ging etwas schief, gab es keinerlei Spur.
 * Genau deshalb war die Suche beim Microsoft-Login so muehsam.
 *
 * Hier landen sie stattdessen im Spiel-Log. Damit das Log nicht zulaeuft, wird
 * jede Fehlerstelle nur EINMAL mit vollem Stapelverlauf gemeldet; danach zaehlt
 * sie im Stillen weiter. Beim Beenden laesst sich so nachsehen, was wie oft
 * schiefging.
 *
 * Aufruf: Errors.report("BlockEsp.scan", t);
 */
public final class Errors {

    private static final org.slf4j.Logger LOGGER =
            org.slf4j.LoggerFactory.getLogger("vortexclient");

    /** Wie oft ist an einer Stelle schon etwas schiefgegangen? */
    private static final Map<String, Integer> COUNTS = new ConcurrentHashMap<>();

    private Errors() {}

    /**
     * Meldet einen Fehler. Der erste an einer Stelle kommt mit vollem
     * Stapelverlauf ins Log, alle weiteren werden nur gezaehlt.
     */
    public static void report(String where, Throwable t) {
        if (where == null) where = "unbekannt";
        Integer prev = COUNTS.merge(where, 1, Integer::sum);
        if (prev != null && prev == 1) {
            LOGGER.warn("[vortexclient] Error in {} (further ones are only counted)", where, t);
        }
    }

    /** Kurzmeldung ohne Ausnahme -- fuer Faelle, die auffaellig, aber harmlos sind. */
    public static void note(String where, String message) {
        Integer prev = COUNTS.merge(where, 1, Integer::sum);
        if (prev != null && prev == 1) {
            LOGGER.info("[vortexclient] {}: {}", where, message);
        }
    }

    /** Wie oft eine bestimmte Stelle bisher gemeldet hat. */
    public static int count(String where) {
        Integer n = COUNTS.get(where);
        return n == null ? 0 : n;
    }

    /** Uebersicht aller Fehlerstellen -- fuer den Befehl /errors. */
    public static String summary() {
        if (COUNTS.isEmpty()) return "No errors recorded.";
        StringBuilder sb = new StringBuilder("Recorded errors:");
        COUNTS.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .limit(15)
                .forEach(e -> sb.append("\n  ").append(e.getKey())
                                .append("  x").append(e.getValue()));
        sb.append("\nDetails are in latest.log.");
        return sb.toString();
    }

    public static void clear() {
        COUNTS.clear();
    }
}
