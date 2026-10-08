package com.vortex.client.cosmetics;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Die waehlbaren Huete & Co. -- seit 4.27 glatte, animierte 3D-Formen
 * (siehe {@link Kopfschmuck}). Die alten Kloetzchen-Huete (Zylinder,
 * Partyhut, Wikingerhelm, Weihnachtsmuetze, Vortex-Kappe, Katzenohren) gibt
 * es nicht mehr; Halo und Krone heissen gleich und sehen jetzt neu aus.
 */
public final class Huete {
    private Huete() {}

    public record Hut(String id, String name, String text) {}

    private static final Map<String, Hut> ALLE = new LinkedHashMap<>();
    static {
        for (Kopfschmuck.Design d : Kopfschmuck.alle().values()) ALLE.put(d.id(), new Hut(d.id(), d.name(), d.text()));
    }

    public static Map<String, Hut> alle() { return ALLE; }
    public static Hut get(String id) { return id == null ? null : ALLE.get(id); }
}
