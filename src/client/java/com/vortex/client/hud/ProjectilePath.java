package com.vortex.client.hud;

import com.vortex.client.module.ModuleManager;
import com.vortex.client.module.modules.ProjectilePathModule;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.phys.AABB;
import java.util.ArrayList;
import java.util.List;

/**
 * Berechnet und zeichnet die Flugbahn des Wurfobjekts in der Hand.
 *
 * Die Rechnung bildet Minecrafts eigene Wurfphysik nach:
 *   - Startpunkt: knapp unter Augenhoehe
 *   - Startgeschwindigkeit: Blickrichtung mal einer Konstanten je Wurfobjekt,
 *     dazu die eigene Bewegung (das macht Vanilla ebenso)
 *   - pro Tick: Position um die Geschwindigkeit versetzen, dann Luftwiderstand
 *     (Faktor 0.99) und Schwerkraft anwenden
 *
 * Abgebrochen wird, sobald ein fester Block getroffen wird oder die Vorausschau
 * aufgebraucht ist. Das ist reine Vorschau-Mathematik im Client -- es wird nichts
 * gesendet und nichts veraendert.
 */
public final class ProjectilePath {

    /** Eigenschaften eines Wurfobjekts: Startgeschwindigkeit, Schwerkraft, Neigung. */
    private static final class Kind {
        final double speed, gravity, pitchOffset;
        Kind(double speed, double gravity, double pitchOffset) {
            this.speed = speed; this.gravity = gravity; this.pitchOffset = pitchOffset;
        }
    }

    private static final double DRAG = 0.99;

    /** Immutable extraction result used by the deferred 26.2 draw callback. */
    private record PathResult(List<Vec3> points, Vec3 hit) {}

    private ProjectilePath() {}

    /**
     * One-entry memo for kindOf: the registry lookup, the id-string build and
     * the string switch ran on EVERY FRAME for an item that changes at human
     * speed. Identity compare on the Item is enough -- items are singletons.
     * (Items.* constants were checked as an alternative: ENDER_PEARL etc. do
     * NOT exist under those names in the 1.21.11 mappings, so the id strings
     * stay and only run when the held item actually changes.)
     */
    private static net.minecraft.world.item.Item lastKindItem = null;
    private static Kind lastKind = null;

    /** bowKind memo: 0 = neither, 1 = bow, 2 = crossbow. */
    private static net.minecraft.world.item.Item lastHeldItem = null;
    private static int lastHeldType = 0;

    /** Ordnet einem Gegenstand seine Wurfeigenschaften zu (oder null). */
    private static Kind kindOf(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        net.minecraft.world.item.Item item = stack.getItem();
        if (item == lastKindItem) return lastKind;

        Kind k;
        String id;
        try {
            id = BuiltInRegistries.ITEM.getKey(item).toString();
        } catch (Throwable t) {
            return null;
        }
        switch (id) {
            case "minecraft:ender_pearl":
            case "minecraft:snowball":
            case "minecraft:egg":
                k = new Kind(1.5, 0.03, 0.0);
                break;
            case "minecraft:splash_potion":
            case "minecraft:lingering_potion":
                // Wurftraenke fliegen langsamer, faellen schneller und werden
                // leicht nach oben geworfen.
                k = new Kind(0.5, 0.05, -20.0);
                break;
            case "minecraft:experience_bottle":
                k = new Kind(0.7, 0.07, -20.0);
                break;
            default:
                k = null;
        }
        lastKindItem = item;
        lastKind = k;
        return k;
    }

    /**
     * Wurfeigenschaften fuer Pfeile, abhaengig vom Spannzustand.
     *
     * Beim Bogen waechst die Geschwindigkeit mit der Spanndauer (voll gespannt
     * entspricht Faktor 3.0). Die Armbrust schiesst dagegen immer mit fester
     * Geschwindigkeit. Wird gerade nicht gespannt, gibt es keine Vorschau --
     * eine Bahn ohne bekannte Geschwindigkeit waere geraten.
     */
    private static Kind bowKind(LocalPlayer self) {
        try {
            // Same one-entry memo as kindOf: classify the held ITEM once,
            // re-run only when it changes. The draw progress below still
            // updates per frame -- only the registry+string work is memoized.
            net.minecraft.world.item.Item item = self.getMainHandItem().getItem();
            if (item != lastHeldItem) {
                String held = BuiltInRegistries.ITEM.getKey(item).toString();
                lastHeldType = "minecraft:crossbow".equals(held) ? 2
                        : "minecraft:bow".equals(held) ? 1 : 0;
                lastHeldItem = item;
            }
            boolean crossbow = lastHeldType == 2;
            boolean bow = lastHeldType == 1;
            if (!crossbow && !bow) return null;

            if (crossbow) {
                // Armbrust: feste Geschwindigkeit (3.15), Schwerkraft wie Pfeil.
                return new Kind(3.15, 0.05, 0.0);
            }

            if (!self.isUsingItem()) return null;
            // Spannfortschritt aus der bisherigen Nutzungsdauer.
            int useTicks = self.getUseItem().getUseDuration(self) - self.getTicksUsingItem();
            float pull = net.minecraft.world.item.BowItem.getPowerForTime(useTicks);
            if (pull <= 0.1f) return null;   // noch zu wenig gespannt
            return new Kind(pull * 3.0, 0.05, 0.0);
        } catch (Throwable t) {
            return null;
        }
    }

    public static void register() {
        LevelRenderEvents.AFTER_TRANSLUCENT_FEATURES.register(context -> {
            long pvpT0 = System.nanoTime();
            try {
            ProjectilePathModule mod =
                    ModuleManager.INSTANCE.get(ProjectilePathModule.class);
            if (mod == null || !mod.isEnabled()) return;

            Minecraft client = Minecraft.getInstance();
            if (client.player == null || client.level == null) return;

            // Welches Wurfobjekt liegt in der Hand?
            LocalPlayer self = client.player;
            Kind kind = kindOf(self.getMainHandItem());
            if (kind == null && mod.offHand.get()) {
                kind = kindOf(self.getOffhandItem());
            }
            // Pfeile: nur waehrend des Spannens, denn erst dann steht die
            // Geschwindigkeit fest.
            if (kind == null && mod.bow.get()) {
                kind = bowKind(self);
            }
            if (kind == null) return;

            PoseStack matrices = context.poseStack();
            SubmitNodeCollector collector = context.submitNodeCollector();
            if (matrices == null || collector == null) return;

            try {
                float tickDelta = client.getDeltaTracker().getGameTimeDeltaPartialTick(false);
                Vec3 cam = EspRender.cameraOffset(client, tickDelta);

                int color = mod.color.get();
                if ((color >>> 24) == 0) color |= 0xFF000000;
                float lineWidth = mod.lineWidth.getFloat();
                PathResult path = simulate(client, self, kind, mod.maxSteps.getInt());

                if (path.points().size() > 1) {
                    final List<Vec3> points = List.copyOf(path.points());
                    final Vec3 renderCam = cam;
                    final int renderColor = color;
                    final float renderWidth = lineWidth;
                    EspRender.submitLines(collector, matrices, (matrix, lines) -> {
                        for (int i = 1; i < points.size(); i++) {
                            EspRender.drawTracer(matrix, lines, points.get(i - 1), points.get(i),
                                    renderCam, renderColor, renderWidth);
                        }
                    });
                }

                // Kaestchen am Einschlagpunkt.
                Vec3 hit = path.hit();
                if (hit != null && mod.marker.get()) {
                    double s = 0.15;
                    AABB box = new AABB(hit.x - s, hit.y - s, hit.z - s,
                            hit.x + s, hit.y + s, hit.z + s);
                    EspRender.submitBox(collector, matrices, box, cam, color, lineWidth);
                }
            } catch (Throwable pvpErr) {
                com.vortex.client.core.Errors.report("ProjectilePath", pvpErr);
            }
                    } finally {
                com.vortex.client.core.Profiler.record("ProjectilePath",
                        System.nanoTime() - pvpT0);
            }
        });
    }

    /**
     * Rechnet die Bahn Schritt fuer Schritt durch und zeichnet dabei jedes
     * Teilstueck. Rueckgabe ist der Auftreffpunkt (oder null, wenn die
     * Vorausschau vorher endet).
     */
    private static PathResult simulate(Minecraft client, LocalPlayer self,
                                       Kind kind, int maxSteps) {
        float yawDeg = self.getYRot();
        float pitchDeg = self.getXRot() + (float) kind.pitchOffset;

        double yaw = Math.toRadians(yawDeg);
        double pitch = Math.toRadians(pitchDeg);
        double dirX = -Math.sin(yaw) * Math.cos(pitch);
        double dirY = -Math.sin(pitch);
        double dirZ = Math.cos(yaw) * Math.cos(pitch);

        double vx = dirX * kind.speed;
        double vy = dirY * kind.speed;
        double vz = dirZ * kind.speed;
        Vec3 own = self.getDeltaMovement();
        vx += own.x;
        vz += own.z;
        if (!self.onGround()) vy += own.y;

        double x = self.getX();
        double y = self.getEyeY() - 0.1;
        double z = self.getZ();
        List<Vec3> points = new ArrayList<>();
        Vec3 previous = new Vec3(x, y, z);
        points.add(previous);

        for (int i = 0; i < maxSteps; i++) {
            x += vx;
            y += vy;
            z += vz;
            vx *= DRAG;
            vy *= DRAG;
            vz *= DRAG;
            vy -= kind.gravity;

            Vec3 current = new Vec3(x, y, z);
            points.add(current);

            try {
                BlockPos blockPos = BlockPos.containing(current);
                if (!client.level.getBlockState(blockPos).isAir()) {
                    return new PathResult(points, current);
                }
                if (y < client.level.getMinY() - 8) {
                    return new PathResult(points, current);
                }
            } catch (Throwable ignored) {
                return new PathResult(points, current);
            }
            previous = current;
        }
        return new PathResult(points, null);
    }
}
