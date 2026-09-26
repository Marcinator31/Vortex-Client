package com.vortex.client.module.modules;

import com.vortex.client.core.setting.BooleanSetting;
import com.vortex.client.core.setting.ColorSetting;
import com.vortex.client.core.setting.ModeSetting;
import com.vortex.client.core.setting.NumberSetting;
import com.vortex.client.module.Module;

/**
 * Eigener Rahmen um den anvisierten Block -- Farbe und Dicke frei.
 *
 * Der duenne schwarze Rahmen von Minecraft wird dabei ausgeblendet, sonst
 * saehe man zwei uebereinander. Der Rahmen folgt der echten Form des Blocks:
 * Treppen, Stufen und Zaeune bekommen ihre eigene Kontur, keinen Wuerfel.
 */
public class BlockOutlineModule extends Module {

    public final ColorSetting color = new ColorSetting("Color", 0xFF8B5CF6);
    public final NumberSetting width = new NumberSetting("Width", 2.5, 1.0, 8.0, 0.5);
    public final BooleanSetting throughWalls = new BooleanSetting("Through Walls", false);

    public BlockOutlineModule() {
        super("Block Outline", Category.MISC);
        addSetting(color);
        addSetting(width);
        addSetting(throughWalls);
    }
}
