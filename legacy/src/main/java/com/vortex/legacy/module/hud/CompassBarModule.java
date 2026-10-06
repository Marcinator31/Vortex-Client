package com.vortex.legacy.module.hud;

import com.vortex.legacy.gui.Render2D;
import com.vortex.legacy.hud.HudModule;

/** Kompassband wie in Shootern: Himmelsrichtungen ziehen mit der Blickrichtung mit. */
public class CompassBarModule extends HudModule {
    public CompassBarModule() { super("Compass Bar", "A compass strip at the top of the screen.", 330, 4); }
    private static final float W = 180, H = 16;
    @Override public float width() { return W; }
    @Override public float height() { return H; }

    @Override
    public void render(boolean editor) {
        float yaw = mc.player == null ? 0 : mc.player.yaw;
        if (background.get()) Render2D.round(0, 0, W, H, 3, 0x90000000);
        Render2D.scissor(0, 0, W, H);
        float pxPerDeg = 1.4f;
        float center = W / 2f;
        String[] names = { "S", "SW", "W", "NW", "N", "NE", "E", "SE" };
        for (int d = -180; d <= 540; d += 15) {
            float rel = (float) (((d - yaw) % 360 + 540) % 360 - 180);
            float x = center + rel * pxPerDeg;
            if (x < -10 || x > W + 10) continue;
            if (d % 45 == 0) {
                String n = names[Math.floorMod(d / 45, 8)];
                int c = n.length() == 1 ? (n.equals("N") ? accent() | 0xFF000000 : textColor.get()) : 0xFFB9B3CC;
                Render2D.textCentered(n, x, 4, c, textShadow.get());
            } else {
                Render2D.rect(x - 0.5f, H - 5, 1, 3, 0x88FFFFFF);
            }
        }
        Render2D.endScissor();
        Render2D.rect(center - 0.5f, 1, 1, 3, accent());
    }
}
