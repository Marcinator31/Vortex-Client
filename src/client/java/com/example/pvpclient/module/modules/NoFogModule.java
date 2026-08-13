package com.example.pvpclient.module.modules;

import com.example.pvpclient.module.Module;

/**
 * No Fog: entfernt den normalen atmosphaerischen Distanz-Nebel, sodass man bis
 * zum Rand der Render-Distanz klar sieht. Betrifft NICHT Wasser/Lava -- dafuer
 * gibt es eigene Module (Klarsicht Wasser / Klarsicht Lava).
 *
 * Wirkung im MixinFogRenderer (Zweig fuer den atmosphaerischen Nebel).
 */
public class NoFogModule extends Module {
    public NoFogModule() {
        super("No Fog", Category.MISC);
    }
}
