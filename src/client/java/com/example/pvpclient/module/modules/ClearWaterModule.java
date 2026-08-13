package com.example.pvpclient.module.modules;

import com.example.pvpclient.module.Module;

/**
 * Klarsicht Wasser: entfernt den Unterwasser-Nebel, sodass man unter Wasser so
 * weit sieht wie an der Oberflaeche (ohne den blauen Sicht-Schleier).
 *
 * Wirkung im WaterFogModifierMixin.
 */
public class ClearWaterModule extends Module {
    public ClearWaterModule() {
        super("Klarsicht Wasser", Category.MISC);
    }
}
