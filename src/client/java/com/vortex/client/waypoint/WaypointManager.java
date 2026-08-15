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

    /** Adds a ready-made marker. Used by the import. */
    public static synchronized void add(Waypoint w) {
        if (w != null) LIST.add(w);
    }

    /**
     * Pins every unassigned marker of this address to the world you are in.
     *
     * Saves going through them one at a time with W. Only markers that carry
     * the same address are touched -- a marker from a different server is left
     * alone, because it certainly does not belong here.
     *
     * @return how many were pinned
     */
    public static synchronized int pinLooseHere(String worldKey) {
        if (worldKey == null) return 0;
        String[] here = split(worldKey);
        int n = 0;
        for (Waypoint w : LIST) {
            if (w.dimension == null) continue;
            String[] a = split(w.dimension);
            // Same place and dimension, but no seed and no fingerprint yet.
            if (!a[1].isEmpty() || !a[2].isEmpty()) continue;
            if (!a[0].equals(here[0])) continue;
            if (!a[3].equals(here[3])) continue;
            w.dimension = worldKey;
            n++;
        }
        return n;
    }

    // ------------------------------------------------------------- sharing

    /**
     * Puts one marker on the clipboard as text.
     *
     * Includes the marked blocks, so a shared base arrives with its chests and
     * traps already in place rather than as a bare coordinate.
     */
    public static String export(Waypoint w) {
        if (w == null) return "";
        return "vortex-wp:" + serializeOne(w);
    }

    /**
     * Reads a marker from text and adds it.
     *
     * The world is deliberately dropped: the sender's world key means nothing
     * here, and keeping it would leave the marker invisible in the world you
     * are standing in -- which looks like the import failed. It is attached to
     * where you are instead.
     *
     * @return the new marker, or null if the text was not one
     */
    public static Waypoint importFrom(String text, String worldKey) {
        if (text == null) return null;
        String t = text.trim();
        if (!t.startsWith("vortex-wp:")) return null;
        try {
            Waypoint w = deserializeOne(t.substring("vortex-wp:".length()));
            if (w == null) return null;
            w.dimension = worldKey;
            add(w);
            return w;
        } catch (Throwable pvpErr) {
            com.vortex.client.core.Errors.report("WaypointManager.import", pvpErr);
            return null;
        }
    }

    /**
     * Does this marker belong to the given world?
     *
     * A key looks like {@code mp:host|s1a2b3c4|minecraft:overworld}: where you
     * are, optionally the world seed, and the dimension.
     *
     * The comparison is deliberately tolerant about the seed. It was added
     * later, so markers saved before that do not carry one — and they must not
     * vanish because of it. The rule is: place and dimension always have to
     * match, the seed only when both sides actually have one.
     */
    public static boolean matches(Waypoint w, String worldKey) {
        if (w.dimension == null || worldKey == null) return true;
        if (w.dimension.equals(worldKey)) return true;

        String[] a = split(w.dimension);
        String[] b = split(worldKey);

        // Dimension must always match.
        if (!a[3].equals(b[3])) return false;

        // Older entries carry only the dimension — keep showing them there.
        if (a[0].isEmpty()) return true;

        if (!a[0].equals(b[0])) return false;

        // Seed and fingerprint: a difference always means a different server.
        if (!a[1].isEmpty() && !b[1].isEmpty() && !a[1].equals(b[1])) return false;
        if (!a[2].isEmpty() && !b[2].isEmpty() && !a[2].equals(b[2])) return false;

        // A marker that has neither, in a world that has both.
        //
        // These come from before the client could tell the servers of a network
        // apart. They carry the address, which on a proxy is the same for every
        // server -- so they turn up everywhere, and a base marked on one server
        // floats over the spawn of another.
        //
        // Strict mode hides them rather than showing them in the wrong place.
        // They stay in the manager, marked "not pinned", and W pins them here.
        if (a[1].isEmpty() && a[2].isEmpty() && !(b[1].isEmpty() && b[2].isEmpty())) {
            return !WaypointSettings.INSTANCE.strictWorld.get();
        }
        return true;
    }

    /** Splits a key into place, seed and dimension. Missing parts stay empty. */
    private static String[] split(String key) {
        String place = "";
        String seed = "";
        String finger = "";
        String dim = key;
        String[] parts = key.split("\\|");
        if (parts.length == 1) {
            // Only a dimension — the oldest format.
            return new String[] { "", "", "", parts[0] };
        }
        place = parts[0];
        dim = parts[parts.length - 1];
        for (int i = 1; i < parts.length - 1; i++) {
            if (parts[i].startsWith("s")) seed = parts[i].substring(1);
            else if (parts[i].startsWith("f")) finger = parts[i].substring(1);
        }
        return new String[] { place, seed, finger, dim };
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
            sb.append(serializeOne(w));
        }
        return sb.toString();
    }

    /**
     * One marker as text.
     *
     * Pulled out of serialize() so sharing and saving use the very same form.
     * Two separate versions of this would drift apart, and a marker exported
     * by one and read by the other would come back wrong.
     *
     * Separators are escaped in every text field. That was a real bug once: the
     * world key contains a "|" itself, the same character that separates the
     * fields, so on loading everything after it shifted by one -- visibility
     * read the dimension, the type read the visibility, and markers came back
     * scrambled.
     */
    public static String serializeOne(Waypoint w) {
        StringBuilder sb = new StringBuilder();
        sb.append(esc(w.name)).append('|')
          .append(w.x).append('|').append(w.y).append('|').append(w.z).append('|')
          .append(w.color).append('|')
          .append(w.dimension == null ? "" : esc(w.dimension)).append('|')
          .append(w.visible ? '1' : '0').append('|')
          .append(w.kind.name()).append('|')
          .append(serializeBlocks(w)).append('|')
          .append(w.tracer ? '1' : '0');
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
            Waypoint w = deserializeOne(entry);
            if (w != null) LIST.add(w);
        }
    }

    /**
     * One marker from text, or null if the line cannot be read.
     *
     * Shared by loading and importing, so a marker that survives a restart also
     * survives being sent to someone -- two separate readers would drift apart
     * and one of them would eventually get it wrong.
     */
    public static Waypoint deserializeOne(String entry) {
        if (entry == null || entry.isEmpty()) return null;
        String[] p = entry.split("\\|");
        if (p.length < 7) return null;

        // RESCUING OLD DATA: the world key used to be written unescaped, which
        // produced one field too many. A line longer than expected has its
        // extra pieces put back together into the dimension.
        final int EXPECTED = 10;
        if (p.length > EXPECTED) {
            int extra = p.length - EXPECTED;
            StringBuilder dim = new StringBuilder(p[5]);
            for (int k = 1; k <= extra; k++) {
                dim.append('|').append(p[5 + k]);
            }
            String[] fixed = new String[EXPECTED];
            System.arraycopy(p, 0, fixed, 0, 5);
            fixed[5] = dim.toString();
            for (int k = 6; k < EXPECTED; k++) {
                fixed[k] = p[k + extra];
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
            // Field 8 (kind) came later -- older files do not have it.
            if (p.length > 7) {
                try {
                    w.kind = Kind.valueOf(p[7].trim());
                } catch (Throwable ignored) {
                    w.kind = Kind.ALLGEMEIN;
                }
            }
            // Field 9 (marked blocks) came later still.
            if (p.length > 8) deserializeBlocks(w, p[8]);
            if (p.length > 9) w.tracer = "1".equals(p[9].trim());
            return w;
        } catch (Throwable pvpErr) {
            // A broken line is skipped rather than losing everything.
            return null;
        }
    }
}
