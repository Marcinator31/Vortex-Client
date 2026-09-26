package com.vortex.client.module.modules;

import com.vortex.client.core.setting.BooleanSetting;
import com.vortex.client.core.setting.ColorSetting;
import com.vortex.client.core.setting.ModeSetting;
import com.vortex.client.core.setting.NumberSetting;
import com.vortex.client.module.Module;

/**
 * Bossleisten verschieben, skalieren und umfaerben.
 *
 * "Vanilla" behaelt das Aussehen von Minecraft, nur Position, Groesse und
 * Namensfarbe aendern sich. "Custom" zeichnet eigene, schlanke Leisten in
 * einer frei waehlbaren Farbe -- oder in der Farbe, die der Server vorgibt.
 */
public class BossBarModule extends Module {

    public final ModeSetting style = new ModeSetting("Style", 1, "Vanilla", "Custom");
    public final NumberSetting offsetX = new NumberSetting("Offset X", 0, -600, 600, 1);
    public final NumberSetting offsetY = new NumberSetting("Offset Y", 0, -20, 400, 1);
    public final NumberSetting scale = new NumberSetting("Scale", 1.0, 0.4, 2.0, 0.05);
    public final ColorSetting barColor = new ColorSetting("Bar Color", 0xFF8B5CF6);
    public final BooleanSetting serverColors = new BooleanSetting("Use Server Colors", false);
    /** Alpha 0 = Farbe des Servers beibehalten. */
    public final ColorSetting nameColor = new ColorSetting("Name Color", 0x00FFFFFF);
    public final BooleanSetting percent = new BooleanSetting("Show Percent", true);
    public final BooleanSetting hide = new BooleanSetting("Hide Completely", false);

    public BossBarModule() {
        super("Boss Bar", Category.HUD);
        addSetting(style);
        addSetting(offsetX);
        addSetting(offsetY);
        addSetting(scale);
        addSetting(barColor);
        addSetting(serverColors);
        addSetting(nameColor);
        addSetting(percent);
        addSetting(hide);
    }
}
