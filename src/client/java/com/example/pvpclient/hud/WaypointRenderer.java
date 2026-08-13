package com.example.pvpclient.hud;

import com.example.pvpclient.module.ModuleManager;
import com.example.pvpclient.waypoint.WaypointSettings;
import com.example.pvpclient.waypoint.WaypointManager;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;

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
    public static String currentDimension(MinecraftClient client) {
        try {
            return client.world.getRegistryKey().getValue().toString();
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

    public static String currentWorldKey(MinecraftClient client) {
        long now = System.currentTimeMillis();
        if (cachedKey != null && (now - cachedAt) < 500L) return cachedKey;
        String key = buildWorldKey(client);
        cachedKey = key;
        cachedAt = now;
        return key;
    }

    /** Erzwingt eine Neuberechnung (z.B. beim Weltwechsel). */
    public static void invalidateWorldKey() {
        cachedKey = null;
    }

    private static String buildWorldKey(MinecraftClient client) {
        String place = "?";
        try {
            if (client.isInSingleplayer()) {
                var server = client.getServer();
                String name = (server != null && server.getSaveProperties() != null)
                        ? server.getSaveProperties().getLevelName() : "Einzelspieler";
                place = "sp:" + name;
            } else {
                var entry = client.getCurrentServerEntry();
                place = "mp:" + ((entry != null && entry.address != null)
                        ? entry.address : "unbekannt");
            }
        } catch (Throwable pvpErr) {
            com.example.pvpclient.core.Errors.report("WaypointRenderer.worldKey", pvpErr);
        }
        String dim = currentDimension(client);
        return place + "|" + (dim == null ? "?" : dim);
    }

    public static void register() {
        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            long pvpT0 = System.nanoTime();
            try {
            WaypointSettings mod = WaypointSettings.INSTANCE;
            if (!mod.isEnabled()) return;

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

                float lw = mod.lineWidth.getFloat();
                double half = mod.beamHeight.get() / 2.0;
                double maxDist = mod.maxDistance.get();

                // Startpunkt immer berechnen -- einzelne Marker koennen einen
                // Tracer haben, auch wenn er global aus ist.
                Vec3d tracerStart = EspRender.tracerStart(client, cam, tickDelta);

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
                        for (net.minecraft.util.math.BlockPos bp : w.blocks) {
                            double bdx = bp.getX() + 0.5 - pxx;
                            double bdy = bp.getY() + 0.5 - pyy;
                            double bdz = bp.getZ() + 0.5 - pzz;
                            if (bdx * bdx + bdy * bdy + bdz * bdz > blockRangeSq) continue;
                            net.minecraft.util.math.Box box =
                                    new net.minecraft.util.math.Box(
                                            bp.getX(), bp.getY(), bp.getZ(),
                                            bp.getX() + 1.0, bp.getY() + 1.0, bp.getZ() + 1.0);
                            EspRender.drawBox(matrices, lines, box, cam, color, lw);
                        }
                    }

                    // Tracer: global ODER fuer diesen Marker einzeln eingeschaltet.
                    if (tracerStart != null && (mod.tracers.get() || w.tracer)) {
                        EspRender.drawTracer(mat, lines, tracerStart,
                                new Vec3d(cx, w.y, cz), cam, color, lw);
                    }
                }
            } catch (Throwable pvpErr) {
                com.example.pvpclient.core.Errors.report("WaypointRender", pvpErr);
            }
                    } finally {
                com.example.pvpclient.core.Profiler.record("Waypoints",
                        System.nanoTime() - pvpT0);
            }
        });
    }
}
