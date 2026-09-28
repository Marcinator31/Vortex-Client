package com.vortex.client.module.modules;

import com.vortex.client.core.setting.BooleanSetting;
import com.vortex.client.core.setting.NumberSetting;
import com.vortex.client.module.Module;

/**
 * TNT & Explosion Timer: Countdown ueber gezuendetem TNT, TNT-Loren und
 * zischenden Creepern.
 *
 * TNT und TNT-Loren zaehlen ihre Zuendschnur auch auf dem Client herunter --
 * die Zeit ist genau. Beim Creeper schickt der Server nur, OB er zischt; die
 * Restzeit ist geschaetzt (Standard-Zuendzeit 1,5 s).
 */
public class ExplosionTimerModule extends Module {

    public final BooleanSetting tnt = new BooleanSetting("TNT", true);
    public final BooleanSetting minecarts = new BooleanSetting("TNT Minecarts", true);
    public final BooleanSetting creepers = new BooleanSetting("Creepers", true);
    public final NumberSetting range = new NumberSetting("Range", 48, 8, 128, 4);
    public final NumberSetting size = new NumberSetting("Size", 1.0, 0.5, 2.5, 0.1);
    public final BooleanSetting bar = new BooleanSetting("Progress Bar", true);

    public ExplosionTimerModule() {
        super("Explosion Timer", Category.HUD);
        addSetting(tnt);
        addSetting(minecarts);
        addSetting(creepers);
        addSetting(range);
        addSetting(size);
        addSetting(bar);
    }
}
