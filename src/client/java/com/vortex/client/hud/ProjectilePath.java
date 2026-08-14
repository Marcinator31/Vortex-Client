package com.vortex.client.hud;

import com.vortex.client.module.ModuleManager;
import com.vortex.client.module.modules.ProjectilePathModule;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

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

    private ProjectilePath() {}

    /** Ordnet einem Gegenstand seine Wurfeigenschaften zu (oder null). */
    private static Kind kindOf(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        String id;
        try {
            id = Registries.ITEM.getId(stack.getItem()).toString();
        } catch (Throwable t) {
            return null;
        }
        switch (id) {
            case "minecraft:ender_pearl":
            case "minecraft:snowball":
            case "minecraft:egg":
                return new Kind(1.5, 0.03, 0.0);
            case "minecraft:splash_potion":
            case "minecraft:lingering_potion":
                // Wurftraenke fliegen langsamer, faellen schneller und werden
                // leicht nach oben geworfen.
                return new Kind(0.5, 0.05, -20.0);
            case "minecraft:experience_bottle":
                return new Kind(0.7, 0.07, -20.0);
            default:
                return null;
        }
    }

    /**
     * Wurfeigenschaften fuer Pfeile, abhaengig vom Spannzustand.
     *
     * Beim Bogen waechst die Geschwindigkeit mit der Spanndauer (voll gespannt
     * entspricht Faktor 3.0). Die Armbrust schiesst dagegen immer mit fester
     * Geschwindigkeit. Wird gerade nicht gespannt, gibt es keine Vorschau --
     * eine Bahn ohne bekannte Geschwindigkeit waere geraten.
     */
    private static Kind bowKind(ClientPlayerEntity self) {
        try {
            String held = Registries.ITEM.getId(self.getMainHandStack().getItem()).toString();
            boolean crossbow = "minecraft:crossbow".equals(held);
            boolean bow = "minecraft:bow".equals(held);
            if (!crossbow && !bow) return null;

            if (crossbow) {
                // Armbrust: feste Geschwindigkeit (3.15), Schwerkraft wie Pfeil.
                return new Kind(3.15, 0.05, 0.0);
            }

            if (!self.isUsingItem()) return null;
            // Spannfortschritt aus der bisherigen Nutzungsdauer.
            int useTicks = self.getActiveItem().getMaxUseTime(self) - self.getItemUseTime();
            float pull = net.minecraft.item.BowItem.getPullProgress(useTicks);
            if (pull <= 0.1f) return null;   // noch zu wenig gespannt
            return new Kind(pull * 3.0, 0.05, 0.0);
        } catch (Throwable t) {
            return null;
        }
    }

    public static void register() {
        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            long pvpT0 = System.nanoTime();
            try {
            ProjectilePathModule mod =
                    ModuleManager.INSTANCE.get(ProjectilePathModule.class);
            if (mod == null || !mod.isEnabled()) return;

            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player == null || client.world == null) return;

            // Welches Wurfobjekt liegt in der Hand?
            ClientPlayerEntity self = client.player;
            Kind kind = kindOf(self.getMainHandStack());
            if (kind == null && mod.offHand.get()) {
                kind = kindOf(self.getOffHandStack());
            }
            // Pfeile: nur waehrend des Spannens, denn erst dann steht die
            // Geschwindigkeit fest.
            if (kind == null && mod.bow.get()) {
                kind = bowKind(self);
            }
            if (kind == null) return;

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

                Vec3d hit = simulate(client, self, kind, mod.maxSteps.getInt(),
                        mat, lines, cam, color, lw);

                // Kaestchen am Einschlagpunkt.
                if (hit != null && mod.marker.get()) {
                    double s = 0.15;
                    Box box = new Box(hit.x - s, hit.y - s, hit.z - s,
                            hit.x + s, hit.y + s, hit.z + s);
                    EspRender.drawBox(matrices, lines, box, cam, color, lw);
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
    private static Vec3d simulate(MinecraftClient client, ClientPlayerEntity self,
                                  Kind kind, int maxSteps,
                                  org.joml.Matrix4f mat, VertexConsumer lines,
                                  Vec3d cam, int color, float lw) {
        float yawDeg = self.getYaw();
        float pitchDeg = self.getPitch() + (float) kind.pitchOffset;

        double yaw = Math.toRadians(yawDeg);
        double pitch = Math.toRadians(pitchDeg);

        // Blickrichtung als Einheitsvektor (selbst gerechnet, unabhaengig von
        // Hilfsmethoden der Spielklassen).
        double dirX = -Math.sin(yaw) * Math.cos(pitch);
        double dirY = -Math.sin(pitch);
        double dirZ = Math.cos(yaw) * Math.cos(pitch);

        double vx = dirX * kind.speed;
        double vy = dirY * kind.speed;
        double vz = dirZ * kind.speed;

        // Eigene Bewegung draufrechnen -- das macht Vanilla beim Werfen auch.
        Vec3d own = self.getVelocity();
        vx += own.x;
        vz += own.z;
        if (!self.isOnGround()) vy += own.y;

        double x = self.getX();
        double y = self.getEyeY() - 0.1;
        double z = self.getZ();

        Vec3d prev = new Vec3d(x, y, z);

        for (int i = 0; i < maxSteps; i++) {
            x += vx;
            y += vy;
            z += vz;

            vx *= DRAG;
            vy *= DRAG;
            vz *= DRAG;
            vy -= kind.gravity;

            Vec3d cur = new Vec3d(x, y, z);
            EspRender.drawTracer(mat, lines, prev, cur, cam, color, lw);
            prev = cur;

            // Auf festen Block getroffen?
            try {
                BlockPos bp = BlockPos.ofFloored(cur);
                if (!client.world.getBlockState(bp).isAir()) {
                    return cur;
                }
                // Unterhalb der Welt -> abbrechen.
                if (y < client.world.getBottomY() - 8) return cur;
            } catch (Throwable t) {
                return cur; // Chunk nicht geladen -> hier aufhoeren
            }
        }
        return null;
    }
}
