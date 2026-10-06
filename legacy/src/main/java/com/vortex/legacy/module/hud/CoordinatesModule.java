package com.vortex.legacy.module.hud;

import com.vortex.legacy.core.BoolSetting;
import com.vortex.legacy.gui.Render2D;
import com.vortex.legacy.hud.HudModule;

public class CoordinatesModule extends HudModule {
    private final BoolSetting direction = add(new BoolSetting("Direction", true));
    private final BoolSetting biome = add(new BoolSetting("Biome", false));
    private float w = 80, h = 30;
    public CoordinatesModule() { super("Coordinates", "Your position, facing and biome.", 10000, 4); }

    @Override public float width() { return w; }
    @Override public float height() { return h; }

    private static final String[] DIR = { "S", "SW", "W", "NW", "N", "NE", "E", "SE" };

    @Override
    public void render(boolean editor) {
        if (mc.player == null) return;
        java.util.List<String> lines = new java.util.ArrayList<String>();
        lines.add(String.format("X: %.1f", mc.player.x));
        lines.add(String.format("Y: %.1f", mc.player.y));
        lines.add(String.format("Z: %.1f", mc.player.z));
        if (direction.get()) {
            int i = Math.floorMod(Math.round(mc.player.yaw / 45f), 8);
            lines.add("Facing: " + DIR[i]);
        }
        if (biome.get() && mc.world != null) {
            try { lines.add("Biome: " + mc.world.getBiome(new net.minecraft.util.math.BlockPos(mc.player)).name); } catch (Throwable ignored) { }
        }
        float mw = 0;
        for (String s : lines) mw = Math.max(mw, Render2D.width(s));
        w = mw + PAD * 2;
        h = lines.size() * (Render2D.height() + 1) + PAD * 2 - 1;
        if (background.get()) Render2D.round(0, 0, w, h, 3, 0x90000000);
        float yy = PAD;
        for (String s : lines) { Render2D.text(s, PAD, yy, textColor.get(), textShadow.get()); yy += Render2D.height() + 1; }
    }
}
