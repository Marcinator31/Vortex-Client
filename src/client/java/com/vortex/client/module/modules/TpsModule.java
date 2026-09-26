package com.vortex.client.module.modules;

import com.vortex.client.core.setting.BooleanSetting;
import com.vortex.client.core.setting.ColorSetting;
import com.vortex.client.core.setting.ModeSetting;
import com.vortex.client.core.setting.NumberSetting;
import com.vortex.client.hud.HudElement;
import com.vortex.client.module.Module;

/**
 * Geschaetzte Ticks pro Sekunde des Servers.
 *
 * 20 ist normal. Weniger heisst: der Server kommt nicht hinterher, alles
 * laeuft langsamer -- nicht deine Verbindung. Die Zahl dahinter zeigt, wie
 * lange das letzte Lebenszeichen des Servers her ist; waechst sie, haengt der
 * Server oder die Verbindung.
 */
public class TpsModule extends Module implements HudElement {

    public final NumberSetting x = new NumberSetting("X", 4, 0, 1920, 1);
    public final NumberSetting y = new NumberSetting("Y", 88, 0, 1080, 1);
    public final ColorSetting color = new ColorSetting("Text Color", 0xFFFFFFFF);
    public final NumberSetting scale = new NumberSetting("Scale", 1.0, 0.5, 3.0, 0.1);
    public final BooleanSetting lagTimer = new BooleanSetting("Show Lag Timer", true);
    public final BooleanSetting colorByValue = new BooleanSetting("Color By Value", true);

    public TpsModule() {
        super("TPS", Category.HUD);
        addSetting(x);
        addSetting(y);
        addSetting(color);
        addSetting(scale);
        addSetting(lagTimer);
        addSetting(colorByValue);
    }

    @Override public String hudName() { return "TPS"; }
    @Override public NumberSetting hudX() { return x; }
    @Override public NumberSetting hudY() { return y; }
    @Override public NumberSetting hudScale() { return scale; }
    @Override public ColorSetting hudColor() { return color; }
    @Override public int hudWidth() { return 90; }
    @Override public int hudHeight() { return 12; }
}
