package com.vortex.client.gui;

import com.vortex.client.core.setting.ColorSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.util.List;

/**
 * Menue zum Anpassen der Oberflaechenfarben.
 *
 * Oben eine Reihe fertiger Farbstimmungen (setzt die Akzentfarbe), darunter jede
 * Einzelfarbe mit Farbfeld. Ein Klick auf eine Zeile oeffnet den Farbwaehler;
 * Aenderungen sind sofort sichtbar, weil die gesamte Oberflaeche ihre Farben aus
 * {@link Theme} bezieht.
 */
public class ThemeScreen extends Screen {

    private static final int WIN_W = 320;
    private static final int ROW_H = 24;
    private static final int HEADER_H = 62;
    private static final int FOOTER_H = 26;

    private static final int C_DIM    = 0xB4000000;
    private static final int C_WINDOW = 0xF21B1B21;
    private static final int C_BAR    = 0xFF16161B;
    private static final int C_CARD   = 0xFF24242B;
    private static final int C_HOV    = 0xFF2E2E38;
    private static final int C_INNER  = 0xFF1C1C22;
    private static final int C_LINE   = 0xFF31313A;

    /** Fertige Akzent-Stimmungen. */
    private static final int[] MOODS = {
        0xFF4C8BF5, // blau (Standard)
        0xFF55FF7A, // gruen
        0xFFFF5555, // rot
        0xFFAA66FF, // violett
        0xFFFFAA00, // orange
        0xFF22D3D3, // tuerkis
        0xFFFF66C4, // pink
        0xFFE0E0E0  // weiss
    };

    private final Screen parent;
    private float openAnim = 0f;
    private long lastNano = 0L;
    private int mx = 0, my = 0;

    private int winX, winY, winH;
    private int moodX, moodY, moodCell;

    public ThemeScreen(Screen parent) {
        super(Text.literal("Theme"));
        this.parent = parent;
    }

    private List<ColorSetting> colors() {
        return Theme.INSTANCE.all();
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        this.mx = mouseX;
        this.my = mouseY;

        long now = System.nanoTime();
        float dt = (lastNano == 0L) ? 0.016f : (now - lastNano) / 1_000_000_000.0f;
        lastNano = now;
        if (dt > 0.1f) dt = 0.1f;
        openAnim += (1f - openAnim) * Math.min(1f, 14f * dt);

        ctx.fill(0, 0, this.width, this.height, fade(C_DIM, openAnim));

        List<ColorSetting> list = colors();
        winH = HEADER_H + list.size() * (ROW_H + 4) + FOOTER_H;
        winX = (this.width - WIN_W) / 2;
        winY = (this.height - winH) / 2 + (int) ((1f - openAnim) * 12f);

        int accent = Theme.INSTANCE.accent.get() | 0xFF000000;

        roundRect(ctx, winX, winY, WIN_W, winH, fade(C_WINDOW, openAnim));
        ctx.fill(winX, winY, winX + WIN_W, winY + 1, fade(accent, openAnim));

        // Kopfzeile
        ctx.fill(winX, winY, winX + WIN_W, winY + HEADER_H, fade(C_BAR, openAnim));
        ctx.fill(winX, winY + HEADER_H - 1, winX + WIN_W, winY + HEADER_H,
                fade(C_LINE, openAnim));

        boolean backHov = inRect(mx, my, winX + 8, winY + 8, 16, 16);
        ctx.drawTextWithShadow(this.textRenderer, Text.literal("<"),
                winX + 12, winY + 12, fade(backHov ? accent : 0xFF9A9AA6, openAnim));
        ctx.drawTextWithShadow(this.textRenderer, Text.literal("Theme"),
                winX + 30, winY + 11, fade(0xFFFFFFFF, openAnim));

        // Farbstimmungen
        ctx.drawText(this.textRenderer, Text.literal("Accent"),
                winX + 10, winY + 32, fade(0xFF74747F, openAnim), false);
        moodCell = 16;
        moodX = winX + 52;
        moodY = winY + 29;
        int curAccent = Theme.INSTANCE.accent.get() | 0xFF000000;
        for (int i = 0; i < MOODS.length; i++) {
            int px = moodX + i * (moodCell + 5);
            boolean sel = (MOODS[i] == curAccent);
            boolean hov = inRect(mx, my, px, moodY, moodCell, moodCell);
            if (sel || hov) {
                ctx.fill(px - 2, moodY - 2, px + moodCell + 2, moodY + moodCell + 2,
                        sel ? 0xFFFFFFFF : 0x80FFFFFF);
            }
            roundRect(ctx, px, moodY, moodCell, moodCell, MOODS[i]);
        }

        // Farbzeilen
        int y = winY + HEADER_H + 4;
        for (ColorSetting c : list) {
            boolean hov = inRect(mx, my, winX + 8, y, WIN_W - 16, ROW_H);
            roundRect(ctx, winX + 8, y, WIN_W - 16, ROW_H, hov ? C_HOV : C_CARD);
            ctx.drawText(this.textRenderer, Text.literal(c.getName()),
                    winX + 18, y + 8, 0xFFD0D0DA, false);

            // Farbfeld + Hex-Wert
            String hx = String.format(java.util.Locale.ROOT, "#%08X", c.get());
            int hw = this.textRenderer.getWidth(hx);
            ctx.drawText(this.textRenderer, Text.literal(hx),
                    winX + WIN_W - 34 - hw - 8, y + 8, 0xFF74747F, false);
            roundRect(ctx, winX + WIN_W - 34, y + 5, 26, 14, 0xFF000000);
            roundRect(ctx, winX + WIN_W - 33, y + 6, 24, 12, c.get() | 0xFF000000);

            y += ROW_H + 4;
        }

        // Fusszeile mit Zuruecksetzen
        int fy = winY + winH - FOOTER_H;
        ctx.fill(winX, fy, winX + WIN_W, winY + winH, fade(C_BAR, openAnim));
        ctx.fill(winX, fy, winX + WIN_W, fy + 1, fade(C_LINE, openAnim));
        String reset = "Reset";
        int rw = this.textRenderer.getWidth(reset) + 16;
        boolean rHov = inRect(mx, my, winX + 10, fy + 4, rw, 17);
        roundRect(ctx, winX + 10, fy + 4, rw, 17, rHov ? mix(C_INNER, accent, 0.4f) : C_INNER);
        ctx.drawText(this.textRenderer, Text.literal(reset),
                winX + 18, fy + 9, 0xFFD0D0DA, false);

        ctx.drawText(this.textRenderer, Text.literal("Click a row to open the colour picker"),
                winX + 20 + rw, fy + 9, 0xFF5A5A66, false);

        super.render(ctx, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.gui.Click click, boolean doubled) {
        if (super.mouseClicked(click, doubled)) return true;

        // Zurueck
        if (inRect(mx, my, winX + 8, winY + 8, 16, 16)) {
            this.close();
            return true;
        }
        // Farbstimmung
        for (int i = 0; i < MOODS.length; i++) {
            int px = moodX + i * (moodCell + 5);
            if (inRect(mx, my, px, moodY, moodCell, moodCell)) {
                Theme.INSTANCE.accent.set(MOODS[i]);
                return true;
            }
        }
        // Zuruecksetzen
        int fy = winY + winH - FOOTER_H;
        String reset = "Reset";
        int rw = this.textRenderer.getWidth(reset) + 16;
        if (inRect(mx, my, winX + 10, fy + 4, rw, 17)) {
            Theme.INSTANCE.resetDefaults();
            return true;
        }
        // Farbzeile -> Farbwaehler
        int y = winY + HEADER_H + 4;
        for (ColorSetting c : colors()) {
            if (inRect(mx, my, winX + 8, y, WIN_W - 16, ROW_H)) {
                MinecraftClient.getInstance().setScreen(new ColorPickerScreen(this, c));
                return true;
            }
            y += ROW_H + 4;
        }
        return false;
    }

    private boolean inRect(int px, int py, int x, int y, int w, int h) {
        return px >= x && px < x + w && py >= y && py < y + h;
    }

    private void roundRect(DrawContext ctx, int x, int y, int w, int h, int color) {
        if (w <= 0 || h <= 0) return;
        ctx.fill(x + 1, y, x + w - 1, y + h, color);
        ctx.fill(x, y + 1, x + 1, y + h - 1, color);
        ctx.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
    }

    private static int fade(int argb, float f) {
        if (f >= 1f) return argb;
        if (f <= 0f) return argb & 0x00FFFFFF;
        int a = (int) (((argb >>> 24) & 0xFF) * f);
        return (a << 24) | (argb & 0x00FFFFFF);
    }

    private static int mix(int a, int b, float t) {
        if (t < 0f) t = 0f;
        if (t > 1f) t = 1f;
        int aa = (a >>> 24) & 0xFF, ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int ba = (b >>> 24) & 0xFF, br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        return (((int) (aa + (ba - aa) * t)) << 24)
                | (((int) (ar + (br - ar) * t)) << 16)
                | (((int) (ag + (bg - ag) * t)) << 8)
                | ((int) (ab + (bb - ab) * t));
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void close() {
        this.client.setScreen(parent);
    }
}
