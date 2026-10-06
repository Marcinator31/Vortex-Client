package com.vortex.legacy.hud;

import com.mojang.blaze3d.platform.GlStateManager;
import com.vortex.legacy.core.Errors;
import com.vortex.legacy.core.Module;
import com.vortex.legacy.core.ModuleManager;
import net.minecraft.client.MinecraftClient;

/** Zeichnet alle eingeschalteten HUD-Elemente (aus InGameHud.render). */
public final class HudRenderer {
    private HudRenderer() {}

    public static void render(float tickDelta) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.options.hudHidden || mc.currentScreen instanceof com.vortex.legacy.gui.HudEditorScreen) return;
        if (mc.options.debugEnabled) return; // F3 belegt den Bildschirm
        for (Module m : ModuleManager.INSTANCE.all()) {
            if (!(m instanceof HudModule) || !m.isEnabled()) continue;
            draw((HudModule) m, false);
        }
    }

    /** Position auf dem Bildschirm -- immer ganz sichtbar (grosse Werte = rechter/unterer Rand). */
    public static float[] position(HudModule h) {
        net.minecraft.client.util.Window w = new net.minecraft.client.util.Window(MinecraftClient.getInstance());
        float s = h.scale.getFloat();
        float x = Math.max(0, Math.min(w.getWidth() - h.width() * s, h.x.getFloat()));
        float y = Math.max(0, Math.min(w.getHeight() - h.height() * s, h.y.getFloat()));
        return new float[]{ x, y };
    }

    public static void draw(HudModule h, boolean editor) {
        GlStateManager.pushMatrix();
        try {
            float s = h.scale.getFloat();
            float[] pos = position(h);
            GlStateManager.translate(pos[0], pos[1], 0);
            GlStateManager.scale(s, s, 1);
            h.render(editor);
        } catch (Throwable t) {
            Errors.report(h.getName() + ".render", t);
        } finally {
            GlStateManager.popMatrix();
            GlStateManager.color(1, 1, 1, 1);
        }
    }
}
