package com.vortex.client.hud;

import com.mojang.blaze3d.vertex.PoseStack;
import com.vortex.client.module.ModuleManager;
import com.vortex.client.module.modules.PearlTrackerModule;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Flugbahnen fremder Enderperlen (siehe PearlTrackerModule). */
public final class PearlTracker {

    private PearlTracker() {}

    private record Track(String owner, List<Vec3> points, Vec3 land, int ticks) {}

    private static volatile List<Track> tracks = List.of();

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(PearlTracker::tick);
        LevelRenderEvents.AFTER_TRANSLUCENT_FEATURES.register(context -> {
            List<Track> list = tracks;
            if (list.isEmpty()) return;
            PearlTrackerModule m = ModuleManager.INSTANCE.get(PearlTrackerModule.class);
            if (m == null || !m.isEnabled()) return;
            Minecraft mc = Minecraft.getInstance();
            PoseStack matrices = context.poseStack();
            SubmitNodeCollector collector = context.submitNodeCollector();
            if (matrices == null || collector == null || mc.player == null) return;
            try {
                float tickDelta = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
                Vec3 cam = EspRender.cameraOffset(mc, tickDelta);
                int color = m.color.get();
                if ((color >>> 24) == 0) color |= 0xFF000000;
                final int c = color;
                final float lw = m.lineWidth.getFloat();
                for (Track t : list) {
                    final List<Vec3> pts = t.points();
                    if (pts.size() > 1) {
                        EspRender.submitLines(collector, matrices, (matrix, lines) -> {
                            for (int i = 1; i < pts.size(); i++) EspRender.drawTracer(matrix, lines, pts.get(i - 1), pts.get(i), cam, c, lw);
                        });
                    }
                    if (t.land() != null) {
                        Vec3 l = t.land();
                        EspRender.submitBox(collector, matrices, new AABB(l.x - 0.3, l.y, l.z - 0.3, l.x + 0.3, l.y + 1.8, l.z + 0.3), cam, c, lw);
                    }
                }
            } catch (Throwable e) {
                com.vortex.client.core.Errors.report("PearlTracker", e);
            }
        });
    }

    private static void tick(Minecraft mc) {
        PearlTrackerModule m = ModuleManager.INSTANCE.get(PearlTrackerModule.class);
        if (m == null || !m.isEnabled() || mc.level == null || mc.player == null) { tracks = List.of(); return; }
        List<Track> out = new ArrayList<>();
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof ThrownEnderpearl pearl) || pearl.isRemoved()) continue;
            Entity owner = pearl.getOwner();
            if (owner == mc.player && !m.showOwn.get()) continue;
            out.add(simulate(mc, pearl, owner == null ? "Someone" : owner.getName().getString()));
            if (out.size() >= 8) break;
        }
        tracks = out;
    }

    /** Wie ThrowableProjectile.tick: Schwerkraft, Widerstand, dann Bewegung (mit Blockkollision). */
    private static Track simulate(Minecraft mc, ThrownEnderpearl pearl, String owner) {
        List<Vec3> pts = new ArrayList<>();
        Vec3 pos = pearl.position();
        Vec3 v = pearl.getDeltaMovement();
        pts.add(pos);
        for (int i = 0; i < 300; i++) {
            v = v.add(0, -0.03, 0);
            boolean water = mc.level.getFluidState(BlockPos.containing(pos)).is(FluidTags.WATER);
            v = v.scale(water ? 0.8 : 0.99);
            Vec3 next = pos.add(v);
            BlockHitResult hit = mc.level.clip(new ClipContext(pos, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, pearl));
            if (hit.getType() != HitResult.Type.MISS) {
                pts.add(hit.getLocation());
                return new Track(owner, pts, hit.getLocation(), i + 1);
            }
            pos = next;
            pts.add(pos);
            if (pos.y < mc.level.getMinY() - 16) break;
        }
        return new Track(owner, pts, null, -1);
    }

    /** Hinweis oben: "<Name>s Perle -> x y z (12 m von dir, 0,8 s)". */
    public static void render(GuiGraphicsExtractor ctx, Minecraft mc) {
        List<Track> list = tracks;
        if (list.isEmpty() || mc.player == null) return;
        PearlTrackerModule m = ModuleManager.INSTANCE.get(PearlTrackerModule.class);
        if (m == null || !m.isEnabled() || !m.landingText.get()) return;
        int y = 22;
        for (Track t : list) {
            if (t.land() == null) continue;
            Vec3 l = t.land();
            double dist = Math.sqrt(mc.player.distanceToSqr(l));
            boolean near = m.warnNear.get() && dist < 8;
            String s = String.format(Locale.ROOT, "%s's pearl  ->  %d %d %d   (%.0f m, %.1fs)", t.owner(),
                    (int) Math.floor(l.x), (int) Math.floor(l.y), (int) Math.floor(l.z), dist, t.ticks() / 20f);
            if (near) s = "! " + s + " !";
            int w = mc.font.width(s);
            int x = (ctx.guiWidth() - w) / 2;
            ctx.fill(x - 4, y - 3, x + w + 4, y + 10, near ? 0xA0501020 : 0x90101018);
            ctx.text(mc.font, Component.literal(s), x, y, near ? 0xFFFF7A7A : (m.color.get() | 0xFF000000), true);
            y += 15;
        }
    }
}
