package com.vortex.legacy.module.visual;

import com.vortex.legacy.core.Module;

/** Alles hell, auch nachts und in Hoehlen (Helligkeit 1000 %). */
public class Fullbright extends Module {
    private float before = 0.5f;
    public Fullbright() { super("Fullbright", Category.VISUAL, "See in the dark."); }
    @Override protected void onEnable() { before = mc.options.gamma; }
    @Override public void onTick() { mc.options.gamma = 10f; }
    @Override protected void onDisable() { mc.options.gamma = before > 1f ? 1f : before; }
}
