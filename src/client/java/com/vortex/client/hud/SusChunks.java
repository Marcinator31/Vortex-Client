package com.vortex.client.hud;

import com.vortex.client.module.Module;
import com.vortex.client.module.ModuleManager;
import com.vortex.client.module.modules.SusChunksModule;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.inventory.Inventory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Sus Chunks: faerbt Chunks als Heatmap nach Spieler-Aktivitaet.
 *
 * Hintergrund-Thread berechnet pro geladenem Chunk einen Score:
 *   Score = Container * 3 + sonstige Block-Entities * 1
 * (Container zaehlen staerker, weil sie der deutlichste Base-Indikator sind.)
 * Chunks ueber dem Mindest-Score bekommen eine vertikale Saeulen-Outline, deren
 * Farbe von gruen (wenig) ueber gelb nach rot (viel) geht.
 */
public final class SusChunks {

    private static final class ChunkMark {
        final Box box;
        final int color;
        ChunkMark(Box box, int color) { this.box = box; this.color = color; }
    }

    private static final AtomicReference<List<ChunkMark>> RESULT =
            new AtomicReference<>(new ArrayList<>());
    private static final int CHUNK_RADIUS = 10;

    private static volatile boolean running = false;
    private static Thread worker;

    public static void register() {
        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            SusChunksModule mod = (SusChunksModule) find(SusChunksModule.class);
            if (mod == null || !mod.isEnabled()) return;

            MinecraftClient client = MinecraftClient.getInstance();
            if (client.world == null || client.player == null) return;

            ensureWorker();

            MatrixStack matrices = context.matrices();
            VertexConsumerProvider consumers = context.consumers();
            if (matrices == null || consumers == null) return;

            try {
                float tickDelta = client.getRenderTickCounter().getTickProgress(false);
                Vec3d cam = EspRender.cameraOffset(client, tickDelta);
                VertexConsumer lines = consumers.getBuffer(EspRenderLayer.espLines());

                List<ChunkMark> marks = RESULT.get();
                for (int i = 0; i < marks.size(); i++) {
                    ChunkMark m = marks.get(i);
                    EspRender.drawBox(matrices, lines, m.box, cam, m.color, 2.0f);
                }
            } catch (Throwable pvpErr) {
                com.vortex.client.core.Errors.report("SusChunks", pvpErr);
            }
        });
    }

    private static void ensureWorker() {
        if (running) return;
        running = true;
        worker = new Thread(SusChunks::workerLoop, "vortexclient-suschunks");
        worker.setDaemon(true);
        worker.start();
    }

    private static void workerLoop() {
        while (true) {
            try {
                Thread.sleep(400);

                MinecraftClient client = MinecraftClient.getInstance();
                SusChunksModule mod = (SusChunksModule) find(SusChunksModule.class);
                if (client == null || mod == null || !mod.isEnabled()) {
                    if (!RESULT.get().isEmpty()) RESULT.set(new ArrayList<>());
                    continue;
                }
                ClientWorld world = client.world;
                if (world == null || client.player == null) {
                    if (!RESULT.get().isEmpty()) RESULT.set(new ArrayList<>());
                    continue;
                }

                int minScore = mod.getMinScore();
                int maxScore = Math.max(mod.getMaxScore(), minScore + 1);
                int minY = world.getBottomY();
                int maxY = world.getTopYInclusive();

                // Daten aus der sicheren Momentaufnahme (Haupt-Thread).
                WorldScan.Snapshot snap = WorldScan.get();
                if (snap.chunkCounts.isEmpty()) {
                    if (!RESULT.get().isEmpty()) RESULT.set(new ArrayList<>());
                    continue;
                }

                List<ChunkMark> marks = new ArrayList<>();
                for (java.util.Map.Entry<Long, int[]> e : snap.chunkCounts.entrySet()) {
                    int[] c = e.getValue();
                    // Kisten zaehlen dreifach, sonstige Block-Entities einfach.
                    int score = c[0] * 3 + c[1];
                    if (score < minScore) continue;

                    long ck = e.getKey();
                    int ccx = (int) (ck >> 32);
                    int ccz = (int) ck;

                    // Score auf 0..1 normieren -> Heatmap-Farbe.
                    float t = (float) (score - minScore) / (float) (maxScore - minScore);
                    if (t > 1f) t = 1f;
                    int color = heatColor(t);

                    double x0 = ccx << 4, z0 = ccz << 4;
                    Box box = new Box(x0 + 0.5, minY, z0 + 0.5,
                            x0 + 15.5, maxY, z0 + 15.5);
                    marks.add(new ChunkMark(box, color));
                }

                RESULT.set(marks);
            } catch (InterruptedException ie) {
                return;
            } catch (Throwable pvpErr) {
                // Durchlauf ueberspringen.
            }
        }
    }

    /** Heatmap: t=0 -> gruen, t=0.5 -> gelb, t=1 -> rot. ARGB mit fixem Alpha. */
    private static int heatColor(float t) {
        int r, g;
        if (t < 0.5f) {
            // gruen -> gelb
            r = (int) (255 * (t / 0.5f));
            g = 255;
        } else {
            // gelb -> rot
            r = 255;
            g = (int) (255 * (1f - (t - 0.5f) / 0.5f));
        }
        int a = 0xC0; // leicht transparent
        return (a << 24) | (r << 16) | (g << 8);
    }

    private static Module find(Class<? extends Module> type) {
        // Konstante Laufzeit statt die ganze Liste zu durchlaufen.
        return ModuleManager.INSTANCE.get(type);
    }
}
