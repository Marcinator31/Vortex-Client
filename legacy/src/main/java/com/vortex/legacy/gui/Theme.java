package com.vortex.legacy.gui;

import com.vortex.legacy.core.ClientSettings;

/** Vortex-Farben (wie im grossen Client). */
public final class Theme {
    private Theme() {}
    public static final int BG = 0xF0100D18;
    public static final int PANEL = 0xF5151120;
    public static final int CARD = 0xFF1B1726;
    public static final int CARD_HOVER = 0xFF231E33;
    public static final int LINE = 0x22FFFFFF;
    public static final int TEXT = 0xFFF2F0F8;
    public static final int TEXT2 = 0xFFB9B3CC;
    public static final int DIM = 0xFF7F7896;
    public static final int VIOLET = 0xFF8B5CF6;
    public static final int BLUE = 0xFF3B82F6;
    public static final int OK = 0xFF22C55E;
    public static final int ERR = 0xFFEF4444;

    public static int accent() {
        try { return ClientSettings.INSTANCE.accent.get() | 0xFF000000; } catch (Throwable t) { return VIOLET; }
    }
    /** Zweite Verlaufsfarbe zum Akzent (Richtung Blau). */
    public static int accent2() { return Render2D.mix(accent(), BLUE, 0.6f); }
}
