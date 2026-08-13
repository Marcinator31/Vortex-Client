package com.example.pvpclient.hud;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;

/**
 * Gemeinsame Render-Helfer fuer die verschiedenen ESP-Module (Container, Item,
 * Spawner, ...). Buendelt das Zeichnen von Box-Outlines und Tracer-Linien, damit
 * der Code nicht in jedem Modul dupliziert wird.
 *
 * Alle Methoden zeichnen in den no-depth Linien-Layer (durch Waende sichtbar)
 * und sind freecam-kompatibel (der cam-Offset wird vom Aufrufer passend
 * uebergeben).
 */
public final class EspRender {

    private EspRender() {}

    /** Aktuelle Kamera-Position als Render-Offset (freecam-bewusst). */
    public static Vec3d cameraOffset(MinecraftClient client, float tickDelta) {
        Vec3d cam = client.player.getCameraPosVec(tickDelta);
        if (com.example.pvpclient.freecam.Freecam.isActive()) {
            cam = com.example.pvpclient.freecam.Freecam.getPos();
        }
        return cam;
    }

    /** Startpunkt fuer Tracer: knapp vor der Kamera in Blickrichtung. */
    public static Vec3d tracerStart(MinecraftClient client, Vec3d cam, float tickDelta) {
        float yaw, pitch;
        if (com.example.pvpclient.freecam.Freecam.isActive()) {
            yaw = com.example.pvpclient.freecam.Freecam.getYaw();
            pitch = com.example.pvpclient.freecam.Freecam.getPitch();
        } else {
            yaw = client.player.getYaw(tickDelta);
            pitch = client.player.getPitch(tickDelta);
        }
        double yawRad = Math.toRadians(yaw);
        double pitchRad = Math.toRadians(pitch);
        double fx = -Math.sin(yawRad) * Math.cos(pitchRad);
        double fy = -Math.sin(pitchRad);
        double fz = Math.cos(yawRad) * Math.cos(pitchRad);
        return new Vec3d(cam.x + fx * 0.5, cam.y + fy * 0.5, cam.z + fz * 0.5);
    }

    /** Zeichnet die Outline einer Box (Welt-Koordinaten), relativ zur Kamera. */
    public static void drawBox(MatrixStack matrices, VertexConsumer lines,
                               Box box, Vec3d cam, int argb, float lineWidth) {
        VoxelShape shape = VoxelShapes.cuboid(box);
        net.minecraft.client.render.VertexRendering.drawOutline(
                matrices, lines, shape, -cam.x, -cam.y, -cam.z, argb, lineWidth);
    }

    /**
     * Zeichnet eine Tracer-Linie von start zu target (beide Welt-Koordinaten),
     * relativ zur Kamera. lineWidth ist Pflicht (sonst crasht das Lines-Format).
     */
    public static void drawTracer(org.joml.Matrix4f mat, VertexConsumer lines,
                                  Vec3d start, Vec3d target, Vec3d cam,
                                  int argb, float lineWidth) {
        float r = ((argb >> 16) & 0xFF) / 255.0f;
        float g = ((argb >> 8) & 0xFF) / 255.0f;
        float b = (argb & 0xFF) / 255.0f;
        float a = ((argb >>> 24) & 0xFF) / 255.0f;
        if (a == 0f) a = 1f;

        double sx = start.x - cam.x, sy = start.y - cam.y, sz = start.z - cam.z;
        double ex = target.x - cam.x, ey = target.y - cam.y, ez = target.z - cam.z;
        double dx = ex - sx, dy = ey - sy, dz = ez - sz;
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1.0e-4) return;
        float nx = (float) (dx / len), ny = (float) (dy / len), nz = (float) (dz / len);

        lines.vertex(mat, (float) sx, (float) sy, (float) sz)
                .color(r, g, b, a).normal(nx, ny, nz).lineWidth(lineWidth);
        lines.vertex(mat, (float) ex, (float) ey, (float) ez)
                .color(r, g, b, a).normal(nx, ny, nz).lineWidth(lineWidth);
    }
}
