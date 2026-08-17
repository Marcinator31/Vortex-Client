package com.vortex.client.hud;

import com.vortex.client.module.Module;
import com.vortex.client.module.ModuleManager;
import com.vortex.client.module.modules.HitboxModule;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexRendering;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;

/**
 * Zeichnet die Hitboxen aller Entities in der Welt (wie F3+B), aber
 * eingefaerbt nach Kategorie und einzeln im ClickGUI schaltbar.
 *
 * Verifiziert gegen die ECHTEN 1.21.11-Yarn-Mappings (build.4):
 *   - WorldRenderEvents liegt jetzt im Paket ...rendering.v1.world
 *   - Lines-RenderLayer: RenderLayers.lines() (frueher RenderLayer.getLines())
 *   - VertexRendering.drawOutline(MatrixStack, VertexConsumer, VoxelShape,
 *       double offsetX, offsetY, offsetZ, int color, float lineWidth)
 *     (frueher drawBox mit r,g,b,a -- in 1.21.11 ein gepackter int + lineWidth)
 *   - VoxelShapes.cuboid(Box) wandelt die Entity-Box in eine VoxelShape
 *   - Koordinaten relativ zur Kamera -> Kamera-Pos als Offset uebergeben
 */
public final class HitboxRenderer {

    public static void register() {
        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            HitboxModule mod = (HitboxModule) find(HitboxModule.class);
            if (mod == null || !mod.isEnabled()) return;

            MinecraftClient client = MinecraftClient.getInstance();
            if (client.world == null || client.player == null) return;

            MatrixStack matrices = context.matrices();
            VertexConsumerProvider consumers = context.consumers();
            if (matrices == null || consumers == null) return;

            long pvpT0 = System.nanoTime();
            try {
                // WICHTIG fuer ruckelfreie Hitboxen: denselben tickDelta fuer
                // Kamera UND Entity-Position verwenden. Sonst ruckelt die Box
                // zwischen Tick-Positionen, waehrend die Kamera smooth laeuft.
                float tickDelta = client.getRenderTickCounter().getTickProgress(false);

                // Kamera-Position (interpoliert) als Bezugspunkt.
                // Via the shared helper, which uses the real render camera and
                // handles freecam. Computing this locally from the player's
                // eyes shifted everything as soon as the view changed.
                Vec3d cam = EspRender.cameraOffset(client, tickDelta);
                // Bei aktiver Freecam ist die echte Kamera woanders -> deren
                // Position als Offset nutzen, sonst stehen die Boxen falsch.
                if (com.vortex.client.freecam.Freecam.isActive()) {
                    cam = com.vortex.client.freecam.Freecam.getPos();
                }

                // Linien-Buffer (1.21.11: RenderLayers.lines()).
                VertexConsumer lines = consumers.getBuffer(RenderLayers.lines());

                float lineWidth = mod.lineWidth.getFloat();

                // 2.28.0: entities from the per-tick EntityCache instead of a
                // client.world.getEntitiesByClass(...) query on EVERY FRAME.
                // That query walks the world's entity sections each time; the
                // cache is built once per tick and shared by all renderers --
                // which is exactly what core/EntityCache.java exists for.
                // The old 128-block query box is kept as a cheap per-axis
                // check. Trade-off: entities appear one tick late (50 ms),
                // same as every other cache-based renderer here.
                double range = 128.0;
                double px = client.player.getX();
                double py = client.player.getY();
                double pz = client.player.getZ();

                for (Entity entity : com.vortex.client.core.EntityCache.all()) {
                    if (entity == client.player) continue;
                    if (Math.abs(entity.getX() - px) > range
                            || Math.abs(entity.getY() - py) > range
                            || Math.abs(entity.getZ() - pz) > range) continue;
                    // Kategorie + Farbe bestimmen.
                    // Reihenfolge wichtig: erst Spieler, dann Monster, dann Tiere.
                    // 'Monster' ist ein Interface, das ALLE feindlichen Mobs
                    // markiert -- auch solche, die nicht von HostileEntity erben
                    // (z.B. Slimes, Magma-Wuerfel). Das war vorher der Grund,
                    // warum Slimes als "Sonstige" gezaehlt wurden.
                    int color;
                    if (entity instanceof PlayerEntity) {
                        if (!mod.showPlayers.get()) continue;
                        color = mod.playerColor.get();
                    } else if (entity instanceof Monster) {
                        if (!mod.showHostiles.get()) continue;
                        color = mod.hostileColor.get();
                    } else if (entity instanceof AnimalEntity || entity instanceof PassiveEntity) {
                        // AnimalEntity deckt klassische Tiere ab; PassiveEntity
                        // zusaetzlich friedliche Mobs wie z.B. Dorfbewohner.
                        if (!mod.showAnimals.get()) continue;
                        color = mod.animalColor.get();
                    } else {
                        if (!mod.showMisc.get()) continue;
                        color = mod.miscColor.get();
                    }

                    // Sicherstellen, dass Alpha gesetzt ist (sonst unsichtbar).
                    if ((color >>> 24) == 0) {
                        color = 0xFF000000 | color;
                    }

                    // Hitbox aus der INTERPOLIERTEN Position bauen (smooth),
                    // nicht aus getBoundingBox() (die nur pro Tick aktualisiert
                    // und deshalb bei Bewegung ruckelt).
                    Vec3d pos = entity.getLerpedPos(tickDelta);
                    double hw = entity.getWidth() / 2.0;   // halbe Breite
                    double h = entity.getHeight();         // Hoehe
                    Box box = new Box(
                            pos.x - hw, pos.y, pos.z - hw,
                            pos.x + hw, pos.y + h, pos.z + hw);
                    VoxelShape shape = VoxelShapes.cuboid(box);

                    // Box bleibt in Welt-Koords; Kamera-Pos als Offset.
                    VertexRendering.drawOutline(matrices, lines, shape,
                            -cam.x, -cam.y, -cam.z,
                            color, lineWidth);
                }
            } catch (Throwable ignored) {
                // Falls eine Render-Methode in dieser Version doch abweicht:
                // Hitboxen still ausfallen lassen, NICHT das Spiel crashen.
            } finally {
                com.vortex.client.core.Profiler.record("Hitboxes",
                        System.nanoTime() - pvpT0);
            }
        });
    }

    private static Module find(Class<? extends Module> type) {
        // Konstante Laufzeit statt die ganze Liste zu durchlaufen.
        return ModuleManager.INSTANCE.get(type);
    }
}
