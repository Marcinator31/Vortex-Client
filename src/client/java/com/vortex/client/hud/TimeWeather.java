package com.vortex.client.hud;

import com.vortex.client.core.PacketHooks;
import com.vortex.client.module.ModuleManager;
import com.vortex.client.module.modules.TimeChangerModule;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;

/**
 * Eigene Tageszeit und eigenes Wetter (Modul Time Changer).
 *
 * ZEIT: In 26.2 richtet sich der Himmel nach der Weltzeit, die der Client in
 * seinen Weltdaten fuehrt. Die wird jeden Tick auf den Wunschwert gesetzt,
 * und die Zeit-Updates des Servers werden solange nicht uebernommen -- sonst
 * wuerde der Himmel jede Sekunde kurz zurueckspringen. Die letzte Serverzeit
 * wird aber mitgeschrieben, damit sie beim Ausschalten sofort wieder stimmt.
 *
 * WETTER: Regen- und Gewitterstaerke der Welt, ebenfalls jeden Tick. Beim
 * Ausschalten gehen die Werte zurueck, die vorher galten.
 */
public final class TimeWeather {

    private TimeWeather() {}

    private static volatile long serverZeit = -1;
    private static boolean zeitAktiv = false;
    private static boolean wetterAktiv = false;
    private static float alterRegen = 0f, alterDonner = 0f;
    /** Was wir zuletzt gesetzt haben -- weicht die Welt davon ab, hat der Server das Wetter geaendert. */
    private static float gesetztRegen = -1f, gesetztDonner = -1f;

    public static void register() {
        // Netzwerk-Thread: nur merken und ggf. verwerfen, nichts an der Welt.
        PacketHooks.onReceive(p -> {
            if (!(p instanceof ClientboundSetTimePacket zeit)) return false;
            TimeChangerModule m = ModuleManager.INSTANCE.get(TimeChangerModule.class);
            if (m == null || !m.isEnabled() || m.wunschZeit() < 0) return false;
            serverZeit = zeit.gameTime();
            return true;
        });

        ClientTickEvents.END_CLIENT_TICK.register(TimeWeather::tick);
    }

    private static void tick(Minecraft mc) {
        if (mc.level == null) {
            // Welt verlassen: alles vergessen, sonst kaeme beim naechsten
            // Server die Zeit und das Wetter des alten zurueck.
            zeitAktiv = false;
            wetterAktiv = false;
            serverZeit = -1;
            gesetztRegen = gesetztDonner = -1f;
            return;
        }
        TimeChangerModule m = ModuleManager.INSTANCE.get(TimeChangerModule.class);
        boolean an = m != null && m.isEnabled();

        long wunsch = an ? m.wunschZeit() : -1;
        if (wunsch >= 0) {
            if (!zeitAktiv) {
                if (serverZeit < 0) serverZeit = mc.level.getGameTime();
                zeitAktiv = true;
            }
            // Tage beibehalten, nur die Uhrzeit ersetzen -- sonst springt der
            // Mond in eine andere Phase.
            long tage = Math.max(0, serverZeit) / 24000L;
            mc.level.getLevelData().setGameTime(tage * 24000L + wunsch);
        } else if (zeitAktiv) {
            zeitZurueck(mc);
        }

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
                // steht jetzt SEIN Wert in der Welt. Den merken -- dann stimmt
                // das Wetter nach dem Ausschalten sofort, statt dass ein
                // veralteter Wert bis zum naechsten Wetterwechsel bleibt.
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

    private static void zeitZurueck(Minecraft mc) {
        if (serverZeit >= 0 && mc.level != null) {
            mc.level.getLevelData().setGameTime(serverZeit);
        }
        zeitAktiv = false;
        serverZeit = -1;
    }

    /** Beim Ausschalten des Moduls: Serverzeit und -wetter sofort zurueck. */
    public static void zuruecksetzen() {
        Minecraft mc = Minecraft.getInstance();
        if (zeitAktiv) zeitZurueck(mc);
        if (wetterAktiv && mc.level != null) {
            mc.level.setRainLevel(alterRegen);
            mc.level.setThunderLevel(alterDonner);
            wetterAktiv = false;
        }
    }
}
