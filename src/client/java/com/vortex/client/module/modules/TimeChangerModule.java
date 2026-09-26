package com.vortex.client.module.modules;

import com.vortex.client.core.setting.BooleanSetting;
import com.vortex.client.core.setting.ColorSetting;
import com.vortex.client.core.setting.ModeSetting;
import com.vortex.client.core.setting.NumberSetting;
import com.vortex.client.module.Module;

/**
 * Tageszeit und Wetter nur bei dir -- der Server merkt nichts davon.
 *
 * Solange eine eigene Zeit gewaehlt ist, werden die Zeit-Updates des Servers
 * nicht uebernommen (nur fuer die Anzeige; die TPS-Messung sieht sie
 * weiterhin). Beim Ausschalten gilt sofort wieder die Zeit des Servers.
 */
public class TimeChangerModule extends Module {

    public final ModeSetting time = new ModeSetting("Time", 1, "Server", "Day", "Noon", "Sunset", "Night", "Midnight", "Custom");
    public final NumberSetting customTime = new NumberSetting("Custom Time", 6000, 0, 23999, 100);
    public final ModeSetting weather = new ModeSetting("Weather", 1, "Server", "Clear", "Rain", "Thunder");

    public TimeChangerModule() {
        super("Time Changer", Category.MISC);
        addSetting(time);
        addSetting(customTime);
        addSetting(weather);
    }

    /** Gewuenschte Weltzeit oder -1 fuer "wie der Server". */
    public long wunschZeit() {
        switch (time.get()) {
            case "Day": return 1000L;
            case "Noon": return 6000L;
            case "Sunset": return 12500L;
            case "Night": return 14000L;
            case "Midnight": return 18000L;
            case "Custom": return (long) customTime.get();
            default: return -1L;
        }
    }

    @Override
    protected void onDisable() {
        com.vortex.client.hud.TimeWeather.zuruecksetzen();
    }
}
