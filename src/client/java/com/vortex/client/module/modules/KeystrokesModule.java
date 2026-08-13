package com.vortex.client.module.modules;

import com.vortex.client.core.setting.BooleanSetting;
import com.vortex.client.core.setting.ColorSetting;
import com.vortex.client.core.setting.NumberSetting;
import com.vortex.client.hud.HudElement;
import com.vortex.client.module.Module;

/**
 * Keystrokes: zeigt WASD, Leertaste und die beiden Maustasten als Tastenfeld an.
 * Gedrueckte Tasten leuchten auf.
 *
 * Beliebt beim Aufnehmen und Streamen, aber auch praktisch, um das eigene
 * Klickverhalten zu sehen -- die Maustasten zeigen zusaetzlich die Klicks pro
 * Sekunde an.
 */
public class KeystrokesModule extends Module implements HudElement {

    public final NumberSetting x = new NumberSetting("X", 4, 0, 1920, 1);
    public final NumberSetting y = new NumberSetting("Y", 120, 0, 1080, 1);
    public final ColorSetting color = new ColorSetting("Textfarbe", 0xFFFFFFFF);
    public final NumberSetting scale = new NumberSetting("Skalierung", 1.0, 0.5, 3.0, 0.1);

    /** Farbe der Tasten im Ruhezustand und beim Druecken. */
    public final ColorSetting idleColor = new ColorSetting("Taste normal", 0x80000000);
    public final ColorSetting pressColor = new ColorSetting("Taste gedrueckt", 0xC0FFFFFF);

    /** Maustasten mit anzeigen (inkl. Klicks pro Sekunde). */
    public final BooleanSetting showMouse = new BooleanSetting("Maustasten", true);
    /** Leertaste als breiten Balken darunter anzeigen. */
    public final BooleanSetting showSpace = new BooleanSetting("Leertaste", true);

    public KeystrokesModule() {
        super("Keystrokes", Category.HUD);
        addSetting(x);
        addSetting(y);
        addSetting(color);
        addSetting(scale);
        addSetting(idleColor);
        addSetting(pressColor);
        addSetting(showMouse);
        addSetting(showSpace);
    }

    @Override public String hudName() { return "Keystrokes"; }
    @Override public NumberSetting hudX() { return x; }
    @Override public NumberSetting hudY() { return y; }
    @Override public NumberSetting hudScale() { return scale; }
    @Override public ColorSetting hudColor() { return color; }

    @Override
    public int hudWidth() {
        return (int) (68 * scale.get());
    }

    @Override
    public int hudHeight() {
        int rows = 2;                       // W-Reihe + ASD-Reihe
        if (showSpace.get()) rows++;
        if (showMouse.get()) rows++;
        return (int) ((rows * 22) * scale.get());
    }
}
