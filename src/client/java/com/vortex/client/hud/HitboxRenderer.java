package com.vortex.client.hud;

import com.vortex.client.module.Module;
import com.vortex.client.module.ModuleManager;
import com.vortex.client.module.modules.HitboxModule;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Zeichnet die Hitboxen aller Entities in der Welt (wie F3+B), aber
 * eingefaerbt nach Kategorie und einzeln im ClickGUI schaltbar.
 *
 * Verifiziert gegen die ECHTEN 1.21.11-Yarn-Mappings (build.4):
 *   - WorldRenderEvents liegt jetzt im Paket ...rendering.v1.world
 *   - Lines-RenderLayer: RenderTypes.lines() (frueher RenderLayer.getLines())
 *   - ShapeRenderer.renderShape(PoseStack, VertexConsumer, VoxelShape,
 *       double offsetX, offsetY, offsetZ, int color, float lineWidth)
 *     (frueher drawBox mit r,g,b,a -- in 1.21.11 ein gepackter int + lineWidth)
 *   - Shapes.create(AABB) wandelt die Entity-AABB in eine VoxelShape
 *   - Koordinaten relativ zur Kamera -> Kamera-Pos als Offset uebergeben
 */
public final class HitboxRenderer {

    public static void register() {
        LevelRenderEvents.AFTER_TRANSLUCENT_FEATURES.register(context -> {
            HitboxModule mod = (HitboxModule) find(HitboxModule.class);
            if (mod == null || !mod.isEnabled()) return;

            Minecraft client = Minecraft.getInstance();
            if (client.level == null || client.player == null) return;

            PoseStack matrices = context.poseStack();
            SubmitNodeCollector collector = context.submitNodeCollector();
            if (matrices == null || collector == null) return;

            long pvpT0 = System.nanoTime();
            try {
                // WICHTIG fuer ruckelfreie Hitboxen: denselben tickDelta fuer
                // Kamera UND Entity-Position verwenden. Sonst ruckelt die AABB
                // zwischen Tick-Positionen, waehrend die Kamera smooth laeuft.
                float tickDelta = client.getDeltaTracker().getGameTimeDeltaPartialTick(false);

                // Kamera-Position (interpoliert) als Bezugspunkt.
                // Via the shared helper, which uses the real render camera and
                // handles freecam. Computing this locally from the player's
                // eyes shifted everything as soon as the view changed.
                Vec3 cam = EspRender.cameraOffset(client, tickDelta);
                // Bei aktiver Freecam ist die echte Kamera woanders -> deren
                // Position als Offset nutzen, sonst stehen die Boxen falsch.
                if (com.vortex.client.freecam.Freecam.isActive()) {
                    cam = com.vortex.client.freecam.Freecam.getPos();
                }

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
                    // Reihenfolge wichtig: erst Spieler, dann Enemy, dann Tiere.
                    // 'Enemy' ist ein Interface, das ALLE feindlichen Mobs
                    // markiert -- auch solche, die nicht von HostileEntity erben
                    // (z.B. Slimes, Magma-Wuerfel). Das war vorher der Grund,
                    // warum Slimes als "Sonstige" gezaehlt wurden.
                    int color;
                    if (entity instanceof Player) {
                        if (!mod.showPlayers.get()) continue;
                        color = mod.playerColor.get();
                    } else if (entity instanceof Enemy) {
                        if (!mod.showHostiles.get()) continue;
                        color = mod.hostileColor.get();
                    } else if (entity instanceof Animal || entity instanceof AgeableMob) {
                        // Animal deckt klassische Tiere ab; AgeableMob
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
                    Vec3 pos = entity.getPosition(tickDelta);
                    double hw = entity.getBbWidth() / 2.0;   // halbe Breite
                    double h = entity.getBbHeight();         // Hoehe
                    AABB box = new AABB(
                            pos.x - hw, pos.y, pos.z - hw,
                            pos.x + hw, pos.y + h, pos.z + hw);
                    // AABB bleibt in Welt-Koordinaten; die zentrale 26.2-Hilfe
                    // reiht ihre durch Wände sichtbare Outline als Render-Node ein.
                    EspRender.submitBox(collector, matrices, box, cam, color, lineWidth);
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
