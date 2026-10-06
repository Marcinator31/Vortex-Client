package com.vortex.legacy.module.visual;

import com.vortex.legacy.core.Module;
import com.vortex.legacy.core.ModeSetting;
import com.vortex.legacy.core.NumberSetting;

/** Eigene Tageszeit und eigenes Wetter (nur bei dir). */
public class TimeChanger extends Module {
    public final ModeSetting time = add(new ModeSetting("Time", 1, "Server", "Day", "Sunset", "Night", "Custom"));
    public final NumberSetting custom = add(new NumberSetting("Custom Time", 6000, 0, 23999, 100).visibleIf(() -> time.is("Custom")));
    public final ModeSetting weather = add(new ModeSetting("Weather", 1, "Server", "Clear", "Rain"));

    public TimeChanger() { super("Time Changer", Category.VISUAL, "Choose your own time of day and weather."); }

    public boolean overridesTime() { return isEnabled() && !time.is("Server"); }
    public long timeOfDay() {
        if (time.is("Day")) return 6000;
        if (time.is("Sunset")) return 12500;
        if (time.is("Night")) return 18000;
        return custom.getInt();
    }

    @Override
    public void onTick() {
        if (mc.world == null) return;
        if (overridesTime()) mc.world.setTimeOfDay(timeOfDay());
        if (weather.is("Clear")) { mc.world.setRainGradient(0); mc.world.setThunderGradient(0); }
        else if (weather.is("Rain")) { mc.world.setRainGradient(1); }
    }
}
