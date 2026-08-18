package com.vortex.client.hud;

import com.vortex.client.module.ModuleManager;
import com.vortex.client.waypoint.WaypointSettings;
import com.vortex.client.waypoint.WaypointManager;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.phys.Vec3;

/**
 * Zeichnet die Waypoints in der Welt: je Marker eine senkrechte Saeule aus vier
 * Linien, damit er auch aus der Ferne und ueber Gelaende hinweg auffaellt.
 *
 * Die Beschriftung und die Randpfeile laufen getrennt davon ueber das HUD (siehe
 * WaypointHud) -- dort laesst sich Text sauber und lesbar zeichnen.
 */
public final class WaypointRenderer {

    private WaypointRenderer() {}

    /** Aktuelle Dimension als Text, oder null wenn nicht ermittelbar. */
    public static String currentDimension(Minecraft client) {
        try {
            return client.level.dimension().location().toString();
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Kennung der aktuellen Welt: Server-Adresse bzw. Name der Einzelspieler-
     * Welt, zusammen mit der Dimension.
     *
     * Damit gelten Marker nur dort, wo sie gesetzt wurden. Vorher tauchten
     * Marker von einem Server auf jedem anderen wieder auf -- und beim Wechsel
     * zwischen Welten desselben Servers ebenso.
     *
     * Beispiele:
     *   "mp:donutsmp.net|minecraft:overworld"
     *   "sp:Meine Welt|minecraft:the_nether"
     */
    // Zwischengespeicherte Welt-Kennung.
    //
    // Diese Kennung wurde bisher bei JEDEM Bild neu gebaut -- mit
    // Server-Abfrage und Zusammensetzen von Zeichenketten, und das gleich
    // dreimal pro Bild (Welt-Render, HUD, naechster Marker). Das war eine der
    // Ursachen fuer das Ruckeln. Jetzt wird sie nur noch zweimal pro Sekunde
    // erneuert; oefter aendert sie sich ohnehin nicht.
    private static String cachedKey = null;
    private static long cachedAt = 0L;
    private static Object cachedWorld = null;

    public static String currentWorldKey(Minecraft client) {
        // Tied to the world object, not just to a timer.
        //
        // When a proxy moves you to another server the client throws the world
        // away and builds a new one. Recognising that here means the switch is
        // noticed the moment it happens, instead of up to half a second later
        // with markers from the previous server still on screen.
        Object world = client.level;
        long now = System.currentTimeMillis();
        if (cachedKey != null && world == cachedWorld && (now - cachedAt) < 500L) {
            return cachedKey;
        }
        String key = buildWorldKey(client);
        cachedKey = key;
        cachedWorld = world;
        cachedAt = now;
        return key;
    }

    /** Erzwingt eine Neuberechnung (z.B. beim Weltwechsel). */
    public static void invalidateWorldKey() {
        cachedKey = null;
        cachedWorld = null;
    }

    private static String buildWorldKey(Minecraft client) {
        // A profile the player set by hand always wins.
        String profile = com.vortex.client.waypoint.WorldProfiles.getActive();
        if (profile != null) {
            String d = currentDimension(client);
            return "pr:" + profile + "|" + (d == null ? "?" : d);
        }

        String place = "?";
        try {
            if (client.isLocalServer()) {
                var server = client.getSingleplayerServer();
                String name = (server != null && server.getWorldData() != null)
                        ? server.getWorldData().getLevelName() : "Singleplayer";
                place = "sp:" + name;
            } else {
                var entry = client.getCurrentServer();
                place = "mp:" + ((entry != null && entry.ip != null)
                        ? entry.ip : "unknown");
            }
        } catch (Throwable pvpErr) {
            com.vortex.client.core.Errors.report("WaypointRenderer.worldKey", pvpErr);
        }

        // The world seed, which tells backend servers apart.
        //
        // On a proxy network every server answers on the same address, so the
        // address alone cannot distinguish them — two worlds both called
        // "Spawn" looked like one place, and markers leaked across. The seed
        // is per world, arrives on join, and survives reconnects.
        String seed = worldSeed(client);

        // Fingerprint of the server itself, for the case where two backend
        // servers share a seed (flat lobby worlds often do).
        String fp = com.vortex.client.waypoint.ServerFingerprint.get();

        String dim = currentDimension(client);
        return place + (seed == null ? "" : "|s" + seed)
                     + (fp == null ? "" : "|f" + fp)
                     + "|" + (dim == null ? "?" : dim);
    }

    /** World seed as text, or null if it cannot be read. */
    private static String worldSeed(Minecraft client) {
        try {
            if (client.level == null) return null;
            var access = client.level.getBiomeManager();
            if (access == null) return null;
            long s = ((com.vortex.client.mixin.client.BiomeAccessSeedAccessor) (Object) access)
                    .vortex$getSeed();
            // Shortened: the full number adds nothing and makes the key unwieldy.
            return Long.toHexString(s).substring(0, Math.min(8, Long.toHexString(s).length()));
        } catch (Throwable pvpErr) {
            com.vortex.client.core.Errors.report("WaypointRenderer.seed", pvpErr);
            return null;
        }
    }

    public static void register() {
        LevelRenderEvents.AFTER_TRANSLUCENT_FEATURES.register(context -> {
            long pvpT0 = System.nanoTime();
            try {
            WaypointSettings mod = WaypointSettings.INSTANCE;
            if (!mod.isEnabled()) return;

            Minecraft client = Minecraft.getInstance();
            if (client.player == null || client.level == null) return;

            PoseStack matrices = context.poseStack();
            var consumers = context.bufferSource();
            if (matrices == null || consumers == null) return;

            try {
                float tickDelta = client.getFrameTime();
                Vec3 cam = EspRender.cameraOffset(client, tickDelta);
                VertexConsumer lines = consumers.getBuffer(EspRenderLayer.espLines());
                org.joml.Matrix4f mat = matrices.last().pose();

                float lw = mod.lineWidth.getFloat();
                double maxDist = mod.maxDistance.get();

                // Startpunkt immer berechnen -- einzelne Marker koennen einen
                // Tracer haben, auch wenn er global aus ist.
                Vec3 tracerStart = EspRender.tracerStart(client, cam, tickDelta);

                String dim = currentWorldKey(client);
                // Sichtweite fuer markierte Bloecke (einstellbar).
                double blockRange = mod.blockRadius.get();
                double blockRangeSq = blockRange * blockRange;
                double pxx = client.player.getX();
                double pyy = client.player.getY();
                double pzz = client.player.getZ();

                // Direkt ueber die Gesamtliste laufen statt jedes Bild eine gefilterte
                // Kopie anzulegen -- das sparte pro Sekunde hunderte kurzlebige Listen.
                for (WaypointManager.Waypoint w : WaypointManager.all()) {
                    if (!w.visible || !WaypointManager.matches(w, dim)) continue;
                    double cx = w.x + 0.5, cz = w.z + 0.5;
                    double dx = cx - client.player.getX();
                    double dz = cz - client.player.getZ();
                    double distSq = dx * dx + dz * dz;
                    if (maxDist > 0 && distSq > maxDist * maxDist) continue;

                    int color = w.color | 0xFF000000;

                    // Der Marker selbst wird als Punkt am Bildschirm gezeichnet
                    // (siehe WaypointHud) -- hier in der Welt nur noch die
                    // zugehoerigen Bloecke, und nur wenn man nah genug ist.
                    // Entfernung JE BLOCK pruefen, nicht nur zum Marker: eine
                    // Gruppe kann sich ueber mehrere Bloecke erstrecken, und es
                    // soll das gezeigt werden, was wirklich in der Naehe ist.
                    if (!w.blocks.isEmpty()) {
                        for (net.minecraft.core.BlockPos bp : w.blocks) {
                            double bdx = bp.getX() + 0.5 - pxx;
                            double bdy = bp.getY() + 0.5 - pyy;
                            double bdz = bp.getZ() + 0.5 - pzz;
                            if (bdx * bdx + bdy * bdy + bdz * bdz > blockRangeSq) continue;
                            net.minecraft.world.phys.AABB box =
                                    new net.minecraft.world.phys.AABB(
                                            bp.getX(), bp.getY(), bp.getZ(),
                                            bp.getX() + 1.0, bp.getY() + 1.0, bp.getZ() + 1.0);
                            // Eigene Farbe und Breite fuer Block-Umrandungen;
                            // bei Farbe 0 die des Markers verwenden.
                            int bc = mod.blockColor.get();
                            if ((bc >>> 24) == 0) bc = color;
                            EspRender.drawBox(matrices, lines, box, cam, bc,
                                    mod.blockLineWidth.getFloat());
                        }
                    }

                    // Tracer: global ODER fuer diesen Marker einzeln eingeschaltet.
                    if (tracerStart != null && (mod.tracers.get() || w.tracer)) {
                        EspRender.drawTracer(mat, lines, tracerStart,
                                new Vec3(cx, w.y, cz), cam, color, lw);
                    }
                }
            } catch (Throwable pvpErr) {
                com.vortex.client.core.Errors.report("Waypoints", pvpErr);
            }
                    } finally {
                com.vortex.client.core.Profiler.record("Waypoints",
                        System.nanoTime() - pvpT0);
            }
        });
    }
}
