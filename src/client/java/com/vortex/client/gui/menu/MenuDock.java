package com.vortex.client.gui.menu;

import com.vortex.client.gui.Branding;
import com.vortex.client.gui.Theme;
import com.vortex.client.gui.VortexStyle;
import com.vortex.client.gui.glatt.Glatt;
import com.vortex.client.gui.glatt.Glatt.Schrift;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Gemeinsame Glasflaeche hinter den Knoepfen der Vortex-Leiste (seit 4.30.2).
 *
 * Vorher standen die Knoepfe als einzelne Kacheln am Rand; jetzt liegen sie
 * in einem Dock mit Kopfzeile (Logo + "VORTEX"). Gezeichnet wird das Dock
 * vom ersten Knopf der Leiste -- so braucht es keinen eigenen Mixin und
 * erscheint im Haupt- und im Pausenmenue gleich.
 */
public final class MenuDock {

    /** Innenabstand und Hoehe der Kopfzeile (GUI-Einheiten). */
    public static final int PAD = 5, KOPF = 18, KOPF_KOMPAKT = 20;

    final int x, y, w, h;
    final boolean kompakt;

    MenuDock(int x, int y, int w, int h, boolean kompakt) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        this.kompakt = kompakt;
    }

    static int akzent() {
        try {
            return Theme.INSTANCE.accent.get() | 0xFF000000;
        } catch (Throwable e) {
            return VortexStyle.VIOLETT;
        }
    }

    /** Flaeche, Rahmen und Kopfzeile; alpha = Einblenden des Menues. */
    void zeichnen(GuiGraphicsExtractor g, float alpha) {
        if (alpha <= 0.01f) return;
        int akz = akzent();
        float r = kompakt ? 10f : 11f;
        Glatt.schatten(g, x, y + 2, w, h, r, 10, Glatt.alpha(0xFF000000, 0.45f * alpha));
        Glatt.rund(g, x, y, w, h, r, Glatt.alpha(0xD00B0912, alpha));
        // Leichter Akzentschein oben, wie Licht, das ins Glas faellt
        Glatt.licht(g, x + w / 2f, y, w * 0.7f, Glatt.alpha(Glatt.mix(akz, VortexStyle.VIOLETT, 0.5f), 0.16f * alpha));
        Glatt.rahmen(g, x, y, w, h, r, 1, Glatt.alpha(0x26FFFFFF, alpha));
        // Glanzkante oben innen
        Glatt.linieW(g, x + r, x + w - r, y + 1, Glatt.alpha(0x14FFFFFF, alpha));

        if (kompakt) {
            float lg = 12;
            Glatt.logo(g, x + (w - lg) / 2f, y + (KOPF_KOMPAKT - lg) / 2f + 1, lg, alpha, 1f);
            Glatt.linieW(g, x + 6, x + w - 6, y + KOPF_KOMPAKT, Glatt.alpha(0x1CFFFFFF, alpha));
            return;
        }
        float lg = 11;
        float ky = y + PAD + (KOPF - lg) / 2f - 1;
        Glatt.logo(g, x + PAD + 4, ky, lg, alpha, 1f);
        String titel = titel();
        float tx = x + PAD + 4 + lg + 5;
        Glatt.text(g, titel, tx, ky + (lg - 9) / 2f + 0.5f, Glatt.alpha(0xFFF2F0F8, alpha), Schrift.FETT);
        // Kleiner Punkt in der Akzentfarbe rechts -- "verbunden"
        Glatt.kreis(g, x + w - PAD - 7, ky + lg / 2f, 4f, Glatt.alpha(Glatt.mix(akz, 0xFFFFFFFF, 0.25f), alpha));
        Glatt.licht(g, x + w - PAD - 7, ky + lg / 2f, 6f, Glatt.alpha(akz, 0.5f * alpha));
        Glatt.linieW(g, x + PAD + 2, x + w - PAD - 2, y + PAD + KOPF, Glatt.alpha(0x1CFFFFFF, alpha));
    }

    private static String titel() {
        try {
            return Branding.title();
        } catch (Throwable e) {
            return "VORTEX";
        }
    }
}
