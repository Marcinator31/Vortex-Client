package com.vortex.client.module.modules;

import com.vortex.client.core.setting.BooleanSetting;
import com.vortex.client.core.setting.ColorSetting;
import com.vortex.client.core.setting.ModeSetting;
import com.vortex.client.core.setting.NumberSetting;
import com.vortex.client.module.Module;

/**
 * Light Level Overlay: markiert Stellen, an denen Monster spawnen koennen.
 *
 * Seit 1.18 spawnen Monster nur bei Blocklicht 0. Rot = auch tagsueber
 * (kein Himmelslicht, z.B. Hoehle), Gelb = nur nachts (Himmelslicht vorhanden),
 * Gruen (optional) = sicher, weil eine Lichtquelle in der Naehe ist.
 *
 * Rein lokal: liest nur die Lichtwerte, die der Client ohnehin kennt.
 */
public class LightLevelModule extends Module {

    public final NumberSetting range = new NumberSetting("Range", 16, 4, 32, 1);
    public final NumberSetting height = new NumberSetting("Height", 6, 2, 16, 1);
    public final ModeSetting style = new ModeSetting("Style", 0, "Cross", "Square", "Numbers");
    public final BooleanSetting showSafe = new BooleanSetting("Show Safe Spots", false);
    public final ColorSetting danger = new ColorSetting("Always Spawns", 0xFFFF3B3B).noGradient();
    public final ColorSetting night = new ColorSetting("Spawns At Night", 0xFFFFD23F).noGradient();
    public final ColorSetting safe = new ColorSetting("Safe", 0xFF45D66B).noGradient();

    public LightLevelModule() {
        super("Light Level Overlay", Category.MISC);
        addSetting(range);
        addSetting(height);
        addSetting(style);
        addSetting(showSafe);
        addSetting(danger);
        addSetting(night);
        addSetting(safe);
    }
}
