package com.vortex.client.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Gemeinsame Farbwelt und Zeichenhilfen fuer ALLE Bildschirme.
 *
 * WARUM ES DAS GIBT: Jeder Bildschirm -- Theme, Macros, Waypoints, Keys,
 * Skins, Auswahllisten -- hatte seine eigenen sieben Farbkonstanten. Als das
 * ClickGUI neu gestaltet wurde, blieben sie alle beim alten Grau. Oeffnete
 * man einen davon, wirkte es wie ein anderes Programm.
 *
 * Jetzt verweisen alle Bildschirme hierher. Eine Farbe hier aendern heisst,
 * sie ueberall aendern.
 */
public final class VortexStyle {

    private VortexStyle() {}

    // --- Palette -------------------------------------------------------------
    //
    // Violett gefaerbtes Tiefschwarz als Basis, Akzent als Verlauf von Violett
    // nach Blau. Die Helligkeitsstufen liegen eng beieinander, damit Flaechen
    // als Schichten wirken und nicht als Kaesten mit Rahmen.

    public static final int DIM     = 0xD2060409;  // Welt abdunkeln
    public static final int WINDOW  = 0xFC0E0B16;  // Fensterflaeche
    public static final int BAR     = 0xFF0A0812;  // Leisten, eine Stufe tiefer
    public static final int CARD    = 0xFF15111F;  // Karte
    public static final int HOV     = 0xFF1E1930;  // Karte unter dem Zeiger
    public static final int INNER   = 0xFF120E1B;  // eingelassene Flaeche
    public static final int LINE    = 0xFF241E36;  // Trennlinie
    public static final int TRACK   = 0xFF2A2340;  // Schiene
    public static final int TEXT    = 0xFFF2F0F8;  // Haupttext
    public static final int TEXT_DIM= 0xFF7F7896;  // Nebentext

    public static final int VIOLETT = 0xFF8B5CF6;
    public static final int BLAU    = 0xFF3B82F6;

    /** Akzentverlauf an einer Stelle zwischen 0 und 1. */
    public static int akzent(float t) {
        return mix(VIOLETT, BLAU, Math.max(0f, Math.min(1f, t)));
    }

    // --- Farben rechnen ------------------------------------------------------

    public static int mix(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int aa = (a >>> 24) & 0xFF, ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int ba = (b >>> 24) & 0xFF, br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        return ((int) (aa + (ba - aa) * t) << 24) | ((int) (ar + (br - ar) * t) << 16)
                | ((int) (ag + (bg - ag) * t) << 8) | (int) (ab + (bb - ab) * t);
    }

    public static int fade(int argb, float f) {
        if (f >= 1f) return argb;
        if (f <= 0f) return argb & 0x00FFFFFF;
        int al = (int) (((argb >>> 24) & 0xFF) * f);
        return (al << 24) | (argb & 0x00FFFFFF);
    }

    // --- Zeichnen ------------------------------------------------------------

    /**
     * Akzentlinie oben am Fenster, als Verlauf.
     *
     * Das Erkennungsmerkmal, das alle Bildschirme zusammenhaelt: eine
     * duenne Linie von Violett nach Blau an der Oberkante. Gezeichnet in
     * Baendern, nicht pixelweise -- pixelweise waren es im ClickGUI ueber
     * tausend Aufrufe je Bild und der Grund fuer das Ruckeln beim Scrollen.
     */
    public static void akzentLinie(GuiGraphicsExtractor ctx, int x, int y, int w, float alpha) {
        if (w <= 0) return;
        int baender = Math.max(1, (w + 7) / 8);
        for (int b = 0; b < baender; b++) {
            int ax = x + (int) ((long) w * b / baender);
            int bx = x + (int) ((long) w * (b + 1) / baender);
            if (bx <= ax) continue;
            float t = (b + 0.5f) / baender;
            ctx.fill(ax, y, bx, y + 2, fade(akzent(t), alpha));
        }
    }

    /**
     * Weicher Schatten um ein Fenster.
     *
     * Vier immer blassere Rahmen. Hebt das Fenster vom Spiel ab, ohne einen
     * harten Rand zu ziehen.
     */
    public static void schatten(GuiGraphicsExtractor ctx, int x, int y, int w, int h, float alpha) {
        for (int i = 1; i <= 4; i++) {
            int a = (int) (36 * alpha / i);
            if (a <= 0) continue;
            int c = (a << 24);
            ctx.fill(x - i, y - i, x + w + i, y - i + 1, c);
            ctx.fill(x - i, y + h + i - 1, x + w + i, y + h + i, c);
            ctx.fill(x - i, y - i, x - i + 1, y + h + i, c);
            ctx.fill(x + w + i - 1, y - i, x + w + i, y + h + i, c);
        }
    }
}
