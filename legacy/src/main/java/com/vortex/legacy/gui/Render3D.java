package com.vortex.legacy.gui;

import com.mojang.blaze3d.platform.GlStateManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Box;
import org.lwjgl.opengl.GL11;

/** Zeichnen in der Welt (Koordinaten relativ zur Kamera). */
public final class Render3D {
    private Render3D() {}
    public static double camX, camY, camZ;

    /** Kameraposition fuer dieses Bild merken (aus WorldRenderMixin). */
    public static void setCamera(float tickDelta) {
        Entity e = MinecraftClient.getInstance().getCameraEntity();
        if (e == null) return;
        camX = e.prevTickX + (e.x - e.prevTickX) * tickDelta;
        camY = e.prevTickY + (e.y - e.prevTickY) * tickDelta;
        camZ = e.prevTickZ + (e.z - e.prevTickZ) * tickDelta;
    }

    public static void begin(float lineWidth, boolean throughWalls) {
        GlStateManager.pushMatrix();
        GlStateManager.enableBlend();
        GlStateManager.blendFuncSeparate(770, 771, 1, 0);
        GlStateManager.disableTexture();
        GlStateManager.disableLighting();
        GlStateManager.disableCull();
        GlStateManager.depthMask(false);
        if (throughWalls) GlStateManager.disableDepthTest();
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glLineWidth(lineWidth);
    }
    public static void end() {
        GL11.glDisable(GL11.GL_LINE_SMOOTH);
        GL11.glLineWidth(1f);
        GlStateManager.enableDepthTest();
        GlStateManager.depthMask(true);
        GlStateManager.enableCull();
        GlStateManager.enableTexture();
        GlStateManager.disableBlend();
        GlStateManager.color(1, 1, 1, 1);
        GlStateManager.popMatrix();
    }

    private static void c(BufferBuilder b, int c) { b.color((c >> 16) & 0xFF, (c >> 8) & 0xFF, c & 0xFF, (c >>> 24) & 0xFF); }

    /** Kasten (Welt-Koordinaten): Flaechen mit fill, Kanten mit line (Alpha 0 = weglassen). */
    public static void box(Box w, int fill, int line) {
        Box b = w.offset(-camX, -camY, -camZ);
        Tessellator t = Tessellator.getInstance();
        BufferBuilder bb = t.getBuffer();
        if ((fill >>> 24) != 0) {
            bb.begin(GL11.GL_QUADS, VertexFormats.POSITION_COLOR);
            double[][] f = {
                { b.minX, b.minY, b.minZ, b.maxX, b.minY, b.minZ, b.maxX, b.minY, b.maxZ, b.minX, b.minY, b.maxZ },
                { b.minX, b.maxY, b.minZ, b.minX, b.maxY, b.maxZ, b.maxX, b.maxY, b.maxZ, b.maxX, b.maxY, b.minZ },
                { b.minX, b.minY, b.minZ, b.minX, b.maxY, b.minZ, b.maxX, b.maxY, b.minZ, b.maxX, b.minY, b.minZ },
                { b.minX, b.minY, b.maxZ, b.maxX, b.minY, b.maxZ, b.maxX, b.maxY, b.maxZ, b.minX, b.maxY, b.maxZ },
                { b.minX, b.minY, b.minZ, b.minX, b.minY, b.maxZ, b.minX, b.maxY, b.maxZ, b.minX, b.maxY, b.minZ },
                { b.maxX, b.minY, b.minZ, b.maxX, b.maxY, b.minZ, b.maxX, b.maxY, b.maxZ, b.maxX, b.minY, b.maxZ } };
            for (double[] q : f) for (int i = 0; i < 12; i += 3) { bb.vertex(q[i], q[i + 1], q[i + 2]); c(bb, fill); bb.next(); }
            t.draw();
        }
        if ((line >>> 24) != 0) {
            bb.begin(GL11.GL_LINES, VertexFormats.POSITION_COLOR);
            double[] xs = { b.minX, b.maxX }, ys = { b.minY, b.maxY }, zs = { b.minZ, b.maxZ };
            for (double y : ys) for (double z : zs) { bb.vertex(b.minX, y, z); c(bb, line); bb.next(); bb.vertex(b.maxX, y, z); c(bb, line); bb.next(); }
            for (double x : xs) for (double z : zs) { bb.vertex(x, b.minY, z); c(bb, line); bb.next(); bb.vertex(x, b.maxY, z); c(bb, line); bb.next(); }
            for (double x : xs) for (double y : ys) { bb.vertex(x, y, b.minZ); c(bb, line); bb.next(); bb.vertex(x, y, b.maxZ); c(bb, line); bb.next(); }
            t.draw();
        }
    }

    /** Linie zwischen zwei Weltpunkten. */
    public static void line(double x1, double y1, double z1, double x2, double y2, double z2, int c) {
        Tessellator t = Tessellator.getInstance();
        BufferBuilder bb = t.getBuffer();
        bb.begin(GL11.GL_LINES, VertexFormats.POSITION_COLOR);
        bb.vertex(x1 - camX, y1 - camY, z1 - camZ); c(bb, c); bb.next();
        bb.vertex(x2 - camX, y2 - camY, z2 - camZ); c(bb, c); bb.next();
        t.draw();
    }
}
