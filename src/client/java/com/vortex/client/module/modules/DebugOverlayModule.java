package com.vortex.client.module.modules;

import com.vortex.client.core.setting.BooleanSetting;
import com.vortex.client.core.setting.ColorSetting;
import com.vortex.client.core.setting.ModeSetting;
import com.vortex.client.core.setting.NumberSetting;
import com.vortex.client.hud.DebugOverlay;
import com.vortex.client.hud.HudElement;
import com.vortex.client.module.Module;

/**
 * Ein lesbares F3 (neu gebaut in 4.7.2).
 *
 * Das Vanilla-F3 ist eine Textwand fuer Entwickler. Das hier zeigt die Dinge,
 * die beim Spielen wirklich gefragt sind -- in Abschnitten, zwei Spalten,
 * Werte nach Zustand gefaerbt (FPS, Ping, TPS, Speicher):
 *
 *   PERFORMANCE  FPS, Frametime, 1%-Low (die Ruckler), Frame-Graph
 *   POSITION     XYZ, passende Nether-/Oberwelt-Koordinaten, Chunk, Blickrichtung, Tempo
 *   WORLD        Biom, Dimension, Licht (Mobs spawnen?), Uhrzeit, Tag, Wetter
 *   LOOKING AT   Block mit Position/Abstand (optional Blockzustaende) oder Wesen mit Leben
 *   SYSTEM       Arbeitsspeicher mit Balken, geladene Wesen, Java
 *   SERVER       Adresse, Server-Software, Ping, TPS, Spieler
 *
 * Bewusst KEIN "F3 ersetzen": Das Vanilla-F3 ist das, worauf man zurueckgreift,
 * wenn etwas nicht stimmt -- auch wenn dieser Client das Problem ist.
 * Standard: Unser Overlay versteckt sich, solange F3 offen ist.
 */
public class DebugOverlayModule extends Module implements HudElement {

    // ---- Abschnitte (Namen wie bisher -- gespeicherte Einstellungen bleiben) ----
    public final BooleanSetting showFps = new BooleanSetting("Performance", true);
    public final BooleanSetting frameGraph = new BooleanSetting("Frame Graph", true);
    public final BooleanSetting showPosition = new BooleanSetting("Position", true);
    public final BooleanSetting otherDimension = new BooleanSetting("Nether Coords", true);
    public final BooleanSetting speed = new BooleanSetting("Speed", true);
    public final BooleanSetting showWorld = new BooleanSetting("World", true);
    public final BooleanSetting showTarget = new BooleanSetting("Looking At", true);
    public final BooleanSetting blockStates = new BooleanSetting("Block States", false);
    public final BooleanSetting showSystem = new BooleanSetting("System", false);
    public final BooleanSetting showServer = new BooleanSetting("Server", true);

    // ---- Aussehen ----
    /** Panel: Ueberschriften je Abschnitt. Compact: ohne, enger. */
    public final ModeSetting layout = new ModeSetting("Layout", 0, "Panel", "Compact");
    public final BooleanSetting hideWithF3 = new BooleanSetting("Hide With F3", true);
    /** FPS/Ping/TPS/Speicher gruen-gelb-rot statt in der Wertfarbe. */
    public final BooleanSetting coloredValues = new BooleanSetting("Colored Values", true);
    public final BooleanSetting background = new BooleanSetting("Background", true);
    public final NumberSetting opacity = new NumberSetting("Background Opacity", 60, 0, 100, 5);
    public final ColorSetting accent =
            new ColorSetting("Accent Color", 0xFF8B5CF6, 0xFF3B82F6, ColorSetting.GRADIENT);
    public final ColorSetting labelColor = new ColorSetting("Label Colour", 0xFF9AA3B8);
    public final ColorSetting valueColor = new ColorSetting("Value Colour", 0xFFF1F1F5);
    public final BooleanSetting shadow = new BooleanSetting("Text Shadow", false);

    public final NumberSetting x = new NumberSetting("X", 4, 0, 1920, 1);
    public final NumberSetting y = new NumberSetting("Y", 40, 0, 1080, 1);
    public final NumberSetting scale = new NumberSetting("Scale", 1.0, 0.5, 3.0, 0.1);

    public DebugOverlayModule() {
        super("Debug Overlay", Category.HUD);
        addSetting(showFps);
        addSetting(frameGraph);
        addSetting(showPosition);
        addSetting(otherDimension);
        addSetting(speed);
        addSetting(showWorld);
        addSetting(showTarget);
        addSetting(blockStates);
        addSetting(showSystem);
        addSetting(showServer);
        addSetting(layout);
        addSetting(hideWithF3);
        addSetting(coloredValues);
        addSetting(background);
        addSetting(opacity);
        addSetting(accent);
        addSetting(labelColor);
        addSetting(valueColor);
        addSetting(shadow);
        addSetting(x);
        addSetting(y);
        addSetting(scale);
    }

    @Override public String hudName() { return "Debug Overlay"; }
    @Override public NumberSetting hudX() { return x; }
    @Override public NumberSetting hudY() { return y; }
    @Override public NumberSetting hudScale() { return scale; }
    @Override public ColorSetting hudColor() { return valueColor; }
    /** Die zuletzt gezeichnete Groesse -- der Editor-Kasten passt so immer. */
    @Override public int hudWidth() { return DebugOverlay.lastWidth(170, scale.getFloat()); }
    @Override public int hudHeight() { return DebugOverlay.lastHeight(120, scale.getFloat()); }
}
