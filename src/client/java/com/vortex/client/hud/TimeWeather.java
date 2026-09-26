package com.vortex.client.hud;

import com.vortex.client.module.ModuleManager;
import com.vortex.client.module.modules.TimeChangerModule;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;

/**
 * Eigene Tageszeit und eigenes Wetter (Modul Time Changer).
 *
 * UHRZEIT: ClockTimeMixin fragt uhrzeit() ab, sobald der Himmel die Uhr der
 * Dimension liest. Nichts am Spielstand wird veraendert, kein Paket des
 * Servers verworfen -- ausgeschaltet gilt sofort wieder die Serverzeit.
 *
 * WETTER: Regen- und Gewitterstaerke der Welt, jeden Tick gesetzt. Beim
 * Ausschalten gehen die Werte zurueck, die der Server zuletzt geschickt hat.
 */
public final class TimeWeather {

    private TimeWeather() {}

    private static boolean wetterAktiv = false;
    private static float alterRegen = 0f, alterDonner = 0f;
    /** Was wir zuletzt gesetzt haben -- weicht die Welt davon ab, hat der Server das Wetter geaendert. */
    private static float gesetztRegen = -1f, gesetztDonner = -1f;

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(TimeWeather::tick);
    }

    /**
     * Gewuenschte Uhrzeit innerhalb des Tages (0..23999, 0 = Sonnenaufgang)
     * oder -1 fuer "wie der Server".
     */
    public static long uhrzeit() {
        TimeChangerModule m = ModuleManager.INSTANCE.get(TimeChangerModule.class);
        if (m == null || !m.isEnabled()) return -1L;
        return m.wunschZeit();
    }

    private static void tick(Minecraft mc) {
        if (mc.level == null) {
            wetterAktiv = false;
            gesetztRegen = gesetztDonner = -1f;
            return;
        }
        TimeChangerModule m = ModuleManager.INSTANCE.get(TimeChangerModule.class);
        boolean an = m != null && m.isEnabled();

        String w = an ? m.weather.get() : "Server";
        if (!"Server".equals(w)) {
            float jetztRegen = mc.level.getRainLevel(1f);
            float jetztDonner = mc.level.getThunderLevel(1f);
            if (!wetterAktiv) {
                alterRegen = jetztRegen;
                alterDonner = jetztDonner;
                wetterAktiv = true;
            } else {
                // Hat der Server seit dem letzten Tick das Wetter geaendert,
                // steht jetzt SEIN Wert in der Welt -- den merken fuers Ausschalten.
                if (Math.abs(jetztRegen - gesetztRegen) > 0.001f) alterRegen = jetztRegen;
                if (Math.abs(jetztDonner - gesetztDonner) > 0.001f) alterDonner = jetztDonner;
            }
            float regen = "Clear".equals(w) ? 0f : 1f;
            float donner = "Thunder".equals(w) ? 1f : 0f;
            mc.level.setRainLevel(regen);
            mc.level.setThunderLevel(donner);
            gesetztRegen = regen;
            gesetztDonner = donner;
        } else if (wetterAktiv) {
            mc.level.setRainLevel(alterRegen);
            mc.level.setThunderLevel(alterDonner);
            wetterAktiv = false;
        }
    }

    /** Beim Ausschalten des Moduls: Serverwetter sofort zurueck. */
    public static void zuruecksetzen() {
        Minecraft mc = Minecraft.getInstance();
        if (wetterAktiv && mc.level != null) {
            mc.level.setRainLevel(alterRegen);
            mc.level.setThunderLevel(alterDonner);
            wetterAktiv = false;
        }
    }
}
