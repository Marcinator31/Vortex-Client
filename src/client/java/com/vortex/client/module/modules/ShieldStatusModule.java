package com.vortex.client.module.modules;

import com.vortex.client.core.setting.BooleanSetting;
import com.vortex.client.module.Module;

/**
 * Shield Status: faerbt Schilde nach Zustand ein (eigene Umsetzung seit 4.30,
 * siehe cosmetics/SchildStatus und SchildSkinMixin):
 *   gruen = blockt, gelb = wird hochgenommen, rot = von einer Axt gebrochen.
 *
 * Bis 4.29 schaltete dieses Modul per Reflection die Mod "ShieldStatus" um --
 * die war aber nie im Client eingebaut, das Modul hatte also keine Wirkung.
 */
public class ShieldStatusModule extends Module {

    public final BooleanSetting grayscaleBroken =
        new BooleanSetting("Grey Out Broken", false);
    public final BooleanSetting opponentShields =
        new BooleanSetting("Enemy Shields", true);
    public final BooleanSetting smoothColor =
        new BooleanSetting("Smooth Gradient", false);

    public ShieldStatusModule() {
        super("Shield Status", Category.PVP);
        addSetting(grayscaleBroken);
        addSetting(opponentShields);
        addSetting(smoothColor);
        enabledByDefault();
    }
}
