package com.vortex.client.waypoint;

import com.vortex.client.core.setting.BooleanSetting;
import com.vortex.client.core.setting.ColorSetting;
import com.vortex.client.core.setting.KeySetting;
import com.vortex.client.core.setting.NumberSetting;
import com.vortex.client.core.setting.Setting;

import java.util.List;

/**
 * Einstellungen des Waypoint-Systems.
 *
 * Bewusst KEIN Modul: Waypoints sind kein An/Aus-Schalter unter vielen, sondern
 * ein eigenstaendiger Bereich mit eigener Verwaltung. Sie stehen deshalb im
 * Hauptmenue des Clients und nicht in der Modul-Liste.
 *
 * Die Einstellungen bleiben trotzdem gewoehnliche Setting-Objekte -- damit
 * funktionieren die vorhandenen Bedienelemente (Schieberegler, Schalter) und das
 * Speichern unveraendert weiter.
 */
public final class WaypointSettings {

    public static final WaypointSettings INSTANCE = new WaypointSettings();

    /** Waypoints ueberhaupt anzeigen. */
    public final BooleanSetting enabled = new BooleanSetting("Anzeigen", true);

    /** Linienbreite der Tracer. */
    public final NumberSetting lineWidth = new NumberSetting("Tracer-Breite", 2.0, 0.5, 5.0, 0.5);

    // ---- Markierte Bloecke -------------------------------------------------

    /** Linienbreite der Block-Umrandungen. */
    public final NumberSetting blockLineWidth =
            new NumberSetting("Block-Linienbreite", 2.0, 0.5, 6.0, 0.5);

    /**
     * Farbe der Block-Umrandungen. Bei 0 wird die Farbe des jeweiligen Markers
     * benutzt -- so bleiben mehrere Gruppen auseinanderzuhalten.
     */
    public final ColorSetting blockColor = new ColorSetting("Block-Farbe", 0x00000000);

    /** Ab welcher Entfernung nicht mehr gezeichnet wird (0 = unbegrenzt). */
    public final NumberSetting maxDistance = new NumberSetting("Max. Distanz", 0, 0, 2000, 50);

    /** Beschriftung mit Name und Entfernung am Bildschirm. */
    public final BooleanSetting labels = new BooleanSetting("Beschriftung", true);
    /** Pfeile am Bildschirmrand fuer Marker ausserhalb des Blickfelds. */
    public final BooleanSetting edgeArrows = new BooleanSetting("Randpfeile", true);
    /** Linie vom Fadenkreuz zum Marker. */
    public final BooleanSetting tracers = new BooleanSetting("Tracer", false);

    // ---- Aussehen der Punkte ---------------------------------------------

    /** Kantenlaenge des Punktes in Pixeln. */
    public final NumberSetting dotSize = new NumberSetting("Ringgroesse", 6, 4, 24, 1);

    /** Dicke des Rings in Pixeln. */
    public final NumberSetting borderWidth = new NumberSetting("Ringdicke", 1, 1, 4, 1);

    /**
     * Deckkraft der Marker (1.0 = voll, 0.2 = stark durchsichtig).
     *
     * Beim Anvisieren wird automatisch zur vollen Deckkraft aufgeblendet -- im
     * Ruhezustand bleibt die Anzeige dezent, beim Anpeilen ist sie klar da.
     */
    public final NumberSetting markerOpacity =
            new NumberSetting("Deckkraft", 0.7, 0.2, 1.0, 0.05);
    public final ColorSetting borderColor = new ColorSetting("Randfarbe", 0xC0000000);

    /** Farbe des Buchstabens. Bei 0 wird automatisch hell/dunkel gewaehlt. */
    public final ColorSetting letterColor = new ColorSetting("Buchstabenfarbe", 0x00000000);

    /** Buchstaben ueberhaupt anzeigen. */
    public final BooleanSetting showLetter = new BooleanSetting("Buchstabe", true);

    /**
     * Name und Entfernung in der Aktionsleiste ueber dem Inventar anzeigen,
     * statt als Text neben dem Punkt. Ruhiger und besser lesbar.
     */
    public final BooleanSetting useActionBar = new BooleanSetting("Info in Aktionsleiste", true);

    /** Ab welcher Entfernung markierte Bloecke eingeblendet werden. */
    public final NumberSetting blockRadius =
            new NumberSetting("Block-Sichtweite", 50, 8, 200, 2);

    /** Marker ausblenden, sobald man nah genug dran ist (0 = nie). */
    public final NumberSetting hideNear = new NumberSetting("Ausblenden ab naeher als", 0, 0, 32, 2);

    /** Naechsten Marker als Zeile im Bild anzeigen. */
    public final BooleanSetting showNearest = new BooleanSetting("Naechsten anzeigen", true);

    /** Beim Sterben automatisch einen Todespunkt setzen. */
    public final BooleanSetting deathWaypoint = new BooleanSetting("Todespunkt automatisch", true);

    // ---- Tastenbelegungen -------------------------------------------------
    //
    // Bewusst standardmaessig NICHT belegt (Taste "Unbekannt"): sonst wuerden
    // Tasten belegt, die man vielleicht anders nutzt. Einmal im Menue
    // zuweisen genuegt.

    /** Marker an der aktuellen Position setzen. */
    public final KeySetting keyAddHere =
            new KeySetting("Taste: Hier setzen", org.lwjgl.glfw.GLFW.GLFW_KEY_UNKNOWN);

    /** Den Block markieren, auf den das Fadenkreuz zeigt. */
    public final KeySetting keyMarkBlock =
            new KeySetting("Taste: Block markieren", org.lwjgl.glfw.GLFW.GLFW_KEY_UNKNOWN);

    /** Anzeige der Marker an- und ausschalten. */
    public final KeySetting keyToggle =
            new KeySetting("Taste: Anzeige umschalten", org.lwjgl.glfw.GLFW.GLFW_KEY_UNKNOWN);

    /**
     * Zweite Ecke fuer eine Bereichsmarkierung.
     *
     * Erster Druck merkt sich die Ecke, zweiter Druck fuellt alles dazwischen.
     * Damit markiert man ein 4x4-Feld mit zwei Tastendruecken statt sechzehn.
     */
    public final KeySetting keyMarkArea =
            new KeySetting("Taste: Bereich markieren", org.lwjgl.glfw.GLFW.GLFW_KEY_UNKNOWN);

    /** Verwaltung oeffnen. */
    public final KeySetting keyManage =
            new KeySetting("Taste: Verwaltung", org.lwjgl.glfw.GLFW.GLFW_KEY_UNKNOWN);

    private final List<Setting> settings;

    private WaypointSettings() {
        settings = List.of(enabled, lineWidth, maxDistance,
                labels, edgeArrows, tracers,
                dotSize, borderWidth, markerOpacity, borderColor, letterColor,
                showLetter, useActionBar,
                blockRadius, blockLineWidth, blockColor, hideNear, showNearest,
                deathWaypoint,
                keyAddHere, keyMarkBlock, keyMarkArea, keyToggle, keyManage);
        // Ausgangswerte festhalten, damit ein frisches Preset zurueck kann.
        for (Setting s : settings) {
            s.rememberDefault();
        }
    }

    public List<Setting> getSettings() {
        return settings;
    }

    public boolean isEnabled() {
        return enabled.get();
    }

    /** Auf Auslieferungszustand zuruecksetzen. */
    public void resetDefaults() {
        for (Setting s : settings) {
            s.resetToDefault();
        }
    }
}
