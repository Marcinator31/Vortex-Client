package com.vortex.client.hud;

import com.vortex.client.module.ModuleManager;
import com.vortex.client.module.modules.FreelookModule;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Freelook: Taste halten und mit der Maus um dich herumschauen, waehrend du
 * weiter in dieselbe Richtung laeufst.
 *
 *  - EntityLookMixin: die Maus dreht diese Kamera-Werte statt des Spielers.
 *    Der Spieler (und damit die Laufrichtung) bleibt, wie er war.
 *  - CameraMixin: die Kamera nimmt diese Werte statt der des Spielers. Die
 *    Ansicht von hinten rechnet Minecraft daraus selbst, samt Abstand zu
 *    Waenden.
 *  - Taste loslassen: Kamera springt zurueck hinter den Spieler, die vorige
 *    Ansicht (Ego-Perspektive) kommt wieder.
 *
 * SELBSTPRUEFUNG: Dreht sich die Kamera trotz Mausbewegung nicht mit (etwa
 * weil eine andere Mod dieselbe Stelle belegt), wuerde man sonst in einer
 * starren Ansicht festhaengen, in der die Maus gar nichts tut. Das wird
 * erkannt, Freelook beendet sich und sagt es im Chat.
 */
public final class Freelook {

    private Freelook() {}

    private static boolean aktiv = false;
    private static float yaw, pitch;
    private static CameraType vorher = null;
    private static boolean tasteVorher = false;

    /** Kamera folgt nicht -- abgeschaltet, bis das Modul neu eingeschaltet wird. */
    private static boolean kaputt = false;
    private static float gedreht = 0f;

    public static boolean aktiv() {
        return aktiv;
    }

    public static float yaw() { return yaw; }
    public static float pitch() { return pitch; }

    /** Vom Maus-Mixin: Bewegung auf die Kamera statt auf den Spieler. */
    public static void drehe(double dx, double dy) {
        FreelookModule m = ModuleManager.INSTANCE.get(FreelookModule.class);
        float f = (m == null ? 1f : m.sensitivity.getFloat()) * 0.15f;
        yaw += (float) (dx * f);
        pitch = Math.max(-90f, Math.min(90f, pitch + (float) (dy * f)));
        gedreht += (float) (Math.abs(dx) + Math.abs(dy)) * f;
    }

    /** Beim Einschalten des Moduls: neuer Versuch, auch nach einem Fehlschlag. */
    public static void zuruecksetzen() {
        kaputt = false;
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(Freelook::tick);
    }

    private static void tick(Minecraft mc) {
        FreelookModule m = ModuleManager.INSTANCE.get(FreelookModule.class);
        if (mc.player == null || m == null || !m.isEnabled() || kaputt
                || com.vortex.client.freecam.Freecam.isActive()) {
            if (aktiv) beenden();
            tasteVorher = false;
            return;
        }
        int code = m.key.getKeyCode();
        boolean gedrueckt = mc.gui.screen() == null
                && code != org.lwjgl.glfw.GLFW.GLFW_KEY_UNKNOWN
                && com.mojang.blaze3d.platform.InputConstants.isKeyDown(mc.getWindow(), code);

        boolean soll;
        if (m.mode.getIndex() == 1) {
            soll = aktiv;
            if (gedrueckt && !tasteVorher) soll = !aktiv;
        } else {
            soll = gedrueckt;
        }
        tasteVorher = gedrueckt;

        if (soll && !aktiv) {
            yaw = mc.player.getYRot();
            pitch = mc.player.getXRot();
            gedreht = 0f;
            aktivSeit = System.currentTimeMillis();
            if (m.thirdPerson.get()) {
                vorher = mc.options.getCameraType();
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            }
            aktiv = true;
        } else if (!soll && aktiv) {
            beenden();
        }

        if (aktiv) pruefeKamera(mc);
    }

    /** Zuletzt, als der Kamera-Eingriff (CameraMixin) die Drehung gesetzt hat. */
    private static volatile long kameraZuletzt = 0;
    private static long aktivSeit = 0;

    /** Vom CameraMixin: die Kamera hat gerade unsere Drehung bekommen. */
    public static void kameraGreift() {
        kameraZuletzt = System.currentTimeMillis();
    }

    /**
     * SELBSTPRUEFUNG, NEU (4.5.2): Laeuft der Kamera-Eingriff ueberhaupt?
     *
     * Die alte Pruefung verglich Winkel -- bei schnellem Umsehen hinkt die
     * Kamera aber ein Bild hinterher, der Unterschied war dann groesser als
     * erlaubt, und Freelook schaltete sich bis zum Neustart ab. Genau das war
     * "oeffnet sich gar nicht mehr". Jetzt wird nur noch gefragt, ob der
     * Eingriff in der letzten Sekunde ueberhaupt einmal gelaufen ist. Tut er
     * das nie, ist die Stelle wirklich nicht erreichbar.
     */
    private static void pruefeKamera(Minecraft mc) {
        long jetzt = System.currentTimeMillis();
        if (jetzt - aktivSeit < 1500) return;          // erst nach 1,5 s urteilen
        if (jetzt - kameraZuletzt < 1000) return;      // Eingriff laeuft
        kaputt = true;
        beenden();
        if (mc.player != null) {
            mc.player.sendSystemMessage(Component.literal(
                    "\u00a7c[Freelook] The camera hook did not run -- another mod probably changes the camera. "
                    + "Turn Freelook off and on to try again."));
        }
    }

    private static float winkel(float w) {
        w %= 360f;
        if (w >= 180f) w -= 360f;
        if (w < -180f) w += 360f;
        return w;
    }

    /** Kamera zurueck, Ansicht wie vorher. */
    public static void beenden() {
        if (!aktiv) return;
        aktiv = false;
        Minecraft mc = Minecraft.getInstance();
        if (vorher != null && mc.options != null) {
            mc.options.setCameraType(vorher);
        }
        vorher = null;
    }
}
