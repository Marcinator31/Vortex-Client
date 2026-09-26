package com.vortex.client.module.modules;

import com.vortex.client.core.setting.ModeSetting;
import com.vortex.client.core.setting.NumberSetting;
import com.vortex.client.module.Module;

/**
 * Tageszeit und Wetter nur bei dir -- der Server merkt nichts davon.
 *
 * EINSTELLUNGEN
 *   Time of Day   Server = wie der Server. Sonst eine feste Tageszeit:
 *                 Sunrise 06:00, Morning 08:00, Noon 12:00, Sunset 18:00,
 *                 Night 21:00, Midnight 00:00. "Custom" nimmt die Stunde
 *                 aus "Custom Hour", "Real Time" die Uhrzeit deines PCs.
 *   Custom Hour   Nur fuer "Custom": die Uhrzeit in Stunden (14.5 = 14:30).
 *   Weather       Server = wie der Server, sonst immer klar, Regen oder
 *                 Gewitter.
 */
public class TimeChangerModule extends Module {

    public final ModeSetting time = new ModeSetting("Time of Day", 3,
            "Server", "Sunrise", "Morning", "Noon", "Sunset", "Night", "Midnight", "Custom", "Real Time");
    public final NumberSetting customHour = new NumberSetting("Custom Hour", 12.0, 0.0, 23.75, 0.25);
    public final ModeSetting weather = new ModeSetting("Weather", 1, "Server", "Clear", "Rain", "Thunder");

    public TimeChangerModule() {
        super("Time Changer", Category.MISC);
        addSetting(time);
        addSetting(customHour);
        addSetting(weather);
    }

    /**
     * Gewuenschte Uhrzeit in Minecraft-Ticks (0 = 06:00 Sonnenaufgang,
     * 6000 = Mittag, 12000 = 18:00, 18000 = Mitternacht) oder -1 fuer
     * "wie der Server".
     */
    public long wunschZeit() {
        switch (time.get()) {
            case "Sunrise": return stunde(6.0);
            case "Morning": return stunde(8.0);
            case "Noon": return stunde(12.0);
            case "Sunset": return stunde(18.0);
            case "Night": return stunde(21.0);
            case "Midnight": return stunde(0.0);
            case "Custom": return stunde(customHour.get());
            case "Real Time": {
                java.time.LocalTime t = java.time.LocalTime.now();
                return stunde(t.getHour() + t.getMinute() / 60.0 + t.getSecond() / 3600.0);
            }
            default: return -1L;
        }
    }

    /** Uhrzeit in Stunden -> Minecraft-Ticks. Minecraft zaehlt ab 06:00. */
    private static long stunde(double h) {
        return Math.floorMod(Math.round((h - 6.0) * 1000.0), 24000L);
    }

    @Override
    protected void onDisable() {
        com.vortex.client.hud.TimeWeather.zuruecksetzen();
    }
}
