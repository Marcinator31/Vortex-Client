package com.vortex.client.hud;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.vortex.client.module.ModuleManager;
import com.vortex.client.module.modules.ChunkBordersModule;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.phys.Vec3;

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
        LevelRenderEvents.AFTER_TRANSLUCENT_FEATURES.register(context -> {
            long pvpT0 = System.nanoTime();
            try {
                ChunkBordersModule mod = ModuleManager.INSTANCE.get(ChunkBordersModule.class);
                if (mod == null || !mod.isEnabled()) return;

                Minecraft client = Minecraft.getInstance();
                if (client.player == null || client.level == null) return;

                PoseStack matrices = context.poseStack();
                SubmitNodeCollector collector = context.submitNodeCollector();
                if (matrices == null || collector == null) return;

                float tickDelta = client.getDeltaTracker().getGameTimeDeltaPartialTick(false);
                Vec3 cam = EspRender.cameraOffset(client, tickDelta);
                int color = mod.color.get();
                if ((color >>> 24) == 0) color |= 0xFF000000;
                final int borderColor = color;
                float lineWidth = mod.lineWidth.getFloat();

                int playerChunkX = client.player.getBlockX() >> 4;
                int playerChunkZ = client.player.getBlockZ() >> 4;
                double playerY = client.player.getY();
                double height = mod.height.get();
                double yLow = playerY - height;
                double yHigh = playerY + height;
                int radius = mod.neighbors.get() ? 1 : 0;

                for (int dx = -radius; dx <= radius; dx++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        int chunkX = (playerChunkX + dx) << 4;
                        int chunkZ = (playerChunkZ + dz) << 4;
                        EspRender.submitLines(collector, matrices,
                                (matrix, lines) -> drawChunk(matrix, lines, cam,
                                        chunkX, chunkZ, yLow, yHigh, borderColor, lineWidth));
                    }
                }
            } catch (Throwable error) {
                com.vortex.client.core.Errors.report("ChunkBorders", error);
            } finally {
                com.vortex.client.core.Profiler.record("ChunkBorders",
                        System.nanoTime() - pvpT0);
            }
        });
    }

    /** Kanten eines einzelnen Chunks: vier Senkrechte + Rahmen oben und unten. */
    private static void drawChunk(org.joml.Matrix4f matrix, VertexConsumer lines, Vec3 cam,
                                  int x0, int z0, double yLow, double yHigh,
                                  int color, float lineWidth) {
        double x1 = x0 + 16.0;
        double z1 = z0 + 16.0;

        line(matrix, lines, cam, x0, yLow, z0, x0, yHigh, z0, color, lineWidth);
        line(matrix, lines, cam, x1, yLow, z0, x1, yHigh, z0, color, lineWidth);
        line(matrix, lines, cam, x0, yLow, z1, x0, yHigh, z1, color, lineWidth);
        line(matrix, lines, cam, x1, yLow, z1, x1, yHigh, z1, color, lineWidth);

        for (double y : new double[] { yLow, yHigh }) {
            line(matrix, lines, cam, x0, y, z0, x1, y, z0, color, lineWidth);
            line(matrix, lines, cam, x1, y, z0, x1, y, z1, color, lineWidth);
            line(matrix, lines, cam, x1, y, z1, x0, y, z1, color, lineWidth);
            line(matrix, lines, cam, x0, y, z1, x0, y, z0, color, lineWidth);
        }
    }

    private static void line(org.joml.Matrix4f matrix, VertexConsumer lines, Vec3 cam,
                             double x1, double y1, double z1,
                             double x2, double y2, double z2,
                             int color, float lineWidth) {
        EspRender.drawTracer(matrix, lines,
                new Vec3(x1, y1, z1), new Vec3(x2, y2, z2), cam, color, lineWidth);
    }
}
