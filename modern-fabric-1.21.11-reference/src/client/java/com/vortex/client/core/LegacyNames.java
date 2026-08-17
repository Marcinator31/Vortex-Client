package com.vortex.client.core;

import java.util.HashMap;
import java.util.Map;

/**
 * Translates old German config keys to the current English ones.
 *
 * WHY THIS EXISTS: Module and setting names are also the keys used in the
 * config file. Renaming them for the English release would silently invalidate
 * every existing preset -- all modules would fall back to their defaults, and
 * it would look like the settings had simply been lost.
 *
 * This table maps the old names to the new ones while loading. It only ever
 * runs on entries that no longer match anything, so it costs nothing in normal
 * operation and can be removed once no old configs remain in the wild.
 */
public final class LegacyNames {

    private static final Map<String, String> MODULE_NAMEN = new HashMap<>();
    private static final Map<String, String> EINSTELLUNGEN = new HashMap<>();

    static {
        MODULE_NAMEN.put("Chunk-Grenzen", "Chunk Borders");
        MODULE_NAMEN.put("HUD-Farbe", "HUD Color");
        MODULE_NAMEN.put("Item-Groesse Hand", "Hand Item Size");
        MODULE_NAMEN.put("Klarsicht Lava", "Clear Lava");
        MODULE_NAMEN.put("Klarsicht Wasser", "Clear Water");
        MODULE_NAMEN.put("Projektil-Flugbahn", "Projectile Path");
        MODULE_NAMEN.put("Ziel-Info", "Target Info");
        EINSTELLUNGEN.put("Ab Hoehe", "Min Fall Height");
        EINSTELLUNGEN.put("Aktiviert", "Enabled");
        EINSTELLUNGEN.put("Akzent", "Accent");
        EINSTELLUNGEN.put("An-Farbe", "Enabled Color");
        EINSTELLUNGEN.put("Anzeige", "Display");
        EINSTELLUNGEN.put("Anzeigen", "Show");
        EINSTELLUNGEN.put("Auch Grundgestein", "Include Bedrock");
        EINSTELLUNGEN.put("Aus-Farbe", "Disabled Color");
        EINSTELLUNGEN.put("Ausblenden ab naeher als", "Hide When Closer Than");
        EINSTELLUNGEN.put("Ausser Reichweite", "Out of Range");
        EINSTELLUNGEN.put("Beschriftung", "Labels");
        EINSTELLUNGEN.put("Bis Hoehe", "Max Height");
        EINSTELLUNGEN.put("Block-Farbe", "Block Color");
        EINSTELLUNGEN.put("Block-Linienbreite", "Block Line Width");
        EINSTELLUNGEN.put("Block-Sichtweite", "Block View Distance");
        EINSTELLUNGEN.put("Bogen/Armbrust", "Bow / Crossbow");
        EINSTELLUNGEN.put("Brust X", "Chestplate X");
        EINSTELLUNGEN.put("Brust Y", "Chestplate Y");
        EINSTELLUNGEN.put("Buchstabe", "Letter");
        EINSTELLUNGEN.put("Chat-Meldung", "Chat Message");
        EINSTELLUNGEN.put("Deckkraft", "Opacity");
        EINSTELLUNGEN.put("Eigene Totems", "Own Totems");
        EINSTELLUNGEN.put("Farbe", "Color");
        EINSTELLUNGEN.put("Feindliche", "Hostile");
        EINSTELLUNGEN.put("Food-Vorschau", "Food Preview");
        EINSTELLUNGEN.put("Gebrochen ausgrauen", "Grey Out Broken");
        EINSTELLUNGEN.put("Gegner anzeigen", "Show Enemies");
        EINSTELLUNGEN.put("Gegner-Farbe", "Enemy Color");
        EINSTELLUNGEN.put("Gegner-Schilde", "Enemy Shields");
        EINSTELLUNGEN.put("Geschwindigkeit", "Speed");
        EINSTELLUNGEN.put("Glaettung", "Smoothing");
        EINSTELLUNGEN.put("Glow-Farbe", "Glow Color");
        EINSTELLUNGEN.put("Groesse", "Size");
        EINSTELLUNGEN.put("Haltbarkeit", "Durability");
        EINSTELLUNGEN.put("Health-Vorschau", "Health Preview");
        EINSTELLUNGEN.put("Helm X", "Helmet X");
        EINSTELLUNGEN.put("Helm Y", "Helmet Y");
        EINSTELLUNGEN.put("Hervorhebung", "Highlight");
        EINSTELLUNGEN.put("Hintergrund", "Background");
        EINSTELLUNGEN.put("Hoechste CPS", "Best CPS");
        EINSTELLUNGEN.put("Hoehe", "Height");
        EINSTELLUNGEN.put("Hose X", "Leggings X");
        EINSTELLUNGEN.put("Hose Y", "Leggings Y");
        EINSTELLUNGEN.put("In Reichweite", "In Range");
        EINSTELLUNGEN.put("Info in Aktionsleiste", "Info in Action Bar");
        EINSTELLUNGEN.put("Jetzt anwenden", "Apply Now");
        EINSTELLUNGEN.put("Keine Explosions-Partikel", "No Explosion Particles");
        EINSTELLUNGEN.put("Keine Totem-Partikel", "No Totem Particles");
        EINSTELLUNGEN.put("Landepunkt", "Landing Marker");
        EINSTELLUNGEN.put("Leertaste", "Spacebar");
        EINSTELLUNGEN.put("Linienbreite", "Line Width");
        EINSTELLUNGEN.put("Maustasten", "Mouse Buttons");
        EINSTELLUNGEN.put("Max Drehung/Tick", "Max Turn / Tick");
        EINSTELLUNGEN.put("Max-Score", "Max Score");
        EINSTELLUNGEN.put("Max. Distanz", "Max Distance");
        EINSTELLUNGEN.put("Max. Eintraege", "Max Entries");
        EINSTELLUNGEN.put("Min. Aufladung", "Min Charge");
        EINSTELLUNGEN.put("Mindest-Score", "Min Score");
        EINSTELLUNGEN.put("Mindestlaenge", "Min Length");
        EINSTELLUNGEN.put("Monster", "Monsters");
        EINSTELLUNGEN.put("Nachbar-Chunks", "Neighbour Chunks");
        EINSTELLUNGEN.put("Naechsten anzeigen", "Show Nearest");
        EINSTELLUNGEN.put("Nebenhand", "Off Hand");
        EINSTELLUNGEN.put("Neue hervorheben", "Highlight New");
        EINSTELLUNGEN.put("Nur Spieler", "Players Only");
        EINSTELLUNGEN.put("Nur bei Angriff", "Only While Attacking");
        EINSTELLUNGEN.put("Nur bei gedrueckter Taste", "Only While Key Held");
        EINSTELLUNGEN.put("Nur freiliegende", "Exposed Only");
        EINSTELLUNGEN.put("Rahmenfarbe", "Frame Color");
        EINSTELLUNGEN.put("Randfarbe", "Border Color");
        EINSTELLUNGEN.put("Randpfeile", "Edge Arrows");
        EINSTELLUNGEN.put("Reichweite", "Range");
        EINSTELLUNGEN.put("Reichweite (Bloecke)", "Range (Blocks)");
        EINSTELLUNGEN.put("Render-Anker", "Render Anchor");
        EINSTELLUNGEN.put("Render-Distanz", "Render Distance");
        EINSTELLUNGEN.put("Ringdicke", "Ring Thickness");
        EINSTELLUNGEN.put("Ringgroesse", "Ring Size");
        EINSTELLUNGEN.put("Ruestung zeigen", "Show Armor");
        EINSTELLUNGEN.put("Schuhe X", "Boots X");
        EINSTELLUNGEN.put("Schuhe Y", "Boots Y");
        EINSTELLUNGEN.put("Schweben nach Aus", "Hover After Disable");
        EINSTELLUNGEN.put("Schwelle", "Threshold");
        EINSTELLUNGEN.put("Skalierung", "Scale");
        EINSTELLUNGEN.put("Sofort sprengen", "Break Instantly");
        EINSTELLUNGEN.put("Sonstige anzeigen", "Show Others");
        EINSTELLUNGEN.put("Sonstige-Farbe", "Other Color");
        EINSTELLUNGEN.put("Spieler", "Players");
        EINSTELLUNGEN.put("Spieler anzeigen", "Show Players");
        EINSTELLUNGEN.put("Spieler immer zeigen", "Always Show Players");
        EINSTELLUNGEN.put("Spieler-Details", "Player Details");
        EINSTELLUNGEN.put("Spieler-Farbe", "Player Color");
        EINSTELLUNGEN.put("Spielzeit", "Playtime");
        EINSTELLUNGEN.put("Spreng-Reichweite", "Break Range");
        EINSTELLUNGEN.put("Sprint-Faktor", "Sprint Multiplier");
        EINSTELLUNGEN.put("Staerke", "Strength");
        EINSTELLUNGEN.put("Taste", "Key");
        EINSTELLUNGEN.put("Taste gedrueckt", "Key Pressed");
        EINSTELLUNGEN.put("Taste normal", "Key Idle");
        EINSTELLUNGEN.put("Taste: Anzeige umschalten", "Key: Toggle Display");
        EINSTELLUNGEN.put("Taste: Bereich markieren", "Key: Mark Area");
        EINSTELLUNGEN.put("Taste: Block markieren", "Key: Mark Block");
        EINSTELLUNGEN.put("Taste: Hier setzen", "Key: Add Here");
        EINSTELLUNGEN.put("Taste: Verwaltung", "Key: Manage");
        EINSTELLUNGEN.put("Text gedimmt", "Text Dimmed");
        EINSTELLUNGEN.put("Textfarbe", "Text Color");
        EINSTELLUNGEN.put("Tier-Farbe", "Animal Color");
        EINSTELLUNGEN.put("Tiere", "Animals");
        EINSTELLUNGEN.put("Tiere anzeigen", "Show Animals");
        EINSTELLUNGEN.put("Tode", "Deaths");
        EINSTELLUNGEN.put("Todespunkt automatisch", "Auto Death Marker");
        EINSTELLUNGEN.put("Tracer", "Tracers");
        EINSTELLUNGEN.put("Tracer-Breite", "Tracer Width");
        EINSTELLUNGEN.put("Tracer-Farbe", "Tracer Color");
        EINSTELLUNGEN.put("Verzoegerung", "Delay");
        EINSTELLUNGEN.put("Von Hoehe", "Min Height");
        EINSTELLUNGEN.put("Vorausschau", "Prediction Steps");
        EINSTELLUNGEN.put("Waffe X", "Weapon X");
        EINSTELLUNGEN.put("Waffe Y", "Weapon Y");
        EINSTELLUNGEN.put("Weicher Farbverlauf", "Smooth Gradient");
        EINSTELLUNGEN.put("Zeichen-Reichweite", "Draw Distance");
        EINSTELLUNGEN.put("Ziel-Punkt", "Aim Point");
        EINSTELLUNGEN.put("Ziel-Wahl", "Target Priority");
        EINSTELLUNGEN.put("Zuruecksetzen (weiss)", "Reset (White)");
        EINSTELLUNGEN.put("Zurueckwechseln", "Switch Back");
    }

    private LegacyNames() {}

    /** English module name for an old German one (or the input unchanged). */
    public static String module(String name) {
        String neu = MODULE_NAMEN.get(name);
        return (neu == null) ? name : neu;
    }

    /** English setting name for an old German one (or the input unchanged). */
    public static String setting(String name) {
        String neu = EINSTELLUNGEN.get(name);
        return (neu == null) ? name : neu;
    }
}
