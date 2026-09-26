package com.vortex.client.module.modules;

import com.vortex.client.core.setting.BooleanSetting;
import com.vortex.client.core.setting.ColorSetting;
import com.vortex.client.core.setting.ModeSetting;
import com.vortex.client.core.setting.NumberSetting;
import com.vortex.client.module.Module;

/**
 * Eigene Farbe fuer das Aufleuchten, wenn jemand Schaden nimmt.
 *
 * Minecraft faerbt getroffene Wesen kurz rot ein. Diese Farbe steckt in einer
 * kleinen Textur, die das Spiel fuer alle Wesen benutzt -- hier wird genau
 * diese Textur umgefaerbt. Die Deckkraft der Farbe bestimmt, wie stark das
 * Aufleuchten ist. Beim Ausschalten kommt das Vanilla-Rot zurueck.
 */
public class HitColorModule extends Module {

    public final ColorSetting color = new ColorSetting("Color", 0xB28B5CF6);

    public HitColorModule() {
        super("Hit Color", Category.PVP);
        addSetting(color);
    }
}
