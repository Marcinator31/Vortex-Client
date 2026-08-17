package com.vortex.client.module.modules;

import com.vortex.client.core.setting.ColorSetting;
import com.vortex.client.core.setting.NumberSetting;
import com.vortex.client.hud.HudElement;
import com.vortex.client.module.Module;

/** Koordinaten-Anzeige (XYZ + Blickrichtung). */
public class CoordinatesModule extends Module implements HudElement {

    public final NumberSetting x = new NumberSetting("X", 4, 0, 1920, 1);
    public final NumberSetting y = new NumberSetting("Y", 28, 0, 1080, 1);
    public final ColorSetting color = new ColorSetting("Text Color", 0xFFFFFFFF);
    public final NumberSetting scale = new NumberSetting("Scale", 1.0, 0.5, 3.0, 0.1);

    public CoordinatesModule() {
        super("Coordinates", Category.HUD);
        addSetting(x);
        addSetting(y);
        addSetting(color);
        addSetting(scale);
    }

    @Override public String hudName() { return "Coordinates"; }
    @Override public NumberSetting hudX() { return x; }
    @Override public NumberSetting hudY() { return y; }
    @Override public NumberSetting hudScale() { return scale; }
    @Override public com.vortex.client.core.setting.ColorSetting hudColor() { return color; }
    @Override public int hudWidth() { return 140; }
    @Override public int hudHeight() { return 12; }
}
