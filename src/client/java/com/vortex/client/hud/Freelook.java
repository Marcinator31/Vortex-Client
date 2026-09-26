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

    /** Kamera folgt nicht -- fuer diese Sitzung abgeschaltet. */
    private static boolean kaputt = false;
    private static int abweichendeTicks = 0;
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
            abweichendeTicks = 0;
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

    /** Folgt die Kamera der Freelook-Drehung? Sonst abbrechen statt festhaengen. */
    private static void pruefeKamera(Minecraft mc) {
        try {
            var kamera = mc.gameRenderer.mainCamera();
            float diff = Math.abs(winkel(kamera.yRot() - yaw)) + Math.abs(kamera.xRot() - pitch);
            // Erst pruefen, wenn die Maus schon ein Stueck bewegt wurde --
            // sonst stimmen Kamera und Spieler ohnehin ueberein.
            if (gedreht > 10f && diff > 5f) abweichendeTicks++;
            else abweichendeTicks = 0;
            if (abweichendeTicks > 10) {
                kaputt = true;
                beenden();
                if (mc.player != null) {
                    mc.player.sendSystemMessage(Component.literal(
                            "§c[Freelook] The camera does not follow -- another mod probably uses the same spot. Freelook is off until restart."));
                }
            }
        } catch (Throwable pvpErr) {
            com.vortex.client.core.Errors.report("Freelook.pruefe", pvpErr);
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
