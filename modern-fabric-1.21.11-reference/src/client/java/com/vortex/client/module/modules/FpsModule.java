package com.vortex.client.module.modules;

import com.vortex.client.core.setting.ColorSetting;
import com.vortex.client.core.setting.NumberSetting;
import com.vortex.client.hud.HudElement;
import com.vortex.client.module.Module;

/** Einfache FPS-Anzeige. */
public class FpsModule extends Module implements HudElement {

    public final NumberSetting x = new NumberSetting("X", 4, 0, 1920, 1);
    public final NumberSetting y = new NumberSetting("Y", 16, 0, 1080, 1);
    public final ColorSetting color = new ColorSetting("Text Color", 0xFFFFFFFF);
    public final NumberSetting scale = new NumberSetting("Scale", 1.0, 0.5, 3.0, 0.1);

    public FpsModule() {
        super("FPS", Category.HUD);
        enabledByDefault();
        addSetting(x);
        addSetting(y);
        addSetting(color);
        addSetting(scale);
    }

    @Override public String hudName() { return "FPS"; }
    @Override public NumberSetting hudX() { return x; }
    @Override public NumberSetting hudY() { return y; }
    @Override public NumberSetting hudScale() { return scale; }
    @Override public com.vortex.client.core.setting.ColorSetting hudColor() { return color; }
    @Override public int hudWidth() { return 60; }
    @Override public int hudHeight() { return 12; }
}
