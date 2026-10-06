package com.vortex.legacy.module.hud;

import net.minecraft.client.MinecraftClient;

public class FpsModule extends SimpleText {
    public FpsModule() { super("FPS", "Frames per second.", 4, 4); }
    @Override public boolean defaultEnabled() { return true; }
    @Override protected String text(boolean editor) { return MinecraftClient.getCurrentFps() + " FPS"; }
}
