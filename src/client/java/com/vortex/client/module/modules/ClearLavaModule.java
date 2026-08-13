package com.vortex.client.module.modules;

import com.vortex.client.module.Module;

/**
 * Klarsicht Lava: entfernt den dichten Lava-Nebel, sodass man in Lava normal
 * sieht (statt fast blind zu sein).
 *
 * Wirkung im LavaFogModifierMixin.
 */
public class ClearLavaModule extends Module {
    public ClearLavaModule() {
        super("Klarsicht Lava", Category.MISC);
    }
}
