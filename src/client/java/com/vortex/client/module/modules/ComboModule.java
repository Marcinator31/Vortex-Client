package com.vortex.client.module.modules;

import com.vortex.client.core.setting.BooleanSetting;
import com.vortex.client.core.setting.ColorSetting;
import com.vortex.client.core.setting.ModeSetting;
import com.vortex.client.core.setting.NumberSetting;
import com.vortex.client.hud.HudElement;
import com.vortex.client.module.Module;

/**
 * Zaehlt Treffer in Folge, ohne selbst getroffen zu werden.
 *
 * Nur bestaetigte Treffer zaehlen -- ein Schlag in die Unverwundbarkeit des
 * Gegners hinein nicht. Wirst du selbst getroffen oder triffst eine Weile
 * nicht mehr, faengt es wieder bei null an.
 */
public class ComboModule extends Module implements HudElement {

    public final NumberSetting x = new NumberSetting("X", 4, 0, 1920, 1);
    public final NumberSetting y = new NumberSetting("Y", 76, 0, 1080, 1);
    public final ColorSetting color = new ColorSetting("Text Color", 0xFFFFFFFF);
    public final NumberSetting scale = new NumberSetting("Scale", 1.0, 0.5, 3.0, 0.1);
    public final NumberSetting resetAfter = new NumberSetting("Reset After (s)", 3, 1, 10, 1);
    public final BooleanSetting hideAtZero = new BooleanSetting("Hide At Zero", true);

    public ComboModule() {
        super("Combo Counter", Category.PVP);
        addSetting(x);
        addSetting(y);
        addSetting(color);
        addSetting(scale);
        addSetting(resetAfter);
        addSetting(hideAtZero);
    }

    @Override public String hudName() { return "Combo Counter"; }
    @Override public NumberSetting hudX() { return x; }
    @Override public NumberSetting hudY() { return y; }
    @Override public NumberSetting hudScale() { return scale; }
    @Override public ColorSetting hudColor() { return color; }
    @Override public int hudWidth() { return 70; }
    @Override public int hudHeight() { return 12; }
}
