package com.vortex.legacy.gui;

import com.mojang.blaze3d.platform.GlStateManager;
import com.vortex.legacy.core.ClientSettings;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.util.Identifier;

/**
 * "Modern Menus" fuer 1.8.9: Knoepfe aus dunklem Glas mit runden Ecken,
 * Akzent beim Ueberfahren; Hauptmenue mit Vortex-Logo statt Minecraft-Schriftzug.
 * Nur in Minecraft-eigenen Menues -- andere Mods bleiben, wie sie sind.
 */
public final class MenuStil {
    private MenuStil() {}
    private static final Map<ButtonWidget, float[]> HOVER = new WeakHashMap<ButtonWidget, float[]>();
    private static long last;
    private static final Identifier RING = new Identifier("vortexclient", "textures/gui/logo_ring.png");
    private static final Identifier V = new Identifier("vortexclient", "textures/gui/logo_v.png");
    private static final long START = System.currentTimeMillis();

    public static boolean aktiv() {
        try { return ClientSettings.INSTANCE.modernMenus.get(); } catch (Throwable t) { return false; }
    }

    /** Knopf in diesem Menue umgestalten? */
    public static boolean knopf() {
        if (!aktiv()) return false;
        Screen s = MinecraftClient.getInstance().currentScreen;
        return s != null && s.getClass().getName().startsWith("net.minecraft.");
    }

    /** Hintergrund des Knopfs (danach zeichnet ein Schieberegler seinen Griff, dann kommt der Text). */
    public static void zeichneKnopf(ButtonWidget b, int mx, int my, int w, int h) {
        long now = System.currentTimeMillis();
        float dt = last == 0 ? 0 : Math.min(0.1f, (now - last) / 1000f);
        last = now;
        boolean over = b.active && mx >= b.x && my >= b.y && mx < b.x + w && my < b.y + h;
        float[] v = HOVER.get(b);
        if (v == null) { v = new float[1]; HOVER.put(b, v); }
        v[0] += ((over ? 1 : 0) - v[0]) * (1 - (float) Math.exp(-dt * 14));
        float t = v[0];
        int akz = Theme.accent();
        int bg = b.active ? Render2D.mix(0xB0100D18, Render2D.alpha(Render2D.mix(akz, 0xFF100D18, 0.35f), 0.92f), t) : 0x80100D18;
        Render2D.round(b.x, b.y, w, h, 5, bg);
        Render2D.roundOutline(b.x, b.y, w, h, 5, 1, b.active ? Render2D.mix(0x26FFFFFF, Render2D.alpha(akz, 0.9f), t) : 0x14FFFFFF);
        GlStateManager.color(1, 1, 1, 1);
    }

    public static void knopfText(ButtonWidget b, int w, int h) {
        float[] v = HOVER.get(b);
        float t = v == null ? 0 : v[0];
        int c = !b.active ? 0xFF6E6880 : Render2D.mix(0xFFE6E2F0, 0xFFFFFFFF, t);
        Render2D.textCentered(b.message, b.x + w / 2f, b.y + (h - 8) / 2f, c, true);
    }

    /** Hauptmenue: Verlauf + Vortex-Logo + Schriftzug (statt Minecraft-Logo und Spruch). */
    public static void titel(int width, int height) {
        Render2D.gradient(0, 0, width, height / 2f, 0x80050309, 0x20050309, true);
        Render2D.gradient(0, height / 2f, width, height / 2f, 0x20050309, 0xB0050309, true);
        MinecraftClient mc = MinecraftClient.getInstance();
        // Platz bis zu den Knoepfen (die beginnen bei height/4 + 48)
        float frei = height / 4f + 48 - 6;
        float s = Math.max(24, Math.min(52, frei - 29 - 8));
        float block = s + 29;
        float cx = width / 2f, cy = Math.max(4, (frei - block) / 2f) + s / 2f;
        float zeit = (System.currentTimeMillis() - START) / 1000f;
        GlStateManager.enableBlend();
        GlStateManager.blendFuncSeparate(770, 771, 1, 0);
        GlStateManager.color(1, 1, 1, 1);
        GlStateManager.pushMatrix();
        GlStateManager.translate(cx, cy, 0);
        GlStateManager.rotate(zeit * 12f % 360, 0, 0, 1);
        mc.getTextureManager().bindTexture(RING);
        DrawableHelper.drawTexture((int) (-s / 2), (int) (-s / 2), 0, 0, (int) s, (int) s, s, s);
        GlStateManager.popMatrix();
        mc.getTextureManager().bindTexture(V);
        float bob = (float) Math.sin(zeit * 1.6) * 1.5f;
        DrawableHelper.drawTexture((int) (cx - s / 2), (int) (cy - s / 2 + bob), 0, 0, (int) s, (int) s, s, s);
        Render2D.textScaled("VORTEX", cx - Render2D.width("VORTEX") * 1.6f / 2f, cy + s / 2 + 4, 1.6f, 0xFFFFFFFF, true);
        Render2D.textCentered("CLIENT  •  1.8.9", cx, cy + s / 2 + 20, Render2D.alpha(Theme.accent(), 0.95f), true);
    }
}
