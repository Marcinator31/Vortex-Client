package com.vortex.client.core;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Eine kleine Textdatei im Konfigurationsordner (config/vortexclient/NAME),
 * eine Zeile pro Eintrag.
 *
 * Fuer Listen, die man mit Schaltern und Reglern nicht sinnvoll einstellen
 * kann: Filterwoerter, Hervorhebungen, Lautstaerke je Sound, GG-Nachricht je
 * Server. Bearbeitet wird per Befehl (/vchat, /vsound, /autogg) oder direkt in
 * der Datei -- beim naechsten Zugriff nach einer Aenderung der Datei wird neu
 * gelesen.
 *
 * Zeilen der Form "schluessel=wert" lassen sich zusaetzlich als Tabelle lesen.
 */
public final class ListFile {

    private final String name;
    private final List<String> zeilen = new ArrayList<>();
    private long stand = -2;

    public ListFile(String name) {
        this.name = name;
    }

    private Path pfad() {
        return ConfigManager.dataDir().resolve(name);
    }

    /** Neu lesen, wenn die Datei sich seit dem letzten Lesen geaendert hat. */
    private void laden() {
        try {
            Path p = pfad();
            long s = Files.exists(p) ? Files.getLastModifiedTime(p).toMillis() : -1;
            if (s == stand) return;
            stand = s;
            zeilen.clear();
            if (s < 0) return;
            for (String z : Files.readAllLines(p, StandardCharsets.UTF_8)) {
                String t = z.strip();
                if (!t.isEmpty() && !t.startsWith("#")) zeilen.add(t);
            }
        } catch (Exception e) {
            Errors.report("ListFile.load " + name, e);
        }
    }

    private void speichern() {
        try {
            Path p = pfad();
            Files.createDirectories(p.getParent());
            Files.write(p, zeilen, StandardCharsets.UTF_8);
            stand = Files.getLastModifiedTime(p).toMillis();
        } catch (Exception e) {
            Errors.report("ListFile.save " + name, e);
        }
    }

    public synchronized List<String> alle() {
        laden();
        return List.copyOf(zeilen);
    }

    public synchronized boolean hinzu(String eintrag) {
        laden();
        String t = eintrag == null ? "" : eintrag.strip();
        if (t.isEmpty()) return false;
        for (String z : zeilen) if (z.equalsIgnoreCase(t)) return false;
        zeilen.add(t);
        speichern();
        return true;
    }

    public synchronized boolean weg(String eintrag) {
        laden();
        String t = eintrag == null ? "" : eintrag.strip();
        boolean weg = zeilen.removeIf(z -> z.equalsIgnoreCase(t));
        if (weg) speichern();
        return weg;
    }

    public synchronized void leeren() {
        laden();
        zeilen.clear();
        speichern();
    }

    // ------------------------------------------------------------------
    // schluessel=wert

    public synchronized Map<String, String> tabelle() {
        laden();
        Map<String, String> m = new LinkedHashMap<>();
        for (String z : zeilen) {
            int i = z.indexOf('=');
            if (i > 0) m.put(z.substring(0, i).strip().toLowerCase(java.util.Locale.ROOT), z.substring(i + 1).strip());
        }
        return m;
    }

    public synchronized void setze(String schluessel, String wert) {
        laden();
        String k = schluessel.strip().toLowerCase(java.util.Locale.ROOT);
        zeilen.removeIf(z -> {
            int i = z.indexOf('=');
            return i > 0 && z.substring(0, i).strip().equalsIgnoreCase(k);
        });
        if (wert != null) zeilen.add(k + "=" + wert.strip());
        speichern();
    }
}
