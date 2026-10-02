package com.vortex.client.gui.glatt;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/**
 * Kleine Bilder, die zur Laufzeit in GENAU der Pixelgroesse erzeugt werden,
 * in der sie auf dem Bildschirm landen (seit Client 4.17).
 *
 * Warum: Minecraft zeichnet die Oberflaeche in "GUI-Pixeln" -- bei GUI-Groesse
 * 3 ist jeder davon 3x3 echte Pixel gross. Runde Ecken aus Rechtecken werden
 * dadurch zu Treppen, Symbole aus Punkten zu Kloetzchen. Diese Masken werden
 * stattdessen fuer die echten Bildschirmpixel berechnet, mit weichen
 * (kantengeglaetteten) Raendern, und 1:1 gezeichnet.
 *
 * Jede Maske ist weiss; die Farbe kommt beim Zeichnen als Toenung dazu. Der
 * Speicher bleibt klein: hoechstens {@link #MAX} Masken, die am laengsten
 * nicht benutzten werden freigegeben.
 */
public final class Masken {

    private Masken() {}

    /** Erzeugt die Deckkraft (0..1) fuer den Pixel (x, y). */
    @FunctionalInterface
    public interface Deckung {
        float bei(int x, int y);
    }

    public static final class Maske {
        public final Identifier id;
        public final int w, h;
        final DynamicTexture tex;

        Maske(Identifier id, int w, int h, DynamicTexture tex) {
            this.id = id;
            this.w = w;
            this.h = h;
            this.tex = tex;
        }

        boolean gueltig() {
            try {
                return tex.getTexture() != null && !tex.getTexture().isClosed();
            } catch (Throwable e) {
                return false;
            }
        }
    }

    /** Textur mit waehlbarer Filterung (weich fuer gedrehte/skalierte Bilder). */
    private static final class Bild extends DynamicTexture {
        Bild(Supplier<String> name, NativeImage img, boolean weich) {
            super(name, img);
            if (weich) {
                try {
                    this.sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private static final int MAX = 400;
    private static int zaehler = 0;

    private static final LinkedHashMap<String, Maske> CACHE = new LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Maske> e) {
            if (size() <= MAX) return false;
            freigeben(e.getValue());
            return true;
        }
    };

    /** Weisse Maske mit berechneter Deckkraft. */
    public static Maske hole(String schluessel, int w, int h, boolean weich, Deckung d) {
        return holeBild(schluessel, w, h, weich, img -> {
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    float a = d.bei(x, y);
                    int al = a <= 0f ? 0 : (a >= 1f ? 255 : Math.round(a * 255f));
                    img.setPixel(x, y, (al << 24) | 0xFFFFFF);
                }
            }
        });
    }

    /** Beliebiges Bild (z. B. das verkleinerte Logo). */
    public static Maske holeBild(String schluessel, int w, int h, boolean weich, java.util.function.Consumer<NativeImage> maler) {
        Maske m = CACHE.get(schluessel);
        if (m != null && m.gueltig()) return m;
        if (m != null) CACHE.remove(schluessel);
        if (w <= 0 || h <= 0) return null;
        NativeImage img = new NativeImage(w, h, false);
        try {
            maler.accept(img);
        } catch (RuntimeException e) {
            img.close();
            throw e;
        }
        Identifier id = Identifier.fromNamespaceAndPath("vortexclient", "glatt/m" + (zaehler++));
        Bild t = new Bild(() -> "vortex-glatt " + schluessel, img, weich);
        Minecraft.getInstance().getTextureManager().register(id, t);
        m = new Maske(id, w, h, t);
        CACHE.put(schluessel, m);
        return m;
    }

    private static void freigeben(Maske m) {
        try {
            Minecraft.getInstance().getTextureManager().release(m.id);
        } catch (Throwable ignored) {
        }
    }

    // ------------------------------------------------------------------
    // Formen. Alle als "Neuner-Raster": eine Textur 2Q x 2Q enthaelt die vier
    // Ecken; Zeile/Spalte Q-1 ist schon reiner Rand und wird fuer die Kanten
    // gestreckt.
    // ------------------------------------------------------------------

    /**
     * Abstand zum Rand eines abgerundeten Rechtecks, nur obere linke Ecke
     * betrachtet (negativ = innen). x, y relativ zur Ecke des Rechtecks.
     */
    static float eckAbstand(float x, float y, float r) {
        float qx = r - x, qy = r - y;
        if (qx > 0 && qy > 0) return (float) Math.sqrt(qx * qx + qy * qy) - r;
        return Math.max(qx, qy) - r;
    }

    private static float spiegel(int p, int q) {
        return p < q ? p + 0.5f : 2 * q - (p + 0.5f);
    }

    static float klemme(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }

    /** Gefuellte Ecke, Radius r Pixel. Q = r + 1. */
    public static Maske fuellung(int r) {
        int q = r + 1;
        return hole("f" + r, 2 * q, 2 * q, false, (x, y) ->
                klemme(0.5f - eckAbstand(spiegel(x, q), spiegel(y, q), r)));
    }

    /** Rahmen der Dicke t Pixel, Radius r. Q = r + 1 (mind. t + 1). */
    public static Maske rahmen(int r, int t) {
        int q = Math.max(r, t) + 1;
        return hole("r" + r + "_" + t, 2 * q, 2 * q, false, (x, y) -> {
            float d = eckAbstand(spiegel(x, q), spiegel(y, q), r);
            return klemme(0.5f - d) - klemme(0.5f - (d + t));
        });
    }

    /** Weicher Schatten: Radius r, Weichheit e Pixel nach aussen und innen. Q = r + 2e. */
    public static Maske schatten(int r, int e) {
        int q = r + 2 * e;
        return hole("s" + r + "_" + e, 2 * q, 2 * q, false, (x, y) -> {
            float d = eckAbstand(spiegel(x, q) - e, spiegel(y, q) - e, r);
            float t = klemme((d + e) / (2f * e));
            float s = t * t * (3f - 2f * t);
            float a = 1f - s;
            return a * a;
        });
    }

    /** Q zur jeweiligen Maske (siehe oben). */
    public static int qFuellung(int r) { return r + 1; }
    public static int qRahmen(int r, int t) { return Math.max(r, t) + 1; }
    public static int qSchatten(int r, int e) { return r + 2 * e; }

    /** Weicher runder Lichtfleck (Mitte voll, aussen 0), weich gefiltert zum Strecken. */
    public static Maske licht() {
        int n = 128;
        return hole("licht", n, n, true, (x, y) -> {
            float dx = (x + 0.5f) / n * 2f - 1f, dy = (y + 0.5f) / n * 2f - 1f;
            float d = (float) Math.sqrt(dx * dx + dy * dy);
            float t = klemme(1f - d);
            return t * t * (3f - 2f * t) * t;
        });
    }

    /** Gefuellter Kreis mit d Pixeln Durchmesser. */
    public static Maske kreis(int d) {
        float r = d / 2f;
        return hole("k" + d, d, d, false, (x, y) -> {
            float dx = x + 0.5f - r, dy = y + 0.5f - r;
            return klemme(r - (float) Math.sqrt(dx * dx + dy * dy) + 0.5f);
        });
    }
}
