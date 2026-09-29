package com.vortex.client.hud;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;

/**
 * Gemeinsame Render-Helfer fuer die verschiedenen ESP-Module (Container, Item,
 * Spawner, ...). Buendelt das Zeichnen von AABB-Outlines und Tracer-Linien,
 * damit der Code nicht in jedem Modul dupliziert wird.
 *
 * Ab Minecraft 26.2 werden Weltobjekte nicht mehr direkt in eine
 * MultiBufferSource geschrieben. Stattdessen werden immutable Render-Nodes in
 * den SubmitNodeCollector eingereiht. Die Helfer halten die bisherige
 * Darstellung bei, verwenden aber die neue Extraction-/Draw-Pipeline.
 */
public final class EspRender {

    private EspRender() {}

    @FunctionalInterface
    public interface LineGeometry {
        void draw(org.joml.Matrix4f matrix, VertexConsumer vertices);
    }

    /** Aktuelle Kamera-Position als Render-Offset (freecam-bewusst). */
    public static Vec3 cameraOffset(Minecraft client, float tickDelta) {
        if (com.vortex.client.freecam.Freecam.isActive()) {
            return com.vortex.client.freecam.Freecam.getPos();
        }

        try {
            var camera = client.gameRenderer.mainCamera();
            if (camera != null) {
                Vec3 pos = ((com.vortex.client.mixin.client.CameraPosAccessor)
                        (Object) camera).vortex$getPos();
                if (pos != null) return pos;
            }
        } catch (Throwable pvpErr) {
            com.vortex.client.core.Errors.report("EspRender.camera", pvpErr);
        }

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

    /**
     * Reiht eine durch Wände sichtbare AABB-Outline ein. Die Box bleibt in
     * Weltkoordinaten; nur die kopierte Pose des Render-Nodes wird auf die
     * Kamera relativiert.
     */
    public static void submitBox(SubmitNodeCollector collector, PoseStack matrices,
                                 AABB box, Vec3 cam, int argb, float lineWidth) {
        submitBox(collector, matrices, box, cam, argb, lineWidth, true);
    }

    /**
     * Wie submitBox, aber mit Wahl der Ebene.
     *
     * @param durchWaende true = ESP-Verhalten (ueberall sichtbar),
     *                    false = wie die Vanilla-Hitbox, also von Bloecken
     *                    verdeckt
     */
    public static void submitBox(SubmitNodeCollector collector, PoseStack matrices,
                                 AABB box, Vec3 cam, int argb, float lineWidth,
                                 boolean durchWaende) {
        matrices.pushPose();
        try {
            matrices.translate(-cam.x, -cam.y, -cam.z);
            shapeOutline(collector, matrices, Shapes.create(box),
                    durchWaende ? EspRenderLayer.espLines() : EspRenderLayer.depthLines(),
                    argb, lineWidth);
        } finally {
            matrices.popPose();
        }
    }

    /**
     * Umriss einer Form (alle Kanten). 26.2 hat dafuer submitShapeOutline;
     * in 1.21.11 gibt es das noch nicht -- dort werden die Kanten selbst als
     * Linien eingereiht.
     */
    public static void shapeOutline(SubmitNodeCollector collector, PoseStack matrices,
                                    net.minecraft.world.phys.shapes.VoxelShape shape,
                                    net.minecraft.client.renderer.rendertype.RenderType type,
                                    int argb, float lineWidth) {
        //#if 26.2
        collector.submitShapeOutline(matrices, shape, type, argb, lineWidth, true);
        //#else
        //$ collector.submitCustomGeometry(matrices, type, (pose, vertices) ->
        //$         shape.forAllEdges((x1, y1, z1, x2, y2, z2) ->
        //$                 kante(pose.pose(), vertices, x1, y1, z1, x2, y2, z2, argb, lineWidth)));
        //#endif
    }

    /** Eine Linie in Modellkoordinaten (ohne Kamera-Abzug). */
    static void kante(org.joml.Matrix4f mat, VertexConsumer lines,
                      double x1, double y1, double z1, double x2, double y2, double z2,
                      int argb, float lineWidth) {
        float r = ((argb >> 16) & 0xFF) / 255.0f, g = ((argb >> 8) & 0xFF) / 255.0f, b = (argb & 0xFF) / 255.0f;
        float a = ((argb >>> 24) & 0xFF) / 255.0f;
        if (a == 0f) a = 1f;
        double dx = x2 - x1, dy = y2 - y1, dz = z2 - z1;
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1.0e-6) return;
        float nx = (float) (dx / len), ny = (float) (dy / len), nz = (float) (dz / len);
        lines.addVertex(mat, (float) x1, (float) y1, (float) z1).setColor(r, g, b, a).setNormal(nx, ny, nz).setLineWidth(lineWidth);
        lines.addVertex(mat, (float) x2, (float) y2, (float) z2).setColor(r, g, b, a).setNormal(nx, ny, nz).setLineWidth(lineWidth);
    }

    /**
     * Reiht gebündelte Liniengeometrie ein. Der Callback wird in der
     * Zeichenphase mit einer für den Node eingefrorenen Pose ausgeführt.
     */
    public static void submitLines(SubmitNodeCollector collector, PoseStack matrices,
                                   LineGeometry geometry) {
        collector.submitCustomGeometry(matrices, EspRenderLayer.espLines(),
                (pose, vertices) -> geometry.draw(pose.pose(), vertices));
    }

    /** Zeichnet eine Tracer-Linie in einen 26.2-Custom-Geometry-Callback. */
    public static void drawTracer(org.joml.Matrix4f mat, VertexConsumer lines,
                                  Vec3 start, Vec3 target, Vec3 cam,
                                  int argb, float lineWidth) {
        float r = ((argb >> 16) & 0xFF) / 255.0f;
        float g = ((argb >> 8) & 0xFF) / 255.0f;
        float b = (argb & 0xFF) / 255.0f;
        float a = ((argb >>> 24) & 0xFF) / 255.0f;
        if (a == 0f) a = 1f;

        double sx = start.x - cam.x;
        double sy = start.y - cam.y;
        double sz = start.z - cam.z;
        double ex = target.x - cam.x;
        double ey = target.y - cam.y;
        double ez = target.z - cam.z;
        double dx = ex - sx;
        double dy = ey - sy;
        double dz = ez - sz;
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1.0e-4) return;
        float nx = (float) (dx / len);
        float ny = (float) (dy / len);
        float nz = (float) (dz / len);

        lines.addVertex(mat, (float) sx, (float) sy, (float) sz)
                .setColor(r, g, b, a).setNormal(nx, ny, nz).setLineWidth(lineWidth);
        lines.addVertex(mat, (float) ex, (float) ey, (float) ez)
                .setColor(r, g, b, a).setNormal(nx, ny, nz).setLineWidth(lineWidth);
    }
}
