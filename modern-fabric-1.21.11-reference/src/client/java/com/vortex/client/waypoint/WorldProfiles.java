package com.vortex.client.waypoint;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Weltprofile -- damit Marker auf Netzwerken sauber getrennt bleiben.
 *
 * DAS PROBLEM: Bei einem Netzwerk mit Proxy verbindet man sich immer mit
 * derselben Adresse, egal auf welchem Server man landet. Der Client sieht also
 * ueberall dieselbe IP. Heissen dann zwei Welten gleich (etwa beide "Spawn"),
 * landen die Marker im selben Topf, obwohl es voellig verschiedene Server sind.
 * Von aussen laesst sich das nicht zuverlaessig unterscheiden -- der Client
 * bekommt schlicht keine Information darueber, wo er gerade ist.
 *
 * DIE LOESUNG: Ein frei benennbares Profil, das der Spieler selbst umschaltet
 * ("Survival", "Skyblock", "Event"). Neue Marker gehoeren zum aktiven Profil,
 * und angezeigt wird nur, was dazu passt. Das ist die einzige Angabe, die auf
 * einem Proxy wirklich verlaesslich ist -- weil nur der Spieler weiss, wo er
 * gerade ist.
 *
 * Ohne gesetztes Profil bleibt es beim bisherigen Verhalten (Server-Adresse),
 * was fuer einzelne Server voellig ausreicht.
 */
public final class WorldProfiles {

    /** Aktives Profil, oder null fuer "automatisch nach Server-Adresse". */
    private static String active = null;

    /** Bekannte Profile in der Reihenfolge ihrer Anlage. */
    private static final Map<String, Boolean> KNOWN = new LinkedHashMap<>();

    private WorldProfiles() {}

    public static synchronized String getActive() {
        return active;
    }

    /** Profil setzen (null = automatisch). Legt es bei Bedarf an. */
    public static synchronized void setActive(String name) {
        if (name == null || name.isBlank()) {
            active = null;
            return;
        }
        String clean = name.trim().replace('|', ' ').replace(';', ' ');
        active = clean;
        KNOWN.put(clean, Boolean.TRUE);
    }

    public static synchronized java.util.List<String> known() {
        return new java.util.ArrayList<>(KNOWN.keySet());
    }

    public static synchronized void remove(String name) {
        KNOWN.remove(name);
        if (name != null && name.equals(active)) active = null;
    }

    /** Naechstes Profil beim Durchklicken (null -> erstes -> ... -> null). */
    public static synchronized String next() {
        var list = known();
        if (list.isEmpty()) return null;
        if (active == null) return list.get(0);
        int i = list.indexOf(active);
        return (i < 0 || i + 1 >= list.size()) ? null : list.get(i + 1);
    }

    // ---- Speichern / Laden ----

    public static synchronized String serialize() {
        StringBuilder sb = new StringBuilder();
        sb.append(active == null ? "" : active);
        for (String k : KNOWN.keySet()) {
            sb.append(';').append(k);
        }
        return sb.toString();
    }

    public static synchronized void deserialize(String data) {
        KNOWN.clear();
        active = null;
        if (data == null || data.isEmpty()) return;
        String[] parts = data.split(";");
        if (parts.length > 0 && !parts[0].isBlank()) active = parts[0].trim();
        for (int i = 1; i < parts.length; i++) {
            String p = parts[i].trim();
            if (!p.isEmpty()) KNOWN.put(p, Boolean.TRUE);
        }
        if (active != null) KNOWN.put(active, Boolean.TRUE);
    }
}
