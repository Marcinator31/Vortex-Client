package com.vortex.legacy.module.hud;

/** Server-TPS, geschaetzt aus der Weltzeit, die der Server schickt. */
public class TpsModule extends SimpleText {
    private static long lastWorldTime = -1, lastReal;
    private static double tps = 20;
    public TpsModule() { super("TPS", "Estimated server ticks per second.", 4, 444); }

    /** Aus dem Weltzeit-Paket (alle ~20 Ticks). */
    public static void onWorldTime(long worldTime) {
        long now = System.currentTimeMillis();
        if (lastWorldTime >= 0 && now > lastReal) {
            double t = (worldTime - lastWorldTime) / ((now - lastReal) / 1000.0);
            if (t > 0 && t < 40) tps = tps * 0.6 + Math.min(20, t) * 0.4;
        }
        lastWorldTime = worldTime;
        lastReal = now;
    }

    @Override protected String text(boolean editor) { return String.format("%.1f TPS", tps); }
}
