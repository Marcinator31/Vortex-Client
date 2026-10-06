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

    public static void draw(HudModule h, boolean editor) {
        GlStateManager.pushMatrix();
        try {
            GlStateManager.translate(h.x.getFloat(), h.y.getFloat(), 0);
            float s = h.scale.getFloat();
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
