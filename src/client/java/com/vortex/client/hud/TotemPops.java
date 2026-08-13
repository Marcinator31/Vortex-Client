package com.vortex.client.hud;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Zaehlt, wie viele Totems die Spieler in der Umgebung verbraucht haben.
 *
 * Erkennung: Wenn ein Spieler ein Totem verbraucht, schickt der Server allen
 * Umstehenden ein Ereignis (damit die Totem-Animation abgespielt wird). Genau
 * dieses Ereignis wird abgefangen -- es kommt ohnehin bei jedem Client an, es
 * wird also nichts erfragt oder gesendet.
 *
 * Im Kampf ist das die wichtigste Information ueberhaupt: Wer noch viele Totems
 * hat, ueberlebt weiter -- wer gerade sein letztes gezogen hat, ist angreifbar.
 */
public final class TotemPops {

    /** Spielername -> Anzahl verbrauchter Totems (Reihenfolge bleibt erhalten). */
    private static final Map<String, Integer> COUNTS = new LinkedHashMap<>();

    /** Zeitpunkt des letzten Verbrauchs je Spieler (fuer die Hervorhebung). */
    private static final Map<String, Long> LAST = new LinkedHashMap<>();

    /** Nur fuer die einmalige Bestaetigung im Log. */
    private static boolean logged = false;

    private TotemPops() {}

    public static synchronized void add(String player) {
        if (player == null || player.isEmpty()) return;
        long now = System.currentTimeMillis();
        // Es gibt zwei Einstiegspunkte (Entity und LivingEntity). Falls beide
        // fuer dasselbe Ereignis ausloesen, darf nur einmal gezaehlt werden.
        Long last = LAST.get(player);
        if (last != null && (now - last) < 100L) return;
        COUNTS.merge(player, 1, Integer::sum);
        LAST.put(player, now);
        if (!logged) {
            logged = true;
            // Einmalige Meldung: zeigt im Log, dass die Erkennung greift.
            System.out.println("[pvpclient] Totem-Erkennung laeuft (erster Treffer: "
                    + player + ")");
        }
    }

    public static synchronized void reset() {
        COUNTS.clear();
        LAST.clear();
    }

    /** Ein Eintrag fuer die Anzeige. */
    public static final class Entry {
        public final String name;
        public final int count;
        public final long since;   // Millisekunden seit dem letzten Verbrauch
        Entry(String name, int count, long since) {
            this.name = name; this.count = count; this.since = since;
        }
    }

    /** Liste nach Anzahl sortiert (die mit den meisten Totems zuerst). */
    public static synchronized List<Entry> top(int limit) {
        List<Entry> list = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (Map.Entry<String, Integer> e : COUNTS.entrySet()) {
            long last = LAST.getOrDefault(e.getKey(), 0L);
            list.add(new Entry(e.getKey(), e.getValue(), now - last));
        }
        list.sort((a, b) -> Integer.compare(b.count, a.count));
        if (list.size() > limit) return list.subList(0, limit);
        return list;
    }
}
