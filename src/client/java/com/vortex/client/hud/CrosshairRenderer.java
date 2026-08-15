package com.vortex.client.hud;

import com.vortex.client.module.modules.CrosshairModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

/**
 * Draws the crosshair.
 *
 * Built from filled rectangles rather than a texture, so any size and colour
 * works without an image to go with it.
 */
public final class CrosshairRenderer {

    private CrosshairRenderer() {}

    public static void draw(DrawContext ctx, MinecraftClient client, CrosshairModule mod) {
        int cx = client.getWindow().getScaledWidth() / 2;
        int cy = client.getWindow().getScaledHeight() / 2;

        int len = mod.size.getInt();
        int th = mod.thickness.getInt();
        int gap = mod.gap.getInt();
        int color = mod.color.get();
        boolean outline = mod.outline.get();

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

    private static void arm(DrawContext ctx, int x, int y, int w, int h,
                            int color, boolean outline) {
        fill(ctx, x, y, w, h, color, outline);
    }

    /** Rectangle, with a dark border around it when asked for. */
    private static void fill(DrawContext ctx, int x, int y, int w, int h,
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
    private static void circle(DrawContext ctx, int cx, int cy, int radius, int th,
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
