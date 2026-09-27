package com.vortex.client.hud;

import com.vortex.client.core.setting.BooleanSetting;
import com.vortex.client.core.setting.ColorSetting;
import com.vortex.client.core.setting.ModeSetting;
import com.vortex.client.core.setting.NumberSetting;
import com.vortex.client.core.setting.Setting;
import java.util.function.Consumer;

/**
 * Gemeinsame Aussehen-Einstellungen fuer die Text-HUD-Elemente (neu in 4.6.0).
 *
 * Jedes Text-Element (FPS, CPS, Ping, Koordinaten, TPS, Uhr ...) bekommt
 * dieselben Wahlmoeglichkeiten -- einmal hier gebaut, statt in jedem Modul
 * einzeln (und dann jedes Mal ein bisschen anders).
 *
 *   Text Shadow           Schatten unter der Schrift
 *   Text Case             Normal / UPPERCASE / lowercase
 *   Label Style           Default / Label: Value / Value Label / Value Only
 *   Brackets              [ ... ] um den Text
 *   Separate Label Color  Beschriftung ("FPS", "Ping") in eigener Farbe
 *   Label Color           ... diese Farbe (auch als Verlauf)
 *   Background            Kasten hinter dem Text
 *   Background Color      ... in dieser Farbe (auch als Verlauf)
 *   Border                None / Left Bar / Underline / Outline
 *   Border Color          ... in dieser Farbe (Standard: Vortex-Verlauf)
 *   Padding               Innenabstand von Kasten/Rahmen
 *
 * Die Namen der Einstellungen sind in allen Modulen gleich -- so lassen sie
 * sich auch ueber Presets einheitlich speichern.
 */
public final class HudStyle {

    public static final int FORM_DEFAULT = 0, FORM_LABEL_FIRST = 1, FORM_LABEL_LAST = 2, FORM_VALUE_ONLY = 3;

    public final BooleanSetting shadow = new BooleanSetting("Text Shadow", true);
    public final ModeSetting textCase = new ModeSetting("Text Case", 0, "Normal", "UPPERCASE", "lowercase");
    public final ModeSetting labelStyle =
            new ModeSetting("Label Style", 0, "Default", "Label: Value", "Value Label", "Value Only");
    public final BooleanSetting brackets = new BooleanSetting("Brackets", false);
    public final BooleanSetting ownLabelColor = new BooleanSetting("Separate Label Color", false);
    public final ColorSetting labelColor = new ColorSetting("Label Color", 0xFFAAAAB4);
    public final BooleanSetting background = new BooleanSetting("Background", false);
    public final ColorSetting backgroundColor =
            new ColorSetting("Background Color", 0x90101018, 0x902A1B4D, ColorSetting.SOLID);
    public final ModeSetting border = new ModeSetting("Border", 0, "None", "Left Bar", "Underline", "Outline");
    public final ColorSetting borderColor =
            new ColorSetting("Border Color", 0xFF8B5CF6, 0xFF3B82F6, ColorSetting.GRADIENT);
    public final NumberSetting padding = new NumberSetting("Padding", 3, 0, 8, 1);

    /** Hat das Element eine Beschriftung, die sich umstellen laesst? */
    private final boolean mitBeschriftung;

    // Zuletzt gezeichnete Groesse (Bildschirmpixel, mit Skalierung) -- fuer
    // den Kasten im HUD-Editor.
    int letzteB = -1, letzteH = -1;

    public HudStyle(boolean mitBeschriftung) {
        this.mitBeschriftung = mitBeschriftung;
    }

    /** Meldet alle Einstellungen beim Modul an (in sinnvoller Reihenfolge). */
    public void addTo(Consumer<Setting> add) {
        add.accept(shadow);
        add.accept(textCase);
        if (mitBeschriftung) {
            add.accept(labelStyle);
            add.accept(brackets);
            add.accept(ownLabelColor);
            add.accept(labelColor);
        } else {
            add.accept(brackets);
        }
        add.accept(background);
        add.accept(backgroundColor);
        add.accept(border);
        add.accept(borderColor);
        add.accept(padding);
    }

    public boolean hatBeschriftung() {
        return mitBeschriftung;
    }

    /** Hat das Element einen Kasten oder Rahmen (dann gilt der Innenabstand)? */
    public boolean hatRahmen() {
        return background.get() || border.getIndex() != 0;
    }

    /** Breite fuer den HUD-Editor: zuletzt gezeichnet, sonst der Ersatzwert. */
    public int breite(int ersatz) {
        return letzteB > 0 ? letzteB : ersatz;
    }

    public int hoehe(int ersatz) {
        return letzteH > 0 ? letzteH : ersatz;
    }
}
