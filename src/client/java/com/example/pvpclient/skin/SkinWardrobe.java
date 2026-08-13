package com.example.pvpclient.skin;

import net.fabricmc.loader.api.FabricLoader;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Die Skin-Garderobe: alle jemals hinzugefuegten Skins.
 *
 * Jeder Eintrag hat einen frei waehlbaren Namen, eine PNG-Datei im Ordner
 * <config>/pvpclient/skins/ und die Angabe, woher er stammt (von einem
 * Spielernamen geholt oder selbst hinzugefuegt). Zusaetzlich wird das Modell
 * vermerkt -- klassisch (4 Pixel breite Arme) oder schlank.
 *
 * Bewusst NICHT im Preset gespeichert, sondern in einer eigenen Datei: eine
 * Skin-Sammlung baut man ueber lange Zeit auf, sie soll nicht davon abhaengen,
 * welches Preset gerade aktiv ist.
 *
 * Dateiformat (eine Zeile je Skin, senkrechter Strich als Trenner):
 *   Name|Dateiname|Herkunft|Modell|Zeitstempel
 */
public final class SkinWardrobe {

    /** Ein Skin in der Garderobe. */
    public static final class Skin {
        public String name;          // frei waehlbarer Anzeigename
        public String fileName;      // Dateiname im skins-Ordner
        public String source;        // Spielername oder "eigene Datei"
        public boolean slim;         // schlankes Modell (Alex) statt klassisch
        public long added;           // Zeitpunkt des Hinzufuegens

        public Skin(String name, String fileName, String source, boolean slim, long added) {
            this.name = name;
            this.fileName = fileName;
            this.source = source;
            this.slim = slim;
            this.added = added;
        }

        /** Vollstaendiger Pfad zur PNG-Datei. */
        public Path path() {
            return skinDir().resolve(fileName);
        }

        public boolean exists() {
            try {
                return Files.exists(path());
            } catch (Throwable t) {
                return false;
            }
        }
    }

    private static final List<Skin> LIST = new ArrayList<>();
    private static boolean loaded = false;

    private SkinWardrobe() {}

    /** Ordner, in dem die PNG-Dateien liegen. */
    public static Path skinDir() {
        return FabricLoader.getInstance().getConfigDir()
                .resolve("pvpclient").resolve("skins");
    }

    private static Path indexFile() {
        return skinDir().resolve("garderobe.txt");
    }

    public static synchronized List<Skin> all() {
        ensureLoaded();
        return LIST;
    }

    /**
     * Fuegt einen Skin hinzu. Ein bereits vorhandener Eintrag mit demselben
     * Dateinamen wird ersetzt, damit dieselbe Vorlage nicht doppelt erscheint.
     */
    public static synchronized Skin add(String name, String fileName,
                                        String source, boolean slim) {
        ensureLoaded();
        LIST.removeIf(s -> s.fileName.equalsIgnoreCase(fileName));
        Skin skin = new Skin(name, fileName, source, slim, System.currentTimeMillis());
        LIST.add(0, skin);   // Neueste zuerst
        save();
        return skin;
    }

    public static synchronized void remove(Skin skin) {
        ensureLoaded();
        LIST.remove(skin);
        try {
            // Auch die Datei entfernen -- sonst sammelt sich der Ordner zu.
            Files.deleteIfExists(skin.path());
        } catch (Throwable pvpErr) {
            com.example.pvpclient.core.Errors.report("SkinWardrobe.remove", pvpErr);
        }
        save();
    }

    public static synchronized void rename(Skin skin, String newName) {
        ensureLoaded();
        if (newName == null || newName.trim().isEmpty()) return;
        skin.name = newName.trim();
        save();
    }

    /** Freier Dateiname auf Basis eines Wunschnamens. */
    public static synchronized String freeFileName(String base) {
        String safe = sanitize(base);
        if (safe.isEmpty()) safe = "skin";
        String candidate = safe + ".png";
        int i = 2;
        while (Files.exists(skinDir().resolve(candidate))) {
            candidate = safe + "_" + i + ".png";
            i++;
        }
        return candidate;
    }

    private static String sanitize(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        for (char c : s.trim().toCharArray()) {
            if (Character.isLetterOrDigit(c) || c == '-' || c == '_') sb.append(c);
            else if (c == ' ') sb.append('_');
        }
        return sb.toString();
    }

    // ---- Speichern / Laden ----

    private static void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        try {
            Files.createDirectories(skinDir());
            Path f = indexFile();
            if (!Files.exists(f)) return;
            for (String line : Files.readAllLines(f, StandardCharsets.UTF_8)) {
                String[] p = line.split("\\|");
                if (p.length < 5) continue;
                try {
                    LIST.add(new Skin(p[0], p[1], p[2],
                            "1".equals(p[3].trim()),
                            Long.parseLong(p[4].trim())));
                } catch (Throwable ignored) {
                    // Kaputte Zeile ueberspringen statt alles zu verlieren.
                }
            }
        } catch (Throwable pvpErr) {
            com.example.pvpclient.core.Errors.report("SkinWardrobe.load", pvpErr);
        }
    }

    public static synchronized void save() {
        try {
            Files.createDirectories(skinDir());
            List<String> lines = new ArrayList<>();
            for (Skin s : LIST) {
                String name = s.name.replace('|', ' ');
                lines.add(name + "|" + s.fileName + "|" + s.source.replace('|', ' ')
                        + "|" + (s.slim ? "1" : "0") + "|" + s.added);
            }
            Files.write(indexFile(), lines, StandardCharsets.UTF_8);
        } catch (Throwable pvpErr) {
            com.example.pvpclient.core.Errors.report("SkinWardrobe.save", pvpErr);
        }
    }

    /**
     * Liest den Ordner ein und nimmt PNG-Dateien auf, die noch nicht in der
     * Garderobe stehen. So kann man Skins einfach hineinkopieren, statt sie
     * einzeln hinzuzufuegen.
     */
    public static synchronized int importLooseFiles() {
        ensureLoaded();
        int added = 0;
        try {
            Files.createDirectories(skinDir());
            try (var stream = Files.list(skinDir())) {
                for (Path p : stream.toList()) {
                    String fn = p.getFileName().toString();
                    if (!fn.toLowerCase(java.util.Locale.ROOT).endsWith(".png")) continue;
                    boolean known = LIST.stream().anyMatch(s -> s.fileName.equalsIgnoreCase(fn));
                    if (known) continue;
                    String base = fn.substring(0, fn.length() - 4);
                    LIST.add(0, new Skin(base, fn, "eigene Datei", false,
                            System.currentTimeMillis()));
                    added++;
                }
            }
            if (added > 0) save();
        } catch (Throwable pvpErr) {
            com.example.pvpclient.core.Errors.report("SkinWardrobe.import", pvpErr);
        }
        return added;
    }
}
