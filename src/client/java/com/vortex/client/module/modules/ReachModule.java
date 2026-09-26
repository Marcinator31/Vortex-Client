package com.vortex.client.module.modules;

import com.vortex.client.core.setting.BooleanSetting;
import com.vortex.client.core.setting.ColorSetting;
import com.vortex.client.core.setting.ModeSetting;
import com.vortex.client.core.setting.NumberSetting;
import com.vortex.client.hud.HudElement;
import com.vortex.client.module.Module;

/**
 * Zeigt die Entfernung deines letzten Schlags.
 *
 * Gemessen von den Augen bis zum naechsten Punkt der gegnerischen Hitbox --
 * die Strecke, die auch der Server prueft. Nach der eingestellten Zeit blendet
 * die Anzeige aus (0 = bleibt stehen).
 */
public class ReachModule extends Module implements HudElement {

    public final NumberSetting x = new NumberSetting("X", 4, 0, 1920, 1);
    public final NumberSetting y = new NumberSetting("Y", 64, 0, 1080, 1);
    public final ColorSetting color = new ColorSetting("Text Color", 0xFFFFFFFF);
    public final NumberSetting scale = new NumberSetting("Scale", 1.0, 0.5, 3.0, 0.1);
    public final NumberSetting hideAfter = new NumberSetting("Hide After (s)", 3, 0, 20, 1);
    public final ModeSetting decimals = new ModeSetting("Decimals", 1, "1", "2");

    public ReachModule() {
        super("Reach Display", Category.PVP);
        addSetting(x);
        addSetting(y);
        addSetting(color);
        addSetting(scale);
        addSetting(hideAfter);
        addSetting(decimals);
    }

    @Override public String hudName() { return "Reach Display"; }
    @Override public NumberSetting hudX() { return x; }
    @Override public NumberSetting hudY() { return y; }
    @Override public NumberSetting hudScale() { return scale; }
    @Override public ColorSetting hudColor() { return color; }
    @Override public int hudWidth() { return 70; }
    @Override public int hudHeight() { return 12; }
}
