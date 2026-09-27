package com.vortex.client.gui;

import com.vortex.client.core.setting.ColorSetting;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Farbwaehler im Stil des uebrigen Clients.
 *
 * Aufbau:
 *   - grosses Farbfeld (waagerecht Sattheit, senkrecht Helligkeit)
 *   - Regenbogenleiste fuer den Farbton
 *   - Leiste fuer die Deckkraft (kariertes Muster dahinter, damit man sie sieht)
 *   - Reihe mit Voreinstellungen zum schnellen Zugriff
 *   - Hex-Eingabe (#RRGGBB oder #AARRGGBB) mit Live-Vorschau
 *   - NEU: Art oben (Solid / Gradient / Wave / Rainbow). Bei einem Verlauf
 *     zwei Reiter "Color 1" / "Color 2", eine laufende Vorschau des Verlaufs,
 *     ein Tempo-Regler und fertige Verlaufs-Vorlagen.
 *
 * Die Farbe wird sofort uebernommen -- man sieht die Wirkung also direkt, ohne
 * erst bestaetigen zu muessen.
 */
public class ColorPickerScreen extends Screen {

    private static final int WIN_W = 300;
    private static final int WIN_H_MAX = 300;
    /** Alles ausser dem Farbfeld braucht so viel Hoehe. */
    private static final int FEST_H = 196;

    /**
     * Hoehe des Fensters -- hoechstens 250, sonst so viel, wie das Fenster
     * hergibt.
     *
     * Vorher fest 250. Minecraft verkleinert die Oberflaeche aber bis auf
     * 240 Pixel Hoehe -- dann ragte der Farbwaehler oben und unten heraus,
     * und der Knopf "Fertig" war nicht mehr zu erreichen. Was fehlt, wird
     * vom Farbfeld abgezogen; alles darunter rueckt entsprechend nach.
     */
    private int winH() {
        return Math.max(FEST_H + 40, Math.min(WIN_H_MAX, this.height - 8));
    }

    private static final int C_DIM    = VortexStyle.DIM;
    private static final int C_WINDOW = VortexStyle.WINDOW;
    private static final int C_BAR    = VortexStyle.BAR;
    private static final int C_INNER  = VortexStyle.INNER;
    private static final int C_LINE   = VortexStyle.LINE;

    /** Haeufig gebrauchte Farben als Schnellauswahl. */
    private static final int[] PRESETS = {
        0xFFFFFFFF, 0xFF000000, 0xFFFF5555, 0xFFFFAA00, 0xFFFFFF55,
        0xFF55FF55, 0xFF55FFFF, 0xFFAA00FF
    };

    /** Fertige Verlaeufe: je zwei Farben. Der erste ist der des Clients. */
    private static final int[][] VERLAEUFE = {
        {0xFF8B5CF6, 0xFF3B82F6},   // Vortex: Violett -> Blau
        {0xFFFF7A18, 0xFFFF2E93},   // Sonnenuntergang
        {0xFF22D3EE, 0xFF34D399},   // Aqua
        {0xFFFF3B3B, 0xFFFFD23F},   // Feuer
    };

    /** Welche Farbe gerade bearbeitet wird: 0 = Farbe 1, 1 = Farbe 2. */
    private int bearbeitet = 0;

    private final Screen parent;
    private final ColorSetting setting;
    private final Runnable onChange;

    // Farbe wird intern als HSB gehalten -- damit laesst sich sinnvoll waehlen.
    private float hue = 0f, sat = 1f, bri = 1f;
    private int alpha = 255;

    private EditBox hexField;
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
    private int artX, artY, artB, reiterY, reiterB, tempoX, tempoY, tempoW, vorlageX;

    public ColorPickerScreen(Screen parent, ColorSetting setting) {
        this(parent, setting, null);
    }

    public ColorPickerScreen(Screen parent, ColorSetting setting, Runnable onChange) {
        super(Component.literal("Pick a colour"));
        this.parent = parent;
        this.setting = setting;
        this.onChange = onChange;
        fromArgb(setting.color1());
    }

    private boolean verlauf() {
        return setting.gradientAllowed() && setting.type() != ColorSetting.SOLID;
    }

    /** Hat die Art zwei Farben zum Einstellen? (Rainbow nicht.) */
    private boolean zweiFarben() {
        int t = setting.type();
        return setting.gradientAllowed() && (t == ColorSetting.GRADIENT || t == ColorSetting.WAVE);
    }

    private int bearbeiteteFarbe() {
        return bearbeitet == 1 ? setting.color2() : setting.color1();
    }

    private void wechsleZu(int welche) {
        bearbeitet = welche;
        fromArgb(bearbeiteteFarbe());
        if (hexField != null) hexField.setValue(hex(currentArgb()));
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
        schreibe(currentArgb());
        if (onChange != null) {
            try {
                onChange.run();
            } catch (Throwable pvpErr) {
                com.vortex.client.core.Errors.report("ColorPickerScreen", pvpErr);
            }
        }
        if (hexField != null && !hexField.isFocused()) {
            hexField.setValue(hex(currentArgb()));
        }
    }

    /** In die gerade bearbeitete Farbe schreiben. */
    private void schreibe(int argb) {
        if (bearbeitet == 1 && zweiFarben()) setting.setColor2(argb);
        else setting.set(argb);
    }

    private void geaendert() {
        if (onChange == null) return;
        try {
            onChange.run();
        } catch (Throwable pvpErr) {
            com.vortex.client.core.Errors.report("ColorPickerScreen", pvpErr);
        }
    }

    private static String hex(int argb) {
        return String.format(Locale.ROOT, "#%08X", argb);
    }

    // ---------------------------------------------------------------- Aufbau

    @Override
    protected void init() {
        int wx = (this.width - WIN_W) / 2;
        int wy = (this.height - winH()) / 2;

        this.hexField = new EditBox(this.font,
                wx + 60, wy + winH() - 30, 100, 14, Component.literal(""));
        this.hexField.setBordered(false);
        this.hexField.setMaxLength(9);
        this.hexField.setValue(hex(currentArgb()));
        this.hexField.setResponder(this::onHexTyped);
        this.addRenderableWidget(this.hexField);
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
            schreibe(currentArgb());
            if (onChange != null) onChange.run();
        } catch (Throwable pvpErr) {
                com.vortex.client.core.Errors.report("ColorPickerScreen", pvpErr);
            }
    }

    // -------------------------------------------------------------- Zeichnen

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        this.mx = mouseX;
        this.my = mouseY;

        long now = System.nanoTime();
        float dt = (lastNano == 0L) ? 0.016f : (now - lastNano) / 1_000_000_000.0f;
        lastNano = now;
        if (dt > 0.1f) dt = 0.1f;
        openAnim += (1f - openAnim) * (1f - (float) Math.exp(-14f * dt));

        ctx.fill(0, 0, this.width, this.height, fade(C_DIM, openAnim));

        int wx = (this.width - WIN_W) / 2;
        int wy = (this.height - winH()) / 2 + (int) ((1f - openAnim) * 12f);
        int accent = Theme.INSTANCE.accent.get() | 0xFF000000;

        // Schatten und Akzentlinie wie im ClickGUI -- das haelt alle

        // Bildschirme optisch zusammen.

        VortexStyle.schatten(ctx, wx, wy, WIN_W, winH(), openAnim);

        roundRect(ctx, wx, wy, WIN_W, winH(), fade(C_WINDOW, openAnim));

        VortexStyle.akzentLinie(ctx, wx + 4, wy, WIN_W - 8, openAnim);
        ctx.fill(wx, wy, wx + WIN_W, wy + 1, fade(accent, openAnim));

        // Kopfzeile mit Titel und aktueller Farbe.
        ctx.fill(wx, wy, wx + WIN_W, wy + 26, fade(C_BAR, openAnim));
        ctx.fill(wx, wy + 25, wx + WIN_W, wy + 26, fade(C_LINE, openAnim));
        ctx.text(this.font, Component.literal(setting.getName()),
                wx + 10, wy + 9, fade(0xFFFFFFFF, openAnim));
        drawChecker(ctx, wx + WIN_W - 44, wy + 7, 34, 12);
        verlaufsBalken(ctx, wx + WIN_W - 44, wy + 7, 34, 12);

        // --- Art: Solid / Gradient / Wave / Rainbow ------------------------
        artY = wy + 32;
        artX = wx + 10;
        artB = (WIN_W - 20 - 3 * 4) / 4;
        if (setting.gradientAllowed()) {
            for (int i = 0; i < 4; i++) {
                int bx = artX + i * (artB + 4);
                boolean an = setting.type() == i;
                boolean hov = inRect(mx, my, bx, artY, artB, 14);
                roundRect(ctx, bx, artY, artB, 14,
                        an ? mix(C_INNER, accent, 0.55f) : (hov ? mix(C_INNER, accent, 0.2f) : C_INNER));
                String t = ColorSetting.TYPES[i];
                ctx.text(this.font, Component.literal(t), bx + (artB - this.font.width(t)) / 2, artY + 3,
                        an ? 0xFFFFFFFF : 0xFFB4B4C0, false);
            }
        } else {
            ctx.text(this.font, Component.literal("Single colour only"), artX, artY + 3, 0xFF7F7896, false);
        }

        // --- Reiter Farbe 1 / Farbe 2 und laufende Vorschau -----------------
        reiterY = wy + 50;
        reiterB = 60;
        int vorschauX = wx + 10;
        if (zweiFarben()) {
            for (int i = 0; i < 2; i++) {
                int bx = wx + 10 + i * (reiterB + 4);
                boolean an = bearbeitet == i;
                boolean hov = inRect(mx, my, bx, reiterY, reiterB, 14);
                roundRect(ctx, bx, reiterY, reiterB, 14,
                        an ? mix(C_INNER, accent, 0.45f) : (hov ? mix(C_INNER, accent, 0.15f) : C_INNER));
                int farbe = i == 0 ? setting.color1() : setting.color2();
                ctx.fill(bx + 4, reiterY + 3, bx + 12, reiterY + 11, farbe | 0xFF000000);
                ctx.text(this.font, Component.literal(i == 0 ? "Color 1" : "Color 2"), bx + 16, reiterY + 3,
                        an ? 0xFFFFFFFF : 0xFFB4B4C0, false);
            }
            vorschauX = wx + 10 + 2 * (reiterB + 4) + 4;
        }
        if (verlauf()) {
            int vb = wx + WIN_W - 10 - vorschauX;
            drawChecker(ctx, vorschauX, reiterY + 1, vb, 12);
            verlaufsBalken(ctx, vorschauX, reiterY + 1, vb, 12);
        }

        // --- Farbfeld: waagerecht Sattheit, senkrecht Helligkeit ---
        fieldX = wx + 10;
        fieldY = wy + 70;
        fieldW = WIN_W - 20;
        // Farbfeld gibt nach, wenn das Fenster kleiner ist.
        fieldH = Math.max(40, winH() - FEST_H);
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
        // In Streifen von 3 Pixeln statt pixelweise -- vorher 280 Aufrufe
        // je Bild allein fuer diese Leiste. Fuer das Auge gleich.
        for (int i = 0; i < hueW; i += 3) {
            int c = hsbToRgb((i + 1.5f) / (float) hueW, 1f, 1f) | 0xFF000000;
            ctx.fill(hueX + i, hueY, hueX + Math.min(i + 3, hueW), hueY + hueH, c);
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
        for (int i = 0; i < alphaW; i += 4) {
            int a = (int) (255f * (i + 2f) / (float) alphaW);
            ctx.fill(alphaX + i, alphaY, alphaX + Math.min(i + 4, alphaW), alphaY + alphaH,
                    (Math.min(255, a) << 24) | solid);
        }
        int ax = alphaX + (int) (alpha / 255f * alphaW);
        ctx.fill(ax - 1, alphaY - 2, ax + 2, alphaY + alphaH + 2, 0xFFFFFFFF);
        ctx.fill(ax, alphaY - 1, ax + 1, alphaY + alphaH + 1, 0xFF000000);

        // --- Tempo (nur bei Verlauf) ---
        tempoX = wx + 50;
        tempoY = alphaY + alphaH + 10;
        tempoW = WIN_W - 60 - 30;
        if (verlauf()) {
            ctx.text(this.font, Component.literal("Speed"), wx + 10, tempoY, 0xFFB4B4C0, false);
            ctx.fill(tempoX, tempoY + 3, tempoX + tempoW, tempoY + 5, VortexStyle.TRACK);
            float p = (setting.speed() - 0.1f) / 4.9f;
            int kx = tempoX + (int) (p * tempoW);
            ctx.fill(tempoX, tempoY + 3, kx, tempoY + 5, accent);
            ctx.fill(kx - 2, tempoY, kx + 2, tempoY + 8, 0xFFFFFFFF);
            String sp = String.format(Locale.ROOT, "%.1fx", setting.speed());
            ctx.text(this.font, Component.literal(sp), tempoX + tempoW + 6, tempoY, 0xFFFFFFFF, false);
        }

        // --- Voreinstellungen ---
        presetCell = 16;
        presetX = wx + 10;
        presetY = tempoY + 14;
        for (int i = 0; i < PRESETS.length; i++) {
            int px = presetX + i * (presetCell + 4);
            boolean hov = inRect(mx, my, px, presetY, presetCell, presetCell);
            if (hov) {
                ctx.fill(px - 1, presetY - 1, px + presetCell + 1, presetY + presetCell + 1,
                        0xFFFFFFFF);
            }
            roundRect(ctx, px, presetY, presetCell, presetCell, PRESETS[i]);
        }
        // Verlaufs-Vorlagen rechts daneben
        if (setting.gradientAllowed()) {
            vorlageX = wx + WIN_W - 10 - VERLAEUFE.length * (presetCell + 4) + 4;
            for (int i = 0; i < VERLAEUFE.length; i++) {
                int px = vorlageX + i * (presetCell + 4);
                boolean hov = inRect(mx, my, px, presetY, presetCell, presetCell);
                if (hov) ctx.fill(px - 1, presetY - 1, px + presetCell + 1, presetY + presetCell + 1, 0xFFFFFFFF);
                for (int k = 0; k < presetCell; k += 2) {
                    int c = mix(VERLAEUFE[i][0], VERLAEUFE[i][1], (k + 1f) / presetCell);
                    ctx.fill(px + k, presetY, px + Math.min(k + 2, presetCell), presetY + presetCell, c);
                }
            }
        }

        // --- Hex-Eingabe ---
        int hy = wy + winH() - 33;
        ctx.text(this.font, Component.literal("Hex"),
                wx + 10, hy + 6, 0xFFB4B4C0, false);
        roundRect(ctx, wx + 50, hy, 120, 20, C_INNER);

        // --- Fertig-Knopf ---
        String done = "Done";
        int dw = this.font.width(done) + 20;
        int dx = wx + WIN_W - dw - 10;
        boolean dHov = inRect(mx, my, dx, hy, dw, 20);
        roundRect(ctx, dx, hy, dw, 20, dHov ? mix(C_INNER, accent, 0.45f) : C_INNER);
        ctx.text(this.font, Component.literal(done),
                dx + 10, hy + 6, 0xFFFFFFFF, false);

        if (hexField != null) {
            hexField.setX(wx + 56);
            hexField.setY(hy + 6);
        }
        super.extractRenderState(ctx, mouseX, mouseY, delta);
    }

    /**
     * Farbfeld zeichnen. Aus Aufwandsgruenden in schmalen Streifen: waagerecht
     * die Sattheit, senkrecht per Verlauf die Helligkeit.
     */
    private void drawSatBriField(GuiGraphicsExtractor ctx, int x, int y, int w, int h) {
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

    /** Die Einstellung als Balken -- einfarbig oder als (laufender) Verlauf. */
    private void verlaufsBalken(GuiGraphicsExtractor ctx, int x, int y, int w, int h) {
        if (!verlauf()) {
            roundRect(ctx, x, y, w, h, currentArgb());
            return;
        }
        for (int i = 0; i < w; i += 2) {
            int c = setting.at((i + 1f) / Math.max(1, w));
            ctx.fill(x + i, y, x + Math.min(i + 2, w), y + h, c);
        }
    }

    /** Kariertes Muster als Untergrund fuer halbdurchsichtige Farben. */
    private void drawChecker(GuiGraphicsExtractor ctx, int x, int y, int w, int h) {
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
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent click, boolean doubled) {
        // Our own areas are tested BEFORE the screen's own handling.
        //
        // The other way round, the first press went to the widgets -- focus
        // changes and the hex field -- and the hue bar only started working
        // after something else had taken that first click. Which is exactly
        // what "I have to click the big square first" looked like.
        int mode = regionAt(mx, my);
        if (mode != 0) {
            dragMode = mode;
            applyDrag();
            return true;
        }

        if (super.mouseClicked(click, doubled)) return true;

        // Art
        if (setting.gradientAllowed()) {
            for (int i = 0; i < 4; i++) {
                if (inRect(mx, my, artX + i * (artB + 4), artY, artB, 14)) {
                    setting.setType(i);
                    if (!zweiFarben() && bearbeitet == 1) wechsleZu(0);
                    geaendert();
                    return true;
                }
            }
        }
        // Reiter Farbe 1 / 2
        if (zweiFarben()) {
            for (int i = 0; i < 2; i++) {
                if (inRect(mx, my, artX + i * (reiterB + 4), reiterY, reiterB, 14)) {
                    wechsleZu(i);
                    return true;
                }
            }
        }
        // Verlaufs-Vorlagen: setzen beide Farben (und schalten auf Verlauf)
        if (setting.gradientAllowed()) {
            for (int i = 0; i < VERLAEUFE.length; i++) {
                int px = vorlageX + i * (presetCell + 4);
                if (inRect(mx, my, px, presetY, presetCell, presetCell)) {
                    int deck = setting.color1() & 0xFF000000;
                    setting.set(deck | (VERLAEUFE[i][0] & 0xFFFFFF));
                    setting.setColor2(deck | (VERLAEUFE[i][1] & 0xFFFFFF));
                    if (!zweiFarben()) setting.setType(ColorSetting.GRADIENT);
                    wechsleZu(bearbeitet);
                    geaendert();
                    return true;
                }
            }
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
        int wy = (this.height - winH()) / 2;
        int hy = wy + winH() - 33;
        String done = "Done";
        int dw = this.font.width(done) + 20;
        int dx = wx + WIN_W - dw - 10;
        if (inRect(mx, my, dx, hy, dw, 20)) {
            this.onClose();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent click, double dx, double dy) {
        // Dragging without a press we saw: pick up whatever is under the
        // cursor. Belt and braces -- if the press was swallowed somewhere,
        // the slider still follows the mouse instead of doing nothing.
        if (dragMode == 0) {
            dragMode = regionAt(mx, my);
        }
        if (dragMode != 0) {
            applyDrag();
            return true;
        }
        return super.mouseDragged(click, dx, dy);
    }

    /**
     * Which of the three areas is at this point.
     *
     * @return 1 field, 2 hue, 3 opacity, 0 none
     */
    private int regionAt(int px, int py) {
        if (inRect(px, py, fieldX, fieldY, fieldW, fieldH)) return 1;
        // A little taller than drawn, so the thin bars are easy to grab.
        if (inRect(px, py, hueX, hueY - 3, hueW, hueH + 6)) return 2;
        if (inRect(px, py, alphaX, alphaY - 3, alphaW, alphaH + 6)) return 3;
        if (verlauf() && inRect(px, py, tempoX - 3, tempoY - 2, tempoW + 6, 12)) return 4;
        return 0;
    }

    /** Runs the change for whatever is being dragged. */
    private void applyDrag() {
        switch (dragMode) {
            case 1: updateField(); break;
            case 2: updateHue(); break;
            case 3: updateAlpha(); break;
            case 4:
                setting.setSpeed(0.1f + clamp01((mx - tempoX) / (float) tempoW) * 4.9f);
                geaendert();
                break;
            default: break;
        }
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent click) {
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

    private void roundRect(GuiGraphicsExtractor ctx, int x, int y, int w, int h, int color) {
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
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(parent);
    }
}
