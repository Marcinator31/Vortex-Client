package com.vortex.legacy.module.visual;

import com.vortex.legacy.core.ColorSetting;
import com.vortex.legacy.core.Module;
import com.vortex.legacy.gui.Render3D;
import com.vortex.legacy.module.WorldRenderers;

/** Chunk-Grenzen anzeigen (gibt es in 1.8 noch nicht mit F3+G). */
public class ChunkBorders extends Module implements WorldRenderers.InWorld {
    public final ColorSetting color = add(new ColorSetting("Color", 0xCCF5B942));
    public ChunkBorders() { super("Chunk Borders", Category.VISUAL, "Shows the edges of the chunk you are in."); }

    @Override
    public void renderWorld(float tickDelta) {
        if (mc.player == null) return;
        int cx = ((int) Math.floor(mc.player.x)) >> 4 << 4, cz = ((int) Math.floor(mc.player.z)) >> 4 << 4;
        Render3D.begin(1.5f, false);
        int c = color.get(), dim = (c & 0xFFFFFF) | 0x50000000;
        for (int dx = 0; dx <= 16; dx += 16) for (int dz = 0; dz <= 16; dz += 16)
            Render3D.line(cx + dx, 0, cz + dz, cx + dx, 256, cz + dz, c);
        int y0 = Math.max(0, (int) mc.player.y - 16), y1 = Math.min(256, (int) mc.player.y + 24);
        for (int y = y0 - y0 % 2; y <= y1; y += 2) {
            int col = y % 16 == 0 ? c : dim;
            Render3D.line(cx, y, cz, cx + 16, y, cz, col);
            Render3D.line(cx, y, cz + 16, cx + 16, y, cz + 16, col);
            Render3D.line(cx, y, cz, cx, y, cz + 16, col);
            Render3D.line(cx + 16, y, cz, cx + 16, y, cz + 16, col);
        }
        Render3D.end();
    }
}
