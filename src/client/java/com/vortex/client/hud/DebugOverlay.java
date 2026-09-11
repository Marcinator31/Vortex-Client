package com.vortex.client.hud;

import com.vortex.client.core.Errors;
import com.vortex.client.module.ModuleManager;
import com.vortex.client.module.modules.DebugOverlayModule;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Draws the debug overlay.
 *
 * Laid out as label and value in two columns, so the eye can run down the left
 * side to find what it wants instead of reading every line.
 *
 * The values that need work -- biome lookups, memory figures -- are refreshed a
 * few times a second rather than every frame. None of them change faster than
 * that, and doing it per frame was what made the HUD the most expensive thing
 * in the client the last time it was measured.
 */
public final class DebugOverlay {

    /**
     * One line: a label, a value, and whether it should stand out.
     * A plain class rather than a record so the Component objects can be built
     * once here instead of twice per line per frame in render().
     * Accessor names match the old record, so no call site changes.
     */
    private static final class Line {
        private final String label, value;
        private final boolean warn;
        private final Component labelText, valueText;
        Line(String label, String value, boolean warn) {
            this.label = label;
            this.value = value;
            this.warn = warn;
            this.labelText = Component.literal(label);
            this.valueText = Component.literal(value);
        }
        String label() { return label; }
        String value() { return value; }
        boolean warn() { return warn; }
        Component labelText() { return labelText; }
        Component valueText() { return valueText; }
    }

    private static final List<Line> LINES = new ArrayList<>();

    /** Column widths, computed in rebuild() -- see render(). */
    private static int cachedLabelW = 0;
    private static int cachedWidth = 0;

    /** When the lines were last rebuilt. */
    private static long built = 0L;

    /** Recent frame times, for the steadiness figure. */
    private static final int[] FPS_HISTORY = new int[20];
    private static int fpsIndex = 0;

    private DebugOverlay() {}

    public static void render(GuiGraphicsExtractor ctx, Minecraft client) {
        long pvpT0 = System.nanoTime();
        try {
            DebugOverlayModule mod = ModuleManager.INSTANCE.get(DebugOverlayModule.class);
            if (mod == null || !mod.isEnabled()) return;
            if (client.player == null || client.level == null) return;

            // The vanilla screen is already a wall of text; two at once helps
            // nobody, so ours steps aside while F3 is open.
            if (client.getDebugOverlay() != null && client.getDebugOverlay().showDebugScreen()) return;

            long now = System.currentTimeMillis();
            if (now - built > 200) {
                built = now;
                rebuild(client, mod);
            }
            if (LINES.isEmpty()) return;

            int px = mod.x.getInt();
            int py = mod.y.getInt();
            HudRenderer.pushScale(ctx, px, py, mod.scale.getFloat());

            // Widths and Component objects come from rebuild(): three getWidth
            // loops and two Component.literal per line ran here on EVERY FRAME,
            // for strings that only change when rebuild runs (5x/second).
            int valueX = px + cachedLabelW + 8;
            int width = cachedWidth;
            int height = LINES.size() * 10;

            if (mod.background.get()) {
                ctx.fill(px - 4, py - 3, px + width + 4, py + height + 1, 0x90000000);
                ctx.fill(px - 4, py - 3, px - 3, py + height + 1, mod.labelColor.get());
            }

            int line = 0;
            for (Line l : LINES) {
                int ly = py + line * 10;
                ctx.text(client.font, l.labelText(),
                        px, ly, mod.labelColor.get());
                ctx.text(client.font, l.valueText(),
                        valueX, ly, l.warn() ? 0xFFFF7A7A : mod.valueColor.get());
                line++;
            }
            HudRenderer.popScale(ctx);
        } catch (Throwable pvpErr) {
            Errors.report("DebugOverlay", pvpErr);
        } finally {
            // Subset of the "HUD" section, see HudRenderer.
            com.vortex.client.core.Profiler.record("DebugOverlay",
                    System.nanoTime() - pvpT0);
        }
    }

    /** Works out the lines. Called a few times a second, not per frame. */
    private static void rebuild(Minecraft client, DebugOverlayModule mod) {
        LINES.clear();
        var player = client.player;

        if (mod.showFps.get()) {
            int fps = client.getFps();
            FPS_HISTORY[fpsIndex++ % FPS_HISTORY.length] = fps;

            // The lowest of the last twenty readings.
            //
            // An average hides exactly what you want to see: the average stays
            // fine while the low point is where the stutter you felt happened.
            int low = Integer.MAX_VALUE;
            for (int v : FPS_HISTORY) {
                if (v > 0 && v < low) low = v;
            }
            String text = (low == Integer.MAX_VALUE)
                    ? fps + " fps"
                    : fps + " fps  (low " + low + ")";
            LINES.add(new Line("FPS", text, fps < 30));
        }

        if (mod.showPosition.get()) {
            LINES.add(new Line("XYZ", String.format(java.util.Locale.ROOT, "%.1f %.1f %.1f",
                    player.getX(), player.getY(), player.getZ()), false));

            BlockPos pos = player.blockPosition();
            LINES.add(new Line("Chunk", (pos.getX() >> 4) + " " + (pos.getZ() >> 4)
                    + "   in-chunk " + (pos.getX() & 15) + " " + (pos.getZ() & 15), false));

            String facing = switch (player.getDirection()) {
                case NORTH -> "north  (-Z)";
                case SOUTH -> "south  (+Z)";
                case WEST  -> "west   (-X)";
                case EAST  -> "east   (+X)";
                default    -> "?";
            };
            LINES.add(new Line("Facing", facing, false));
        }

        if (mod.showWorld.get()) {
            try {
                var biome = client.level.getBiome(player.blockPosition());
                String name = biome.unwrapKey().map(k -> k.identifier().getPath()).orElse("unknown");
                LINES.add(new Line("Biome", name.replace('_', ' '), false));
            } catch (Throwable ignored) {
                // A biome that cannot be read is not worth a broken overlay.
            }

            long time = client.level.getLevelData().getGameTime() % 24000L;
            String clock = String.format(java.util.Locale.ROOT, "%02d:%02d",
                    (time / 1000 + 6) % 24, (time % 1000) * 60 / 1000);
            LINES.add(new Line("Time", clock + "   day " + (client.level.getLevelData().getGameTime() / 24000L), false));
        }

        if (mod.showTarget.get()) {
            HitResult hit = client.hitResult;
            if (hit instanceof BlockHitResult block && hit.getType() == HitResult.Type.BLOCK) {
                var state = client.level.getBlockState(block.getBlockPos());
                var id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock());
                LINES.add(new Line("Looking at",
                        (id == null ? "?" : id.getPath().replace('_', ' ')), false));
                BlockPos bp = block.getBlockPos();
                LINES.add(new Line("Block", bp.getX() + " " + bp.getY() + " " + bp.getZ(), false));
            } else if (hit instanceof EntityHitResult entity) {
                LINES.add(new Line("Looking at",
                        entity.getEntity().getName().getString(), false));
            } else {
                LINES.add(new Line("Looking at", "nothing", false));
            }
        }

        if (mod.showSystem.get()) {
            Runtime rt = Runtime.getRuntime();
            long used = (rt.totalMemory() - rt.freeMemory()) / 1024L / 1024L;
            long max = rt.maxMemory() / 1024L / 1024L;
            int percent = (max == 0) ? 0 : (int) (used * 100 / max);
            LINES.add(new Line("Memory", used + " / " + max + " MB  (" + percent + "%)",
                    percent > 85));
            LINES.add(new Line("Entities",
                    String.valueOf(client.level.getEntityCount()), false));
        }

        if (mod.showServer.get()) {
            var entry = client.getCurrentServer();
            LINES.add(new Line("Server",
                    entry == null ? "singleplayer" : entry.ip, false));

            int ping = PingMeter.get();
            if (ping >= 0 && PingMeter.age() < 15_000L) {
                LINES.add(new Line("Ping", ping + " ms", ping > 200));
            }
            if (client.getConnection() != null) {
                LINES.add(new Line("Players",
                        String.valueOf(client.getConnection().getOnlinePlayers().size()), false));
            }
        }

        // Column widths, once per rebuild. The widest label decides where the
        // value column starts, so it lines up instead of stepping in and out.
        int lw = 0;
        for (Line l : LINES) {
            lw = Math.max(lw, client.font.width(l.label()));
        }
        int w = lw + 8;
        for (Line l : LINES) {
            w = Math.max(w, lw + 8 + client.font.width(l.value()));
        }
        cachedLabelW = lw;
        cachedWidth = w;
    }
}
