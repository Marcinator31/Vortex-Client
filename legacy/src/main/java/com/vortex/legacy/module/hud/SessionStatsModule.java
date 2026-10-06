package com.vortex.legacy.module.hud;

import com.vortex.legacy.gui.Render2D;
import com.vortex.legacy.hud.HudModule;

/** Spielzeit dieser Sitzung, Kills und Tode (aus dem Chat erkannt). */
public class SessionStatsModule extends HudModule {
    public static int kills, deaths;
    private static long start = System.currentTimeMillis();
    private float w = 90, h = 40;
    public SessionStatsModule() { super("Session Stats", "Play time, kills and deaths this session.", 4, 400); }
    @Override public float width() { return w; }
    @Override public float height() { return h; }

    public static void reset() { kills = 0; deaths = 0; start = System.currentTimeMillis(); }

    @Override
    public void render(boolean editor) {
        long s = (System.currentTimeMillis() - start) / 1000;
        String[] l = { String.format("Time: %d:%02d:%02d", s / 3600, s / 60 % 60, s % 60), "Kills: " + kills, "Deaths: " + deaths,
                "K/D: " + (deaths == 0 ? String.valueOf(kills) : String.format("%.2f", kills / (double) deaths)) };
        float mw = 0;
        for (String t : l) mw = Math.max(mw, Render2D.width(t));
        w = mw + PAD * 2; h = l.length * 10 + PAD * 2 - 1;
        if (background.get()) Render2D.round(0, 0, w, h, 3, 0x90000000);
        for (int i = 0; i < l.length; i++) Render2D.text(l[i], PAD, PAD + i * 10, textColor.get(), textShadow.get());
    }
}
