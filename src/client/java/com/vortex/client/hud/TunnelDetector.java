package com.vortex.client.hud;

import com.vortex.client.module.Module;
import com.vortex.client.module.ModuleManager;
import com.vortex.client.module.modules.TunnelDetectorModule;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Cave/Tunnel Detector: findet gerade, von Spielern gegrabene Tunnel.
 *
 * Algorithmus (im Hintergrund-Thread): Fuer jede Position unter "Max Y" wird
 * geprueft, ob dort ein 1x2-Tunnelsegment beginnt -- also Luft auf zwei Hoehen,
 * mit festem Boden, fester Decke und festen Seitenwaenden (genau 1 Block breit).
 * Von einem Startsegment aus wird in X- und in Z-Richtung verfolgt, wie weit
 * sich das gerade fortsetzt. Erreicht die Linie die Mindestlaenge, wird sie als
 * Tunnel markiert (eine Box ueber die ganze Linie).
 *
 * "Fester Block" = nicht-Luft (robust, ohne fragile isSolid-Abfragen). Die
 * Mindestlaenge haelt die Fehlalarme durch natuerliche Hoehlen gering.
 */
public final class TunnelDetector {

    private static final AtomicReference<List<Box>> RESULT =
            new AtomicReference<>(new ArrayList<>());

    private static final int RADIUS = 24;       // horizontaler Suchradius (reduziert)
    private static final int Y_DEPTH = 24;       // wie viele Y-Ebenen unter Max Y (reduziert)
    private static final int MAX_RESULTS = 200;

    private static volatile boolean running = false;
    private static Thread worker;

    public static void register() {
        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            TunnelDetectorModule mod = (TunnelDetectorModule) find(TunnelDetectorModule.class);
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

                int color = mod.getColor();
                if ((color >>> 24) == 0) color |= 0xFF000000;

                List<Box> boxes = RESULT.get();
                for (int i = 0; i < boxes.size(); i++) {
                    EspRender.drawBox(matrices, lines, boxes.get(i), cam, color, 2.0f);
                }
            } catch (Throwable pvpErr) {
                com.vortex.client.core.Errors.report("TunnelDetector", pvpErr);
            }
        });
    }

    private static void ensureWorker() {
        if (running) return;
        running = true;
        worker = new Thread(TunnelDetector::workerLoop, "vortexclient-tunneldetector");
        worker.setDaemon(true);
        worker.start();
    }

    private static void workerLoop() {
        while (true) {
            try {
                Thread.sleep(1500); // teurer Scan -> deutlich seltener (gegen Lag)

                MinecraftClient client = MinecraftClient.getInstance();
                TunnelDetectorModule mod = (TunnelDetectorModule) find(TunnelDetectorModule.class);
                if (client == null || mod == null || !mod.isEnabled()) {
                    if (!RESULT.get().isEmpty()) RESULT.set(new ArrayList<>());
                    continue;
                }
                ClientWorld world = client.world;
                if (world == null || client.player == null) {
                    if (!RESULT.get().isEmpty()) RESULT.set(new ArrayList<>());
                    continue;
                }

                int minLen = mod.getMinLength();
                int topY = Math.min(mod.getMaxY(), world.getTopYInclusive());
                int bottomY = Math.max(topY - Y_DEPTH, world.getBottomY() + 1);

                int px = client.player.getBlockX();
                int pz = client.player.getBlockZ();

                List<Box> found = new ArrayList<>();
                BlockPos.Mutable pos = new BlockPos.Mutable();

                // Bereits erfasste Startpunkte, damit ein Tunnel nicht mehrfach
                // (von jedem seiner Bloecke aus) gemeldet wird.
                java.util.HashSet<Long> used = new java.util.HashSet<>();

                for (int y = bottomY; y <= topY; y++) {
                    // WICHTIG gegen Freezes: nach jeder Y-Ebene kurz schlafen.
                    // getBlockState greift live auf Chunk-Daten zu und kann sich
                    // mit dem Render-Thread um Locks streiten. Ohne Pause haelt
                    // dieser Scan die Locks zu lange am Stueck -> das Spiel
                    // friert periodisch fuer ~1s ein. Die kurze Pause gibt die
                    // Locks frei und verteilt die Last.
                    try { Thread.sleep(8); } catch (InterruptedException ie) { return; }

                    // Merker fuer die Chunk-Pruefung (pro Chunk statt pro Block).
                    int lastCX = Integer.MIN_VALUE, lastCZ = Integer.MIN_VALUE;
                    boolean lastLoaded = false;

                    for (int dx = -RADIUS; dx <= RADIUS; dx++) {
                        for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                            int x = px + dx, z = pz + dz;

                            // Nicht geladene Chunks liefern bei getBlockState
                            // einfach Luft -- das saehe wie ein Tunnel aus. Also
                            // ueberspringen statt Falschmeldungen zu erzeugen.
                            int cX = x >> 4, cZ = z >> 4;
                            if (cX != lastCX || cZ != lastCZ) {
                                lastCX = cX;
                                lastCZ = cZ;
                                try {
                                    lastLoaded = world.isChunkLoaded(cX, cZ);
                                } catch (Throwable t) {
                                    lastLoaded = false;
                                }
                            }
                            if (!lastLoaded) continue;

                            long key = pack(x, y, z);
                            if (used.contains(key)) continue;
                            if (!isTunnelCell(world, pos, x, y, z)) continue;

                            // In +X-Richtung verfolgen.
                            int lenX = 1;
                            while (isTunnelCell(world, pos, x + lenX, y, z)
                                    && isStraightX(world, pos, x + lenX, y, z)) {
                                lenX++;
                            }
                            // In +Z-Richtung verfolgen.
                            int lenZ = 1;
                            while (isTunnelCell(world, pos, x, y, z + lenZ)
                                    && isStraightZ(world, pos, x, y, z + lenZ)) {
                                lenZ++;
                            }

                            if (lenX >= minLen) {
                                for (int i = 0; i < lenX; i++) used.add(pack(x + i, y, z));
                                found.add(new Box(x, y, z,
                                        x + lenX, y + 2.0, z + 1.0));
                            } else if (lenZ >= minLen) {
                                for (int i = 0; i < lenZ; i++) used.add(pack(x, y, z + i));
                                found.add(new Box(x, y, z,
                                        x + 1.0, y + 2.0, z + lenZ));
                            }
                            if (found.size() >= MAX_RESULTS) {
                                RESULT.set(found);
                                throw new StopScan();
                            }
                        }
                    }
                }

                RESULT.set(found);
            } catch (StopScan s) {
                // Limit erreicht -> Ergebnis steht schon.
            } catch (InterruptedException ie) {
                return;
            } catch (Throwable t) {
                // Durchlauf ueberspringen.
            }
        }
    }

    private static final class StopScan extends RuntimeException {}

    /**
     * Ist an (x,y,z) ein begehbares 1x2-Tunnelsegment? Luft auf y und y+1,
     * fester Boden (y-1) und feste Decke (y+2).
     */
    private static boolean isTunnelCell(ClientWorld world, BlockPos.Mutable pos,
                                        int x, int y, int z) {
        return isAir(world, pos, x, y, z)
                && isAir(world, pos, x, y + 1, z)
                && isSolid(world, pos, x, y - 1, z)
                && isSolid(world, pos, x, y + 2, z);
    }

    /** Fuer einen X-Tunnel: Seiten in Z-Richtung muessen fest sein (1 breit). */
    private static boolean isStraightX(ClientWorld world, BlockPos.Mutable pos,
                                       int x, int y, int z) {
        return isSolid(world, pos, x, y, z - 1)
                && isSolid(world, pos, x, y, z + 1);
    }

    /** Fuer einen Z-Tunnel: Seiten in X-Richtung muessen fest sein (1 breit). */
    private static boolean isStraightZ(ClientWorld world, BlockPos.Mutable pos,
                                       int x, int y, int z) {
        return isSolid(world, pos, x - 1, y, z)
                && isSolid(world, pos, x + 1, y, z);
    }

    private static boolean isAir(ClientWorld world, BlockPos.Mutable pos,
                                 int x, int y, int z) {
        pos.set(x, y, z);
        try {
            return world.getBlockState(pos).isAir();
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean isSolid(ClientWorld world, BlockPos.Mutable pos,
                                   int x, int y, int z) {
        pos.set(x, y, z);
        try {
            BlockState s = world.getBlockState(pos);
            // "Fest" = nicht Luft und keine Fluessigkeit. Robust ohne isSolid.
            return !s.isAir() && s.getFluidState().isEmpty();
        } catch (Throwable t) {
            return false;
        }
    }

    private static long pack(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38)
                | ((long) (z & 0x3FFFFFF) << 12)
                | (long) (y & 0xFFF);
    }

    private static Module find(Class<? extends Module> type) {
        // Konstante Laufzeit statt die ganze Liste zu durchlaufen.
        return ModuleManager.INSTANCE.get(type);
    }
}
