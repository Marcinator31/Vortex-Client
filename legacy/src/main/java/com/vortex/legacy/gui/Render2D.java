package com.vortex.legacy.gui;

import com.mojang.blaze3d.platform.GlStateManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import org.lwjgl.opengl.GL11;

/**
 * 2D-Zeichnen fuer 1.8.9 (Immediate Mode ueber Tessellator): Rechtecke,
 * runde Rechtecke mit weicher Kante (1 px Ausblendung statt Treppchen),
 * Verlaeufe, Kreise, Text. Alle Koordinaten als float (GUI-Einheiten).
 */
public final class Render2D {
    private Render2D() {}

    private static final int SEG = 6; // Segmente je Viertelkreis

    public static int alpha(int c, float a) {
        int al = Math.round(((c >>> 24) & 0xFF) * Math.max(0, Math.min(1, a)));
        return (al << 24) | (c & 0xFFFFFF);
    }
    public static int mix(int a, int b, float t) {
        t = Math.max(0, Math.min(1, t));
        int aa = (a >>> 24) & 0xFF, ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int ba = (b >>> 24) & 0xFF, br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        return ((int) (aa + (ba - aa) * t) << 24) | ((int) (ar + (br - ar) * t) << 16) | ((int) (ag + (bg - ag) * t) << 8) | (int) (ab + (bb - ab) * t);
    }

    private static void begin() {
        GlStateManager.enableBlend();
        GlStateManager.disableTexture();
        GlStateManager.disableAlphaTest();
        GlStateManager.blendFuncSeparate(770, 771, 1, 0);
        GlStateManager.shadeModel(GL11.GL_SMOOTH);
        GlStateManager.disableCull(); // Faecher/Streifen haben gemischte Wicklung
    }
    private static void end() {
        GlStateManager.shadeModel(GL11.GL_FLAT);
        GlStateManager.enableCull();
        GlStateManager.enableAlphaTest();
        GlStateManager.enableTexture();
        GlStateManager.disableBlend();
        GlStateManager.color(1f, 1f, 1f, 1f);
    }
    private static void col(BufferBuilder b, int c) {
        b.color((c >> 16) & 0xFF, (c >> 8) & 0xFF, c & 0xFF, (c >>> 24) & 0xFF);
    }

    public static void rect(float x, float y, float w, float h, int c) {
        if (w <= 0 || h <= 0 || (c >>> 24) == 0) return;
        begin();
        Tessellator t = Tessellator.getInstance();
        BufferBuilder b = t.getBuffer();
        b.begin(GL11.GL_QUADS, VertexFormats.POSITION_COLOR);
        b.vertex(x, y + h, 0).color((c >> 16) & 0xFF, (c >> 8) & 0xFF, c & 0xFF, (c >>> 24) & 0xFF).next();
        b.vertex(x + w, y + h, 0).color((c >> 16) & 0xFF, (c >> 8) & 0xFF, c & 0xFF, (c >>> 24) & 0xFF).next();
        b.vertex(x + w, y, 0).color((c >> 16) & 0xFF, (c >> 8) & 0xFF, c & 0xFF, (c >>> 24) & 0xFF).next();
        b.vertex(x, y, 0).color((c >> 16) & 0xFF, (c >> 8) & 0xFF, c & 0xFF, (c >>> 24) & 0xFF).next();
        t.draw();
        end();
    }

    /** Verlauf: oben -> unten (vertical) oder links -> rechts. */
    public static void gradient(float x, float y, float w, float h, int c1, int c2, boolean vertical) {
        if (w <= 0 || h <= 0) return;
        begin();
        Tessellator t = Tessellator.getInstance();
        BufferBuilder b = t.getBuffer();
        b.begin(GL11.GL_QUADS, VertexFormats.POSITION_COLOR);
        int tl = c1, tr = vertical ? c1 : c2, br = c2, bl = vertical ? c2 : c1;
        b.vertex(x, y + h, 0); col(b, bl); b.next();
        b.vertex(x + w, y + h, 0); col(b, br); b.next();
        b.vertex(x + w, y, 0); col(b, tr); b.next();
        b.vertex(x, y, 0); col(b, tl); b.next();
        t.draw();
        end();
    }

    /** Punkte des Umrisses eines runden Rechtecks (im Uhrzeigersinn), Radius r, um d nach aussen versetzt. */
    private static float[] umriss(float x, float y, float w, float h, float r, float d) {
        r = Math.max(0, Math.min(r, Math.min(w, h) / 2f));
        float[] p = new float[(SEG + 1) * 4 * 2];
        float[][] ecken = { { x + w - r, y + r, -90 }, { x + w - r, y + h - r, 0 }, { x + r, y + h - r, 90 }, { x + r, y + r, 180 } };
        int i = 0;
        for (float[] e : ecken) {
            for (int s = 0; s <= SEG; s++) {
                double a = Math.toRadians(e[2] + 90.0 * s / SEG);
                p[i++] = (float) (e[0] + Math.cos(a) * (r + d));
                p[i++] = (float) (e[1] + Math.sin(a) * (r + d));
            }
        }
        return p;
    }

    /** Rundes Rechteck mit weicher (geglaetteter) Kante. */
    public static void round(float x, float y, float w, float h, float r, int c) {
        if (w <= 0 || h <= 0 || (c >>> 24) == 0) return;
        begin();
        Tessellator t = Tessellator.getInstance();
        BufferBuilder b = t.getBuffer();
        float[] in = umriss(x + 0.25f, y + 0.25f, w - 0.5f, h - 0.5f, Math.max(0, r - 0.25f), 0);
        // Flaeche
        b.begin(GL11.GL_TRIANGLE_FAN, VertexFormats.POSITION_COLOR);
        b.vertex(x + w / 2f, y + h / 2f, 0); col(b, c); b.next();
        for (int i = 0; i < in.length; i += 2) { b.vertex(in[i], in[i + 1], 0); col(b, c); b.next(); }
        b.vertex(in[0], in[1], 0); col(b, c); b.next();
        t.draw();
        // weiche Kante: Ring nach aussen auf Alpha 0
        float[] out = umriss(x + 0.25f, y + 0.25f, w - 0.5f, h - 0.5f, Math.max(0, r - 0.25f), 0.9f);
        int c0 = c & 0xFFFFFF;
        b.begin(GL11.GL_TRIANGLE_STRIP, VertexFormats.POSITION_COLOR);
        for (int i = 0; i <= in.length; i += 2) {
            int k = i % in.length;
            b.vertex(in[k], in[k + 1], 0); col(b, c); b.next();
            b.vertex(out[k], out[k + 1], 0); col(b, c0); b.next();
        }
        t.draw();
        end();
    }

    /** Rahmen eines runden Rechtecks (Linienstaerke th). */
    public static void roundOutline(float x, float y, float w, float h, float r, float th, int c) {
        if ((c >>> 24) == 0) return;
        begin();
        Tessellator t = Tessellator.getInstance();
        BufferBuilder b = t.getBuffer();
        float[] a = umriss(x, y, w, h, r, 0);
        float[] in = umriss(x + th, y + th, w - 2 * th, h - 2 * th, Math.max(0, r - th), 0);
        b.begin(GL11.GL_TRIANGLE_STRIP, VertexFormats.POSITION_COLOR);
        for (int i = 0; i <= a.length; i += 2) {
            int k = i % a.length;
            b.vertex(a[k], a[k + 1], 0); col(b, c); b.next();
            b.vertex(in[k], in[k + 1], 0); col(b, c); b.next();
        }
        t.draw();
        end();
    }

    /** Weicher Schatten unter einem runden Rechteck. */
    public static void shadow(float x, float y, float w, float h, float r, float size, int c) {
        begin();
        Tessellator t = Tessellator.getInstance();
        BufferBuilder b = t.getBuffer();
        float[] in = umriss(x, y, w, h, r, 0);
        float[] out = umriss(x, y, w, h, r, size);
        int c0 = c & 0xFFFFFF;
        b.begin(GL11.GL_TRIANGLE_STRIP, VertexFormats.POSITION_COLOR);
        for (int i = 0; i <= in.length; i += 2) {
            int k = i % in.length;
            b.vertex(in[k], in[k + 1], 0); col(b, c); b.next();
            b.vertex(out[k], out[k + 1], 0); col(b, c0); b.next();
        }
        t.draw();
        end();
    }

    public static void circle(float cx, float cy, float r, int c) {
        begin();
        Tessellator t = Tessellator.getInstance();
        BufferBuilder b = t.getBuffer();
        int n = Math.max(12, (int) (r * 2.5f));
        b.begin(GL11.GL_TRIANGLE_FAN, VertexFormats.POSITION_COLOR);
        b.vertex(cx, cy, 0); col(b, c); b.next();
        for (int i = 0; i <= n; i++) {
            double a = Math.PI * 2 * i / n;
            b.vertex(cx + Math.cos(a) * r, cy + Math.sin(a) * r, 0); col(b, c); b.next();
        }
        t.draw();
        b.begin(GL11.GL_TRIANGLE_STRIP, VertexFormats.POSITION_COLOR);
        for (int i = 0; i <= n; i++) {
            double a = Math.PI * 2 * i / n;
            b.vertex(cx + Math.cos(a) * r, cy + Math.sin(a) * r, 0); col(b, c); b.next();
            b.vertex(cx + Math.cos(a) * (r + 0.9), cy + Math.sin(a) * (r + 0.9), 0); col(b, c & 0xFFFFFF); b.next();
        }
        t.draw();
        end();
    }

    // ---------------------------------------------------------------- Text

    public static TextRenderer font() { return MinecraftClient.getInstance().textRenderer; }
    public static int width(String s) { return font().getStringWidth(s); }
    public static int height() { return font().fontHeight; }

    public static void text(String s, float x, float y, int c, boolean shadow) {
        if ((c >>> 24) < 4) return; // fast unsichtbar: TextRenderer wuerde es voll deckend zeichnen
        if ((c >>> 24) < 0xFF) { GlStateManager.enableBlend(); GlStateManager.blendFuncSeparate(770, 771, 1, 0); }
        font().draw(s, x, y, c, shadow);
    }
    public static void textCentered(String s, float cx, float y, int c, boolean shadow) {
        text(s, cx - width(s) / 2f, y, c, shadow);
    }
    /** Text skaliert (z. B. Ueberschriften). */
    public static void textScaled(String s, float x, float y, float scale, int c, boolean shadow) {
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, 0);
        GlStateManager.scale(scale, scale, 1);
        text(s, 0, 0, c, shadow);
        GlStateManager.popMatrix();
    }
    public static String trim(String s, int maxW) {
        if (width(s) <= maxW) return s;
        String e = "...";
        return font().trimToWidth(s, Math.max(0, maxW - width(e))) + e;
    }

    // ------------------------------------------------------------- Scissor

    /** Zeichnen auf ein Rechteck (GUI-Koordinaten) beschraenken. */
    public static void scissor(float x, float y, float w, float h) {
        MinecraftClient mc = MinecraftClient.getInstance();
        net.minecraft.client.util.Window win = new net.minecraft.client.util.Window(mc);
        int f = win.getScaleFactor();
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor((int) (x * f), (int) (mc.height - (y + h) * f), Math.max(0, (int) (w * f)), Math.max(0, (int) (h * f)));
    }
    public static void endScissor() { GL11.glDisable(GL11.GL_SCISSOR_TEST); }
}
