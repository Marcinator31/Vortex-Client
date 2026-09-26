package com.vortex.client.module.modules;

import com.vortex.client.core.setting.BooleanSetting;
import com.vortex.client.core.setting.ColorSetting;
import com.vortex.client.core.setting.ModeSetting;
import com.vortex.client.core.setting.NumberSetting;
import com.vortex.client.hud.HudElement;
import com.vortex.client.module.Module;

/**
 * Uhrzeit, Spieltag und Arbeitsspeicher in einer Zeile.
 *
 * Jeder Teil ist einzeln schaltbar. Der Spieltag zaehlt ab Weltbeginn.
 */
public class ClockModule extends Module implements HudElement {

    public final NumberSetting x = new NumberSetting("X", 4, 0, 1920, 1);
    public final NumberSetting y = new NumberSetting("Y", 100, 0, 1080, 1);
    public final ColorSetting color = new ColorSetting("Text Color", 0xFFFFFFFF);
    public final NumberSetting scale = new NumberSetting("Scale", 1.0, 0.5, 3.0, 0.1);
    public final BooleanSetting showTime = new BooleanSetting("Show Time", true);
    public final ModeSetting format = new ModeSetting("Format", 0, "24h", "12h");
    public final BooleanSetting showDay = new BooleanSetting("Show Day", true);
    public final BooleanSetting showMemory = new BooleanSetting("Show Memory", false);

    public ClockModule() {
        super("Clock", Category.HUD);
        addSetting(x);
        addSetting(y);
        addSetting(color);
        addSetting(scale);
        addSetting(showTime);
        addSetting(format);
        addSetting(showDay);
        addSetting(showMemory);
    }

    @Override public String hudName() { return "Clock"; }
    @Override public NumberSetting hudX() { return x; }
    @Override public NumberSetting hudY() { return y; }
    @Override public NumberSetting hudScale() { return scale; }
    @Override public ColorSetting hudColor() { return color; }
    @Override public int hudWidth() { return 150; }
    @Override public int hudHeight() { return 12; }
}
