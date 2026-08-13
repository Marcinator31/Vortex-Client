package com.example.pvpclient.module.modules;

import com.example.pvpclient.core.setting.ColorSetting;
import com.example.pvpclient.core.setting.NumberSetting;
import com.example.pvpclient.hud.HudElement;
import com.example.pvpclient.module.Module;

/**
 * CPS-Anzeige als Modul.
 *
 * Sieh dir an, wie wenig hier steht: nur die Settings. Die eigentliche
 * Klick-Zaehlung lebt weiter in CpsCounter, das Zeichnen im HudRenderer.
 * Das Modul ist nur der "Vertrag": Bin ich an? Wo? Welche Farbe?
 */
public class CpsModule extends Module implements HudElement {

    public final NumberSetting x = new NumberSetting("X", 4, 0, 1920, 1);
    public final NumberSetting y = new NumberSetting("Y", 4, 0, 1080, 1);
    public final ColorSetting color = new ColorSetting("Textfarbe", 0xFFFFFFFF);
    public final NumberSetting scale = new NumberSetting("Skalierung", 1.0, 0.5, 3.0, 0.1);

    public CpsModule() {
        super("CPS", Category.HUD);
        enabledByDefault();
        addSetting(x);
        addSetting(y);
        addSetting(color);
        addSetting(scale);
    }

    @Override public String hudName() { return "CPS"; }
    @Override public NumberSetting hudX() { return x; }
    @Override public NumberSetting hudY() { return y; }
    @Override public NumberSetting hudScale() { return scale; }
    @Override public com.example.pvpclient.core.setting.ColorSetting hudColor() { return color; }
    @Override public int hudWidth() { return 50; }
    @Override public int hudHeight() { return 12; }
}
