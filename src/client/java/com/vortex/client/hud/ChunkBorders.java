package com.vortex.client.hud;

import com.vortex.client.module.ModuleManager;
import com.vortex.client.module.modules.ChunkBordersModule;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;

/**
 * Zeichnet die Kanten des Chunks, in dem man steht (auf Wunsch auch der
 * Nachbarn).
 *
 * Gezeichnet werden die vier senkrechten Eckkanten sowie oben und unten je ein
 * Rahmen -- das reicht, um den Chunk raeumlich zu erfassen, ohne das Bild mit
 * Linien zuzustellen. Es wird nichts gesucht oder gespeichert, nur gezeichnet:
 * die Chunk-Position ergibt sich direkt aus der Spielerposition.
 */
public final class ChunkBorders {

    private ChunkBorders() {}

    public static void register() {
        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            long pvpT0 = System.nanoTime();
            try {
            ChunkBordersModule mod =
                    ModuleManager.INSTANCE.get(ChunkBordersModule.class);
            if (mod == null || !mod.isEnabled()) return;

            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player == null || client.world == null) return;

            MatrixStack matrices = context.matrices();
            var consumers = context.consumers();
            if (matrices == null || consumers == null) return;

            try {
                float tickDelta = client.getRenderTickCounter().getTickProgress(false);
                Vec3d cam = EspRender.cameraOffset(client, tickDelta);
                VertexConsumer lines = consumers.getBuffer(EspRenderLayer.espLines());
                org.joml.Matrix4f mat = matrices.peek().getPositionMatrix();

                int color = mod.color.get();
                if ((color >>> 24) == 0) color |= 0xFF000000;
                float lw = mod.lineWidth.getFloat();

                int pcx = client.player.getBlockX() >> 4;
                int pcz = client.player.getBlockZ() >> 4;

                double py = client.player.getY();
                double h = mod.height.get();
                double yLow = py - h;
                double yHigh = py + h;

                int radius = mod.neighbors.get() ? 1 : 0;
                for (int dx = -radius; dx <= radius; dx++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        drawChunk(mat, lines, cam,
                                (pcx + dx) << 4, (pcz + dz) << 4,
                                yLow, yHigh, color, lw);
                    }
                }
            } catch (Throwable pvpErr) {
                com.vortex.client.core.Errors.report("ChunkBorders", pvpErr);
            }
                    } finally {
                com.vortex.client.core.Profiler.record("ChunkBorders",
                        System.nanoTime() - pvpT0);
            }
        });
    }

    /** Kanten eines einzelnen Chunks: vier Senkrechte + Rahmen oben und unten. */
    private static void drawChunk(org.joml.Matrix4f mat, VertexConsumer lines, Vec3d cam,
                                  int x0, int z0, double yLow, double yHigh,
                                  int color, float lw) {
        double x1 = x0 + 16.0;
        double z1 = z0 + 16.0;

        // Senkrechte Eckkanten.
        line(mat, lines, cam, x0, yLow, z0, x0, yHigh, z0, color, lw);
        line(mat, lines, cam, x1, yLow, z0, x1, yHigh, z0, color, lw);
        line(mat, lines, cam, x0, yLow, z1, x0, yHigh, z1, color, lw);
        line(mat, lines, cam, x1, yLow, z1, x1, yHigh, z1, color, lw);

        // Rahmen unten und oben.
        for (double y : new double[] { yLow, yHigh }) {
            line(mat, lines, cam, x0, y, z0, x1, y, z0, color, lw);
            line(mat, lines, cam, x1, y, z0, x1, y, z1, color, lw);
            line(mat, lines, cam, x1, y, z1, x0, y, z1, color, lw);
            line(mat, lines, cam, x0, y, z1, x0, y, z0, color, lw);
        }
    }

    private static void line(org.joml.Matrix4f mat, VertexConsumer lines, Vec3d cam,
                             double x1, double y1, double z1,
                             double x2, double y2, double z2,
                             int color, float lw) {
        EspRender.drawTracer(mat, lines,
                new Vec3d(x1, y1, z1), new Vec3d(x2, y2, z2), cam, color, lw);
    }
}
