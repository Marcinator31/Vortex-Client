package com.vortex.client.module.modules;

import com.vortex.client.core.setting.BooleanSetting;
import com.vortex.client.core.setting.ColorSetting;
import com.vortex.client.core.setting.ModeSetting;
import com.vortex.client.core.setting.NumberSetting;
import com.vortex.client.module.Module;

/**
 * Fallengelassene Gegenstaende liegen flach auf dem Boden, statt zu
 * schweben und sich zu drehen.
 *
 * Flache Gegenstaende (Schwerter, Nahrung, Erze ...) liegen hingestreckt,
 * Bloecke stehen auf dem Boden. Jeder bekommt eine eigene, zufaellige
 * Ausrichtung, die beim Rutschen mitrollt und in Ruhe stehen bleibt.
 */
public class ItemPhysicsModule extends Module {

    public final BooleanSetting randomRotation = new BooleanSetting("Random Rotation", true);
    public final BooleanSetting rolling = new BooleanSetting("Roll When Moving", true);

    public ItemPhysicsModule() {
        super("Item Physics", Category.MISC);
        addSetting(randomRotation);
        addSetting(rolling);
    }
}
