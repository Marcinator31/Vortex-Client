package com.vortex.legacy.module.hud;

public class SpeedometerModule extends SimpleText {
    public SpeedometerModule() { super("Speedometer", "Horizontal speed in blocks per second.", 4, 160); }
    @Override protected String text(boolean editor) {
        if (mc.player == null) return editor ? "4.32 m/s" : null;
        double dx = mc.player.x - mc.player.prevX, dz = mc.player.z - mc.player.prevZ;
        return String.format("%.2f m/s", Math.sqrt(dx * dx + dz * dz) * 20);
    }
}
