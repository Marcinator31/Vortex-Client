package com.vortex.client.core;

import com.vortex.client.core.setting.Setting;
import com.vortex.client.module.Module;
import com.vortex.client.module.ModuleManager;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Speichert und laedt alle Modul-Einstellungen (an/aus, Position, Farben,
 * Skalierung ...) in einer Textdatei, damit sie einen Neustart ueberleben.
 *
 * Format pro Zeile:  ModulName\tSettingName\tWert
 * (Tab-getrennt, damit Namen mit Leerzeichen/Sonderzeichen kein Problem sind.)
 *
 * Jedes Setting kann sich schon selbst serialisieren (serialize/deserialize),
 * wir muessen die Werte also nur einsammeln und wieder zuordnen.
 */
public final class ConfigManager {

    private ConfigManager() {}

    // ---- Presets ----------------------------------------------------------
    //
    // Es gibt drei getrennte Einstellungs-Saetze (z.B. "PvP", "Base-Hunting",
    // "Normal"). Gespeichert wird immer in die Datei des aktiven Presets; beim
    // Umschalten wird der aktuelle Stand gesichert und der andere geladen.
    //
    // Welches Preset zuletzt aktiv war, steht in einer kleinen Extra-Datei,
    // damit es einen Neustart ueberlebt.

    public static final int PRESET_COUNT = 3;

    private static int activePreset = 0;

    /** Ordner, in dem alles liegt. */
    private static Path dir() {
        Path base = FabricLoader.getInstance().getConfigDir();
        Path neu = base.resolve("vortexclient");
        // Beim Umbenennen des Clients: vorhandenen Ordner uebernehmen, damit
        // Presets, Marker und Skins nicht verloren gehen.
        try {
            if (!Files.exists(neu)) {
                Path alt = base.resolve("pvpclient");
                if (Files.exists(alt)) {
                    Files.move(alt, neu);
                    Errors.note("ConfigManager", "Imported settings from the previous version.");
                }
            }
        } catch (Throwable pvpErr) {
            Errors.report("ConfigManager.migrate", pvpErr);
        }
        return neu;
    }

    /** Datei des aktiven Presets: <config>/pvpclient/preset1.txt usw. */
    private static Path configFile() {
        return dir().resolve("preset" + (activePreset + 1) + ".txt");
    }

    /**
     * EINMALIGE UEBERNAHME beim ersten Start nach dem Update (4.5.0).
     *
     * Bis 4.4.x hatte jedes Preset seine eigenen Waypoints, Makros und Freunde.
     * Jetzt gibt es sie nur noch einmal (global.txt). Damit dabei nichts
     * verloren geht:
     *   1. alle Preset-Dateien werden unveraendert nach backup-4.4/ kopiert,
     *   2. Waypoints, Makros und Freunde ALLER drei Presets werden
     *      zusammengefuehrt (doppelte nur einmal),
     *   3. alles andere (Tasten, Design, Waypoint-Einstellungen) kommt aus dem
     *      zuletzt aktiven Preset.
     */
    private static void migriereGlobal() {
        try {
            java.util.LinkedHashMap<String, String> global = new java.util.LinkedHashMap<>();
            java.util.LinkedHashSet<String> wegpunkte = new java.util.LinkedHashSet<>();
            java.util.LinkedHashSet<String> makros = new java.util.LinkedHashSet<>();
            java.util.LinkedHashSet<String> freunde = new java.util.LinkedHashSet<>();
            String freundeSchluessel = null;
            boolean irgendwas = false;

            Path sicherung = dir().resolve("backup-4.4");
            // Aktives Preset zuerst -- seine Tasten, Einstellungen und sein
            // Design gewinnen; die anderen steuern nur Waypoints, Makros und
            // Freunde bei.
            int[] reihenfolge = new int[PRESET_COUNT];
            reihenfolge[0] = activePreset;
            for (int i = 0, k = 1; i < PRESET_COUNT; i++) if (i != activePreset) reihenfolge[k++] = i;

            for (int idx : reihenfolge) {
                Path f = dir().resolve("preset" + (idx + 1) + ".txt");
                if (!Files.exists(f)) continue;
                irgendwas = true;
                Files.createDirectories(sicherung);
                Path ziel = sicherung.resolve(f.getFileName());
                if (!Files.exists(ziel)) Files.copy(f, ziel);

                for (String z : Files.readAllLines(f, StandardCharsets.UTF_8)) {
                    if (!istGlobal(z)) continue;
                    String[] p = z.split("\t", 3);
                    String k = p[0] + "\t" + p[1];
                    if (p[0].equals("__waypoints__")) {
                        for (String e : p[2].split(";")) if (!e.isEmpty()) wegpunkte.add(e);
                    } else if (p[0].equals("__macros__")) {
                        for (String e : p[2].split("%0A")) if (!e.isEmpty()) makros.add(e);
                    } else if (p[1].equals("__friends__")) {
                        freundeSchluessel = k;
                        for (String e : p[2].split(",")) if (!e.isBlank()) freunde.add(e.trim());
                    } else {
                        global.putIfAbsent(k, z);   // erstes (= aktives) Preset gewinnt
                    }
                }
            }
            if (!irgendwas) return;
            List<String> zeilen = new ArrayList<>(global.values());
            zeilen.add("__waypoints__\tliste\t" + String.join(";", wegpunkte));
            zeilen.add("__macros__\tdaten\t" + String.join("%0A", makros));
            if (freundeSchluessel != null) zeilen.add(freundeSchluessel + "\t" + String.join(",", freunde));
            sicherSchreiben(globalFile(), zeilen);
            Errors.note("ConfigManager", "Presets now hold mod settings only. Waypoints, macros and friends "
                    + "of all presets were merged; the old files are in backup-4.4.");
        } catch (Throwable pvpErr) {
            Errors.report("ConfigManager.migriereGlobal", pvpErr);
        }
    }

    /**
     * Schreibt erst in eine Zwischendatei und tauscht dann aus. Stuerzt das
     * Spiel mitten im Schreiben ab, bleibt die alte Datei heil -- wichtig fuer
     * global.txt, die alle Waypoints und Makros enthaelt.
     */
    private static void sicherSchreiben(Path ziel, List<String> zeilen) throws IOException {
        Files.createDirectories(ziel.getParent());
        Path tmp = ziel.resolveSibling(ziel.getFileName() + ".tmp");
        Files.write(tmp, zeilen, StandardCharsets.UTF_8);
        try {
            Files.move(tmp, ziel, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            Files.move(tmp, ziel, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * Ging das Laden schief (z. B. eine von Hand kaputt bearbeitete Datei),
     * wird NICHT gespeichert -- sonst wuerden die Dateien mit Standardwerten
     * ueberschrieben und alles waere weg.
     */
    private static boolean ladenFehlgeschlagen = false;

    /** Was fuer alle Presets gilt: Makros, Waypoints, Freunde, Tasten, Design. */
    private static Path globalFile() {
        return dir().resolve("global.txt");
    }

    /** Merkt sich, welches Preset zuletzt aktiv war. */
    private static Path activeFile() {
        Path neu = dir().resolve("active.txt");
        // Beim Umbenennen der Datei den alten Namen uebernehmen, damit das
        // zuletzt gewaehlte Preset nicht verloren geht.
        try {
            Path alt = dir().resolve("aktiv.txt");
            if (!Files.exists(neu) && Files.exists(alt)) Files.move(alt, neu);
        } catch (Throwable pvpErr) {
            Errors.report("ConfigManager.activeFile", pvpErr);
        }
        return neu;
    }

    public static int getActivePreset() {
        return activePreset;
    }

    /** Anzeigename eines Presets: eigener Name, sonst "Preset 1" usw. */
    public static String presetName(int index) {
        String[] n = namen();
        if (index >= 0 && index < n.length && n[index] != null && !n[index].isBlank()) return n[index];
        return "Preset " + (index + 1);
    }

    /** Eigenen Namen fuer ein Preset setzen (leer = Standardname). */
    public static void setPresetName(int index, String name) {
        if (index < 0 || index >= PRESET_COUNT) return;
        String[] n = namen();
        for (int i = 0; i < n.length; i++) if (n[i] == null) n[i] = "";
        String sauber = name == null ? "" : name.replace("\n", " ").replace("\t", " ").trim();
        if (sauber.length() > 24) sauber = sauber.substring(0, 24);
        n[index] = sauber;
        try {
            Files.createDirectories(dir());
            Files.writeString(dir().resolve("names.txt"), String.join("\n", n), StandardCharsets.UTF_8);
        } catch (Throwable pvpErr) {
            Errors.report("ConfigManager.setPresetName", pvpErr);
        }
        namenCache = n;
    }

    private static String[] namenCache = null;

    private static String[] namen() {
        if (namenCache != null) return namenCache;
        String[] n = new String[PRESET_COUNT];
        try {
            Path f = dir().resolve("names.txt");
            if (Files.exists(f)) {
                List<String> z = Files.readAllLines(f, StandardCharsets.UTF_8);
                for (int i = 0; i < PRESET_COUNT && i < z.size(); i++) n[i] = z.get(i).trim();
            }
        } catch (Throwable ignored) {
        }
        namenCache = n;
        return n;
    }

    /** Gibt es fuer dieses Preset schon eine Datei (wurde es je benutzt)? */
    public static boolean presetExists(int index) {
        return Files.exists(dir().resolve("preset" + (index + 1) + ".txt"));
    }

    /**
     * Auf ein anderes Preset umschalten: aktuellen Stand sichern, dann den
     * neuen laden. Existiert die Datei des Ziels noch nicht, bleiben die
     * aktuellen Werte stehen und werden als Startpunkt gespeichert.
     */
    public static void switchTo(int index) {
        if (index < 0 || index >= PRESET_COUNT || index == activePreset) return;
        save();                 // aktuellen Stand sichern
        activePreset = index;
        writeActive();
        Path file = configFile();
        if (Files.exists(file)) {
            load();
        } else {
            // Neues, noch nie benutztes Preset: FRISCH anfangen.
            //
            // Frueher wurden hier die aktuellen Werte uebernommen. Das war
            // verwirrend: man wechselte auf ein "neues" Preset und alles war
            // exakt wie vorher -- es sah aus, als wuerde der Wechsel nicht
            // funktionieren. Ein neues Preset soll ein sauberer Ausgangspunkt
            // sein, kein Abbild des alten.
            //
            // Nur die MODULE fangen frisch an. Makros, Waypoints, Freunde,
            // Tastenbelegungen und Design gehoeren seit 4.5.0 nicht mehr zum
            // Preset -- vorher loeschte der erste Wechsel auf ein leeres Preset
            // sogar alle Waypoints.
            resetModules();
            // Unbekannte MODUL-Zeilen (Addon-Einstellungen ohne Addon) des
            // alten Presets nicht ins neue kopieren; globale bleiben.
            FREMDE_ZEILEN.removeIf(z -> !istGlobal(z));
            save();
        }
    }

    /** Nur die Modul-Einstellungen auf Werkszustand -- Tasten und Freunde bleiben. */
    public static void resetModules() {
        for (Module m : ModuleManager.INSTANCE.getModules()) {
            try {
                for (Setting st : m.getSettings()) {
                    if (st == m.getToggleKey()) continue;
                    st.resetToDefault();
                }
                if (m instanceof com.vortex.client.module.ExtraData ed
                        && !"__friends__".equals(ed.extraKey())) {
                    ed.clearExtra();
                }
                m.syncState();
            } catch (Throwable pvpErr) {
                Errors.report("ConfigManager.resetModules:" + m.getName(), pvpErr);
            }
        }
    }

    /**
     * Setzt alles auf den Auslieferungszustand zurueck: alle Modul-
     * Einstellungen, die Auswahllisten (Mobs, Bloecke, Entities), das
     * Farbschema, Favoriten, Fensterposition und die Marker.
     */
    /**
     * Writes shared preset content into one of the three slots and switches to it.
     *
     * The slot is overwritten, so the caller has to be sure -- the community
     * screen asks first. Switching straight afterwards is the point: importing
     * something and then having to go and select it is a step nobody wants,
     * and forgetting it looks exactly like the import failed.
     *
     * The current settings are saved first, so whatever was open before is not
     * lost just because it happened to be in the slot next door.
     *
     * @param index 0, 1 or 2
     * @return true if it was written
     */
    public static boolean importInto(int index, String content) {
        if (index < 0 || index >= PRESET_COUNT) return false;
        if (content == null || content.isBlank()) return false;
        try {
            // Aktuellen Stand sichern, bevor etwas ueberschrieben wird.
            save();

            // NUR MOD-EINSTELLUNGEN UEBERNEHMEN.
            //
            // Fremde Makros, Waypoints, Freunde oder Tastenbelegungen kommen
            // nie herein -- auch nicht aus aelteren, vollstaendigen Preset-
            // Dateien. Module, die im geteilten Preset fehlen (z. B. Addon-
            // Module, wenn der Ersteller das Addon nicht hatte), behalten ihre
            // bisherigen Werte in diesem Platz.
            Path target = dir().resolve("preset" + (index + 1) + ".txt");
            java.util.LinkedHashMap<String, String> zusammen = new java.util.LinkedHashMap<>();
            if (Files.exists(target)) {
                for (String z : Files.readAllLines(target, StandardCharsets.UTF_8)) {
                    String k = schluessel(z);
                    if (k != null && !istGlobal(z)) zusammen.put(k, z);
                }
            }
            int neu = 0;
            int bekannt = 0;
            for (String z : content.split("\\r?\\n")) {
                String k = schluessel(z);
                if (k == null || istGlobal(z)) continue;
                zusammen.put(k, z);
                neu++;
                String[] p = z.split("\t", 3);
                if (findSetting(p[0], p[1]) != null) bekannt++;
            }
            // Irgendein Text mit Tabs (z. B. aus einer Tabelle) ist kein
            // Preset: mindestens eine Zeile muss eine echte Einstellung sein.
            if (neu == 0 || bekannt == 0) return false;
            Files.createDirectories(target.getParent());
            Files.write(target, new ArrayList<>(zusammen.values()), StandardCharsets.UTF_8);

            // Gleich auswaehlen -- wer importiert, will es auch benutzen.
            if (index != activePreset) {
                activePreset = index;
                writeActive();
            }
            load();
            return true;
        } catch (Throwable pvpErr) {
            Errors.report("ConfigManager.importInto", pvpErr);
            return false;
        }
    }

    /** "Modul<TAB>Einstellung" einer Zeile, oder null, wenn sie keine ist. */
    private static String schluessel(String line) {
        if (line == null || line.isBlank()) return null;
        String[] p = line.split("\t", 3);
        if (p.length < 3) return null;
        return p[0] + "\t" + p[1];
    }

    /**
     * Gehoert diese Zeile NICHT zum Preset, sondern gilt fuer alle?
     *
     * Alles mit "__" vorne (Makros, Waypoints, Weltprofile, GUI, Design,
     * Beschreibungszeilen), die Tastenbelegungen der Module und die
     * Freundesliste. Presets sind nur fuer Mod-Einstellungen da.
     */
    public static boolean istGlobal(String line) {
        String[] p = line.split("\t", 3);
        if (p.length < 3) return false;
        return p[0].startsWith("__") || p[1].equals("Toggle Key") || p[1].equals("__friends__");
    }

    /**
     * Die Mod-Einstellungen des aktiven Presets als Text zum Teilen.
     *
     * Oben drei Beschreibungszeilen (Format, Name, empfohlene Aufloesung),
     * dann nur Modul-Einstellungen -- keine Makros, Waypoints, Freunde oder
     * Tasten. Die Website erkennt das Format an den Tab-getrennten Zeilen.
     */
    public static String exportMods(String name, String aufloesung) {
        return exportMods(activePreset, name, aufloesung);
    }

    /** Wie oben, aber fuer einen beliebigen der drei Plaetze. */
    public static String exportMods(int slot, String name, String aufloesung) {
        save();
        List<String> out = new ArrayList<>();
        out.add("__meta__\tformat\tvortex-mods-1");
        out.add("__meta__\tname\t" + einzeilig(name));
        if (aufloesung != null && !aufloesung.isBlank()) {
            out.add("__meta__\tresolution\t" + einzeilig(aufloesung));
        }
        try {
            Path quelle = dir().resolve("preset" + (slot + 1) + ".txt");
            if (Files.exists(quelle)) {
                for (String z : Files.readAllLines(quelle, StandardCharsets.UTF_8)) {
                    if (schluessel(z) != null && !istGlobal(z)) out.add(z);
                }
            }
        } catch (Throwable pvpErr) {
            Errors.report("ConfigManager.exportMods", pvpErr);
        }
        return String.join("\n", out) + "\n";
    }

    /** Wie viele Mod-Einstellungen stehen in diesem Text? (0 = leeres Preset) */
    public static int modZeilen(String content) {
        if (content == null) return 0;
        int n = 0;
        for (String z : content.split("\\r?\\n")) {
            if (schluessel(z) != null && !istGlobal(z)) n++;
        }
        return n;
    }

    /** Wert einer Beschreibungszeile (__meta__) aus geteiltem Text, oder "". */
    public static String meta(String content, String key) {
        if (content == null) return "";
        for (String z : content.split("\\r?\\n")) {
            String[] p = z.split("\t", 3);
            if (p.length == 3 && p[0].equals("__meta__") && p[1].equals(key)) return p[2].trim();
        }
        return "";
    }

    private static String einzeilig(String s) {
        return s == null ? "" : s.replace("\t", " ").replace("\n", " ").replace("\r", " ").trim();
    }

    /** Mod-Export als Datei im Export-Ordner. Rueckgabe: Pfad oder null. */
    public static Path exportModsToFile(String name, String aufloesung) {
        try {
            String safe = sanitize(name);
            if (safe.isEmpty()) safe = "preset";
            Files.createDirectories(exportDir());
            Path ziel = exportDir().resolve(safe + ".txt");
            Files.writeString(ziel, exportMods(name, aufloesung), StandardCharsets.UTF_8);
            return ziel;
        } catch (Throwable pvpErr) {
            Errors.report("ConfigManager.exportModsToFile", pvpErr);
            return null;
        }
    }

    /** Mod-Export eines bestimmten Platzes als Datei. Rueckgabe: Pfad oder null. */
    public static Path exportModsToFileFrom(int slot, String name) {
        try {
            String safe = sanitize(name);
            if (safe.isEmpty()) safe = "preset";
            Files.createDirectories(exportDir());
            Path ziel = exportDir().resolve(safe + ".txt");
            Files.writeString(ziel, exportMods(slot, name, ""), StandardCharsets.UTF_8);
            return ziel;
        } catch (Throwable pvpErr) {
            Errors.report("ConfigManager.exportModsToFileFrom", pvpErr);
            return null;
        }
    }

    /** Datei aus dem Export-Ordner lesen (fuer den Import). */
    public static String readExport(String name) {
        try {
            Path q = exportDir().resolve(sanitize(name) + ".txt");
            return Files.exists(q) ? Files.readString(q, StandardCharsets.UTF_8) : null;
        } catch (Throwable pvpErr) {
            return null;
        }
    }

    /** Ordner fuer Dinge, die nur dieser Client braucht (z. B. Anmeldung). */
    public static Path dataDir() {
        return dir();
    }

    public static void resetAll() {
        for (Module m : ModuleManager.INSTANCE.getModules()) {
            try {
                for (Setting st : m.getSettings()) {
                    st.resetToDefault();
                }
                // Auswahllisten leeren -- die haben keinen "Wert" im obigen Sinn.
                if (m instanceof com.vortex.client.module.ExtraData ed) {
                    ed.clearExtra();
                }
                m.syncState();
            } catch (Throwable pvpErr) {
                Errors.report("ConfigManager.resetAll:" + m.getName(), pvpErr);
            }
        }
        try {
            com.vortex.client.gui.Theme.INSTANCE.resetDefaults();
            com.vortex.client.gui.GuiState.getFavorites().clear();
            com.vortex.client.gui.GuiState.resetOffset();
            com.vortex.client.waypoint.WaypointManager.clear();
            com.vortex.client.waypoint.WaypointSettings.INSTANCE.resetDefaults();
        } catch (Throwable pvpErr) {
            Errors.report("ConfigManager.resetAll", pvpErr);
        }
    }

    private static void writeActive() {
        try {
            Files.createDirectories(dir());
            Files.writeString(activeFile(), String.valueOf(activePreset));
        } catch (Throwable ignored) {
        }
    }

    // ---- Import / Export ---------------------------------------------------
    //
    // Presets liegen als schlichte Textdatei vor (eine Zeile je Einstellung).
    // Zum Teilen oder Sichern kann das aktive Preset in eine frei benennbare
    // Datei geschrieben und von dort wieder eingelesen werden.

    /** Ordner fuer Exporte: <config>/pvpclient/export/ */
    public static Path exportDir() {
        return dir().resolve("export");
    }

    /**
     * Schreibt das aktive Preset in eine Datei im Export-Ordner.
     * Rueckgabe ist der Pfad, oder null bei einem Fehler.
     */
    public static Path exportPreset(String name) {
        // Seit 4.5.0 nur Mod-Einstellungen -- keine Makros, Waypoints oder
        // Freunde in einer Datei, die man vielleicht weitergibt.
        return exportModsToFile(name, "");
    }

    /**
     * Liest eine Datei aus dem Export-Ordner in das aktive Preset ein
     * (nur Mod-Einstellungen). Rueckgabe: true bei Erfolg.
     */
    public static boolean importPreset(String name) {
        String inhalt = readExport(name);
        return inhalt != null && importInto(activePreset, inhalt);
    }

    /** Alle vorhandenen Exporte (ohne Dateiendung). */
    public static java.util.List<String> listExports() {
        java.util.List<String> out = new ArrayList<>();
        try {
            if (!Files.exists(exportDir())) return out;
            try (var stream = Files.list(exportDir())) {
                stream.forEach(p -> {
                    String fn = p.getFileName().toString();
                    if (fn.endsWith(".txt")) out.add(fn.substring(0, fn.length() - 4));
                });
            }
            out.sort(String::compareToIgnoreCase);
        } catch (Throwable ignored) {
        }
        return out;
    }

    /** Entfernt alles, was in Dateinamen Probleme macht. */
    private static String sanitize(String name) {
        if (name == null) return "";
        StringBuilder sb = new StringBuilder();
        for (char c : name.trim().toCharArray()) {
            if (Character.isLetterOrDigit(c) || c == '-' || c == '_' || c == ' ') {
                sb.append(c == ' ' ? '_' : c);
            }
        }
        return sb.toString();
    }

    /** Beim Start: zuletzt aktives Preset ermitteln (vor dem Laden aufrufen). */
    public static void loadActivePreset() {
        try {
            Path f = activeFile();
            if (!Files.exists(f)) return;
            String txt = Files.readString(f).trim();
            int i = Integer.parseInt(txt);
            if (i >= 0 && i < PRESET_COUNT) activePreset = i;
        } catch (Throwable ignored) {
            // Unlesbar -> beim ersten Preset bleiben.
        }
    }

    /** Alle aktuellen Einstellungen in die Datei schreiben. */
    public static void save() {
        try {
            Path file = configFile();
            Files.createDirectories(file.getParent());

            List<String> lines = new ArrayList<>();
            for (Module m : ModuleManager.INSTANCE.getModules()) {
                for (Setting s : m.getSettings()) {
                    // ModulName \t SettingName \t serialisierterWert
                    String line = m.getName() + "\t" + s.getName() + "\t" + s.serialize();
                    lines.add(line);
                }
            }
            // Zusatzlisten generisch: jedes Modul mit ExtraData liefert
            // Schluessel und Inhalt selbst. Vorher stand hier je Modul ein
            // eigener Zweig -- damit war der Kern an konkrete Module gebunden.
            for (Module m : ModuleManager.INSTANCE.getModules()) {
                if (m instanceof com.vortex.client.module.ExtraData ed) {
                    lines.add(m.getName() + "\t" + ed.extraKey() + "\t" + ed.serializeExtra());
                }
            }

            // Einstellungen des Waypoint-Systems (kein Modul, eigener Bereich).
            for (Setting ws : com.vortex.client.waypoint.WaypointSettings.INSTANCE.getSettings()) {
                lines.add("__wpsettings__\t" + ws.getName() + "\t" + ws.serialize());
            }

            // Makros.
            lines.add("__macros__\tdaten\t" + com.vortex.client.macro.MacroManager.serialize());

            // Weltprofile (fuer Netzwerke mit Proxy).
            lines.add("__wpprofiles__\tdaten\t"
                    + com.vortex.client.waypoint.WorldProfiles.serialize());

            // Waypoints mitspeichern (gehoeren zum jeweiligen Preset).
            lines.add("__waypoints__\tliste\t"
                    + com.vortex.client.waypoint.WaypointManager.serialize());

            // Favoriten und Fensterposition des ClickGUI mitspeichern.
            lines.add("__gui__\tfavoriten\t"
                    + com.vortex.client.gui.GuiState.serializeFavorites());
            lines.add("__gui__\tfenster\t"
                    + com.vortex.client.gui.GuiState.serializeWindow());

            // Farbschema mitspeichern (Pseudo-Modul "__theme__"), damit die
            // gewaehlten Farben einen Neustart ueberleben.
            for (com.vortex.client.core.setting.ColorSetting c
                    : com.vortex.client.gui.Theme.INSTANCE.all()) {
                lines.add("__theme__\t" + c.getName() + "\t" + c.serialize());
            }
            // Deckkraft ist eine Zahl, keine Farbe -- eigene Zeile.
            lines.add("__theme__\t" + com.vortex.client.gui.Theme.INSTANCE.opacity.getName()
                    + "\t" + com.vortex.client.gui.Theme.INSTANCE.opacity.serialize());

            // Fremde Zeilen unveraendert zurueckschreiben.
            for (String fremd : FREMDE_ZEILEN) {
                lines.add(fremd);
            }

            // AUFTEILEN: Mod-Einstellungen ins Preset, alles andere (Makros,
            // Waypoints, Freunde, Tasten, Design) in global.txt -- gilt fuer
            // alle Presets gleich.
            List<String> presetZeilen = new ArrayList<>();
            List<String> globalZeilen = new ArrayList<>();
            for (String z : lines) {
                if (istGlobal(z)) globalZeilen.add(z);
                else presetZeilen.add(z);
            }
            if (ladenFehlgeschlagen) {
                System.out.println("[vortexclient] Not saving: loading failed earlier, files are kept as they are.");
                return;
            }
            sicherSchreiben(file, presetZeilen);
            sicherSchreiben(globalFile(), globalZeilen);
        } catch (IOException | RuntimeException e) {
            // Speichern soll das Spiel nie crashen lassen.
            System.out.println("[vortexclient] Could not save config: " + e.getMessage());
        }
    }

    /** Einstellungen aus der Datei laden und auf die Module anwenden. */
    /**
     * Zeilen, zu denen es gerade kein Modul gibt.
     *
     * Die Cheat-Module liegen im Addon. Ohne Addon kennt der Client deren
     * Einstellungen nicht -- wuerde er sie beim Speichern weglassen, waeren
     * sie beim naechsten Start mit Addon unwiderruflich weg.
     */
    private static final List<String> FREMDE_ZEILEN = new ArrayList<>();

    public static void load() {
        try {
            Path file = configFile();
            if (!Files.exists(globalFile())) migriereGlobal();
            boolean global = Files.exists(globalFile());
            if (!Files.exists(file) && !global) {
                return; // Noch keine Config -> Defaults behalten.
            }

            // Preset (nur Mods) + global.txt (alles andere). Aeltere Preset-
            // Dateien enthalten noch Makros, Waypoints usw. -- die werden
            // ignoriert, sobald es global.txt gibt. Ohne global.txt (erster
            // Start nach dem Update) gilt das Preset komplett; das erste
            // Speichern teilt es dann auf.
            List<String> lines = new ArrayList<>();
            if (Files.exists(file)) {
                for (String z : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                    if (global && istGlobal(z)) continue;
                    lines.add(z);
                }
            }
            if (global) lines.addAll(Files.readAllLines(globalFile(), StandardCharsets.UTF_8));
            // Vor jedem Laden leeren, sonst wandern fremde Zeilen beim
            // Preset-Wechsel von einem Preset ins andere.
            FREMDE_ZEILEN.clear();
            int unknown = 0;   // Zeilen ohne passendes Modul/Setting
            for (String line : lines) {
                if (line.isBlank()) continue;
                // In drei Teile zerlegen: ModulName, SettingName, Wert.
                // limit=3, damit ein Wert selbst Tabs enthalten duerfte.
                String[] parts = line.split("\t", 3);
                if (parts.length < 3) continue;

                String modName = parts[0];
                String settingName = parts[1];
                String value = parts[2];

                // Beschreibungszeilen geteilter Presets: nur Information.
                if (modName.equals("__meta__")) continue;

                // Zusatzlisten generisch: an den Schluessel gebunden, nicht an
                // die Klasse. Funktioniert damit auch fuer Addon-Module.
                if (settingName.startsWith("__") && settingName.endsWith("__")
                        && !modName.startsWith("__")) {
                    boolean behandelt = false;
                    for (Module m : ModuleManager.INSTANCE.getModules()) {
                        if (m.getName().equals(modName)
                                && m instanceof com.vortex.client.module.ExtraData ed
                                && ed.extraKey().equals(settingName)) {
                            ed.deserializeExtra(value);
                            behandelt = true;
                            break;
                        }
                    }
                    if (behandelt) continue;
                    FREMDE_ZEILEN.add(line);
                    unknown++;
                    continue;
                }

                // Sonderfall: Waypoint-Einstellungen.
                if (modName.equals("__wpsettings__")) {
                    for (Setting ws : com.vortex.client.waypoint.WaypointSettings
                            .INSTANCE.getSettings()) {
                        if (ws.getName().equals(settingName)) {
                            ws.deserialize(value);
                            break;
                        }
                    }
                    continue;
                }

                // Sonderfall: Makros.
                if (modName.equals("__macros__")) {
                    com.vortex.client.macro.MacroManager.deserialize(value);
                    continue;
                }

                // Sonderfall: Weltprofile.
                if (modName.equals("__wpprofiles__")) {
                    com.vortex.client.waypoint.WorldProfiles.deserialize(value);
                    continue;
                }

                // Sonderfall: Waypoints.
                if (modName.equals("__waypoints__")) {
                    com.vortex.client.waypoint.WaypointManager.deserialize(value);
                    continue;
                }

                // Sonderfall: Favoriten / Fensterposition.
                if (modName.equals("__gui__")) {
                    if (settingName.equals("favoriten")) {
                        com.vortex.client.gui.GuiState.deserializeFavorites(value);
                    } else if (settingName.equals("fenster")) {
                        com.vortex.client.gui.GuiState.deserializeWindow(value);
                    }
                    continue;
                }

                // Sonderfall: Farbschema.
                if (modName.equals("__theme__")) {
                    if (settingName.equals(
                            com.vortex.client.gui.Theme.INSTANCE.opacity.getName())) {
                        com.vortex.client.gui.Theme.INSTANCE.opacity.deserialize(value);
                        continue;
                    }
                    for (com.vortex.client.core.setting.ColorSetting c
                            : com.vortex.client.gui.Theme.INSTANCE.all()) {
                        if (c.getName().equals(settingName)) {
                            c.deserialize(value);
                            break;
                        }
                    }
                    continue;
                }




                Setting target = findSetting(modName, settingName);
                if (target != null) {
                    target.deserialize(value);
                } else {
                    // Gehoert zu einem Modul, das hier nicht geladen ist --
                    // typischerweise ein Cheat aus dem Addon. Wortwoertlich
                    // aufbewahren statt verwerfen.
                    FREMDE_ZEILEN.add(line);
                    unknown++;
                }
            }
            // WICHTIG: Die Werte sind jetzt gesetzt -- aber damit ist noch nichts
            // passiert. Module, die ihre Wirkung ueber onEnable()/onDisable()
            // entfalten, muessen den geladenen Zustand aktiv anwenden. Ohne
            // diesen Schritt aendert sich beim Preset-Wechsel zwar der Wert,
            // aber nicht das Verhalten -- genau das war der Fehler.
            int applied = 0;
            for (Module m : ModuleManager.INSTANCE.getModules()) {
                try {
                    m.syncState();
                    applied++;
                } catch (Throwable pvpErr) {
                    Errors.report("ConfigManager.syncState:" + m.getName(), pvpErr);
                }
            }
            Errors.note("ConfigManager.load",
                    "Preset " + (activePreset + 1) + " loaded: " + lines.size()
                    + " lines, " + unknown + " unknown, "
                    + applied + " modules applied");
            ladenFehlgeschlagen = false;
        } catch (IOException | RuntimeException e) {
            ladenFehlgeschlagen = true;
            System.out.println("[vortexclient] Could not load config: " + e.getMessage());
        }
    }

    /** Findet ein Setting anhand von Modul- und Settingname. */
    private static Setting findSetting(String modName, String settingName) {
        Setting direkt = suche(modName, settingName);
        if (direkt != null) return direkt;

        // Nicht gefunden? Dann stammt die Zeile vermutlich aus einer aelteren
        // Fassung mit deutschen Namen -- ueber die Tabelle nochmal versuchen.
        String m2 = LegacyNames.module(modName);
        String s2 = LegacyNames.setting(settingName);
        if (!m2.equals(modName) || !s2.equals(settingName)) {
            return suche(m2, s2);
        }
        return null;
    }

    private static Setting suche(String modName, String settingName) {
        for (Module m : ModuleManager.INSTANCE.getModules()) {
            if (!m.getName().equals(modName)) continue;
            for (Setting s : m.getSettings()) {
                if (s.getName().equals(settingName)) {
                    return s;
                }
            }
        }
        return null;
    }
}
