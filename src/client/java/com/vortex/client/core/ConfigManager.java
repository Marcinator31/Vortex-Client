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
                    Errors.note("ConfigManager", "Alte Einstellungen uebernommen.");
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

    /** Merkt sich, welches Preset zuletzt aktiv war. */
    private static Path activeFile() {
        return dir().resolve("aktiv.txt");
    }

    public static int getActivePreset() {
        return activePreset;
    }

    /** Anzeigename eines Presets. */
    public static String presetName(int index) {
        return "Preset " + (index + 1);
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
            resetAll();
            save();
        }
    }

    /**
     * Setzt alles auf den Auslieferungszustand zurueck: alle Modul-
     * Einstellungen, die Auswahllisten (Mobs, Bloecke, Entities), das
     * Farbschema, Favoriten, Fensterposition und die Marker.
     */
    public static void resetAll() {
        for (Module m : ModuleManager.INSTANCE.getModules()) {
            try {
                for (Setting st : m.getSettings()) {
                    st.resetToDefault();
                }
                // Auswahllisten leeren -- die haben keinen "Wert" im obigen Sinn.
                if (m instanceof com.vortex.client.module.modules.EspModule esp) {
                    esp.getEnabledMobs().clear();
                }
                if (m instanceof com.vortex.client.module.modules.BlockEspModule besp) {
                    besp.getEnabledBlocks().clear();
                }
                if (m instanceof com.vortex.client.module.modules.AntiRenderModule ar) {
                    for (String id : new ArrayList<>(ar.getHiddenTypes())) {
                        ar.set(id, false);
                    }
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
        try {
            String safe = sanitize(name);
            if (safe.isEmpty()) safe = "preset";
            Files.createDirectories(exportDir());
            Path target = exportDir().resolve(safe + ".txt");
            // Erst speichern, damit auch ungespeicherte Aenderungen mitgehen.
            save();
            Files.copy(configFile(), target,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return target;
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Liest eine Datei aus dem Export-Ordner in das aktive Preset ein.
     * Rueckgabe: true bei Erfolg.
     */
    public static boolean importPreset(String name) {
        try {
            String safe = sanitize(name);
            Path source = exportDir().resolve(safe + ".txt");
            if (!Files.exists(source)) return false;
            Files.createDirectories(dir());
            Files.copy(source, configFile(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            load();   // sofort anwenden
            return true;
        } catch (Throwable t) {
            return false;
        }
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
            // ESP-Modul: aktive Mobs als Sonder-Zeile (Setting-Name __mobs__).
            for (Module m : ModuleManager.INSTANCE.getModules()) {
                if (m instanceof com.vortex.client.module.modules.EspModule esp) {
                    lines.add(m.getName() + "\t__mobs__\t" + esp.serializeMobs());
                }
                if (m instanceof com.vortex.client.module.modules.BlockEspModule besp) {
                    lines.add(m.getName() + "\t__blocks__\t" + besp.serializeBlocks());
                }
                if (m instanceof com.vortex.client.module.modules.AntiRenderModule ar) {
                    lines.add(m.getName() + "\t__antirender__\t" + ar.serialize());
                }
            }

            // Einstellungen des Waypoint-Systems (kein Modul, eigener Bereich).
            for (Setting ws : com.vortex.client.waypoint.WaypointSettings.INSTANCE.getSettings()) {
                lines.add("__wpsettings__\t" + ws.getName() + "\t" + ws.serialize());
            }

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

            Files.write(file, lines, StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException e) {
            // Speichern soll das Spiel nie crashen lassen.
            System.out.println("[pvpclient] Konnte Config nicht speichern: " + e.getMessage());
        }
    }

    /** Einstellungen aus der Datei laden und auf die Module anwenden. */
    public static void load() {
        try {
            Path file = configFile();
            if (!Files.exists(file)) {
                return; // Noch keine Config -> Defaults behalten.
            }

            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
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

                // Sonderfall: ESP-Mob-Liste.
                if (settingName.equals("__mobs__")) {
                    for (Module m : ModuleManager.INSTANCE.getModules()) {
                        if (m.getName().equals(modName)
                                && m instanceof com.vortex.client.module.modules.EspModule esp) {
                            esp.deserializeMobs(value);
                        }
                    }
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

                // Sonderfall: Block-ESP-Liste.
                if (settingName.equals("__blocks__")) {
                    for (Module m : ModuleManager.INSTANCE.getModules()) {
                        if (m.getName().equals(modName)
                                && m instanceof com.vortex.client.module.modules.BlockEspModule besp) {
                            besp.deserializeBlocks(value);
                        }
                    }
                    continue;
                }
                // Sonderfall: Anti-Render-Liste.
                if (settingName.equals("__antirender__")) {
                    for (Module m : ModuleManager.INSTANCE.getModules()) {
                        if (m.getName().equals(modName)
                                && m instanceof com.vortex.client.module.modules.AntiRenderModule ar) {
                            ar.deserialize(value);
                        }
                    }
                    continue;
                }

                Setting target = findSetting(modName, settingName);
                if (target != null) {
                    target.deserialize(value);
                } else {
                    // Zeile gehoert zu einem Modul/Setting, das es nicht (mehr)
                    // gibt. Kein Fehler, aber gut zu wissen.
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
                    "Preset " + (activePreset + 1) + " geladen: " + lines.size()
                    + " Zeilen, " + unknown + " unbekannt, "
                    + applied + " Module angewendet");
        } catch (IOException | RuntimeException e) {
            System.out.println("[pvpclient] Konnte Config nicht laden: " + e.getMessage());
        }
    }

    /** Findet ein Setting anhand von Modul- und Settingname. */
    private static Setting findSetting(String modName, String settingName) {
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
