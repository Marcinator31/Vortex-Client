package com.vortex.legacy.module;

import com.vortex.legacy.core.ModuleManager;
import com.vortex.legacy.module.hud.*;
import com.vortex.legacy.module.pvp.*;
import com.vortex.legacy.module.visual.*;
import com.vortex.legacy.module.misc.*;

/** Alle Module des 1.8.9-Clients. */
public final class Modules {
    private Modules() {}

    public static void registerAll() {
        ModuleManager m = ModuleManager.INSTANCE;
        // HUD
        m.register(new FpsModule());
        m.register(new CpsModule());
        m.register(new KeystrokesModule());
        m.register(new CoordinatesModule());
        m.register(new PingModule());
        m.register(new ClockModule());
        m.register(new SpeedometerModule());
        m.register(new ComboModule());
        m.register(new ReachModule());
        m.register(new MemoryModule());
        m.register(new ServerAddressModule());
        m.register(new ArmorHudModule());
        m.register(new PotionEffectsModule());
        m.register(new CompassBarModule());
        m.register(new TargetInfoModule());
        m.register(new InventoryHudModule());
        m.register(new ItemCounterModule());
        m.register(new ArmorWarningModule());
        m.register(new SaturationModule());
        m.register(new SessionStatsModule());
        m.register(new TpsModule());
        // PvP
        m.register(new ToggleSprint());
        m.register(new ToggleSneak());
        m.register(new Crosshair());
        m.register(new HitColor());
        m.register(new Hitboxes());
        m.register(new HitEffects());
        // Visual
        m.register(new Zoom());
        m.register(new Fullbright());
        m.register(new Freelook());
        m.register(new TimeChanger());
        m.register(new SimpleToggles.NoFog());
        m.register(new SimpleToggles.ClearWater());
        m.register(new SimpleToggles.ClearLava());
        m.register(new SimpleToggles.LowFire());
        m.register(new SimpleToggles.NoPumpkinBlur());
        m.register(new SimpleToggles.NoHurtCam());
        m.register(new SimpleToggles.NoBob());
        m.register(new BlockOutline());
        m.register(new ChunkBorders());
        // Misc
        m.register(new Chat());
        m.register(new AutoGG());
        m.register(new AutoReconnect());
    }
}
