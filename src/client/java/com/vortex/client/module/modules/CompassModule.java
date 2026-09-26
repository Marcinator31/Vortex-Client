package com.vortex.client.module.modules;

import com.vortex.client.core.setting.BooleanSetting;
import com.vortex.client.core.setting.ColorSetting;
import com.vortex.client.core.setting.ModeSetting;
import com.vortex.client.core.setting.NumberSetting;
import com.vortex.client.hud.HudElement;
import com.vortex.client.module.Module;

/**
 * Kompass-Leiste oben am Bildschirm, wie in Survival-Spielen.
 *
 * Himmelsrichtungen gleiten beim Drehen durch die Leiste, die Mitte markiert
 * deine Blickrichtung. "Centered" haelt sie in der Bildmitte; sobald du sie
 * im HUD-Editor verschiebst, schaltet sich das von selbst ab.
 */
public class CompassModule extends Module implements HudElement {

    public final NumberSetting x = new NumberSetting("X", 0, 0, 1920, 1);
    public final NumberSetting y = new NumberSetting("Y", 2, 0, 1080, 1);
    public final ColorSetting color = new ColorSetting("Text Color", 0xFFFFFFFF);
    public final NumberSetting scale = new NumberSetting("Scale", 1.0, 0.5, 3.0, 0.1);
    public final ColorSetting accent = new ColorSetting("Marker Color", 0xFF8B5CF6);
    public final NumberSetting width = new NumberSetting("Width", 200, 100, 400, 10);
    public final BooleanSetting degrees = new BooleanSetting("Show Degrees", true);
    public final BooleanSetting background = new BooleanSetting("Background", true);
    public final BooleanSetting centered = new BooleanSetting("Centered", true);
    
    /** Letzte gezeichnete X-Position -- um das Verschieben im Editor zu erkennen. */
    public double lastX = Double.NaN;

    public CompassModule() {
        super("Compass Bar", Category.HUD);
        addSetting(x);
        addSetting(y);
        addSetting(color);
        addSetting(scale);
        addSetting(accent);
        addSetting(width);
        addSetting(degrees);
        addSetting(background);
        addSetting(centered);
    }

    @Override public String hudName() { return "Compass Bar"; }
    @Override public NumberSetting hudX() { return x; }
    @Override public NumberSetting hudY() { return y; }
    @Override public NumberSetting hudScale() { return scale; }
    @Override public ColorSetting hudColor() { return color; }
    @Override public int hudWidth() { return width.getInt(); }
    @Override public int hudHeight() { return 22; }
}
