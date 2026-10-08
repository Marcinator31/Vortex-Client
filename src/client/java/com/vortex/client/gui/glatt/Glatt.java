package com.vortex.client.gui.glatt;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2fStack;

/**
 * Glattes Zeichnen fuer die Client-Oberflaeche (seit Client 4.17).
 *
 * Alle Koordinaten sind wie gewohnt in GUI-Einheiten, duerfen aber Bruchteile
 * haben. Gezeichnet wird in ECHTEN Bildschirmpixeln: abgerundete Flaechen,
 * Rahmen, Schatten, Kreise und Symbole kommen aus {@link Masken} bzw.
 * {@link Symbole} und haben weiche Kanten. Text nutzt die mitgelieferte
 * Schrift "Inter", fuer jede GUI-Groesse passend aufgeloest (kein
 * Pixel-Font).
 */
public final class Glatt {

    private Glatt() {}

    // ------------------------------------------------------------------
    // Massstab
    // ------------------------------------------------------------------

    /** Aktuelle GUI-Groesse (echte Pixel je GUI-Einheit), 1..8. */
    public static int skala() {
        try {
            int s = Minecraft.getInstance().getWindow().getGuiScale();
            return Math.max(1, Math.min(8, s));
        } catch (Throwable e) {
            return 2;
        }
    }

    private static int px(float v, int s) {
        return Math.round(v * s);
    }

    /** Ab hier zaehlen Koordinaten in echten Pixeln. Immer mit {@link #ende} schliessen. */
    private static Matrix3x2fStack pixel(GuiGraphicsExtractor ctx, int s) {
        Matrix3x2fStack p = ctx.pose();
        p.pushMatrix();
        p.scale(1f / s, 1f / s);
        return p;
    }

    private static void ende(Matrix3x2fStack p) {
        p.popMatrix();
    }

    // ------------------------------------------------------------------
    // Formen
    // ------------------------------------------------------------------

    /** Gefuellte Flaeche mit runden Ecken (Radius r in GUI-Einheiten). */
    public static void rund(GuiGraphicsExtractor ctx, float x, float y, float w, float h, float r, int farbe) {
        if ((farbe >>> 24) == 0 || w <= 0 || h <= 0) return;
        int s = skala();
        int x0 = px(x, s), y0 = px(y, s), x1 = px(x + w, s), y1 = px(y + h, s);
        int rp = Math.max(0, Math.min(px(r, s), Math.min(x1 - x0, y1 - y0) / 2));
        Matrix3x2fStack p = pixel(ctx, s);
        try {
            if (rp == 0) {
                ctx.fill(x0, y0, x1, y1, farbe);
            } else {
                Masken.Maske m = Masken.fuellung(rp);
                neun(ctx, m, Masken.qFuellung(rp), x0, y0, x1, y1, farbe, true);
            }
        } finally {
            ende(p);
        }
    }

    /** Rahmen mit runden Ecken, Dicke in ECHTEN Pixeln (1 = haarfein). */
    public static void rahmen(GuiGraphicsExtractor ctx, float x, float y, float w, float h, float r, int dickePx, int farbe) {
        if ((farbe >>> 24) == 0 || w <= 0 || h <= 0) return;
        int s = skala();
        int x0 = px(x, s), y0 = px(y, s), x1 = px(x + w, s), y1 = px(y + h, s);
        int rp = Math.max(0, Math.min(px(r, s), Math.min(x1 - x0, y1 - y0) / 2));
        int t = Math.max(1, dickePx);
        Matrix3x2fStack p = pixel(ctx, s);
        try {
            Masken.Maske m = Masken.rahmen(rp, t);
            neun(ctx, m, Masken.qRahmen(rp, t), x0, y0, x1, y1, farbe, false);
        } finally {
            ende(p);
        }
    }

    /** Weicher Schatten um ein Rechteck; weich = Ausbreitung in GUI-Einheiten. */
    public static void schatten(GuiGraphicsExtractor ctx, float x, float y, float w, float h, float r, float weich, int farbe) {
        if ((farbe >>> 24) == 0) return;
        int s = skala();
        int e = Math.max(1, px(weich, s));
        int x0 = px(x, s) - e, y0 = px(y, s) - e, x1 = px(x + w, s) + e, y1 = px(y + h, s) + e;
        int rp = Math.max(0, Math.min(px(r, s), Math.min(x1 - x0 - 4 * e, y1 - y0 - 4 * e) / 2));
        Matrix3x2fStack p = pixel(ctx, s);
        try {
            Masken.Maske m = Masken.schatten(rp, e);
            neun(ctx, m, Masken.qSchatten(rp, e), x0, y0, x1, y1, farbe, true);
        } finally {
            ende(p);
        }
    }

    /** Gefuellter Kreis, Mittelpunkt (cx, cy), Durchmesser d. */
    public static void kreis(GuiGraphicsExtractor ctx, float cx, float cy, float d, int farbe) {
        if ((farbe >>> 24) == 0) return;
        int s = skala();
        int dp = Math.max(1, px(d, s));
        int x0 = Math.round(cx * s - dp / 2f), y0 = Math.round(cy * s - dp / 2f);
        Matrix3x2fStack p = pixel(ctx, s);
        try {
            Masken.Maske m = Masken.kreis(dp);
            ctx.blit(RenderPipelines.GUI_TEXTURED, m.id, x0, y0, 0f, 0f, dp, dp, dp, dp, farbe);
        } finally {
            ende(p);
        }
    }

    /** Symbol in der Groesse g x g (GUI-Einheiten), obere linke Ecke (x, y). */
    public static void symbol(GuiGraphicsExtractor ctx, Symbole.Symbol sym, float x, float y, float g, int farbe) {
        if ((farbe >>> 24) == 0) return;
        int s = skala();
        int n = Math.max(4, px(g, s));
        int x0 = px(x, s), y0 = px(y, s);
        Matrix3x2fStack p = pixel(ctx, s);
        try {
            Masken.Maske m = Symbole.maske(sym, n);
            ctx.blit(RenderPipelines.GUI_TEXTURED, m.id, x0, y0, 0f, 0f, n, n, n, n, farbe);
        } finally {
            ende(p);
        }
    }

    /** Weicher Lichtfleck (Glow) um (cx, cy) mit Radius r. */
    public static void licht(GuiGraphicsExtractor ctx, float cx, float cy, float r, int farbe) {
        if ((farbe >>> 24) == 0) return;
        int s = skala();
        int d = Math.max(2, px(r * 2, s));
        int x0 = Math.round(cx * s - d / 2f), y0 = Math.round(cy * s - d / 2f);
        Matrix3x2fStack p = pixel(ctx, s);
        try {
            Masken.Maske m = Masken.licht();
            ctx.blit(RenderPipelines.GUI_TEXTURED, m.id, x0, y0, 0f, 0f, d, d, m.w, m.h, m.w, m.h, farbe);
        } finally {
            ende(p);
        }
    }

    /** Haarfeine waagerechte Linie (1 echter Pixel). */
    public static void linieW(GuiGraphicsExtractor ctx, float x0, float x1, float y, int farbe) {
        int s = skala();
        Matrix3x2fStack p = pixel(ctx, s);
        try {
            int yy = px(y, s);
            ctx.fill(px(x0, s), yy, px(x1, s), yy + 1, farbe);
        } finally {
            ende(p);
        }
    }

    /** Rechteck, auf echte Pixel gerundet (fuer Balken usw.). */
    public static void flaeche(GuiGraphicsExtractor ctx, float x0, float y0, float x1, float y1, int farbe) {
        if ((farbe >>> 24) == 0) return;
        int s = skala();
        Matrix3x2fStack p = pixel(ctx, s);
        try {
            ctx.fill(px(x0, s), px(y0, s), px(x1, s), px(y1, s), farbe);
        } finally {
            ende(p);
        }
    }

    /**
     * Neuner-Raster: vier Ecken aus der Maske, Kanten aus der gestreckten
     * Randzeile/-spalte, Mitte als Flaeche.
     */
    private static void neun(GuiGraphicsExtractor ctx, Masken.Maske m, int q, int x0, int y0, int x1, int y1, int farbe, boolean mitte) {
        if (m == null) return;
        int w = x1 - x0, h = y1 - y0;
        if (w <= 0 || h <= 0) return;
        int qx = Math.min(q, w / 2), qy = Math.min(q, h / 2);
        int t = 2 * q;
        var id = m.id;
        var pl = RenderPipelines.GUI_TEXTURED;
        if (qx > 0 && qy > 0) {
            ctx.blit(pl, id, x0, y0, 0f, 0f, qx, qy, t, t, farbe);
            ctx.blit(pl, id, x1 - qx, y0, t - qx, 0f, qx, qy, t, t, farbe);
            ctx.blit(pl, id, x0, y1 - qy, 0f, t - qy, qx, qy, t, t, farbe);
            ctx.blit(pl, id, x1 - qx, y1 - qy, t - qx, t - qy, qx, qy, t, t, farbe);
        }
        int mw = w - 2 * qx, mh = h - 2 * qy;
        if (mw > 0 && qy > 0) {
            ctx.blit(pl, id, x0 + qx, y0, q - 1, 0f, mw, qy, 1, qy, t, t, farbe);
            ctx.blit(pl, id, x0 + qx, y1 - qy, q - 1, t - qy, mw, qy, 1, qy, t, t, farbe);
        }
        if (mh > 0 && qx > 0) {
            ctx.blit(pl, id, x0, y0 + qy, 0f, q - 1, qx, mh, qx, 1, t, t, farbe);
            ctx.blit(pl, id, x1 - qx, y0 + qy, t - qx, q - 1, qx, mh, qx, 1, t, t, farbe);
        }
        if (mitte && mw > 0 && mh > 0) {
            ctx.fill(x0 + qx, y0 + qy, x1 - qx, y1 - qy, farbe);
        }
    }

    // ------------------------------------------------------------------
    // Schrift
    // ------------------------------------------------------------------

    public enum Schrift {
        /** Fliesstext, Inter Medium 9. */
        NORMAL("ui"),
        /** Hervorgehoben, Inter SemiBold 9. */
        FETT("fett"),
        /** Ueberschrift, Inter Bold 13 (Grundlinie 3 Einheiten tiefer). */
        TITEL("titel"),
        /** Schriftzug, Inter Bold 22 (Grundlinie bei 17). */
        GROSS("gross");

        final String name;
        private final FontDescription[] fonts = new FontDescription[9];

        Schrift(String name) {
            this.name = name;
        }

        public FontDescription font(int s) {
            FontDescription f = fonts[s];
            if (f == null) {
                f = new FontDescription.Resource(Identifier.fromNamespaceAndPath("vortexclient", name + "_" + s));
                fonts[s] = f;
            }
            return f;
        }
    }

    /** Text als Komponente in der glatten Schrift (fuer eigene Breitenmessung usw.). */
    public static MutableComponent komp(String text, Schrift schrift) {
        FontDescription f = schrift.font(skala());
        return Component.literal(text).withStyle(st -> st.withFont(f));
    }

    /**
     * Gemerkte Breiten und Kuerzungen: Menues zeichnen jedes Bild dieselben
     * Texte -- Vermessen (und erst recht Kuerzen, Zeichen fuer Zeichen) nur
     * beim ersten Mal. Schluessel enthaelt die GUI-Groesse (andere Schrift).
     */
    private static final java.util.Map<String, Integer> BREITEN = new java.util.LinkedHashMap<>(256, 0.75f, true) {
        @Override protected boolean removeEldestEntry(java.util.Map.Entry<String, Integer> e) { return size() > 2048; }
    };
    private static final java.util.Map<String, String> KURZ = new java.util.LinkedHashMap<>(256, 0.75f, true) {
        @Override protected boolean removeEldestEntry(java.util.Map.Entry<String, String> e) { return size() > 1024; }
    };

    public static int breite(String text, Schrift schrift) {
        if (text == null) return 0;
        String k = schrift.ordinal() + "\u0000" + skala() + "\u0000" + text;
        Integer w = BREITEN.get(k);
        if (w == null) {
            w = Minecraft.getInstance().font.width(komp(text, schrift));
            BREITEN.put(k, w);
        }
        return w;
    }

    /** Text an (x, y); y ist die Oberkante wie bei Minecraft. */
    public static void text(GuiGraphicsExtractor ctx, String text, float x, float y, int farbe, Schrift schrift) {
        if ((farbe >>> 24) < 5 || text == null || text.isEmpty()) return;
        int s = skala();
        Matrix3x2fStack p = ctx.pose();
        p.pushMatrix();
        try {
            p.translate(px(x, s) / (float) s, px(y, s) / (float) s);
            ctx.text(Minecraft.getInstance().font, komp(text, schrift), 0, 0, farbe, false);
        } finally {
            p.popMatrix();
        }
    }

    /** Text rechtsbuendig an x. */
    public static void textRechts(GuiGraphicsExtractor ctx, String text, float rechts, float y, int farbe, Schrift schrift) {
        text(ctx, text, rechts - breite(text, schrift), y, farbe, schrift);
    }

    /** Text mittig um cx. */
    public static void textMitte(GuiGraphicsExtractor ctx, String text, float cx, float y, int farbe, Schrift schrift) {
        text(ctx, text, cx - breite(text, schrift) / 2f, y, farbe, schrift);
    }

    /** Kuerzt mit "..." auf hoechstens max Einheiten. */
    public static String kuerzen(String s, int max, Schrift schrift) {
        if (s == null) return "";
        if (max <= 8) return "";
        if (breite(s, schrift) <= max) return s;
        String k = schrift.ordinal() + "\u0000" + skala() + "\u0000" + max + "\u0000" + s;
        String fertig = KURZ.get(k);
        if (fertig == null) {
            fertig = kuerzenNeu(s, max, schrift);
            KURZ.put(k, fertig);
        }
        return fertig;
    }

    private static String kuerzenNeu(String s, int max, Schrift schrift) {
        String c = s;
        var font = Minecraft.getInstance().font;
        while (c.length() > 1 && font.width(komp(c + "…", schrift)) > max) c = c.substring(0, c.length() - 1);
        return c.stripTrailing() + "…";
    }

    // ------------------------------------------------------------------
    // Logo
    // ------------------------------------------------------------------

    // Logo A "Faltung" (4.28): zwei Klingen, die beim Oeffnen zusammenklappen.
    private static final Identifier LOGO_L = Identifier.fromNamespaceAndPath("vortexclient", "textures/gui/sprites/logo_a_l.png");
    private static final Identifier LOGO_R = Identifier.fromNamespaceAndPath("vortexclient", "textures/gui/sprites/logo_a_r.png");
    private static boolean logoKaputt = false;

    /**
     * Das Vortex-Logo, in echter Pixelgroesse sauber verkleinert (statt aus
     * dem 512er Bild Punkte herauszugreifen). Beim Oeffnen kommen die beiden
     * Klingen von links oben und rechts oben und falten sich zum V.
     */
    public static void logo(GuiGraphicsExtractor ctx, float x, float y, float g, float alpha, float oeffnen) {
        if (alpha <= 0.01f) return;
        if (logoKaputt) {
            com.vortex.client.gui.LogoRenderer.zeichne(ctx, Math.round(x), Math.round(y), Math.round(g), alpha, oeffnen);
            return;
        }
        int s = skala();
        int n = Math.max(8, px(g, s));
        try {
            Masken.Maske l = logoTeil(LOGO_L, n);
            Masken.Maske r = logoTeil(LOGO_R, n);
            if (l == null || r == null) throw new IllegalStateException("logo");
            float e = 1f - (float) Math.pow(1f - Masken.klemme(oeffnen), 3);
            int farbe = (Math.round(Masken.klemme(alpha) * 255f) << 24) | 0xFFFFFF;
            float weg = (1f - e) * n * 0.16f;
            Matrix3x2fStack p = pixel(ctx, s);
            try {
                p.translate(x * s + n / 2f, y * s + n / 2f);
                float k = 0.86f + 0.14f * e;
                p.scale(k, k);
                p.pushMatrix();
                p.translate(-weg, -weg * 0.7f);
                ctx.blit(RenderPipelines.GUI_TEXTURED, l.id, -n / 2, -n / 2, 0f, 0f, n, n, n, n, farbe);
                p.popMatrix();
                p.pushMatrix();
                p.translate(weg, -weg * 0.7f);
                ctx.blit(RenderPipelines.GUI_TEXTURED, r.id, -n / 2, -n / 2, 0f, 0f, n, n, n, n, farbe);
                p.popMatrix();
            } finally {
                ende(p);
            }
        } catch (Throwable t) {
            logoKaputt = true;
            com.vortex.client.core.Errors.report("Glatt.logo", t);
        }
    }

    private static Masken.Maske logoTeil(Identifier quelle, int n) {
        return Masken.holeBild("logo_" + quelle.getPath() + "_" + n, n, n, true, ziel -> {
            try (var in = Minecraft.getInstance().getResourceManager().open(quelle);
                 NativeImage src = NativeImage.read(in)) {
                verkleinern(src, ziel, n);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    /** Flaechenmittel mit vormultipliziertem Alpha -- keine Treppen, keine dunklen Raender. */
    private static void verkleinern(NativeImage src, NativeImage ziel, int n) {
        int sw = src.getWidth(), sh = src.getHeight();
        for (int y = 0; y < n; y++) {
            float sy0 = y * (float) sh / n, sy1 = (y + 1) * (float) sh / n;
            for (int x = 0; x < n; x++) {
                float sx0 = x * (float) sw / n, sx1 = (x + 1) * (float) sw / n;
                double a = 0, r = 0, g = 0, b = 0, ges = 0;
                for (int yy = (int) sy0; yy < Math.min(sh, (int) Math.ceil(sy1)); yy++) {
                    float wy = Math.min(sy1, yy + 1) - Math.max(sy0, yy);
                    for (int xx = (int) sx0; xx < Math.min(sw, (int) Math.ceil(sx1)); xx++) {
                        float wx = Math.min(sx1, xx + 1) - Math.max(sx0, xx);
                        float wgt = wx * wy;
                        int c = src.getPixel(xx, yy);
                        double ca = ((c >>> 24) & 0xFF) / 255.0;
                        a += ca * wgt;
                        r += ((c >> 16) & 0xFF) * ca * wgt;
                        g += ((c >> 8) & 0xFF) * ca * wgt;
                        b += (c & 0xFF) * ca * wgt;
                        ges += wgt;
                    }
                }
                int oa = 0, or = 0, og = 0, ob = 0;
                if (a > 1e-6) {
                    oa = (int) Math.round(Math.min(1.0, a / ges) * 255);
                    or = (int) Math.min(255, Math.round(r / a));
                    og = (int) Math.min(255, Math.round(g / a));
                    ob = (int) Math.min(255, Math.round(b / a));
                }
                ziel.setPixel(x, y, (oa << 24) | (or << 16) | (og << 8) | ob);
            }
        }
    }

    // ------------------------------------------------------------------
    // Farben
    // ------------------------------------------------------------------

    public static int alpha(int argb, float f) {
        int a = Math.round(((argb >>> 24) & 0xFF) * Masken.klemme(f));
        return (a << 24) | (argb & 0xFFFFFF);
    }

    public static int mix(int a, int b, float t) {
        t = Masken.klemme(t);
        int aa = (a >>> 24) & 0xFF, ba = (b >>> 24) & 0xFF;
        int ar = (a >> 16) & 0xFF, br = (b >> 16) & 0xFF;
        int ag = (a >> 8) & 0xFF, bg = (b >> 8) & 0xFF;
        int ab = a & 0xFF, bb = b & 0xFF;
        return (Math.round(aa + (ba - aa) * t) << 24) | (Math.round(ar + (br - ar) * t) << 16)
                | (Math.round(ag + (bg - ag) * t) << 8) | Math.round(ab + (bb - ab) * t);
    }
}
