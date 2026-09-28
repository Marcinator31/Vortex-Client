package com.vortex.client.module.modules;

import com.vortex.client.core.setting.BooleanSetting;
import com.vortex.client.core.setting.ColorSetting;
import com.vortex.client.core.setting.NumberSetting;
import com.vortex.client.hud.HudElement;
import com.vortex.client.module.Module;

/**
 * Cooldown HUD: Abklingzeiten als Balken mit Sekunden -- Enderperle, Wind
 * Charge, Chorusfrucht, Ziegenhorn, dein Schild nach einem Axt-Treffer und
 * alles andere, was eine Abklingzeit hat (auch von Servern/Plugins).
 */
public class CooldownHudModule extends Module implements HudElement {

    public final NumberSetting x = new NumberSetting("X", 4, 0, 1920, 1);
    public final NumberSetting y = new NumberSetting("Y", 170, 0, 1080, 1);
    public final NumberSetting scale = new NumberSetting("Scale", 1.0, 0.5, 3.0, 0.1);
    public final ColorSetting color = new ColorSetting("Bar Color", 0xFF8B5CF6, 0xFF3B82F6, ColorSetting.GRADIENT);
    public final BooleanSetting showSeconds = new BooleanSetting("Show Seconds", true);
    public final BooleanSetting showName = new BooleanSetting("Show Name", false);
    public final BooleanSetting background = new BooleanSetting("Background", true);
    public final BooleanSetting horizontal = new BooleanSetting("Horizontal", false);

    public CooldownHudModule() {
        super("Cooldown HUD", Category.HUD);
        addSetting(x); addSetting(y); addSetting(scale); addSetting(color);
        addSetting(showSeconds); addSetting(showName); addSetting(background); addSetting(horizontal);
    }

    @Override public String hudName() { return "Cooldown HUD"; }
    @Override public NumberSetting hudX() { return x; }
    @Override public NumberSetting hudY() { return y; }
    @Override public NumberSetting hudScale() { return scale; }
    @Override public ColorSetting hudColor() { return color; }
    @Override public int hudWidth() { return com.vortex.client.hud.CooldownHud.lastW(scale.getFloat()); }
    @Override public int hudHeight() { return com.vortex.client.hud.CooldownHud.lastH(scale.getFloat()); }
}
