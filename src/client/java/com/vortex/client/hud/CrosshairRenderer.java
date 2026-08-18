package com.vortex.client.hud;

import com.vortex.client.module.modules.CrosshairModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Draws the crosshair.
 *
 * Built from filled rectangles rather than a texture, so any size and colour
 * works without an image to go with it.
 */
public final class CrosshairRenderer {

    private CrosshairRenderer() {}

    public static void draw(GuiGraphicsExtractor ctx, Minecraft client, CrosshairModule mod) {
        int cx = client.getWindow().getGuiScaledWidth() / 2;
        int cy = client.getWindow().getGuiScaledHeight() / 2;

        int len = mod.size.getInt();
        int th = mod.thickness.getInt();
        int gap = mod.gap.getInt();
        int color = mod.color.get();
        boolean outline = mod.outline.get();

        // The charge bar first, so the crosshair sits on top of it rather
        // than the other way round -- vanilla does it in that order too.
        if (mod.attackIndicator.get()) {
            drawAttackIndicator(ctx, client, cx, cy);
        }

        switch (mod.shape.getIndex()) {
            case 1:                     // Dot
                fill(ctx, cx - th, cy - th, th * 2, th * 2, color, outline);
                break;

            case 2:                     // Circle
                circle(ctx, cx, cy, len, th, color, outline);
                break;

            case 3:                     // T-shape: no arm upwards
                arm(ctx, cx - th / 2, cy + gap, th, len, color, outline);            // down
                arm(ctx, cx - gap - len, cy - th / 2, len, th, color, outline);      // left
                arm(ctx, cx + gap, cy - th / 2, len, th, color, outline);            // right
                break;

            default:                    // Cross
                arm(ctx, cx - th / 2, cy - gap - len, th, len, color, outline);      // up
                arm(ctx, cx - th / 2, cy + gap, th, len, color, outline);            // down
                arm(ctx, cx - gap - len, cy - th / 2, len, th, color, outline);      // left
                arm(ctx, cx + gap, cy - th / 2, len, th, color, outline);            // right
                break;
        }
    }

    /**
     * The attack charge bar, as vanilla draws it.
     *
     * Sixteen pixels wide, four high, nine below the centre -- the same numbers
     * the game uses, so it lands where the eye already expects it after years
     * of playing.
     *
     * Only shown while the weapon is still charging. A full bar sitting there
     * permanently is noise; what matters is the moment it fills.
     */
    private static void drawAttackIndicator(GuiGraphicsExtractor ctx, Minecraft client,
                                            int cx, int cy) {
        try {
            if (client.player == null) return;

            // Respect the game's own setting for this.
            //
            // Anyone who moved the indicator to the hotbar, or switched it off,
            // meant it -- putting it back under the crosshair would override a
            // decision they already made.
            Object mode = client.options.attackIndicator().get();
            if (mode != null && !"CROSSHAIR".equals(mode.toString().toUpperCase())) {
                return;
            }

            float progress = client.player.getAttackStrengthScale(0.0f);
            if (progress >= 1.0f) return;

            int x = cx - 8;
            int y = cy + 9;
            int w = 16;
            int h = 4;

            // Dark trough, then the filled part on top.
            ctx.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xC0000000);
            ctx.fill(x, y, x + w, y + h, 0xFF2E2E38);

            int filled = (int) (w * progress);
            if (filled > 0) {
                ctx.fill(x, y, x + filled, y + h, 0xFFFFFFFF);
            }
        } catch (Throwable pvpErr) {
            com.vortex.client.core.Errors.report("CrosshairRenderer.indicator", pvpErr);
        }
    }

    private static void arm(GuiGraphicsExtractor ctx, int x, int y, int w, int h,
                            int color, boolean outline) {
        fill(ctx, x, y, w, h, color, outline);
    }

    /** Rectangle, with a dark border around it when asked for. */
    private static void fill(GuiGraphicsExtractor ctx, int x, int y, int w, int h,
                             int color, boolean outline) {
        if (outline) {
            // One pixel of dark all round, so the crosshair stays visible
            // against snow or sand just as well as against stone.
            ctx.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xC0000000);
        }
        ctx.fill(x, y, x + w, y + h, color);
    }

    /**
     * A circle, drawn as short segments around the edge.
     *
     * Enough segments that it reads as round at any size, few enough that it
     * costs nothing worth measuring.
     */
    private static void circle(GuiGraphicsExtractor ctx, int cx, int cy, int radius, int th,
                               int color, boolean outline) {
        int steps = Math.max(16, radius * 4);
        for (int i = 0; i < steps; i++) {
            double a = (Math.PI * 2 * i) / steps;
            int px = cx + (int) Math.round(Math.cos(a) * radius);
            int py = cy + (int) Math.round(Math.sin(a) * radius);
            fill(ctx, px, py, th, th, color, outline);
        }
    }
}
