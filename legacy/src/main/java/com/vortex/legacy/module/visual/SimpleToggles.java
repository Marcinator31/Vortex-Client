package com.vortex.legacy.module.visual;

import com.vortex.legacy.core.Module;
import com.vortex.legacy.core.NumberSetting;

/** Kleine Module ohne eigene Logik -- die Mixins fragen nur, ob sie an sind. */
public final class SimpleToggles {
    private SimpleToggles() {}

    public static class NoFog extends Module {
        public NoFog() { super("No Fog", Category.VISUAL, "Removes distance fog."); }
    }
    public static class ClearWater extends Module {
        public ClearWater() { super("Clear Water", Category.VISUAL, "See clearly underwater: no fog, no overlay."); }
    }
    public static class ClearLava extends Module {
        public ClearLava() { super("Clear Lava", Category.VISUAL, "See further inside lava."); }
    }
    public static class LowFire extends Module {
        public final NumberSetting height = add(new NumberSetting("Lower By", 0.3, 0, 0.6, 0.05));
        public LowFire() { super("Low Fire", Category.VISUAL, "Makes the burning overlay smaller."); }
        @Override public boolean defaultEnabled() { return true; }
    }
    public static class NoPumpkinBlur extends Module {
        public NoPumpkinBlur() { super("No Pumpkin Blur", Category.VISUAL, "Removes the pumpkin overlay."); }
    }
    public static class NoHurtCam extends Module {
        public final NumberSetting strength = add(new NumberSetting("Strength", 0, 0, 100, 5));
        public NoHurtCam() { super("No Hurt Cam", Category.VISUAL, "Less (or no) screen shake when you get hit."); }
    }
    public static class NoBob extends Module {
        public NoBob() { super("Minimal Bobbing", Category.VISUAL, "Keeps the hand moving but stops the camera from bobbing."); }
    }
}
