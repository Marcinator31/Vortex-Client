package com.vortex.client.module.modules;

import com.vortex.client.core.setting.BooleanSetting;
import com.vortex.client.core.setting.ColorSetting;
import com.vortex.client.core.setting.ModeSetting;
import com.vortex.client.core.setting.NumberSetting;
import com.vortex.client.module.Module;

/**
 * Grosse Titel in der Bildmitte verschieben, verkleinern oder ausblenden.
 *
 * Server blenden damit Countdowns, Kills und Werbung ein -- oft genau dort,
 * wo man gerade hinsieht. Kleiner und etwas hoeher gesetzt stoeren sie kaum.
 */
public class TitlesModule extends Module {

    public final NumberSetting offsetX = new NumberSetting("Offset X", 0, -400, 400, 1);
    public final NumberSetting offsetY = new NumberSetting("Offset Y", -30, -250, 250, 1);
    public final NumberSetting scale = new NumberSetting("Scale", 0.7, 0.3, 1.5, 0.05);
    public final BooleanSetting hide = new BooleanSetting("Hide Completely", false);
    /** Durchsichtig (Standard) = Farben des Servers behalten. */
    public final ColorSetting color = new ColorSetting("Title Color", 0x00FFFFFF);

    public TitlesModule() {
        super("Titles", Category.HUD);
        addSetting(offsetX);
        addSetting(offsetY);
        addSetting(scale);
        addSetting(color);
        addSetting(hide);
    }
}
