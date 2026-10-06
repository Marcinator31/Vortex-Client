package com.vortex.legacy.module.visual;

import com.vortex.legacy.core.Module;
import com.vortex.legacy.core.NumberSetting;
import com.vortex.legacy.mixin.GameRendererAccessor;
import net.minecraft.util.Identifier;

/** Bewegungsunschaerfe: das letzte Bild klingt weich nach (Post-Shader). */
public class MotionBlur extends Module {
    public final NumberSetting strength = add(new NumberSetting("Strength", 5, 1, 9, 1));
    private int loaded = -1;
    private Object ours;

    public MotionBlur() { super("Motion Blur", Category.VISUAL, "Smooth motion blur when you turn."); }

    private Identifier id() { return new Identifier("minecraft", "shaders/post/vortex_motionblur_" + strength.getInt() + ".json"); }

    @Override
    public void onTick() {
        if (mc.world == null || mc.gameRenderer == null) return;
        boolean active = mc.gameRenderer.getShader() != null && mc.gameRenderer.getShader() == ours;
        if (!active || loaded != strength.getInt()) {
            if (mc.gameRenderer.getShader() != null && !active) return; // anderer Shader (z. B. Creeper-Sicht) -- nicht stoeren
            ((GameRendererAccessor) mc.gameRenderer).vortex$loadShader(id());
            loaded = strength.getInt();
            ours = mc.gameRenderer.getShader();
        }
    }

    @Override
    protected void onDisable() {
        if (mc.gameRenderer != null && mc.gameRenderer.getShader() != null && mc.gameRenderer.getShader() == ours)
            mc.gameRenderer.disableShader();
        ours = null;
        loaded = -1;
    }
}
