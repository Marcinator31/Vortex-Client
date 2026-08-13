package com.vortex.client.hud;

import com.vortex.client.module.Module;
import com.vortex.client.module.ModuleManager;
import com.vortex.client.module.modules.BlockEspModule;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexRendering;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.block.BlockState;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Block-ESP mit Outlines um ausgewaehlte Bloecke.
 *
 * PERFORMANCE -- der entscheidende Punkt fuer "keine FPS-Drops": Die Welt-Suche
 * laeuft in einem EIGENEN HINTERGRUND-THREAD, nicht im Render-Thread. Der
 * Render-Thread zeichnet nur die fertige Box-Liste, die der Worker bereitstellt.
 * Dadurch hat das (teure) Scannen NULL Einfluss auf die Framerate -- egal wie
 * gross die Reichweite ist.
 *
 * Datenuebergabe: Der Worker baut eine neue Liste und legt sie atomar in eine
 * AtomicReference. Der Render-Thread liest sie nur. Kein gemeinsames
 * Veraendern, daher keine Sperren noetig.
 *
 * Welt-Lesen aus einem Fremd-Thread ist nicht offiziell unterstuetzt, in der
 * Praxis fuer reines Lesen aber tragbar; alle Zugriffe sind in try/catch
 * gekapselt, damit ein seltener Nebenlaeufigkeitsfehler nichts kaputt macht.
 */
public final class BlockEspRenderer {

    // Vom Worker befuelltes, vom Render-Thread gelesenes Ergebnis.
    private static final AtomicReference<List<Box>> RESULT =
            new AtomicReference<>(new ArrayList<>());

    // Maximale Anzahl Outlines (schuetzt sowohl Scan als auch Zeichnen).
    private static final int MAX_RESULTS = 4000;

    /**
     * Obergrenzen fuers ZEICHNEN. Jeder Kasten erzeugt ein Hilfsobjekt und
     * mehrere Linienzuege -- mehrere tausend pro Bild ergeben hunderttausende
     * Objekte pro Sekunde und koennen Speicher/Grafiktreiber ueberlasten
     * (harter Absturz ohne Crash-Report). Daher: nur Nahes, und gedeckelt.
     */
    private static final double MAX_DRAW_DIST = 96.0;
    private static final int MAX_DRAW_PER_FRAME = 500;

    private static volatile boolean running = false;
    private static Thread worker;

    public static void register() {
        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            long pvpT0 = System.nanoTime();
            try {
            BlockEspModule mod = (BlockEspModule) find(BlockEspModule.class);
            if (mod == null || !mod.isEnabled() || !mod.hasAnyBlock()) {
                return;
            }

            MinecraftClient client = MinecraftClient.getInstance();
            if (client.world == null || client.player == null) return;

            MatrixStack matrices = context.matrices();
            VertexConsumerProvider consumers = context.consumers();
            if (matrices == null || consumers == null) return;

            // Worker sicherstellen (laeuft dauerhaft, scannt im Hintergrund).
            ensureWorker();

            try {
                float tickDelta = client.getRenderTickCounter().getTickProgress(false);
                Vec3d cam = client.player.getCameraPosVec(tickDelta);
                if (com.vortex.client.freecam.Freecam.isActive()) {
                    cam = com.vortex.client.freecam.Freecam.getPos();
                }

                VertexConsumer lines = consumers.getBuffer(EspRenderLayer.espLines());

                int color = mod.getEspColor();
                if ((color >>> 24) == 0) color = 0xFF000000 | color;
                float lineWidth = mod.lineWidth.getFloat();

                // Nur die fertige Liste zeichnen -- KEINE Suche hier.
                List<Box> boxes = RESULT.get();
                double drawDist = mod.drawDistance.get();
                if (drawDist <= 0) drawDist = MAX_DRAW_DIST;
                double maxDistSq = drawDist * drawDist;
                int drawn = 0;
                for (int i = 0; i < boxes.size(); i++) {
                    if (drawn >= MAX_DRAW_PER_FRAME) break;
                    Box box = boxes.get(i);
                    // Entfernte Bloecke ueberspringen (spart Objekte + GPU-Last).
                    double ddx = box.minX + 0.5 - cam.x;
                    double ddy = box.minY + 0.5 - cam.y;
                    double ddz = box.minZ + 0.5 - cam.z;
                    if (ddx*ddx + ddy*ddy + ddz*ddz > maxDistSq) continue;
                    drawn++;
                    VoxelShape shape = VoxelShapes.cuboid(box);
                    VertexRendering.drawOutline(matrices, lines, shape,
                            -cam.x, -cam.y, -cam.z,
                            color, lineWidth);
                }

                // Optional: Tracer-Linien von der Sicht zu den Bloecken.
                if (mod.tracersEnabled() && !boxes.isEmpty()) {
                    int tColor = mod.getTracerColor();
                    if ((tColor >>> 24) == 0) tColor = 0xFF000000 | tColor;

                    // Startpunkt: knapp vor der Kamera in Blickrichtung, damit die
                    // Linie wie aus dem Fadenkreuz wirkt. Wir nehmen die Kamera-
                    // Blickrichtung aus yaw/pitch der echten Kamera.
                    Vec3d start = pvpclient$tracerStart(client, cam, tickDelta);

                    org.joml.Matrix4f mat = matrices.peek().getPositionMatrix();
                    float sr = ((tColor >> 16) & 0xFF) / 255.0f;
                    float sg = ((tColor >> 8) & 0xFF) / 255.0f;
                    float sb = (tColor & 0xFF) / 255.0f;
                    float sa = ((tColor >>> 24) & 0xFF) / 255.0f;
                    float tw = mod.lineWidth.getFloat();

                    int tDrawn = 0;
                    for (int i = 0; i < boxes.size(); i++) {
                        if (tDrawn >= MAX_DRAW_PER_FRAME) break;
                        Box box = boxes.get(i);
                        // Block-Mittelpunkt, relativ zur Kamera.
                        double ex = box.minX + 0.5 - cam.x;
                        double ey = box.minY + 0.5 - cam.y;
                        double ez = box.minZ + 0.5 - cam.z;
                        double sx = start.x - cam.x;
                        double sy = start.y - cam.y;
                        double sz = start.z - cam.z;

                        // Normal = normierte Richtung der Linie (vom Format verlangt).
                        double dx = ex - sx, dy = ey - sy, dz = ez - sz;
                        double len = Math.sqrt(dx*dx + dy*dy + dz*dz);
                        if (len < 1.0e-4) continue;
                        if (dx*dx + dy*dy + dz*dz > maxDistSq) continue;
                        tDrawn++;
                        float nx = (float) (dx / len);
                        float ny = (float) (dy / len);
                        float nz = (float) (dz / len);

                        // WICHTIG: Das Lines-Format verlangt pro Vertex auch
                        // lineWidth -- fehlt es, crasht das Rendering
                        // ("Missing elements in vertex: LineWidth").
                        lines.vertex(mat, (float) sx, (float) sy, (float) sz)
                                .color(sr, sg, sb, sa).normal(nx, ny, nz).lineWidth(tw);
                        lines.vertex(mat, (float) ex, (float) ey, (float) ez)
                                .color(sr, sg, sb, sa).normal(nx, ny, nz).lineWidth(tw);
                    }
                }
            } catch (Throwable pvpErr) {
                com.vortex.client.core.Errors.report("BlockEsp", pvpErr);
            }
                    } finally {
                com.vortex.client.core.Profiler.record("BlockEsp-Render",
                        System.nanoTime() - pvpT0);
            }
        });
    }

    /** Startet den Hintergrund-Worker einmalig. */
    private static void ensureWorker() {
        if (running) return;
        running = true;
        worker = new Thread(BlockEspRenderer::workerLoop, "pvpclient-blockesp");
        worker.setDaemon(true);
        worker.start();
    }

    /**
     * Endlosschleife im Hintergrund: scannt die Welt um den Spieler und legt das
     * Ergebnis ab. Laeuft mit kurzer Pause zwischen den Durchlaeufen, damit der
     * Thread nicht durchdreht.
     */
    private static void workerLoop() {
        while (true) {
            try {
                Thread.sleep(60); // ~16 Scans/Sekunde maximal

                MinecraftClient client = MinecraftClient.getInstance();
                BlockEspModule mod = (BlockEspModule) find(BlockEspModule.class);
                if (client == null || mod == null || !mod.isEnabled()
                        || !mod.hasAnyBlock()) {
                    if (!RESULT.get().isEmpty()) RESULT.set(new ArrayList<>());
                    continue;
                }
                ClientWorld world = client.world;
                if (world == null || client.player == null) {
                    if (!RESULT.get().isEmpty()) RESULT.set(new ArrayList<>());
                    continue;
                }

                int cx = (int) Math.floor(client.player.getX());
                int cy = (int) Math.floor(client.player.getY());
                int cz = (int) Math.floor(client.player.getZ());
                int range = mod.range.getInt();

                List<Box> found = scan(world, mod, cx, cy, cz, range);
                RESULT.set(found); // atomar uebergeben
            } catch (InterruptedException ie) {
                return;
            } catch (Throwable t) {
                // Nebenlaeufigkeitsfehler o.ae. -> Durchlauf ueberspringen.
            }
        }
    }

    /**
     * Durchsucht den Bereich RINGFOERMIG von innen nach aussen und veroeffentlicht
     * das Zwischenergebnis nach jedem Ring. Dadurch erscheinen nahe Bloecke quasi
     * sofort, ferne kommen Ring fuer Ring nach -- statt erst nach einem
     * kompletten (bei grosser Reichweite sekundenlangen) Scan alles auf einmal.
     */
    private static List<Box> scan(ClientWorld world, BlockEspModule mod,
                                  int cx, int cy, int cz, int range) {
        List<Box> out = new ArrayList<>();

        int worldMin = world.getBottomY();
        int worldMax = world.getTopYInclusive();

        // Hoehenbereich: Schnittmenge aus Welt, Einstellung und einer vertikalen
        // Begrenzung um den Spieler (sonst waechst das Suchvolumen ins Uferlose).
        int vRange = Math.min(range, 64);
        int yStart = Math.max(Math.max(cy - vRange, worldMin), mod.minY.getInt());
        int yEnd = Math.min(Math.min(cy + vRange, worldMax), mod.maxY.getInt());
        if (yStart > yEnd) return out;

        boolean onlyExposed = mod.onlyExposed.get();

        BlockPos.Mutable pos = new BlockPos.Mutable();

        // Merker fuer die Chunk-Pruefung: nicht geladene Chunks liefern bei
        // getBlockState einfach Luft -- Bloecke darin wuerden also stillschweigend
        // uebersehen. Die Pruefung passiert pro Chunk (nicht pro Block), deshalb
        // wird das letzte Ergebnis gemerkt.
        int lastChunkX = Integer.MIN_VALUE, lastChunkZ = Integer.MIN_VALUE;
        boolean lastChunkLoaded = false;

        // Ring 0 = nur die Mittelspalte, dann immer groessere Quadrat-Ringe.
        //
        // WICHTIG: Es wird gezielt nur der RAND jedes Rings abgelaufen. Frueher
        // lief die Schleife ueber die ganze Flaeche und verwarf das Innere wieder
        // -- bei Reichweite 64 waren das rund 366.000 statt 16.600 Spalten, also
        // 22-mal so viel Arbeit. Dadurch dauerte ein Durchlauf lange und die
        // angezeigten Bloecke hinkten der Wirklichkeit hinterher.
        for (int r = 0; r <= range; r++) {
            int steps = (r == 0) ? 1 : 8 * r;   // Anzahl Felder auf dem Rand
            for (int i = 0; i < steps; i++) {
                int dx, dz;
                if (r == 0) {
                    dx = 0; dz = 0;
                } else {
                    // Rand im Uhrzeigersinn ablaufen: oben, rechts, unten, links.
                    int side = i / (2 * r);        // 0..3
                    int off = i % (2 * r);         // Position auf dieser Seite
                    switch (side) {
                        case 0:  dx = -r + off; dz = -r;        break;
                        case 1:  dx = r;        dz = -r + off;  break;
                        case 2:  dx = r - off;  dz = r;         break;
                        default: dx = -r;       dz = r - off;   break;
                    }
                }
                {
                    int x = cx + dx;
                    int z = cz + dz;

                    // Chunk geladen? Sonst die ganze Spalte ueberspringen --
                    // spart Arbeit und macht klar, dass hier nicht "nichts" ist,
                    // sondern schlicht keine Daten vorliegen.
                    int chX = x >> 4, chZ = z >> 4;
                    if (chX != lastChunkX || chZ != lastChunkZ) {
                        lastChunkX = chX;
                        lastChunkZ = chZ;
                        try {
                            lastChunkLoaded = world.isChunkLoaded(chX, chZ);
                        } catch (Throwable t) {
                            lastChunkLoaded = false;
                        }
                    }
                    if (!lastChunkLoaded) continue;

                    for (int y = yStart; y <= yEnd; y++) {
                        pos.set(x, y, z);
                        BlockState state;
                        try {
                            state = world.getBlockState(pos);
                        } catch (Throwable t) {
                            continue; // Chunk evtl. gerade entladen
                        }
                        if (state.isAir()) continue;
                        Identifier id = Registries.BLOCK.getId(state.getBlock());
                        if (!mod.isBlockEnabled(id)) continue;
                        // Optional: nur Bloecke, die an mindestens einer Seite
                        // frei liegen. Die Pruefung passiert erst NACH dem Filter,
                        // laeuft also nur fuer die wenigen Treffer.
                        if (onlyExposed && !isExposed(world, x, y, z)) continue;
                        out.add(new Box(x, y, z, x + 1.0, y + 1.0, z + 1.0));
                        if (out.size() >= MAX_RESULTS) return out;
                    }
                }
            }
            // Nach jedem Ring das bisherige Ergebnis sichtbar machen -> nahe
            // Bloecke erscheinen sofort, der Rest fuellt sich auf. Nicht nach
            // JEDEM Ring (das waere bei grosser Reichweite viel Kopierarbeit),
            // sondern alle paar Ringe -- das reicht fuers Gefuehl von "instant".
            if ((r & 7) == 0) {
                RESULT.set(new ArrayList<>(out));
                // Gegen Freezes: Locks zwischendurch freigeben. getBlockState
                // greift live auf Chunk-Daten zu; ohne Pause haelt ein grosser
                // Scan (hohe Reichweite) die Locks zu lange und das Spiel
                // ruckelt periodisch.
                try { Thread.sleep(3); } catch (InterruptedException ie) { return out; }
            }
        }
        return out;
    }

    /**
     * Liegt der Block an mindestens einer der sechs Seiten frei (Luft daneben)?
     * Komplett eingeschlossene Bloecke koennen damit ausgeblendet werden.
     */
    private static boolean isExposed(ClientWorld world, int x, int y, int z) {
        BlockPos.Mutable p = new BlockPos.Mutable();
        int[][] dirs = {{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}};
        for (int[] d : dirs) {
            p.set(x + d[0], y + d[1], z + d[2]);
            try {
                if (world.getBlockState(p).isAir()) return true;
            } catch (Throwable t) {
                return true; // im Zweifel anzeigen
            }
        }
        return false;
    }

    /**
     * Startpunkt der Tracer: ein kleines Stueck vor der Kamera in Blickrichtung.
     * So scheinen die Linien aus dem Fadenkreuz zu kommen statt aus dem Auge.
     * Bei aktiver Freecam nutzen wir deren Blickrichtung.
     */
    private static Vec3d pvpclient$tracerStart(MinecraftClient client, Vec3d cam,
                                               float tickDelta) {
        float yaw, pitch;
        if (com.vortex.client.freecam.Freecam.isActive()) {
            yaw = com.vortex.client.freecam.Freecam.getYaw();
            pitch = com.vortex.client.freecam.Freecam.getPitch();
        } else {
            yaw = client.player.getYaw(tickDelta);
            pitch = client.player.getPitch(tickDelta);
        }
        double yawRad = Math.toRadians(yaw);
        double pitchRad = Math.toRadians(pitch);
        // Blickrichtungs-Vektor (Minecraft-Konvention).
        double fx = -Math.sin(yawRad) * Math.cos(pitchRad);
        double fy = -Math.sin(pitchRad);
        double fz = Math.cos(yawRad) * Math.cos(pitchRad);
        // 0.5 Bloecke vor die Kamera setzen.
        return new Vec3d(cam.x + fx * 0.5, cam.y + fy * 0.5, cam.z + fz * 0.5);
    }

    private static Module find(Class<? extends Module> type) {
        // Konstante Laufzeit statt die ganze Liste zu durchlaufen.
        return ModuleManager.INSTANCE.get(type);
    }
}
