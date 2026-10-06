package com.vortex.legacy.module.visual;

import com.vortex.legacy.core.BoolSetting;
import com.vortex.legacy.core.Module;

/** Weniger Partikel: Block-Abbau-Krümel und -Splitter aus (mehr FPS in Kämpfen). */
public class NoParticles extends Module {
    public final BoolSetting blockBreak = add(new BoolSetting("Block Break", true));
    public final BoolSetting blockHit = add(new BoolSetting("Block Hitting", true));
    public NoParticles() { super("No Particles", Category.PERFORMANCE, "Hides block breaking particles."); }
}
