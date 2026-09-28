package com.vortex.client.module.modules;

import com.vortex.client.core.setting.ColorSetting;
import com.vortex.client.core.setting.NumberSetting;
import com.vortex.client.module.Module;

/**
 * Glint Customizer: Farbe, Tempo und Staerke des Verzauberungs-Glanzes
 * (Items und Ruestung). Farbe auch als Verlauf/Welle/Regenbogen.
 *
 * Tempo und Staerke sind die Vanilla-Einstellungen (Barrierefreiheit), hier
 * nur griffbereit; beim Ausschalten kommen deine alten Werte zurueck.
 */
public class GlintModule extends Module {

    public final ColorSetting color = new ColorSetting("Glint Color", 0xFFFF3B3B);
    public final NumberSetting speed = new NumberSetting("Speed", 100, 0, 100, 5);
    public final NumberSetting strength = new NumberSetting("Strength", 100, 0, 100, 5);

    public GlintModule() {
        super("Glint Customizer", Category.MISC);
        addSetting(color); addSetting(speed); addSetting(strength);
    }

    @Override protected void onDisable() { com.vortex.client.hud.Glint.restore(); }
}
