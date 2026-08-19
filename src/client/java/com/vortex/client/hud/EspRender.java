package com.vortex.client.hud;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

/**
 * Gemeinsame Render-Helfer fuer die verschiedenen ESP-Module (Container, Item,
 * Spawner, ...). Buendelt das Zeichnen von AABB-Outlines und Tracer-Linien, damit
 * der Code nicht in jedem Modul dupliziert wird.
 *
 * Alle Methoden zeichnen in den no-depth Linien-Layer (durch Waende sichtbar)
 * und sind freecam-kompatibel (der cam-Offset wird vom Aufrufer passend
 * uebergeben).
 */
public final class EspRender {

    private EspRender() {}

    /** Aktuelle Kamera-Position als Render-Offset (freecam-bewusst). */
    public static Vec3 cameraOffset(Minecraft client, float tickDelta) {
        // Freecam has its own position and takes precedence.
        if (com.vortex.client.freecam.Freecam.isActive()) {
            return com.vortex.client.freecam.Freecam.getPos();
        }

        // The REAL camera position, not the player's eyes.
        //
        // Those two are the same only in first person. In third person the
        // camera sits behind and above the player, and using the eye position
        // shifted every box, line and marker by exactly that offset — which is
        // why everything looked fine until the view was switched.
        try {
            var camera = client.gameRenderer.getMainCamera();
            if (camera != null) {
                Vec3 pos = ((com.vortex.client.mixin.client.CameraPosAccessor)
                        (Object) camera).vortex$getPos();
                if (pos != null) return pos;
            }
        } catch (Throwable pvpErr) {
            com.vortex.client.core.Errors.report("EspRender.camera", pvpErr);
        }

        // Fallback if the camera cannot be read for some reason.
        return client.player.getEyePosition(tickDelta);
    }

    /** Startpunkt fuer Tracer: knapp vor der Kamera in Blickrichtung. */
    public static Vec3 tracerStart(Minecraft client, Vec3 cam, float tickDelta) {
        float yaw, pitch;
        if (com.vortex.client.freecam.Freecam.isActive()) {
            yaw = com.vortex.client.freecam.Freecam.getYaw();
            pitch = com.vortex.client.freecam.Freecam.getPitch();
        } else {
            yaw = client.player.getViewYRot(tickDelta);
            pitch = client.player.getViewXRot(tickDelta);
        }
        double yawRad = Math.toRadians(yaw);
        double pitchRad = Math.toRadians(pitch);
        double fx = -Math.sin(yawRad) * Math.cos(pitchRad);
        double fy = -Math.sin(pitchRad);
        double fz = Math.cos(yawRad) * Math.cos(pitchRad);
        return new Vec3(cam.x + fx * 0.5, cam.y + fy * 0.5, cam.z + fz * 0.5);
    }

    /** Zeichnet die Outline einer AABB (Welt-Koordinaten), relativ zur Kamera. */
    public static void drawBox(PoseStack matrices, VertexConsumer lines,
                               AABB box, Vec3 cam, int argb, float lineWidth) {
        VoxelShape shape = Shapes.create(box);
        net.minecraft.client.renderer.ShapeRenderer.renderShape(
                matrices, lines, shape, -cam.x, -cam.y, -cam.z, argb, lineWidth);
    }

    /**
     * Zeichnet eine Tracer-Linie von start zu target (beide Welt-Koordinaten),
     * relativ zur Kamera. lineWidth ist Pflicht (sonst crasht das Lines-Format).
     */
    public static void drawTracer(org.joml.Matrix4f mat, VertexConsumer lines,
                                  Vec3 start, Vec3 target, Vec3 cam,
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

        lines.addVertex(mat, (float) sx, (float) sy, (float) sz)
                .setColor(r, g, b, a).setNormal(nx, ny, nz).setLineWidth(lineWidth);
        lines.addVertex(mat, (float) ex, (float) ey, (float) ez)
                .setColor(r, g, b, a).setNormal(nx, ny, nz).setLineWidth(lineWidth);
    }
}
