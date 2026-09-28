package com.vortex.client.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

/**
 * Weltpunkt -> Bildschirmpunkt (GUI-Koordinaten), fuer Beschriftungen im HUD
 * (Schadenszahlen, Perlen-Ziel). Gleiche Rechnung wie WaypointHud: Kamera-
 * Position und -Richtung (auch in dritter Person und Freecam), Sichtfeld mit Zoom.
 *
 * Einmal pro Bild begin() aufrufen, dann beliebig oft project().
 */
public final class ScreenProject {

    private ScreenProject() {}

    private static double camX, camY, camZ, cy, sy, cp, sp, f;
    private static int w, h;

    public static void begin(Minecraft mc, int width, int height) {
        float tickDelta = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        Vec3 cam = EspRender.cameraOffset(mc, tickDelta);
        camX = cam.x; camY = cam.y; camZ = cam.z;
        float yaw, pitch;
        if (com.vortex.client.freecam.Freecam.isActive()) {
            yaw = com.vortex.client.freecam.Freecam.getYaw();
            pitch = com.vortex.client.freecam.Freecam.getPitch();
        } else {
            var camera = mc.gameRenderer.mainCamera();
            yaw = camera != null ? camera.yRot() : mc.player.getYRot();
            pitch = camera != null ? camera.xRot() : mc.player.getXRot();
        }
        double fov = 70.0;
        try { Object v = mc.options.fov().get(); if (v instanceof Number n) fov = n.doubleValue(); } catch (Throwable ignored) { }
        if (fov < 30.0 || fov > 150.0) fov = 70.0;
        double zoom = Zoom.factor();
        if (zoom > 1.001) fov /= zoom;
        double yr = Math.toRadians(yaw), pr = Math.toRadians(pitch);
        cy = Math.cos(yr); sy = Math.sin(yr); cp = Math.cos(pr); sp = Math.sin(pr);
        w = width; h = height;
        f = (h / 2.0) / Math.tan(Math.toRadians(fov) / 2.0);
    }

    /** {x, y, Abstand} oder null, wenn hinter der Kamera. */
    public static double[] project(double px, double py, double pz) {
        double dx = px - camX, dy = py - camY, dz = pz - camZ;
        double rx = dx * cy + dz * sy;
        double rz = -dx * sy + dz * cy;
        double ry = dy * cp + rz * sp;
        double rz2 = rz * cp - dy * sp;
        if (rz2 <= 0.05) return null;
        return new double[]{ w / 2.0 - rx * f / rz2, h / 2.0 - ry * f / rz2, Math.sqrt(dx * dx + dy * dy + dz * dz) };
    }
}
