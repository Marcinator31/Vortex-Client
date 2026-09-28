package com.vortex.client.hud;

import com.mojang.blaze3d.vertex.PoseStack;
import com.vortex.client.module.ModuleManager;
import com.vortex.client.module.modules.LightLevelModule;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Light Level Overlay (siehe LightLevelModule).
 *
 * Alle 10 Ticks werden die Bloecke rund um den Spieler geprueft: Kann dort
 * ein Monster stehen (Boden taugt zum Spawnen, Platz frei, keine
 * Fluessigkeit) und wie hell ist es? Gezeichnet wird nur das Ergebnis der
 * letzten Pruefung -- das Bild kostet damit kaum etwas.
 */
public final class LightOverlay {

    private LightOverlay() {}

    /** art: 0 = spawnt immer, 1 = nur nachts, 2 = sicher. */
    private record Stelle(int x, int y, int z, int art, int licht) {}

    private static volatile List<Stelle> stellen = List.of();
    private static int takt = 0;

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(LightOverlay::tick);
        LevelRenderEvents.AFTER_TRANSLUCENT_FEATURES.register(context -> {
            try {
                LightLevelModule m = ModuleManager.INSTANCE.get(LightLevelModule.class);
                if (m == null || !m.isEnabled() || m.style.getIndex() == 2) return;
                List<Stelle> liste = stellen;
                if (liste.isEmpty()) return;
                Minecraft mc = Minecraft.getInstance();
                if (mc.player == null) return;
                PoseStack matrices = context.poseStack();
                SubmitNodeCollector collector = context.submitNodeCollector();
                if (matrices == null || collector == null) return;
                float td = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
                Vec3 cam = EspRender.cameraOffset(mc, td);
                int[] farben = { deckend(m.danger.get()), deckend(m.night.get()), deckend(m.safe.get()) };
                boolean kreuz = m.style.getIndex() == 0;
                EspRender.submitLines(collector, matrices, (mat, lines) -> {
                    for (Stelle s : liste) {
                        int c = farben[s.art()];
                        double y = s.y() + 0.02, x0 = s.x() + 0.15, z0 = s.z() + 0.15, x1 = s.x() + 0.85, z1 = s.z() + 0.85;
                        if (kreuz) {
                            EspRender.drawTracer(mat, lines, new Vec3(x0, y, z0), new Vec3(x1, y, z1), cam, c, 1.5f);
                            EspRender.drawTracer(mat, lines, new Vec3(x1, y, z0), new Vec3(x0, y, z1), cam, c, 1.5f);
                        } else {
                            EspRender.drawTracer(mat, lines, new Vec3(x0, y, z0), new Vec3(x1, y, z0), cam, c, 1.5f);
                            EspRender.drawTracer(mat, lines, new Vec3(x1, y, z0), new Vec3(x1, y, z1), cam, c, 1.5f);
                            EspRender.drawTracer(mat, lines, new Vec3(x1, y, z1), new Vec3(x0, y, z1), cam, c, 1.5f);
                            EspRender.drawTracer(mat, lines, new Vec3(x0, y, z1), new Vec3(x0, y, z0), cam, c, 1.5f);
                        }
                    }
                });
            } catch (Throwable e) {
                com.vortex.client.core.Errors.report("LightOverlay.render", e);
            }
        });
    }

    private static int deckend(int c) {
        return (c >>> 24) == 0 ? c | 0xFF000000 : c;
    }

    private static void tick(Minecraft mc) {
        LightLevelModule m = ModuleManager.INSTANCE.get(LightLevelModule.class);
        if (m == null || !m.isEnabled() || mc.player == null || mc.level == null) {
            if (!stellen.isEmpty()) stellen = List.of();
            return;
        }
        if (++takt % 10 != 0) return;
        long t0 = System.nanoTime();
        try {
            stellen = scannen(mc.level, mc.player.blockPosition(), m.range.getInt(), m.height.getInt(), m.showSafe.get());
        } catch (Throwable e) {
            com.vortex.client.core.Errors.report("LightOverlay.scan", e);
        } finally {
            com.vortex.client.core.Profiler.record("LightOverlay", System.nanoTime() - t0);
        }
    }

    private static List<Stelle> scannen(ClientLevel level, BlockPos mitte, int r, int h, boolean sichere) {
        List<Stelle> out = new ArrayList<>();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos unten = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos oben = new BlockPos.MutableBlockPos();
        int minY = Math.max(level.getMinY() + 1, mitte.getY() - h);
        int maxY = Math.min(level.getMaxY() - 1, mitte.getY() + h);
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (dx * dx + dz * dz > r * r) continue;
                int x = mitte.getX() + dx, z = mitte.getZ() + dz;
                if (!level.hasChunk(x >> 4, z >> 4)) continue;
                for (int y = minY; y <= maxY; y++) {
                    pos.set(x, y, z);
                    BlockState st = level.getBlockState(pos);
                    if (!st.getCollisionShape(level, pos).isEmpty() || !st.getFluidState().isEmpty()) continue;
                    unten.set(x, y - 1, z);
                    BlockState bo = level.getBlockState(unten);
                    if (!bo.isValidSpawn(level, unten, EntityType.ZOMBIE)) continue;
                    oben.set(x, y + 1, z);
                    BlockState ob = level.getBlockState(oben);
                    if (!ob.getCollisionShape(level, oben).isEmpty() || !ob.getFluidState().isEmpty()) continue;
                    int blockLicht = level.getBrightness(LightLayer.BLOCK, pos);
                    int art;
                    if (blockLicht == 0) art = level.getBrightness(LightLayer.SKY, pos) == 0 ? 0 : 1;
                    else if (sichere) art = 2;
                    else continue;
                    out.add(new Stelle(x, y, z, art, blockLicht));
                    if (out.size() >= 6000) return out;
                }
            }
        }
        return out;
    }

    /** Zahlen-Stil: Blocklicht als Zahl auf jedem Feld (aus ExtraHud aufgerufen). */
    public static void render(GuiGraphicsExtractor ctx, Minecraft mc) {
        LightLevelModule m = ModuleManager.INSTANCE.get(LightLevelModule.class);
        if (m == null || !m.isEnabled() || m.style.getIndex() != 2 || mc.player == null || mc.font == null) return;
        List<Stelle> liste = stellen;
        if (liste.isEmpty()) return;
        ScreenProject.begin(mc, ctx.guiWidth(), ctx.guiHeight());
        int[] farben = { m.danger.get(), m.night.get(), m.safe.get() };
        int n = 0;
        for (Stelle s : liste) {
            double[] p = ScreenProject.project(s.x() + 0.5, s.y() + 0.1, s.z() + 0.5);
            if (p == null || p[2] > 14) continue;
            String t = String.valueOf(s.licht());
            float sk = (float) Math.max(0.5, Math.min(1.2, 5.0 / Math.max(1.0, p[2])));
            var pose = ctx.pose();
            pose.pushMatrix();
            pose.translate((float) p[0], (float) p[1]);
            pose.scale(sk, sk);
            ctx.text(mc.font, Component.literal(t), -mc.font.width(t) / 2, -4, 0xFF000000 | farben[s.art()], true);
            pose.popMatrix();
            if (++n > 400) break;
        }
    }
}
