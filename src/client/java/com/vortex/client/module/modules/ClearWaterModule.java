package com.vortex.client.module.modules;

import com.vortex.client.module.Module;

/**
 * Klarsicht Wasser: entfernt den Unterwasser-Nebel, sodass man unter Wasser so
 * weit sieht wie an der Oberflaeche (ohne den blauen Sicht-Schleier).
 *
 * Wirkung im WaterFogModifierMixin.
 */
public class ClearWaterModule extends Module {
    public ClearWaterModule() {
        super("Clear Water", Category.MISC);
    }
}
