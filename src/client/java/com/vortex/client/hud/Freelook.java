package com.vortex.client.hud;

import com.vortex.client.module.ModuleManager;
import com.vortex.client.module.modules.FreelookModule;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;

/**
 * Zustand von Freelook: ob aktiv, und wohin die Kamera gerade schaut.
 *
 * Die Maus wird in EntityLookMixin umgeleitet (dreht diese Werte statt des
 * Spielers), die Kamera in CameraMixin (nimmt diese Werte statt der des
 * Spielers). Die Ansicht von hinten berechnet Minecraft dann selbst aus der
 * Kamera-Drehung -- inklusive Abstand zu Waenden.
 */
public final class Freelook {

    private Freelook() {}

    private static boolean aktiv = false;
    private static float yaw, pitch;
    private static CameraType vorher = null;
    private static boolean tasteVorher = false;

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
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(Freelook::tick);
    }

    private static void tick(Minecraft mc) {
        FreelookModule m = ModuleManager.INSTANCE.get(FreelookModule.class);
        if (mc.player == null || m == null || !m.isEnabled()
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
            if (m.thirdPerson.get()) {
                vorher = mc.options.getCameraType();
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            }
            aktiv = true;
        } else if (!soll && aktiv) {
            beenden();
        }
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
