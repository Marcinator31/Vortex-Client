package com.vortex.legacy.module.visual;

import com.vortex.legacy.core.ColorSetting;
import com.vortex.legacy.core.Module;

/** Farbe des Verzauberungs-Schimmers. */
public class GlintColor extends Module {
    public final ColorSetting color = add(new ColorSetting("Color", 0xFF8B5CF6));
    public GlintColor() { super("Glint Customizer", Category.VISUAL, "Change the color of the enchantment glint."); }
}
