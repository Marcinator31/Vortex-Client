package com.vortex.legacy.module.hud;

/** Saettigung und Hunger als Zahl (in 1.8 unsichtbar, aber wichtig fuer Regeneration). */
public class SaturationModule extends SimpleText {
    public SaturationModule() { super("Saturation", "Shows food and hidden saturation.", 4, 245); }
    @Override protected String text(boolean editor) {
        if (mc.player == null) return null;
        return String.format("Food %d  Sat %.1f", mc.player.getHungerManager().getFoodLevel(), mc.player.getHungerManager().getSaturationLevel());
    }
}
