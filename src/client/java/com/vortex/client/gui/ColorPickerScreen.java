package com.vortex.client.gui;

import com.vortex.client.core.setting.ColorSetting;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.Locale;

/**
 * Farbwaehler im Stil des uebrigen Clients.
 *
 * Aufbau:
 *   - grosses Farbfeld (waagerecht Sattheit, senkrecht Helligkeit)
 *   - Regenbogenleiste fuer den Farbton
 *   - Leiste fuer die Deckkraft (kariertes Muster dahinter, damit man sie sieht)
 *   - Reihe mit Voreinstellungen zum schnellen Zugriff
 *   - Hex-Eingabe (#RRGGBB oder #AARRGGBB) mit Live-Vorschau
 *
 * Die Farbe wird sofort uebernommen -- man sieht die Wirkung also direkt, ohne
 * erst bestaetigen zu muessen.
 */
public class ColorPickerScreen extends Screen {

    private static final int WIN_W = 300;
    private static final int WIN_H = 250;

    private static final int C_DIM    = 0xB4000000;
    private static final int C_WINDOW = 0xF21B1B21;
    private static final int C_BAR    = 0xFF16161B;
    private static final int C_INNER  = 0xFF1C1C22;
    private static final int C_LINE   = 0xFF31313A;

    /** Haeufig gebrauchte Farben als Schnellauswahl. */
    private static final int[] PRESETS = {
        0xFFFFFFFF, 0xFF000000, 0xFFFF5555, 0xFFFFAA00, 0xFFFFFF55,
        0xFF55FF55, 0xFF55FFFF, 0xFF5555FF, 0xFFAA00FF, 0xFFFF55AA
    };

    private final Screen parent;
    private final ColorSetting setting;
    private final Runnable onChange;

    // Farbe wird intern als HSB gehalten -- damit laesst sich sinnvoll waehlen.
    private float hue = 0f, sat = 1f, bri = 1f;
    private int alpha = 255;

    private TextFieldWidget hexField;
    private float openAnim = 0f;
    private long lastNano = 0L;
    private int mx = 0, my = 0;

    // Was wird gerade gezogen?
    private int dragMode = 0; // 1 = Farbfeld, 2 = Farbton, 3 = Deckkraft

    // Bereiche (in render gesetzt, in den Klick-Methoden genutzt).
    private int fieldX, fieldY, fieldW, fieldH;
    private int hueX, hueY, hueW, hueH;
    private int alphaX, alphaY, alphaW, alphaH;
    private int presetX, presetY, presetCell;

    public ColorPickerScreen(Screen parent, ColorSetting setting) {
        this(parent, setting, null);
    }

    public ColorPickerScreen(Screen parent, ColorSetting setting, Runnable onChange) {
        super(Text.literal("Farbe waehlen"));
        this.parent = parent;
        this.setting = setting;
        this.onChange = onChange;
        fromArgb(setting.get());
    }

    // ------------------------------------------------------------ Farbmodell

    private void fromArgb(int argb) {
        alpha = (argb >>> 24) & 0xFF;
        if (alpha == 0) alpha = 255;
        float[] hsb = rgbToHsb((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF);
        hue = hsb[0];
        sat = hsb[1];
        bri = hsb[2];
    }

    private int currentArgb() {
        int rgb = hsbToRgb(hue, sat, bri) & 0x00FFFFFF;
        return (alpha << 24) | rgb;
    }

    /** Farbe uebernehmen und die Live-Vorschau ausloesen. */
    private void apply() {
        setting.set(currentArgb());
        if (onChange != null) {
            try {
                onChange.run();
            } catch (Throwable pvpErr) {
                com.vortex.client.core.Errors.report("ColorPickerScreen", pvpErr);
            }
        }
        if (hexField != null && !hexField.isFocused()) {
            hexField.setText(hex(currentArgb()));
        }
    }

    private static String hex(int argb) {
        return String.format(Locale.ROOT, "#%08X", argb);
    }

    // ---------------------------------------------------------------- Aufbau

    @Override
    protected void init() {
        int wx = (this.width - WIN_W) / 2;
        int wy = (this.height - WIN_H) / 2;

        this.hexField = new TextFieldWidget(this.textRenderer,
                wx + 60, wy + WIN_H - 30, 100, 14, Text.literal(""));
        this.hexField.setDrawsBackground(false);
        this.hexField.setMaxLength(9);
        this.hexField.setText(hex(currentArgb()));
        this.hexField.setChangedListener(this::onHexTyped);
        this.addDrawableChild(this.hexField);
    }

    /** Hex-Eingabe auswerten -- ungueltige Eingaben werden einfach ignoriert. */
    private void onHexTyped(String txt) {
        if (txt == null) return;
        String t = txt.trim();
        if (t.startsWith("#")) t = t.substring(1);
        if (t.length() != 6 && t.length() != 8) return;
        try {
            long v = Long.parseLong(t, 16);
            int argb = (t.length() == 6)
                    ? (0xFF000000 | (int) v)
                    : (int) v;
            fromArgb(argb);
            setting.set(currentArgb());
            if (onChange != null) onChange.run();
        } catch (Throwable pvpErr) {
                com.vortex.client.core.Errors.report("ColorPickerScreen", pvpErr);
            }
    }

    // -------------------------------------------------------------- Zeichnen

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

        int wx = (this.width - WIN_W) / 2;
        int wy = (this.height - WIN_H) / 2 + (int) ((1f - openAnim) * 12f);
        int accent = Theme.INSTANCE.accent.get() | 0xFF000000;

        roundRect(ctx, wx, wy, WIN_W, WIN_H, fade(C_WINDOW, openAnim));
        ctx.fill(wx, wy, wx + WIN_W, wy + 1, fade(accent, openAnim));

        // Kopfzeile mit Titel und aktueller Farbe.
        ctx.fill(wx, wy, wx + WIN_W, wy + 26, fade(C_BAR, openAnim));
        ctx.fill(wx, wy + 25, wx + WIN_W, wy + 26, fade(C_LINE, openAnim));
        ctx.drawTextWithShadow(this.textRenderer, Text.literal(setting.getName()),
                wx + 10, wy + 9, fade(0xFFFFFFFF, openAnim));
        drawChecker(ctx, wx + WIN_W - 44, wy + 7, 34, 12);
        roundRect(ctx, wx + WIN_W - 44, wy + 7, 34, 12, currentArgb());

        // --- Farbfeld: waagerecht Sattheit, senkrecht Helligkeit ---
        fieldX = wx + 10;
        fieldY = wy + 34;
        fieldW = WIN_W - 20;
        fieldH = 110;
        drawSatBriField(ctx, fieldX, fieldY, fieldW, fieldH);
        // Markierung der aktuellen Position.
        int selX = fieldX + (int) (sat * fieldW);
        int selY = fieldY + (int) ((1f - bri) * fieldH);
        ctx.fill(selX - 4, selY, selX - 1, selY + 1, 0xFFFFFFFF);
        ctx.fill(selX + 2, selY, selX + 5, selY + 1, 0xFFFFFFFF);
        ctx.fill(selX, selY - 4, selX + 1, selY - 1, 0xFFFFFFFF);
        ctx.fill(selX, selY + 2, selX + 1, selY + 5, 0xFFFFFFFF);

        // --- Farbton-Leiste ---
        hueX = wx + 10;
        hueY = fieldY + fieldH + 8;
        hueW = WIN_W - 20;
        hueH = 12;
        for (int i = 0; i < hueW; i++) {
            int c = hsbToRgb(i / (float) hueW, 1f, 1f) | 0xFF000000;
            ctx.fill(hueX + i, hueY, hueX + i + 1, hueY + hueH, c);
        }
        int hx = hueX + (int) (hue * hueW);
        ctx.fill(hx - 1, hueY - 2, hx + 2, hueY + hueH + 2, 0xFFFFFFFF);
        ctx.fill(hx, hueY - 1, hx + 1, hueY + hueH + 1, 0xFF000000);

        // --- Deckkraft-Leiste ---
        alphaX = wx + 10;
        alphaY = hueY + hueH + 8;
        alphaW = WIN_W - 20;
        alphaH = 12;
        drawChecker(ctx, alphaX, alphaY, alphaW, alphaH);
        int solid = hsbToRgb(hue, sat, bri) & 0x00FFFFFF;
        for (int i = 0; i < alphaW; i++) {
            int a = (int) (255f * i / (float) alphaW);
            ctx.fill(alphaX + i, alphaY, alphaX + i + 1, alphaY + alphaH, (a << 24) | solid);
        }
        int ax = alphaX + (int) (alpha / 255f * alphaW);
        ctx.fill(ax - 1, alphaY - 2, ax + 2, alphaY + alphaH + 2, 0xFFFFFFFF);
        ctx.fill(ax, alphaY - 1, ax + 1, alphaY + alphaH + 1, 0xFF000000);

        // --- Voreinstellungen ---
        presetCell = 16;
        presetX = wx + 10;
        presetY = alphaY + alphaH + 10;
        for (int i = 0; i < PRESETS.length; i++) {
            int px = presetX + i * (presetCell + 4);
            boolean hov = inRect(mx, my, px, presetY, presetCell, presetCell);
            if (hov) {
                ctx.fill(px - 1, presetY - 1, px + presetCell + 1, presetY + presetCell + 1,
                        0xFFFFFFFF);
            }
            roundRect(ctx, px, presetY, presetCell, presetCell, PRESETS[i]);
        }

        // --- Hex-Eingabe ---
        int hy = wy + WIN_H - 33;
        ctx.drawText(this.textRenderer, Text.literal("Hex"),
                wx + 10, hy + 6, 0xFFB4B4C0, false);
        roundRect(ctx, wx + 50, hy, 120, 20, C_INNER);

        // --- Fertig-Knopf ---
        String done = "Fertig";
        int dw = this.textRenderer.getWidth(done) + 20;
        int dx = wx + WIN_W - dw - 10;
        boolean dHov = inRect(mx, my, dx, hy, dw, 20);
        roundRect(ctx, dx, hy, dw, 20, dHov ? mix(C_INNER, accent, 0.45f) : C_INNER);
        ctx.drawText(this.textRenderer, Text.literal(done),
                dx + 10, hy + 6, 0xFFFFFFFF, false);

        if (hexField != null) {
            hexField.setX(wx + 56);
            hexField.setY(hy + 6);
        }
        super.render(ctx, mouseX, mouseY, delta);
    }

    /**
     * Farbfeld zeichnen. Aus Aufwandsgruenden in schmalen Streifen: waagerecht
     * die Sattheit, senkrecht per Verlauf die Helligkeit.
     */
    private void drawSatBriField(DrawContext ctx, int x, int y, int w, int h) {
        int step = 2;
        for (int i = 0; i < w; i += step) {
            float s = i / (float) w;
            int top = hsbToRgb(hue, s, 1f) | 0xFF000000;
            // Von der vollen Helligkeit nach Schwarz.
            ctx.fillGradient(x + i, y, x + Math.min(i + step, w), y + h, top, 0xFF000000);
        }
        ctx.fill(x, y, x + w, y + 1, C_LINE);
        ctx.fill(x, y + h - 1, x + w, y + h, C_LINE);
    }

    /** Kariertes Muster als Untergrund fuer halbdurchsichtige Farben. */
    private void drawChecker(DrawContext ctx, int x, int y, int w, int h) {
        int cell = 4;
        for (int iy = 0; iy < h; iy += cell) {
            for (int ix = 0; ix < w; ix += cell) {
                boolean dark = ((ix / cell) + (iy / cell)) % 2 == 0;
                ctx.fill(x + ix, y + iy,
                        x + Math.min(ix + cell, w), y + Math.min(iy + cell, h),
                        dark ? 0xFF808080 : 0xFFC0C0C0);
            }
        }
    }

    // ---------------------------------------------------------------- Eingabe

    @Override
    public boolean mouseClicked(net.minecraft.client.gui.Click click, boolean doubled) {
        if (super.mouseClicked(click, doubled)) return true;

        if (inRect(mx, my, fieldX, fieldY, fieldW, fieldH)) {
            dragMode = 1;
            updateField();
            return true;
        }
        if (inRect(mx, my, hueX, hueY - 2, hueW, hueH + 4)) {
            dragMode = 2;
            updateHue();
            return true;
        }
        if (inRect(mx, my, alphaX, alphaY - 2, alphaW, alphaH + 4)) {
            dragMode = 3;
            updateAlpha();
            return true;
        }
        // Voreinstellungen
        for (int i = 0; i < PRESETS.length; i++) {
            int px = presetX + i * (presetCell + 4);
            if (inRect(mx, my, px, presetY, presetCell, presetCell)) {
                int keepAlpha = alpha;
                fromArgb(PRESETS[i]);
                alpha = keepAlpha; // Deckkraft beibehalten
                apply();
                return true;
            }
        }
        // Fertig-Knopf / ausserhalb -> schliessen
        int wx = (this.width - WIN_W) / 2;
        int wy = (this.height - WIN_H) / 2;
        int hy = wy + WIN_H - 33;
        String done = "Fertig";
        int dw = this.textRenderer.getWidth(done) + 20;
        int dx = wx + WIN_W - dw - 10;
        if (inRect(mx, my, dx, hy, dw, 20)) {
            this.close();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(net.minecraft.client.gui.Click click, double dx, double dy) {
        switch (dragMode) {
            case 1: updateField(); return true;
            case 2: updateHue(); return true;
            case 3: updateAlpha(); return true;
            default: return super.mouseDragged(click, dx, dy);
        }
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.gui.Click click) {
        dragMode = 0;
        return super.mouseReleased(click);
    }

    private void updateField() {
        sat = clamp01((mx - fieldX) / (float) fieldW);
        bri = 1f - clamp01((my - fieldY) / (float) fieldH);
        apply();
    }

    private void updateHue() {
        hue = clamp01((mx - hueX) / (float) hueW);
        apply();
    }

    private void updateAlpha() {
        alpha = (int) (clamp01((mx - alphaX) / (float) alphaW) * 255f);
        apply();
    }

    // ----------------------------------------------------------- Hilfsmittel

    /**
     * HSB -> RGB. Bewusst selbst gerechnet statt ueber java.awt: AWT ist in einer
     * Spiel-Laufzeitumgebung nicht garantiert vorhanden und kann auf manchen
     * Systemen unerwuenschte Nebenwirkungen haben.
     */
    private static int hsbToRgb(float h, float s, float b) {
        h = h - (float) Math.floor(h);          // auf 0..1 bringen
        s = clamp01(s);
        b = clamp01(b);
        int r, g, bl;
        if (s <= 0f) {
            r = g = bl = Math.round(b * 255f);
        } else {
            float hh = h * 6f;
            int sector = (int) Math.floor(hh);
            float f = hh - sector;
            float p = b * (1f - s);
            float q = b * (1f - s * f);
            float t = b * (1f - s * (1f - f));
            switch (sector % 6) {
                case 0:  r = r255(b); g = r255(t); bl = r255(p); break;
                case 1:  r = r255(q); g = r255(b); bl = r255(p); break;
                case 2:  r = r255(p); g = r255(b); bl = r255(t); break;
                case 3:  r = r255(p); g = r255(q); bl = r255(b); break;
                case 4:  r = r255(t); g = r255(p); bl = r255(b); break;
                default: r = r255(b); g = r255(p); bl = r255(q); break;
            }
        }
        return (r << 16) | (g << 8) | bl;
    }

    private static int r255(float v) {
        int i = Math.round(v * 255f);
        if (i < 0) return 0;
        if (i > 255) return 255;
        return i;
    }

    /** RGB -> HSB (Gegenstueck zu hsbToRgb). */
    private static float[] rgbToHsb(int r, int g, int b) {
        float rf = r / 255f, gf = g / 255f, bf = b / 255f;
        float max = Math.max(rf, Math.max(gf, bf));
        float min = Math.min(rf, Math.min(gf, bf));
        float d = max - min;

        float h;
        if (d == 0f) {
            h = 0f;
        } else if (max == rf) {
            h = ((gf - bf) / d) / 6f;
        } else if (max == gf) {
            h = (2f + (bf - rf) / d) / 6f;
        } else {
            h = (4f + (rf - gf) / d) / 6f;
        }
        if (h < 0f) h += 1f;

        float s = (max == 0f) ? 0f : d / max;
        return new float[] { h, s, max };
    }

    private static float clamp01(float v) {
        if (v < 0f) return 0f;
        if (v > 1f) return 1f;
        return v;
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
