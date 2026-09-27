package com.vortex.client.module.modules;

import com.vortex.client.core.setting.BooleanSetting;
import com.vortex.client.core.setting.ColorSetting;
import com.vortex.client.core.setting.ModeSetting;
import com.vortex.client.core.setting.NumberSetting;
import com.vortex.client.module.Module;

/**
 * Eigenes Fadenkreuz -- Form, Farbe, Hitmarker, Angriffsanzeige.
 *
 * WARUM DAS ALTE NICHTS TAT: Es hing sich an "Gui.renderCrosshair". Diese
 * Methode gibt es in 26.2 nicht mehr (dort heisst es Hud.extractCrosshair),
 * und weil der Eingriff als optional markiert war, blieb er stumm -- man sah
 * immer das normale Fadenkreuz, egal was eingestellt war.
 *
 * EINSTELLUNGEN
 *   Form     Style, Size, Thickness, Gap, Dot Size
 *   Farbe    Colour (mit Deckkraft), Rainbow, Outline + Outline Colour
 *   Ziel     Target Colour: andere Farbe, wenn du auf ein Wesen / einen
 *            Spieler zielst
 *   Dynamik  Dynamic Gap: oeffnet sich beim Laufen, Springen und solange die
 *            Waffe noch laedt -- wie in Shootern
 *   Treffer  Hit Marker: kurzes X, wenn dein Schlag wirklich trifft
 *   Angriff  Attack Indicator: Bar, Ring oder Prozent, eigene Farbe, auf
 *            Wunsch immer sichtbar, kurzes Aufleuchten wenn voll.
 *            Seit 4.6.1 ausserdem: Position (unten/oben/links/rechts),
 *            Abstand, Laenge, Dicke, Farbe der leeren Spur, Hintergrund,
 *            Farbe je nach Aufladung (z. B. rot -> weiss), Textgroesse bei
 *            Prozent und "nur, wenn du auf ein Wesen zielst".
 */
public class CrosshairModule extends Module {

    // --- Form -------------------------------------------------------------
    public final ModeSetting style = new ModeSetting("Style", 1,
            "Cross", "Cross + Dot", "Dot", "Circle", "Circle + Dot", "T-Shape", "X", "Square", "Square + Dot");
    public final NumberSetting size = new NumberSetting("Size", 5, 1, 20, 1);
    public final NumberSetting thickness = new NumberSetting("Thickness", 1, 1, 5, 1);
    public final NumberSetting gap = new NumberSetting("Gap", 2, 0, 12, 1);
    public final NumberSetting dotSize = new NumberSetting("Dot Size", 1, 1, 4, 1);

    // --- Farbe ------------------------------------------------------------
    public final ColorSetting color = new ColorSetting("Colour", 0xFFFFFFFF);
    public final BooleanSetting rainbow = new BooleanSetting("Rainbow", false);
    public final BooleanSetting outline = new BooleanSetting("Outline", true);
    public final ColorSetting outlineColor = new ColorSetting("Outline Colour", 0xC0000000);

    // --- Ziel -------------------------------------------------------------
    public final ModeSetting targetMode = new ModeSetting("Target Colour", 2, "Off", "Any Entity", "Players Only");
    public final ColorSetting targetColor = new ColorSetting("Target Colour Value", 0xFFFF4D4D);

    // --- Dynamik ----------------------------------------------------------
    public final BooleanSetting dynamicGap = new BooleanSetting("Dynamic Gap", false);

    // --- Treffer ----------------------------------------------------------
    public final BooleanSetting hitMarker = new BooleanSetting("Hit Marker", true);
    public final ColorSetting hitMarkerColor = new ColorSetting("Hit Marker Colour", 0xFFFFFFFF);

    // --- Angriffsanzeige --------------------------------------------------
    public final ModeSetting indicator = new ModeSetting("Attack Indicator", 1, "Off", "Bar", "Ring", "Percent");
    public final ColorSetting indicatorColor = new ColorSetting("Indicator Colour", 0xFFFFFFFF);
    public final BooleanSetting indicatorAlways = new BooleanSetting("Indicator Always Visible", false);
    public final BooleanSetting readyFlash = new BooleanSetting("Flash When Ready", true);
    // 4.6.1: feiner einstellbar
    public final ModeSetting indicatorPosition =
            new ModeSetting("Indicator Position", 0, "Below", "Above", "Left", "Right");
    public final NumberSetting indicatorDistance = new NumberSetting("Indicator Distance", 4, 0, 30, 1);
    public final NumberSetting indicatorLength = new NumberSetting("Indicator Length", 16, 6, 60, 1);
    public final NumberSetting indicatorThickness = new NumberSetting("Indicator Thickness", 2, 1, 6, 1);
    public final ColorSetting indicatorTrack = new ColorSetting("Indicator Track Colour", 0xFF2E2A3A);
    public final ColorSetting indicatorBackground = new ColorSetting("Indicator Background", 0xB0000000);
    public final BooleanSetting colorByCharge = new BooleanSetting("Colour By Charge", false);
    public final ColorSetting chargingColor = new ColorSetting("Charging Colour", 0xFFFF5555);
    public final NumberSetting indicatorTextScale = new NumberSetting("Indicator Text Scale", 1.0, 0.5, 2.0, 0.1);
    public final BooleanSetting indicatorOnlyTarget = new BooleanSetting("Indicator Only On Target", false);

    // --- Sonstiges --------------------------------------------------------
    public final BooleanSetting thirdPerson = new BooleanSetting("Show in Third Person", false);

    public CrosshairModule() {
        super("Crosshair", Category.PVP);
        addSetting(style);
        addSetting(size);
        addSetting(thickness);
        addSetting(gap);
        addSetting(dotSize);
        addSetting(color);
        addSetting(rainbow);
        addSetting(outline);
        addSetting(outlineColor);
        addSetting(targetMode);
        addSetting(targetColor);
        addSetting(dynamicGap);
        addSetting(hitMarker);
        addSetting(hitMarkerColor);
        addSetting(indicator);
        addSetting(indicatorColor);
        addSetting(indicatorAlways);
        addSetting(readyFlash);
        addSetting(indicatorPosition);
        addSetting(indicatorDistance);
        addSetting(indicatorLength);
        addSetting(indicatorThickness);
        addSetting(indicatorTrack);
        addSetting(indicatorBackground);
        addSetting(colorByCharge);
        addSetting(chargingColor);
        addSetting(indicatorTextScale);
        addSetting(indicatorOnlyTarget);
        addSetting(thirdPerson);
    }
}
