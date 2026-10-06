package com.vortex.legacy.module.misc;

import com.vortex.legacy.core.Module;

/** Mehr FPS auf schwachen PCs: schnelle Grafik, wenig Partikel, keine Schatten/Wolken. Aus = alles zurueck. */
public class PotatoMode extends Module {
    private boolean fancy, shadows; private int particles, ao, clouds, mip;
    public PotatoMode() { super("Potato Mode", Category.PERFORMANCE, "More FPS on weak PCs (fast graphics, fewer particles)."); }

    @Override protected void onEnable() {
        fancy = mc.options.fancyGraphics; shadows = mc.options.entityShadows; particles = mc.options.particle; ao = mc.options.ao; mip = mc.options.mipmapLevels;
        apply();
    }
    @Override public void onTick() { if (mc.options.fancyGraphics || mc.options.particle < 2) apply(); }
    private void apply() {
        mc.options.fancyGraphics = false;
        mc.options.entityShadows = false;
        mc.options.particle = 2;
        mc.options.ao = 0;
    }
    @Override protected void onDisable() {
        mc.options.fancyGraphics = fancy; mc.options.entityShadows = shadows; mc.options.particle = particles; mc.options.ao = ao; mc.options.mipmapLevels = mip;
        if (mc.worldRenderer != null) mc.worldRenderer.reload();
    }
}
