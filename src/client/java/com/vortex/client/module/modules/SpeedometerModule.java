package com.vortex.client.module.modules;

import com.vortex.client.core.setting.BooleanSetting;
import com.vortex.client.core.setting.ColorSetting;
import com.vortex.client.core.setting.ModeSetting;
import com.vortex.client.core.setting.NumberSetting;
import com.vortex.client.hud.HudElement;
import com.vortex.client.module.Module;

/**
 * Zeigt, wie schnell du dich bewegst.
 *
 * Gemessen wird die tatsaechlich zurueckgelegte Strecke pro Tick, nicht die
 * Eingabe -- Eis, Seelensand, Elytra und Rueckstoss sind also mit drin.
 * Standard ist waagerecht; senkrecht zaehlt nur auf Wunsch mit, sonst zeigt
 * jeder Sprung kurz einen falschen Wert.
 */
public class SpeedometerModule extends Module implements HudElement {

    public final NumberSetting x = new NumberSetting("X", 4, 0, 1920, 1);
    public final NumberSetting y = new NumberSetting("Y", 52, 0, 1080, 1);
    public final ColorSetting color = new ColorSetting("Text Color", 0xFFFFFFFF);
    public final NumberSetting scale = new NumberSetting("Scale", 1.0, 0.5, 3.0, 0.1);
    public final ModeSetting unit = new ModeSetting("Unit", 0, "Blocks/s", "km/h");
    public final BooleanSetting vertical = new BooleanSetting("Include Vertical", false);

    public SpeedometerModule() {
        super("Speedometer", Category.HUD);
        addSetting(x);
        addSetting(y);
        addSetting(color);
        addSetting(scale);
        addSetting(unit);
        addSetting(vertical);
    }

    @Override public String hudName() { return "Speedometer"; }
    @Override public NumberSetting hudX() { return x; }
    @Override public NumberSetting hudY() { return y; }
    @Override public NumberSetting hudScale() { return scale; }
    @Override public ColorSetting hudColor() { return color; }
    @Override public int hudWidth() { return 80; }
    @Override public int hudHeight() { return 12; }
}
