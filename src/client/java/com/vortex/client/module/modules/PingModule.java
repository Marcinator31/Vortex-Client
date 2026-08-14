package com.vortex.client.module.modules;

import com.vortex.client.core.setting.BooleanSetting;
import com.vortex.client.core.setting.ColorSetting;
import com.vortex.client.core.setting.NumberSetting;
import com.vortex.client.hud.HudElement;
import com.vortex.client.module.Module;

/** Zeigt den aktuellen Ping (Latenz zum Server) in Millisekunden an. */
public class PingModule extends Module implements HudElement {

    public final NumberSetting x = new NumberSetting("X", 4, 0, 1920, 1);
    public final NumberSetting y = new NumberSetting("Y", 28, 0, 1080, 1);
    public final ColorSetting color = new ColorSetting("Text Color", 0xFFFFFFFF);
    public final NumberSetting scale = new NumberSetting("Scale", 1.0, 0.5, 3.0, 0.1);

    /**
     * Measure the round trip ourselves instead of using the server's number.
     *
     * The server only refreshes its value every thirty seconds, which is why
     * the reading felt stuck. Measuring locally gives a fresh number every
     * second — at the cost of telling you about the network only, not about
     * how busy the server is.
     */
    public final BooleanSetting measure = new BooleanSetting("Measure Myself", true);

    /** Seconds between measurements. */
    public final NumberSetting interval = new NumberSetting("Interval (s)", 1, 1, 10, 1);

    public PingModule() {
        super("Ping", Category.HUD);
        addSetting(measure);
        addSetting(interval);
        addSetting(x);
        addSetting(y);
        addSetting(color);
        addSetting(scale);
    }

    @Override public String hudName() { return "Ping"; }
    @Override public NumberSetting hudX() { return x; }
    @Override public NumberSetting hudY() { return y; }
    @Override public NumberSetting hudScale() { return scale; }
    @Override public ColorSetting hudColor() { return color; }
    @Override public int hudWidth() { return 60; }
    @Override public int hudHeight() { return 12; }
}
