package com.vortex.client.waypoint;

import java.util.ArrayList;
import java.util.List;

/**
 * Verwaltung der eigenen Marker (Waypoints).
 *
 * Ein Waypoint merkt sich Name, Position, Farbe und die Dimension, in der er
 * gesetzt wurde. Die Dimension ist wichtig, damit Marker aus dem Nether nicht in
 * der Oberwelt herumschweben -- angezeigt wird nur, was zur aktuellen Welt passt.
 *
 * Gespeichert wird alles zusammen mit dem aktiven Preset. Jedes Preset kann also
 * eigene Marker haben (z.B. "PvP" ohne, "Base-Hunting" mit vielen).
 *
 * Das Textformat einer Zeile ist bewusst simpel und mit senkrechtem Strich
 * getrennt, damit man die Datei zur Not von Hand bearbeiten kann:
 *   Name|x|y|z|farbe|dimension|sichtbar
 */
public final class WaypointManager {

    /**
     * Art des Markers. Bestimmt Symbol und Vorschlagsfarbe und laesst sich in
     * der Verwaltung zum Filtern nutzen.
     */
    public enum Kind {
        ALLGEMEIN("Marker", 0xFF4C8BF5),
        BASE("Base", 0xFFFF5555),
        LAGER("Lager", 0xFFFFAA00),
        PORTAL("Portal", 0xFFAA66FF),
        FARM("Farm", 0xFF55FF7A),
        GEFAHR("Gefahr", 0xFFFF66C4),
        BLOCK("Block", 0xFF22D3D3),
        TOD("Todespunkt", 0xFFE0E0E0);

        public final String label;
        public final int color;
        Kind(String label, int color) { this.label = label; this.color = color; }
    }

    /** Ein einzelner Marker. */
    public static final class Waypoint {
        public String name;
        public int x, y, z;
        public int color;
        public String dimension;
        public boolean visible = true;
        public Kind kind = Kind.ALLGEMEIN;

        /** Linie vom Fadenkreuz zu diesem Marker (einzeln schaltbar). */
        public boolean tracer = false;

        /**
         * Zusaetzlich markierte Bloecke, die zu diesem Marker gehoeren.
         *
         * Gedacht fuer Faelle wie "an dieser Stelle muss ein Block gesetzt
         * werden, damit die Falle ausloest": man markiert die betreffenden
         * Bloecke und benennt die Gruppe. Sie werden nur eingeblendet, wenn man
         * in der Naehe ist -- sonst waere das Bild voller Kaesten.
         */
        public final java.util.List<net.minecraft.util.math.BlockPos> blocks =
                new java.util.ArrayList<>();

        public Waypoint(String name, int x, int y, int z, int color, String dimension) {
            this.name = name;
            this.x = x; this.y = y; this.z = z;
            this.color = color;
            this.dimension = dimension;
        }
    }

    private static final List<Waypoint> LIST = new ArrayList<>();

    /** Farben, die neuen Markern der Reihe nach zugewiesen werden. */
    private static final int[] PALETTE = {
        0xFF4C8BF5, 0xFF55FF7A, 0xFFFFAA00, 0xFFFF5555,
        0xFFAA66FF, 0xFF22D3D3, 0xFFFF66C4, 0xFFFFFF55
    };
    private static int nextColor = 0;

    private WaypointManager() {}

    public static synchronized List<Waypoint> all() {
        return LIST;
    }

    /**
     * Marker, die in der angegebenen Welt sichtbar sind.
     *
     * Das Feld "dimension" enthaelt inzwischen die vollstaendige Welt-Kennung
     * (Server bzw. Einzelspieler-Welt PLUS Dimension). Aeltere Eintraege haben
     * dort nur die Dimension stehen -- die werden weiterhin angezeigt, wenn die
     * Dimension passt, damit vorhandene Marker nicht verschwinden.
     */
    public static synchronized List<Waypoint> visibleIn(String worldKey) {
        List<Waypoint> out = new ArrayList<>();
        for (Waypoint w : LIST) {
            if (!w.visible) continue;
            if (worldKey != null && !matches(w, worldKey)) continue;
            out.add(w);
        }
        return out;
    }

    /** Gehoert der Marker zur angegebenen Welt? */
    public static boolean matches(Waypoint w, String worldKey) {
        if (w.dimension == null || worldKey == null) return true;
        if (w.dimension.equals(worldKey)) return true;
        // Altbestand ohne Server-Anteil: nur die Dimension vergleichen.
        if (!w.dimension.contains("|")) {
            int i = worldKey.indexOf('|');
            String dim = (i >= 0) ? worldKey.substring(i + 1) : worldKey;
            return w.dimension.equals(dim);
        }
        return false;
    }

    /** Alle vorkommenden Welten -- fuer die Auswahl in der Verwaltung. */
    public static synchronized List<String> knownWorlds() {
        List<String> out = new ArrayList<>();
        for (Waypoint w : LIST) {
            if (w.dimension != null && !out.contains(w.dimension)) out.add(w.dimension);
        }
        out.sort(String::compareToIgnoreCase);
        return out;
    }

    public static synchronized Waypoint add(String name, int x, int y, int z, String dim) {
        return add(name, x, y, z, dim, Kind.ALLGEMEIN);
    }

    /** Marker mit bestimmter Art anlegen -- die Farbe kommt dann vom Typ. */
    public static synchronized Waypoint add(String name, int x, int y, int z,
                                            String dim, Kind kind) {
        int color = (kind == Kind.ALLGEMEIN)
                ? PALETTE[(nextColor++) % PALETTE.length]
                : kind.color;
        Waypoint w = new Waypoint(name, x, y, z, color, dim);
        w.kind = kind;
        LIST.add(0, w);   // neueste zuerst
        return w;
    }

    /** Naechstgelegener Marker zu einer Position (fuer die HUD-Anzeige). */
    public static synchronized Waypoint nearest(String worldKey,
                                                double px, double py, double pz) {
        Waypoint best = null;
        double bestSq = Double.MAX_VALUE;
        for (Waypoint w : LIST) {
            if (!w.visible) continue;
            if (worldKey != null && !matches(w, worldKey)) continue;
            double dx = w.x - px, dy = w.y - py, dz = w.z - pz;
            double sq = dx * dx + dy * dy + dz * dz;
            if (sq < bestSq) { bestSq = sq; best = w; }
        }
        return best;
    }

    public static synchronized boolean remove(String name) {
        return LIST.removeIf(w -> w.name.equalsIgnoreCase(name));
    }

    public static synchronized void remove(Waypoint w) {
        LIST.remove(w);
    }

    public static synchronized Waypoint find(String name) {
        for (Waypoint w : LIST) {
            if (w.name.equalsIgnoreCase(name)) return w;
        }
        return null;
    }

    public static synchronized void clear() {
        LIST.clear();
    }

    // ---- Speichern / Laden ----

    public static synchronized String serialize() {
        StringBuilder sb = new StringBuilder();
        for (Waypoint w : LIST) {
            if (sb.length() > 0) sb.append(';');
            // Trennzeichen in ALLEN Textwerten maskieren.
            //
            // Hier steckte ein boeser Fehler: Die Welt-Kennung enthaelt selbst
            // ein "|" (z.B. "mp:server.net|minecraft:overworld") -- also genau
            // das Zeichen, das die Felder trennt. Beim Laden verschob sich
            // dadurch alles um eine Stelle: die Sichtbarkeit las die Dimension,
            // die Art las die Sichtbarkeit, und so weiter. Genau das war der
            // Grund, warum Marker nach einem Neustart "vertauscht" wirkten.
            String safe = esc(w.name);
            sb.append(safe).append('|')
              .append(w.x).append('|').append(w.y).append('|').append(w.z).append('|')
              .append(w.color).append('|')
              .append(w.dimension == null ? "" : esc(w.dimension)).append('|')
              .append(w.visible ? '1' : '0').append('|')
              .append(w.kind.name()).append('|')
              .append(serializeBlocks(w)).append('|')
              .append(w.tracer ? '1' : '0');
        }
        return sb.toString();
    }

    /**
     * Maskiert die Zeichen, die als Trenner dienen.
     *
     * Bewusst eine Ersetzung, die sich eindeutig rueckgaengig machen laesst --
     * anders als das frueher verwendete Ersetzen durch Leerzeichen, bei dem der
     * Originaltext verloren ging.
     */
    private static String esc(String v) {
        if (v == null) return "";
        return v.replace("%", "%25")
                .replace("|", "%7C")
                .replace(";", "%3B");
    }

    private static String unesc(String v) {
        if (v == null) return "";
        return v.replace("%7C", "|")
                .replace("%3B", ";")
                .replace("%25", "%");
    }

    /** Bloecke als "x,y,z x,y,z ..." (Leerzeichen trennt, Komma innerhalb). */
    private static String serializeBlocks(Waypoint w) {
        if (w.blocks.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (net.minecraft.util.math.BlockPos b : w.blocks) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(b.getX()).append(',').append(b.getY()).append(',').append(b.getZ());
        }
        return sb.toString();
    }

    private static void deserializeBlocks(Waypoint w, String data) {
        w.blocks.clear();
        if (data == null || data.isBlank()) return;
        for (String part : data.trim().split(" ")) {
            String[] c = part.split(",");
            if (c.length != 3) continue;
            try {
                w.blocks.add(new net.minecraft.util.math.BlockPos(
                        Integer.parseInt(c[0]), Integer.parseInt(c[1]),
                        Integer.parseInt(c[2])));
            } catch (Throwable ignored) {
            }
        }
    }

    public static synchronized void deserialize(String data) {
        LIST.clear();
        if (data == null || data.isEmpty()) return;
        for (String entry : data.split(";")) {
            String[] p = entry.split("\\|");
            if (p.length < 7) continue;

            // RETTUNG ALTER DATEN: Frueher wurde die Welt-Kennung unmaskiert
            // geschrieben, wodurch ein zusaetzliches Feld entstand. Ist die
            // Zeile laenger als erwartet, gehoeren die ueberzaehligen Stuecke
            // zur Dimension und werden wieder zusammengefuegt.
            final int ERWARTET = 10;
            if (p.length > ERWARTET) {
                int zuviel = p.length - ERWARTET;
                StringBuilder dim = new StringBuilder(p[5]);
                for (int k = 1; k <= zuviel; k++) {
                    dim.append('|').append(p[5 + k]);
                }
                String[] fixed = new String[ERWARTET];
                System.arraycopy(p, 0, fixed, 0, 5);
                fixed[5] = dim.toString();
                for (int k = 6; k < ERWARTET; k++) {
                    fixed[k] = p[k + zuviel];
                }
                p = fixed;
            }

            try {
                Waypoint w = new Waypoint(unesc(p[0]),
                        Integer.parseInt(p[1].trim()),
                        Integer.parseInt(p[2].trim()),
                        Integer.parseInt(p[3].trim()),
                        Integer.parseInt(p[4].trim()),
                        unesc(p[5]));
                w.visible = "1".equals(p[6].trim());
                // Feld 8 (Art) kam spaeter dazu -- aeltere Dateien haben es nicht.
                if (p.length > 7) {
                    try {
                        w.kind = Kind.valueOf(p[7].trim());
                    } catch (Throwable ignored) {
                        w.kind = Kind.ALLGEMEIN;
                    }
                }
                // Feld 9 (markierte Bloecke) kam noch spaeter dazu.
                if (p.length > 8) deserializeBlocks(w, p[8]);
                if (p.length > 9) w.tracer = "1".equals(p[9].trim());
                LIST.add(w);
            } catch (Throwable ignored) {
                // Kaputte Zeile ueberspringen statt alles zu verlieren.
            }
        }
    }
}
