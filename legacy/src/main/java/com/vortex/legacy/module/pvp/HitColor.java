package com.vortex.legacy.module.pvp;

import com.vortex.legacy.core.ColorSetting;
import com.vortex.legacy.core.Module;

/** Farbe, in der getroffene Wesen aufleuchten (statt Rot). */
public class HitColor extends Module {
    public final ColorSetting color = add(new ColorSetting("Color", 0x4D8B5CF6));
    public HitColor() { super("Hit Color", Category.PVP, "Change the red flash when an entity is hit."); }
    public float r() { return ((color.get() >> 16) & 0xFF) / 255f; }
    public float g() { return ((color.get() >> 8) & 0xFF) / 255f; }
    public float b() { return (color.get() & 0xFF) / 255f; }
    public float a() { return ((color.get() >>> 24) & 0xFF) / 255f; }
}
