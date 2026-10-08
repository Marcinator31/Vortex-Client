package com.vortex.client.cosmetics;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Animierte Vortex-Capes -- jedes Bild wird LAUFEND berechnet (kein Bildstreifen
 * im Jar): Nebel, Lava, Blueten, Blitze ... bewegen sich frei, nichts wiederholt
 * sich sichtbar.
 *
 * Ergebnis sind zwei Texturen im Vanilla-Cape-Format (64x32, hier 8-fach = 512x256):
 *   - BASIS: normal beleuchtet (wie jedes Cape)
 *   - GLUEHEN: leuchtet selbst (RenderTypes.eyes, also auch nachts) -- Funken,
 *     Lava, Sterne, der wandernde Glanz und die Glitzer-Sterne.
 *
 * Bewusst ohne Minecraft-Klassen: so laesst sich jedes Cape auch ausserhalb des
 * Spiels als Bild rendern (CapeVorschau im Test).
 *
 * Koordinaten fuer die Motive: x 0 .. 0.625 (links -> rechts, von hinten
 * gesehen), y 0 .. 1 (oben -> unten). Das Cape ist 10 x 16 Pixel, daher 0.625.
 */
public final class CapeKunst {
    private CapeKunst() {}

    public static final int S = 12, W = 64 * S, H = 32 * S;
    static final float BREITE = 0.625f;

    /** Ein Bildpunkt: Grundfarbe (beleuchtet) und Leuchten (Farbe + Deckkraft). */
    public static final class Px {
        float r, g, b;          // Basis 0..1
        float lr, lg, lb, la;   // Leuchten
        void farbe(int rgb) { r = ((rgb >> 16) & 255) / 255f; g = ((rgb >> 8) & 255) / 255f; b = (rgb & 255) / 255f; }
        void farbe(float r, float g, float b) { this.r = r; this.g = g; this.b = b; }
        /** Basis Richtung Farbe mischen (k 0..1). */
        void mische(int rgb, float k) {
            if (k <= 0) return;
            if (k > 1) k = 1;
            r += (((rgb >> 16) & 255) / 255f - r) * k;
            g += (((rgb >> 8) & 255) / 255f - g) * k;
            b += ((rgb & 255) / 255f - b) * k;
        }
        void hell(float f) { r *= f; g *= f; b *= f; }
        /** Leuchten dazu (Farbe, Staerke 0..1); mehrere Lichter addieren sich weich. */
        void leuchte(int rgb, float a) {
            if (a <= 0.004f) return;
            if (a > 1) a = 1;
            float cr = ((rgb >> 16) & 255) / 255f, cg = ((rgb >> 8) & 255) / 255f, cb = (rgb & 255) / 255f;
            float neu = 1 - (1 - la) * (1 - a);
            float w = a / Math.max(1e-4f, la + a);
            lr += (cr - lr) * w; lg += (cg - lg) * w; lb += (cb - lb) * w;
            la = neu;
        }
        void leer() { r = g = b = 0; lr = lg = lb = la = 0; }
    }

    public interface Maler { void male(float x, float y, float t, Px p); }

    /**
     * Ein Cape. trim = Farbe der Borte (Kanten), glanz = Staerke des wandernden
     * Lichtstreifens, funkeln = Anzahl Glitzer-Sterne.
     */
    public record Design(String id, String name, String text, int trim, int akzent, float glanz, float funkeln, Maler maler) {}

    private static final Map<String, Design> ALLE = new LinkedHashMap<>();

    public static Map<String, Design> alle() { return ALLE; }
    public static Design design(String id) { return id == null ? null : ALLE.get(id); }

    private static void neu(String id, String name, String text, int trim, int akzent, float glanz, float funkeln, Maler m) {
        ALLE.put(id, new Design(id, name, text, trim, akzent, glanz, funkeln, m));
    }

    // =====================================================================
    // Rendern
    // =====================================================================

    /**
     * Ein Bild zur Zeit t (Sekunden) in die beiden Puffer (W*H, ABGR wie
     * NativeImage im Speicher). Gefuellt werden nur die genutzten Bereiche:
     * Aussen, Innen, Kanten, Elytra.
     */
    public static void rendern(Design d, float t, int[] basis, int[] glow) {
        Px p = new Px();
        // Aussenseite (x 1..11, y 1..17) -- volle Aufloesung
        int ax = S, ay = S, aw = 10 * S, ah = 16 * S;
        for (int j = 0; j < ah; j++) {
            float y = (j + 0.5f) / ah;
            int zeile = (ay + j) * W;
            for (int i = 0; i < aw; i++) {
                float x = (i + 0.5f) / aw * BREITE;
                punkt(d, x, y, t, p);
                basis[zeile + ax + i] = abgr(p.r, p.g, p.b, 1f);
                glow[zeile + ax + i] = abgr(p.lr, p.lg, p.lb, p.la);
            }
        }
        // Innenseite (x 12..22): gespiegelt, dunkles Futter mit einem Hauch Farbe
        float tr = ((d.trim >> 16) & 255) / 255f, tg = ((d.trim >> 8) & 255) / 255f, tb = (d.trim & 255) / 255f;
        for (int j = 0; j < ah; j++) {
            int zeile = (ay + j) * W;
            for (int i = 0; i < aw; i++) {
                int q = basis[zeile + ax + (aw - 1 - i)];
                float r = (q & 255) / 255f, g = ((q >> 8) & 255) / 255f, b = ((q >> 16) & 255) / 255f;
                basis[zeile + 12 * S + i] = abgr(r * 0.32f + tr * 0.06f, g * 0.32f + tg * 0.06f, b * 0.32f + tb * 0.06f, 1f);
                int gq = glow[zeile + ax + (aw - 1 - i)];
                int ga = (gq >>> 24) / 4;
                glow[zeile + 12 * S + i] = (ga << 24) | (gq & 0xFFFFFF);
            }
        }
        // Kanten (Seiten x 0..1 / 11..12, oben, unten): Borte mit Glanz
        for (int j = 0; j < 17 * S; j++) {
            float y = Math.max(0, (j - S + 0.5f) / ah);
            for (int i = 0; i < S; i++) {
                int[] spalten = { i, 11 * S + i };
                for (int sx : spalten) {
                    borte(d, y, t, (i + 0.5f) / S, p);
                    basis[j * W + sx] = abgr(p.r, p.g, p.b, 1f);
                    glow[j * W + sx] = abgr(p.lr, p.lg, p.lb, p.la);
                }
            }
        }
        for (int j = 0; j < S; j++) {
            for (int i = S; i < 21 * S; i++) {
                borte(d, (i % (10 * S)) / (10f * S), t, (j + 0.5f) / S, p);
                basis[j * W + i] = abgr(p.r, p.g, p.b, 1f);
                glow[j * W + i] = abgr(p.lr, p.lg, p.lb, p.la);
            }
        }
        // Elytra (x 22..46, y 0..22): dasselbe Motiv, aus der Aussenseite abgetastet
        int ex = 22 * S, ew = 24 * S, eh = 22 * S;
        for (int j = 0; j < eh; j++) {
            int sy = ay + Math.min(ah - 1, j * ah / eh);
            for (int i = 0; i < ew; i++) {
                int sx = ax + Math.min(aw - 1, (i % (12 * S)) * aw / (12 * S));
                basis[j * W + ex + i] = basis[sy * W + sx];
                glow[j * W + ex + i] = glow[sy * W + sx];
            }
        }
    }

    /** Ein Punkt der Aussenseite: Motiv + Faltenwurf + Borte + Glanz + Glitzern. */
    private static final ThreadLocal<Px> HILFE = ThreadLocal.withInitial(Px::new);

    static void punkt(Design d, float x, float y, float t, Px p) {
        p.leer();
        d.maler.male(x, y, t, p);
        // weicher Faltenwurf und Stoffstruktur: wirkt wie Stoff statt wie ein flaches Bild
        float falte = 0.94f + 0.06f * (float) Math.cos(x * 26f + Math.sin(y * 3.1f + t * 0.6f) * 0.8f);
        float faden = 0.975f + 0.05f * (rausch(x * 260f, y * 30f) - 0.5f);
        // Rand etwas dunkler (Tiefe)
        float rand0 = Math.min(Math.min(x, BREITE - x) / BREITE, Math.min(y, 1 - y));
        float vignette = 0.82f + 0.18f * glatt(0f, 0.22f, rand0);
        p.hell(falte * faden * vignette);
        // Borte am Rand: schmal, mit heller Kante (wie gestickt)
        float rand = Math.min(Math.min(x, BREITE - x), Math.min(y, 1 - y) * 0.9f);
        if (rand < 0.022f) {
            float k = glatt(0.022f, 0.014f, rand);
            Px q = HILFE.get();
            borte(d, y + x, t, rand / 0.022f, q);
            p.r += (q.r - p.r) * k; p.g += (q.g - p.g) * k; p.b += (q.b - p.b) * k;
            p.leuchte(packe(q.lr, q.lg, q.lb), q.la * k);
        }
        // wandernder Glanz: feiner, weicher Lichtstreif in der Akzentfarbe, alle ~7 s
        if (d.glanz > 0) {
            float pos = (t * 0.3f) % 2.1f - 0.5f;
            float s = x * 0.75f + y * 0.6f - pos;
            if (s > -0.25f && s < 0.25f) {
                float band = (float) Math.exp(-s * s / 0.0006f) * 0.8f + 0.25f * (float) Math.exp(-s * s / 0.008f);
                p.leuchte(mischRgb(d.akzent, 0xFFFFFF, 0.7f), band * d.glanz * 0.75f);
            }
        }
        // Glitzer: kleine Sterne mit vier Strahlen, die auf- und abblenden
        if (d.funkeln > 0) {
            float f = glitzer(x, y, t, d.funkeln);
            if (f > 0) p.leuchte(mischRgb(d.akzent, 0xFFFFFF, 0.6f), f);
        }
    }

    /** Borte: Metall-Look in der Trim-Farbe mit eigenem Glanz. u = Lage entlang, q = 0 aussen .. 1 innen. */
    static void borte(Design d, float u, float t, float q, Px p) {
        p.leer();
        p.farbe(d.trim);
        float metall = 0.72f + 0.28f * (float) Math.cos(q * 3.1f);
        float streif = 0.9f + 0.1f * (float) Math.sin(u * 90f);
        p.hell(metall * streif);
        float s = u - ((t * 0.5f) % 2.4f - 0.2f);
        float g = (float) Math.exp(-s * s / 0.004f);
        p.r += g * 0.5f; p.g += g * 0.5f; p.b += g * 0.5f;
        p.leuchte(d.trim, 0.25f + 0.35f * g);
    }

    static float glitzer(float x, float y, float t, float dichte) {
        float n = 5f * dichte;
        float gx = x * n, gy = y * n;
        int cx = fl(gx), cy = fl(gy);
        float h = hash(cx, cy + 131);
        if (h > 0.45f) return 0;
        float zyklus = 2.2f + h * 3f;
        float ph = ((t + h * 17f) % zyklus) / zyklus;
        if (ph > 0.35f) return 0;
        float stk = (float) Math.sin(ph / 0.35f * Math.PI);
        float sx = cx + 0.2f + 0.6f * hash(cx + 7, cy), sy = cy + 0.2f + 0.6f * hash(cx, cy + 19);
        float dx = Math.abs(gx - sx) * 9f / n * 5f, dy = Math.abs(gy - sy) * 9f / n * 5f;
        float kern = (float) Math.exp(-(dx * dx + dy * dy) * 6f);
        float strahl = (float) (Math.exp(-dx * 1.6f) * Math.exp(-dy * dy * 60f) + Math.exp(-dy * 1.6f) * Math.exp(-dx * dx * 60f));
        return Math.min(1f, (kern + strahl * 0.8f) * stk);
    }

    // =====================================================================
    // Werkzeug: Rauschen, Mischen
    // =====================================================================

    static int fl(float v) { int i = (int) v; return v < i ? i - 1 : i; }

    static float hash(int x, int y) {
        int h = x * 374761393 + y * 668265263;
        h = (h ^ (h >>> 13)) * 1274126177;
        h ^= h >>> 16;
        return (h & 0xFFFFFF) / 16777216f;
    }

    static float rausch(float x, float y) {
        int ix = fl(x), iy = fl(y);
        float fx = x - ix, fy = y - iy;
        fx = fx * fx * (3 - 2 * fx);
        fy = fy * fy * (3 - 2 * fy);
        float a = hash(ix, iy), b = hash(ix + 1, iy), c = hash(ix, iy + 1), e = hash(ix + 1, iy + 1);
        return a + (b - a) * fx + (c - a) * fy + (a - b - c + e) * fx * fy;
    }

    static float fbm(float x, float y, int okt) {
        float s = 0, a = 0.5f, n = 0;
        for (int i = 0; i < okt; i++) {
            s += a * rausch(x, y);
            n += a;
            x = x * 2.03f + 17.1f;
            y = y * 2.03f + 9.7f;
            a *= 0.5f;
        }
        return s / n;
    }

    static float glatt(float a, float b, float v) {
        float k = (v - a) / (b - a);
        k = k < 0 ? 0 : k > 1 ? 1 : k;
        return k * k * (3 - 2 * k);
    }

    static float frac(float v) { return v - fl(v); }

    static int packe(float r, float g, float b) {
        return (k255(r) << 16) | (k255(g) << 8) | k255(b);
    }

    static int k255(float v) { return v <= 0 ? 0 : v >= 1 ? 255 : (int) (v * 255f + 0.5f); }

    static int abgr(float r, float g, float b, float a) {
        return (k255(a) << 24) | (k255(b) << 16) | (k255(g) << 8) | k255(r);
    }

    static int mischRgb(int a, int b, float k) {
        int r = (int) (((a >> 16) & 255) + ((((b >> 16) & 255) - ((a >> 16) & 255)) * k));
        int g = (int) (((a >> 8) & 255) + ((((b >> 8) & 255) - ((a >> 8) & 255)) * k));
        int bl = (int) ((a & 255) + (((b & 255) - (a & 255)) * k));
        return (r << 16) | (g << 8) | bl;
    }

    /** Farbverlauf ueber mehrere Stuetzfarben (k 0..1). */
    static int verlauf(float k, int... farben) {
        if (k <= 0) return farben[0];
        if (k >= 1) return farben[farben.length - 1];
        float f = k * (farben.length - 1);
        int i = (int) f;
        return mischRgb(farben[i], farben[i + 1], f - i);
    }

    static int hsv(float h, float s, float v) {
        h = frac(h) * 6f;
        int i = (int) h;
        float f = h - i, p = v * (1 - s), q = v * (1 - s * f), u = v * (1 - s * (1 - f));
        float r, g, b;
        switch (i) {
            case 0 -> { r = v; g = u; b = p; }
            case 1 -> { r = q; g = v; b = p; }
            case 2 -> { r = p; g = v; b = u; }
            case 3 -> { r = p; g = q; b = v; }
            case 4 -> { r = u; g = p; b = v; }
            default -> { r = v; g = p; b = q; }
        }
        return packe(r, g, b);
    }

    /** Funkelnde Sterne in einem Raster (je Zelle hoechstens einer). Liefert Helligkeit 0..1. */
    static float sterne(float x, float y, float dichte, float t, int saat, float anteil) {
        float gx = x * dichte, gy = y * dichte;
        int cx = fl(gx), cy = fl(gy);
        float h = hash(cx + saat * 31, cy - saat * 17);
        if (h > anteil) return 0;
        float sx = cx + 0.25f + 0.5f * hash(cx * 3 + 1, cy + saat), sy = cy + 0.25f + 0.5f * hash(cx + saat, cy * 5 + 7);
        float dx = gx - sx, dy = gy - sy;
        float r = 0.07f + 0.1f * hash(cx + 9, cy + saat * 3);
        float d2 = (dx * dx + dy * dy) / (r * r);
        float fun = 0.55f + 0.45f * (float) Math.sin(t * (1.3f + h * 4f) + h * 40f);
        return (float) Math.exp(-d2) * fun;
    }

    /** Partikel, die in Spalten auf- oder absteigen. Liefert Helligkeit 0..1 (weicher Punkt). */
    static float aufsteigend(float x, float y, float t, int spalten, float tempo, float groesse, int saat, boolean runter) {
        float gx = x / BREITE * spalten;
        int cx = fl(gx);
        float best = 0;
        for (int k = 0; k < 2; k++) {
            float h = hash(cx * 7 + k * 101, saat);
            float v = tempo * (0.6f + 0.8f * hash(cx + 3, k + saat * 5));
            float py = frac(h + t * v);
            if (!runter) py = 1 - py;
            float px = cx + 0.5f + 0.32f * (float) Math.sin(t * (1.1f + h * 2f) + h * 30f + py * 5f);
            float dx = (gx - px) * BREITE / spalten, dy = y - py;
            float d2 = (dx * dx + dy * dy) / (groesse * groesse);
            float fade = glatt(0f, 0.15f, runter ? py : 1 - py) * glatt(1f, 0.8f, runter ? py : 1 - py);
            float flack = 0.7f + 0.3f * (float) Math.sin(t * 9f + h * 50f);
            best = Math.max(best, (float) Math.exp(-d2) * fade * flack);
        }
        return best;
    }

    // =====================================================================
    // Die Capes
    // =====================================================================

    static {
        // --- End Cloak: Nebel aus dem End, Sterne in drei Ebenen, die vorbeiziehen
        neu("anim_end", "End Cloak", "Nebula and drifting stars", 0x8B5CF6, 0x67E8F9, 0.5f, 1.0f, (x, y, t, p) -> {
            p.farbe(verlauf(y, 0x0A0616, 0x140A2C, 0x0B0A22));
            float q = fbm(x * 3f + t * 0.03f, y * 3f - t * 0.05f, 3);
            float n = fbm(x * 2.6f + q * 1.9f, y * 2.4f + q * 1.6f - t * 0.045f, 4);
            float neb = glatt(0.42f, 0.86f, n);
            p.mische(0x6D28D9, neb * 0.85f);
            float n2 = fbm(x * 4.2f - t * 0.025f, y * 3.6f + q * 1.2f, 3);
            p.mische(0x0EA5B7, glatt(0.55f, 0.92f, n2) * 0.55f);
            p.mische(0xF0ABFC, glatt(0.78f, 0.98f, n) * 0.6f);
            p.leuchte(0xA855F7, neb * neb * 0.45f);
            p.leuchte(0x22D3EE, glatt(0.7f, 0.95f, n2) * 0.35f);
            // End-Portal-Ebenen: Sterne ziehen schraeg vorbei
            for (int k = 0; k < 3; k++) {
                float s = sterne(x + t * 0.012f * (k + 1), y + t * 0.02f * (k + 1), 13f + k * 7f, t, k + 1, 0.55f);
                int c = k == 0 ? 0x5EEAD4 : k == 1 ? 0xC4B5FD : 0xFFFFFF;
                p.mische(c, s * 0.8f);
                p.leuchte(c, s);
            }
        });

        // --- Nether Cloak: Gestein mit fliessenden Lava-Adern und aufsteigender Glut
        neu("anim_nether", "Nether Cloak", "Flowing lava and rising embers", 0xF97316, 0xFFD54A, 0.35f, 0.6f, (x, y, t, p) -> {
            float n = fbm(x * 7f, y * 7f, 4);
            p.farbe(verlauf(n, 0x1A0404, 0x3B0909, 0x5C1010, 0x6E1A12));
            float fl1 = fbm(x * 3.6f + 3f, y * 3.2f - t * 0.07f, 4);
            float ader = 1 - Math.abs(2 * fl1 - 1);
            float fl2 = fbm(x * 6.5f + 9f, y * 5.5f - t * 0.11f, 3);
            float ader2 = 1 - Math.abs(2 * fl2 - 1);
            float lava = Math.max(glatt(0.86f, 0.97f, ader), glatt(0.9f, 0.985f, ader2) * 0.8f);
            float puls = 0.82f + 0.18f * (float) Math.sin(t * 2.2f + y * 7f + x * 4f);
            int lc = verlauf(glatt(0.88f, 1f, Math.max(ader, ader2)), 0xC2410C, 0xFF5A1F, 0xFFB020, 0xFFF1A8);
            p.mische(0x2A0606, glatt(0.6f, 0.86f, ader) * 0.5f);   // dunkler Rand an der Ader
            p.mische(lc, lava);
            p.leuchte(lc, lava * puls);
            // Hitzeschimmer von unten
            p.leuchte(0xFF3D00, glatt(0.55f, 1f, y) * 0.18f * (0.7f + 0.3f * fbm(x * 5f, y * 3f - t * 0.4f, 2)));
            float glut = aufsteigend(x, y, t, 7, 0.09f, 0.012f, 3, false);
            p.leuchte(0xFFB347, glut);
            p.mische(0xFFC266, glut * 0.8f);
        });

        // --- Sakura: Kirschzweig voller Blueten, fallende Bluetenblaetter
        neu("anim_sakura", "Sakura", "Cherry blossoms in the wind", 0xF9A8D4, 0xFFFFFF, 0.45f, 0.9f, (x, y, t, p) -> {
            p.farbe(verlauf(y, 0xFFE3EE, 0xFFC4DA, 0xE9A7DC, 0xB48AE6));
            float wolke = fbm(x * 2.2f + t * 0.02f, y * 3f, 3);
            p.mische(0xFFFFFF, glatt(0.55f, 0.85f, wolke) * 0.35f);
            // Sonne hinter dem Zweig
            float sx0 = x - 0.43f, sy0 = y - 0.3f, sr = (float) Math.sqrt(sx0 * sx0 + sy0 * sy0);
            p.mische(0xFFF6F0, glatt(0.075f, 0.07f, sr) * 0.85f);
            p.leuchte(0xFFF1E6, glatt(0.2f, 0.07f, sr) * 0.3f);
            // Berge unten
            float huegel = 0.8f + 0.05f * (float) Math.sin(x * 9f + 1f) + 0.02f * (float) Math.sin(x * 23f);
            if (y > huegel) p.mische(0xA77CD8, glatt(huegel, huegel + 0.006f, y) * 0.8f);
            float huegel2 = 0.88f + 0.035f * (float) Math.sin(x * 7f + 3f);
            if (y > huegel2) p.mische(0x7E52BC, glatt(huegel2, huegel2 + 0.006f, y) * 0.9f);
            // Zweige (wiegen leicht im Wind)
            float wind = 0.008f * (float) Math.sin(t * 0.9f);
            float yb = 0.1f + 0.2f * x + 0.03f * (float) Math.sin(x * 11f) + wind * x * 3f;
            float dz = Math.abs(y - yb);
            float dicke = Math.max(0.006f, 0.03f * (1.1f - x * 1.4f));
            float ast = glatt(dicke, dicke * 0.55f, dz);
            float yb2 = yb + (x - 0.15f) * 0.9f + wind * 4f * Math.max(0, x - 0.15f);
            float dz2 = x > 0.15f && x < 0.42f ? Math.abs(y - yb2) : 1f;
            float d2k = Math.max(0.004f, 0.016f * (1 - (x - 0.15f) * 3.5f));
            ast = Math.max(ast, glatt(d2k, d2k * 0.5f, dz2));
            float yb3 = yb - (x - 0.3f) * 0.5f;
            float dz3 = x > 0.3f && x < 0.5f ? Math.abs(y - yb3) : 1f;
            ast = Math.max(ast, glatt(0.008f, 0.004f, dz3));
            p.mische(verlauf(glatt(-dicke, dicke, y - yb), 0x6B3F3A, 0x3B2020), ast);
            // Blueten mit 5 Blaettern: je Zelle eine, nur nahe am Zweig
            float zg = 0.05f;
            int bx = fl(x / zg), by = fl(y / zg);
            float best = 0; int farbe = 0;
            for (int j = -1; j <= 1; j++) for (int i = -1; i <= 1; i++) {
                int cx = bx + i, cy = by + j;
                float h = hash(cx * 3 + 1, cy * 7 + 5);
                float mx = (cx + 0.2f + 0.6f * hash(cx, cy + 11)) * zg, my = (cy + 0.2f + 0.6f * hash(cx + 13, cy)) * zg;
                float ybm = 0.1f + 0.2f * mx + 0.03f * (float) Math.sin(mx * 11f) + wind * mx * 3f;
                float yb2m = ybm + (mx - 0.15f) * 0.9f + wind * 4f * Math.max(0, mx - 0.15f);
                float yb3m = ybm - (mx - 0.3f) * 0.5f;
                float nah = Math.min(Math.abs(my - ybm), Math.min(mx > 0.15f && mx < 0.42f ? Math.abs(my - yb2m) : 1f, mx > 0.3f && mx < 0.5f ? Math.abs(my - yb3m) : 1f));
                if (nah > 0.05f || h > 0.85f) continue;
                float dx = x - mx, dy = y - my;
                float r = (float) Math.sqrt(dx * dx + dy * dy);
                float gr = zg * (0.42f + 0.25f * h);
                if (r > gr) continue;
                float a = (float) Math.atan2(dy, dx) + h * 6f + 0.15f * (float) Math.sin(t * 1.2f + h * 9f);
                float blatt = gr * (0.62f + 0.38f * (float) Math.pow(Math.abs(Math.cos(a * 2.5f)), 0.5f));
                float k = glatt(blatt, blatt * 0.85f, r);
                if (k > best) { best = k; farbe = r < gr * 0.22f ? 0xFFE27A : verlauf(r / gr, 0xFFF0F5, 0xFFB3CF, 0xF77FAF); }
            }
            if (best > 0) { p.mische(farbe, best); p.leuchte(0xFFD1E3, best * 0.15f); }
            // fallende Blaetter, die sich drehen
            float gx = x / BREITE * 7f;
            int cx = fl(gx);
            for (int k = 0; k < 2; k++) {
                float h = hash(cx * 13 + k * 7, 77);
                float py = frac(h + t * (0.05f + 0.04f * hash(cx, k + 5)));
                float px = cx + 0.5f + 0.38f * (float) Math.sin(t * 1.2f + h * 20f + py * 9f);
                float dx = (gx - px) * BREITE / 7f, dy = y - py;
                float w = t * (1.5f + h * 2f) + h * 10f;
                float c = (float) Math.cos(w), s = (float) Math.sin(w);
                float lx = dx * c - dy * s, ly = dx * s + dy * c;
                float flach = 0.45f + 0.55f * Math.abs((float) Math.sin(w * 0.7f));
                float e = (lx * lx) / (0.017f * 0.017f) + (ly * ly) / (0.009f * 0.009f * flach * flach);
                if (e < 1.4f) {
                    float a = glatt(1.4f, 0.8f, e);
                    p.mische(e < 0.5f ? 0xFFD2E2 : 0xFF97BF, a);
                    p.leuchte(0xFFD6E6, a * 0.25f);
                }
            }
        });

        // --- Sunflower: drehende Sonnenblume, Strahlen, Pollen
        neu("anim_sunflower", "Sunflower", "Turning petals and golden rays", 0xFBBF24, 0xFFF3B0, 0.4f, 0.8f, (x, y, t, p) -> {
            float cx = BREITE / 2, cy = 0.42f;
            float dx = x - cx, dy = y - cy;
            float r = (float) Math.sqrt(dx * dx + dy * dy), a = (float) Math.atan2(dy, dx);
            p.farbe(verlauf(y, 0xFFD36B, 0xFFA43B, 0xF06A2A, 0x9C2E4A));
            float strahl = 0.5f + 0.5f * (float) Math.cos(a * 14f + t * 0.5f);
            p.mische(0xFFF0B0, strahl * glatt(0.5f, 0.1f, r) * 0.35f);
            p.leuchte(0xFFD27A, strahl * glatt(0.45f, 0.15f, r) * 0.2f);
            // Stiel und Blatt
            if (y > cy + 0.1f) {
                float sx = cx + 0.025f * (float) Math.sin(y * 7f + t * 0.8f);
                float st = glatt(0.016f, 0.01f, Math.abs(x - sx));
                p.mische(0x2F7D32, st);
                float bx = sx + 0.09f, by = 0.78f + 0.01f * (float) Math.sin(t);
                float lx = (x - bx) / 0.09f, ly = (y - by + (x - sx) * 0.4f) / 0.035f;
                float blatt = glatt(1.05f, 0.9f, lx * lx + ly * ly);
                p.mische(lx < 0.95f && Math.abs(ly) < 0.12f ? 0x3E9B42 : 0x2E7D32, blatt);
            }
            float R = 0.21f;
            float dreh = a - t * 0.18f;
            float form = (float) Math.pow(Math.abs(Math.cos(dreh * 7f)), 0.55f);
            float form2 = (float) Math.pow(Math.abs(Math.cos(dreh * 7f + Math.PI / 2)), 0.55f);
            float rand1 = R * (0.5f + 0.5f * form), rand2 = R * 0.9f * (0.5f + 0.47f * form2);
            if (r < rand2 && r > R * 0.45f) {
                float k = glatt(rand2, rand2 - 0.012f, r);
                p.mische(verlauf(r / R, 0xB45309, 0xE89B0C, 0xF9C80E), k);
            }
            if (r < rand1 && r > R * 0.45f) {
                float k = glatt(rand1, rand1 - 0.012f, r);
                float ader = 0.85f + 0.15f * (float) Math.cos(dreh * 7f * 2f);
                int c = verlauf(r / R, 0xD97706, 0xFBBF24, 0xFFE066);
                p.mische(c, k);
                p.hell(1f - k * (1f - ader) * 0.6f);
                p.leuchte(0xFFE38A, k * 0.15f);
            }
            if (r < R * 0.5f) {
                float k = glatt(R * 0.5f, R * 0.47f, r);
                float samen = (float) (Math.sin(13 * a + r * 140f) * Math.sin(21 * a - r * 140f));
                int c = verlauf(0.5f + 0.5f * samen, 0x2B1503, 0x4A2508, 0x7A4210);
                p.mische(c, k);
                p.mische(0xC2410C, glatt(R * 0.36f, R * 0.5f, r) * k * 0.5f);
            }
            float pollen = aufsteigend(x, y, t, 6, 0.05f, 0.009f, 11, false);
            p.leuchte(0xFFF2A0, pollen);
            p.mische(0xFFF4C2, pollen * 0.7f);
        });

        // --- Pumpkin: geschnitzter Kuerbis mit flackernder Kerze, Fledermaeuse, Nebel
        neu("anim_pumpkin", "Pumpkin", "Flickering jack-o'-lantern", 0xF97316, 0xFFD166, 0.35f, 0.5f, (x, y, t, p) -> {
            p.farbe(verlauf(y, 0x0E0820, 0x24103E, 0x4A1D4A, 0x7A3418));
            float st = sterne(x, y, 16f, t, 4, 0.3f) * glatt(0.6f, 0.2f, y);
            p.mische(0xFFFFFF, st * 0.7f);
            p.leuchte(0xFFFFFF, st * 0.6f);
            // Mond
            float mx = x - 0.47f, my = y - 0.14f, mr = (float) Math.sqrt(mx * mx + my * my);
            p.mische(0xFFF1C1, glatt(0.062f, 0.058f, mr));
            p.mische(0xE8D9A8, glatt(0.062f, 0.058f, mr) * glatt(0.5f, 0.7f, fbm(x * 30f, y * 30f, 2)) * 0.5f);
            p.leuchte(0xFFE9B0, glatt(0.16f, 0.06f, mr) * 0.25f);
            // Fledermaeuse
            for (int k = 0; k < 3; k++) {
                float ph = frac(t * (0.07f + k * 0.015f) + k * 0.37f);
                float bx = -0.1f + ph * (BREITE + 0.2f), by = 0.12f + k * 0.08f + 0.03f * (float) Math.sin(t * 1.7f + k);
                float fl = (float) Math.sin(t * 14f + k * 2f);
                float ux = (x - bx) / 0.03f, uy = (y - by) / 0.03f;
                float koerper = ux * ux * 2.2f + uy * uy * 1.3f;
                float fluegel = Math.abs(ux) < 1.2f && Math.abs(uy + Math.abs(ux) * 0.5f * fl) < 0.28f ? 1 : 0;
                if (koerper < 0.25f || fluegel > 0) p.farbe(0x07040C);
            }
            // Kuerbis
            float kx = (x - BREITE / 2) / 0.235f, ky = (y - 0.6f) / 0.19f;
            float e = kx * kx + ky * ky;
            float flack = 0.75f + 0.15f * rausch(t * 7f, 1f) + 0.1f * rausch(t * 17f, 5f);
            p.leuchte(0xFF8A1F, glatt(2.3f, 1f, e) * 0.25f * flack);
            if (e < 1.04f) {
                float k = glatt(1.04f, 0.96f, e);
                float rippe = (float) Math.abs(Math.cos(kx * 3.5f));
                int c = verlauf(e * 0.8f + (1 - rippe) * 0.3f, 0xFFA43A, 0xF97316, 0xC2410C, 0x7C2D12);
                p.mische(c, k);
                // Gesicht: Augen, Nase, Mund
                float ax = Math.abs(kx) - 0.38f, ay = ky + 0.25f;
                boolean auge = ay < 0.12f && ay > -0.2f && Math.abs(ax) < (0.12f - ay) * 0.75f;
                boolean nase = Math.abs(kx) < (0.1f - (ky - 0.02f)) * 0.5f && ky > -0.06f && ky < 0.12f;
                float mund0 = 0.28f + 0.06f * (float) Math.cos(kx * 2f), zahn = frac(kx * 2.2f + 0.25f) < 0.5f ? 0.07f : 0f;
                boolean mund = Math.abs(kx) < 0.62f && ky > mund0 + zahn - 0.02f && ky < mund0 + 0.24f - Math.abs(kx) * 0.22f - (frac(kx * 2.2f) < 0.5f ? 0.06f : 0f);
                if (auge || nase || mund) {
                    int lc = verlauf(flack, 0xFF8C1A, 0xFFC24A, 0xFFF2A6);
                    p.farbe(lc);
                    p.leuchte(lc, 0.95f * flack);
                }
                // Stiel
            }
            float sx = (x - BREITE / 2 - 0.01f) / 0.03f, sy = (y - 0.41f) / 0.04f;
            if (Math.abs(sx + sy * 0.3f) < 1f && sy > -1.2f && sy < 0.6f) p.farbe(0x3F5A1E);
            // Nebel unten
            float nebel = fbm(x * 4f + t * 0.06f, y * 6f, 3) * glatt(0.7f, 1f, y);
            p.mische(0xB9A6D6, nebel * 0.55f);
        });

        // --- Ghost: schwebender Geist im Nebel, blinzelnde Augen, aufsteigende Lichter
        neu("anim_ghost", "Ghost", "A friendly spirit in the mist", 0x93C5FD, 0x67E8F9, 0.35f, 0.7f, (x, y, t, p) -> {
            p.farbe(verlauf(y, 0x0A0F26, 0x141D45, 0x1E2A5C));
            float nebel = fbm(x * 3f + t * 0.04f, y * 3.5f - t * 0.02f, 4);
            p.mische(0x5468A8, glatt(0.45f, 0.8f, nebel) * 0.55f);
            p.leuchte(0x8EA6FF, glatt(0.6f, 0.9f, nebel) * 0.12f);
            float cy = 0.43f + 0.035f * (float) Math.sin(t * 1.4f), cx = BREITE / 2 + 0.02f * (float) Math.sin(t * 0.7f);
            float dx = x - cx, dy = y - cy;
            float kopf = (float) Math.sqrt(dx * dx + dy * dy);
            float unten = cy + 0.2f + 0.022f * (float) Math.sin(dx * 48f - t * 4f);
            boolean drin = (dy < 0 && kopf < 0.165f) || (dy >= 0 && Math.abs(dx) < 0.165f - dy * 0.06f && y < unten);
            float halo = Math.max(0, 1 - Math.max(kopf - 0.16f, 0) / 0.12f);
            p.leuchte(0x9FD8FF, halo * halo * 0.22f);
            if (drin) {
                float schatten = glatt(-0.15f, 0.25f, dy + dx * 0.4f);
                p.farbe(verlauf(schatten, 0xF4FAFF, 0xDCE9FF, 0xAFC4F0));
                p.leuchte(0xDFF1FF, 0.28f);
                // Augen (blinzeln alle ~4 s)
                float blink = (t % 4.3f) < 0.14f ? 0.15f : 1f;
                for (int s = -1; s <= 1; s += 2) {
                    float ex = (dx - s * 0.055f) / 0.028f, ey = (dy + 0.02f) / (0.042f * blink);
                    if (ex * ex + ey * ey < 1) {
                        p.farbe(0x141833);
                        float px = (dx - s * 0.055f - 0.008f) / 0.012f, py = (dy + 0.03f) / (0.014f * blink);
                        if (px * px + py * py < 1) { p.farbe(0x7DF9FF); p.leuchte(0x7DF9FF, 0.9f); }
                    }
                }
                float mx = dx / 0.03f, my = (dy - 0.06f) / 0.02f;
                if (mx * mx + my * my < 1) p.farbe(0x141833);
                float wange = ((Math.abs(dx) - 0.09f) * (Math.abs(dx) - 0.09f) + (dy - 0.035f) * (dy - 0.035f)) / (0.022f * 0.022f);
                if (wange < 1) p.mische(0xFFB8D9, 0.5f * (1 - wange));
            }
            float licht = aufsteigend(x, y, t, 6, 0.06f, 0.011f, 21, false);
            p.leuchte(0x9AF6FF, licht);
            p.mische(0xC8FBFF, licht * 0.6f);
        });

        // --- Skull: Totenkopf mit pulsierend leuchtenden Augen, Rauch
        neu("anim_skull", "Skull", "Glowing eyes and drifting smoke", 0xA855F7, 0xE879F9, 0.3f, 0.5f, (x, y, t, p) -> {
            float cx = BREITE / 2, cy = 0.42f;
            float dx = x - cx, dy = y - cy;
            float rv = (float) Math.sqrt(dx * dx + dy * dy * 0.6f);
            p.farbe(verlauf(glatt(0f, 0.55f, rv), 0x3A0A4A, 0x1A0624, 0x07060B));
            float rauch = fbm(x * 3.5f + (float) Math.sin(t * 0.3f) * 0.3f, y * 3f + t * 0.12f, 4);
            p.mische(0x5B2A7A, glatt(0.5f, 0.85f, rauch) * 0.5f);
            float puls = 0.6f + 0.4f * (float) Math.sin(t * 2.4f);
            // gekreuzte Knochen
            for (int s = -1; s <= 1; s += 2) {
                float c = 0.8f, si = 0.6f * s;
                float bx = dx, by = y - (cy + 0.27f);
                float lx = bx * c + by * si, ly = -bx * si + by * c;
                if (Math.abs(lx) < 0.2f && Math.abs(ly) < 0.018f) p.farbe(verlauf(Math.abs(ly) / 0.018f, 0xECE6D6, 0xBDB39A));
                float ex = Math.abs(lx) - 0.2f;
                if (Math.abs(ex) < 0.03f) {
                    for (int e2 = -1; e2 <= 1; e2 += 2) {
                        float kx = ex, ky = ly - e2 * 0.018f;
                        if (kx * kx + ky * ky < 0.022f * 0.022f) p.farbe(0xE2DBC8);
                    }
                }
            }
            // Schaedel
            float kopf = (dx * dx) / (0.16f * 0.16f) + (dy * dy) / (0.15f * 0.15f);
            boolean kiefer = Math.abs(dx) < 0.09f && dy > 0.06f && dy < 0.2f;
            if (kopf < 1 || kiefer) {
                float sch = glatt(-0.15f, 0.2f, dy + dx * 0.5f);
                p.farbe(verlauf(sch, 0xF5F1E6, 0xD9D1BC, 0xA79D85));
                p.hell(0.92f + 0.08f * fbm(x * 40f, y * 40f, 2));
                if (kiefer && dy > 0.12f && frac((dx + 0.09f) / 0.03f) < 0.16f) p.farbe(0x2A2230);
                if (kiefer && Math.abs(dy - 0.12f) < 0.006f) p.farbe(0x2A2230);
                for (int s = -1; s <= 1; s += 2) {
                    float ex = (dx - s * 0.062f) / 0.047f, ey = (dy - 0.01f) / 0.05f;
                    float e = ex * ex + ey * ey;
                    if (e < 1) {
                        p.farbe(0x0A0610);
                        float g = (float) Math.exp(-e * 3.2f) * puls;
                        p.mische(0xF0ABFC, g);
                        p.leuchte(0xE879F9, Math.min(1, g * 1.3f));
                    }
                }
                if (Math.abs(dx) < (0.085f - dy) * 0.4f && dy > 0.04f && dy < 0.085f) p.farbe(0x120C18);
            }
            p.leuchte(0xC026D3, glatt(0.25f, 0.05f, rv) * 0.18f * puls);
            float funke = aufsteigend(x, y, t, 5, 0.05f, 0.009f, 33, false);
            p.leuchte(0xF0ABFC, funke * 0.8f);
        });

        // --- Purple Neon: Synthwave-Sonne, laufendes Gitter
        neu("anim_neon", "Purple Neon", "Retro sun and running grid", 0xFF2BD6, 0x22D3EE, 0.3f, 0.5f, (x, y, t, p) -> {
            float hor = 0.6f;
            p.farbe(verlauf(y / hor, 0x0B0221, 0x1E0B45, 0x4A1270, 0x8A1F7A));
            float st = sterne(x, y, 18f, t, 7, 0.4f) * glatt(0.45f, 0.1f, y);
            p.mische(0xFFFFFF, st * 0.6f);
            p.leuchte(0xFFFFFF, st * 0.5f);
            float cx = BREITE / 2, cy = 0.43f;
            float dx = x - cx, dy = y - cy, r = (float) Math.sqrt(dx * dx + dy * dy);
            p.leuchte(0xFF4FD8, glatt(0.3f, 0.15f, r) * 0.28f);
            if (r < 0.165f && y < hor) {
                boolean streifen = dy > 0 && frac(dy * 26f - t * 0.35f) < glatt(0f, 0.16f, dy) * 0.55f;
                if (!streifen) {
                    int c = verlauf(glatt(-0.16f, 0.16f, dy), 0xFFE45E, 0xFF8A3D, 0xFF2E88);
                    p.farbe(c);
                    p.leuchte(c, 0.55f);
                }
            }
            // Berge am Horizont
            float berg = hor - 0.05f * Math.abs((float) Math.sin(x * 11f)) - 0.03f * fbm(x * 9f, 1f, 2);
            if (y > berg && y < hor + 0.001f) p.farbe(0x1A0533);
            if (y >= hor) {
                p.farbe(verlauf((y - hor) / (1 - hor), 0x23054A, 0x0D0221));
                float z = 0.15f / (y - hor + 0.012f);
                float gx = (x - cx) * z * 3.2f;
                float gz = z + t * 0.9f;
                float lx = Math.abs(frac(gx) - 0.5f), lz = Math.abs(frac(gz) - 0.5f);
                float w = 0.06f * Math.min(4f, z * 0.6f);
                float linie = Math.max(glatt(0.5f - w * 0.5f, 0.5f, lx), glatt(0.5f - w * 0.35f, 0.5f, lz));
                float fade = glatt(0f, 0.08f, y - hor);
                int lc = mischRgb(0xFF2BD6, 0x22D3EE, glatt(hor, 1f, y));
                p.mische(lc, linie * fade);
                p.leuchte(lc, linie * fade * 0.9f);
                p.leuchte(0xFF2BD6, glatt(0.05f, 0f, y - hor) * 0.5f);
            }
        });

        // --- Thunder: Gewitterwolken, Blitze, Regen
        neu("anim_storm", "Thunderstorm", "Lightning strikes and rain", 0x60A5FA, 0xE0F2FE, 0.25f, 0.3f, (x, y, t, p) -> {
            float w = fbm(x * 3f + t * 0.06f + fbm(x * 2f, y * 2f - t * 0.03f, 2) * 0.8f, y * 4f, 5);
            p.farbe(verlauf(w, 0x080B16, 0x161D31, 0x2E3854, 0x56638A, 0x8794B8));
            p.mische(0x05070F, glatt(0.5f, 1f, y) * 0.35f);
            float periode = 1.9f, ph = t % periode;
            int nr = (int) (t / periode);
            float blitz = ph < 0.08f ? 1f : ph < 0.13f ? 0.25f : ph < 0.22f ? 0.85f : Math.max(0, 1f - (ph - 0.22f) * 4f) * 0.4f;
            if (ph > 0.6f) blitz = 0;
            p.mische(0xC9D6F5, blitz * glatt(0.4f, 0.75f, w) * 0.6f);
            p.leuchte(0x9DB8FF, blitz * glatt(0.5f, 0.8f, w) * 0.3f);
            // Blitz: Zickzack von oben
            float x0 = 0.12f + 0.4f * hash(nr, 3);
            if (ph < 0.45f) {
                float bx = x0 + (fbm(y * 6f, nr * 3.7f, 3) - 0.5f) * 0.35f + (y > 0.3f ? (y - 0.3f) * 0.2f * (hash(nr, 9) - 0.5f) : 0);
                float d = Math.abs(x - bx);
                float ende = glatt(0.95f, 0.7f, y * (0.8f + 0.4f * hash(nr, 5)));
                float kern = glatt(0.006f, 0.002f, d) * ende, schein = glatt(0.05f, 0f, d) * ende;
                // Seitenast
                float ay = 0.35f + 0.2f * hash(nr, 11);
                if (y > ay) {
                    float ax = bx + (y - ay) * (hash(nr, 13) > 0.5f ? 0.6f : -0.6f) + (rausch(y * 20f, nr) - 0.5f) * 0.03f;
                    float d2 = Math.abs(x - ax);
                    float aende = glatt(ay + 0.25f, ay + 0.1f, y);
                    kern = Math.max(kern, glatt(0.004f, 0.0015f, d2) * aende);
                    schein = Math.max(schein, glatt(0.03f, 0f, d2) * aende * 0.7f);
                }
                float an = Math.max(blitz, 0.35f);
                p.mische(0xFFFFFF, kern * an);
                p.leuchte(0xEAF2FF, kern * an);
                p.leuchte(0x8FB4FF, schein * an * 0.6f);
            }
            // Regen
            float sp = x * 110f;
            int c = fl(sp);
            float h = hash(c, 41);
            float ry = frac(y * 2.2f + x * 0.8f - t * (2.2f + h) + h * 7f);
            if (ry < 0.07f && frac(sp) < 0.35f) p.mische(0xA9B8D6, 0.45f * (1 - ry / 0.07f));
        });

        // --- Aurora: Nordlichter ueber Bergen
        neu("anim_aurora", "Aurora", "Northern lights over the mountains", 0x34D399, 0xA78BFA, 0.3f, 0.6f, (x, y, t, p) -> {
            p.farbe(verlauf(y, 0x02040F, 0x061633, 0x0B2547));
            float st = sterne(x, y, 20f, t, 9, 0.45f);
            p.mische(0xFFFFFF, st * 0.8f);
            p.leuchte(0xFFFFFF, st * 0.7f);
            for (int k = 0; k < 2; k++) {
                float yc = 0.32f + k * 0.12f + 0.07f * (float) Math.sin(x * (5f + k) + t * (0.35f + k * 0.1f)) + 0.06f * (fbm(x * 2.5f + t * 0.08f, k * 3f, 3) - 0.5f);
                float ueber = yc - y;
                float band = ueber > 0 ? (float) Math.exp(-ueber / (0.12f + 0.05f * k)) : (float) Math.exp(ueber * ueber * -900f);
                float strahl = 0.55f + 0.45f * rausch(x * 45f + t * 0.6f * (k + 1), k * 7f + t * 0.2f);
                float a = band * strahl * (0.75f - k * 0.15f);
                int c = verlauf(glatt(-0.02f, 0.22f, ueber), 0x6EE7B7, 0x34D399, 0x22D3EE, 0xA78BFA);
                p.mische(c, a * 0.9f);
                p.leuchte(c, a * 0.85f);
            }
            float t1 = 1 - Math.abs(2 * frac(x * 3.1f + 0.2f) - 1), t2 = 1 - Math.abs(2 * frac(x * 7.3f + 0.55f) - 1);
            float berg = 0.9f - 0.13f * t1 - 0.05f * t2 - 0.02f * fbm(x * 12f, 2f, 2);
            if (y > berg) {
                float k = glatt(berg, berg + 0.004f, y);
                float schnee = glatt(berg + 0.035f * (0.6f + t1), berg, y);
                p.mische(verlauf((y - berg) * 5f, 0x1B2A48, 0x070C18), k);
                p.mische(0xD6E4FF, k * schnee * 0.75f);
                p.la *= 1 - k * 0.85f;
                p.leuchte(0x34D399, k * schnee * 0.12f);
            }
        });

        // --- Ocean: Lichtstrahlen, Kaustik, Blasen, Seetang
        neu("anim_ocean", "Deep Ocean", "Sunlight, waves and bubbles", 0x38BDF8, 0xBAE6FD, 0.35f, 0.6f, (x, y, t, p) -> {
            p.farbe(verlauf(y, 0x1F8FCF, 0x0E5A8C, 0x063457, 0x031A30));
            float strahl = (float) Math.pow(0.5f + 0.5f * Math.sin(x * 24f + Math.sin(t * 0.4f) * 1.5f + y * 2.5f), 6) * glatt(0.9f, 0f, y);
            p.mische(0x9BE7FF, strahl * 0.35f);
            p.leuchte(0x9BE7FF, strahl * 0.15f);
            float c1 = rausch(x * 11f + t * 0.35f, y * 11f), c2 = rausch(x * 11f - t * 0.3f + 5f, y * 11f + t * 0.2f);
            float kaustik = (float) Math.pow(1 - Math.abs(c1 - c2), 10) * glatt(0.85f, 0.05f, y);
            p.mische(0xD4F6FF, kaustik * 0.55f);
            p.leuchte(0xBFF2FF, kaustik * 0.35f);
            for (int k = 0; k < 4; k++) {
                float kx = 0.07f + k * 0.16f + 0.02f * hash(k, 3);
                float hoehe = 0.62f + 0.12f * hash(k, 7);
                if (y > hoehe) {
                    float s = kx + 0.025f * (float) Math.sin(y * 11f + t * 1.4f + k) * (1 - y + 0.3f);
                    float d = Math.abs(x - s), dicke = 0.011f + 0.006f * (y - hoehe);
                    p.mische(k % 2 == 0 ? 0x0F5132 : 0x14532D, glatt(dicke, dicke * 0.5f, d));
                }
            }
            // Blasen (Ringe)
            float gx = x / BREITE * 6f;
            int cx = fl(gx);
            for (int k = 0; k < 2; k++) {
                float h = hash(cx * 5 + k, 61);
                float py = 1 - frac(h + t * (0.08f + 0.06f * hash(cx, k)));
                float px = cx + 0.5f + 0.25f * (float) Math.sin(t * 2f + h * 30f + py * 12f);
                float dx = (gx - px) * BREITE / 6f, dy = y - py;
                float r = (float) Math.sqrt(dx * dx + dy * dy), gr = 0.008f + 0.008f * h;
                float ring = glatt(gr * 0.45f, 0f, Math.abs(r - gr)) * 0.8f + glatt(gr * 0.5f, 0f, Math.abs((dx + gr * 0.35f) * (dx + gr * 0.35f) + (dy + gr * 0.35f) * (dy + gr * 0.35f)) * 60f);
                p.mische(0xE0F7FF, Math.min(1, ring) * 0.8f);
                p.leuchte(0xC9F3FF, Math.min(1, ring) * 0.5f);
            }
        });

        // --- Vortex: drehende Galaxie in Vortex-Farben
        neu("anim_vortex", "Vortex Galaxy", "A spinning galaxy in Vortex colors", 0x8B5CF6, 0x60A5FA, 0.5f, 0.9f, (x, y, t, p) -> {
            float cx = BREITE / 2, cy = 0.45f;
            float dx = (x - cx) * 1.05f, dy = y - cy;
            float r = (float) Math.sqrt(dx * dx + dy * dy), a = (float) Math.atan2(dy, dx);
            p.farbe(verlauf(glatt(0, 0.6f, r), 0x150A33, 0x080418, 0x04020C));
            float st = sterne(x, y, 17f, t, 13, 0.5f);
            p.mische(0xFFFFFF, st * 0.7f);
            p.leuchte(0xFFFFFF, st * 0.6f);
            float spirale = (float) Math.cos(2 * (a - (float) Math.log(r + 0.015f) * 3.0f - t * 0.35f));
            float staub = fbm(r * 9f - t * 0.2f, a * 1.5f + 3f, 3);
            float arm = glatt(0.15f, 1f, spirale) * (float) Math.exp(-r * 4.2f) * (0.6f + 0.6f * staub);
            int ac = verlauf(glatt(0.02f, 0.32f, r), 0xFFE6FF, 0xC084FC, 0x8B5CF6, 0x3B82F6);
            p.mische(ac, Math.min(1, arm * 1.6f));
            p.leuchte(ac, Math.min(1, arm * 1.2f));
            float kern = (float) Math.exp(-r * r / 0.0025f);
            p.mische(0xFFF5FF, kern);
            p.leuchte(0xF5D0FE, Math.min(1, kern * 1.2f + (float) Math.exp(-r * r / 0.012f) * 0.4f));
            float sternArm = sterne(x + 0.01f * (float) Math.sin(t * 0.3f), y, 34f, t * 1.5f, 15, 0.5f) * arm * 3f;
            p.leuchte(0xFFFFFF, Math.min(1, sternArm));
        });

        // --- Frost: Eiskristalle, Schneeflocke, Schneefall
        neu("anim_frost", "Frost", "Ice crystals and falling snow", 0xBAE6FD, 0xFFFFFF, 0.55f, 1.1f, (x, y, t, p) -> {
            p.farbe(verlauf(y, 0xE3F6FF, 0x9ED5F5, 0x5A9FD8, 0x2C5E9E));
            // Kristall-Facetten (Voronoi)
            float gx = x * 9f, gy = y * 9f;
            int cx = fl(gx), cy = fl(gy);
            float d1 = 9, d2 = 9, hb = 0;
            for (int j = -1; j <= 1; j++) for (int i = -1; i <= 1; i++) {
                float px = cx + i + hash(cx + i, cy + j), py = cy + j + hash(cx + i + 31, cy + j + 17);
                float d = (gx - px) * (gx - px) + (gy - py) * (gy - py);
                if (d < d1) { d2 = d1; d1 = d; hb = hash(cx + i + 5, cy + j + 3); } else if (d < d2) d2 = d;
            }
            float kante = glatt(0.08f, 0f, (float) Math.sqrt(d2) - (float) Math.sqrt(d1));
            p.hell(0.88f + 0.22f * hb);
            float schimmer = 0.5f + 0.5f * (float) Math.sin(t * 1.6f + hb * 12f);
            p.mische(0xFFFFFF, kante * 0.55f);
            p.leuchte(0xE0F7FF, kante * 0.25f * schimmer + hb * 0.06f * schimmer);
            // Schneeflocke (6-fach)
            float fx = x - BREITE / 2, fy = y - 0.42f;
            float r = (float) Math.sqrt(fx * fx + fy * fy), a = (float) Math.atan2(fy, fx) + t * 0.15f;
            if (r < 0.2f) {
                float seg = (float) (Math.PI / 3);
                float aa = a - seg * Math.round(a / seg);
                float lx = r * (float) Math.cos(aa), ly = Math.abs(r * (float) Math.sin(aa));
                float d = ly;
                for (int k = 1; k <= 3; k++) {
                    float bx = 0.045f * k;
                    float l = 0.05f - k * 0.01f;
                    float vx = lx - bx, vy = ly;
                    float pr = Math.max(0, Math.min(l, vx * 0.5f + vy * 0.866f));
                    float qx = vx - pr * 0.5f, qy = vy - pr * 0.866f;
                    d = Math.min(d, (float) Math.sqrt(qx * qx + qy * qy));
                }
                if (lx > 0.185f) d = Math.max(d, lx - 0.185f);
                d = Math.min(d, Math.max(0, r - 0.03f));
                float linie = glatt(0.009f, 0.004f, d);
                float puls = 0.75f + 0.25f * (float) Math.sin(t * 1.8f);
                p.mische(0xFFFFFF, linie);
                p.leuchte(0xCFF4FF, linie * puls);
                p.leuchte(0x9DE4FF, glatt(0.03f, 0f, d) * 0.3f * puls);
            }
            float schnee = aufsteigend(x, y, t, 8, 0.06f, 0.008f, 71, true);
            p.mische(0xFFFFFF, schnee);
            p.leuchte(0xFFFFFF, schnee * 0.6f);
        });

        // --- Phoenix: lodernde Flammen von unten, Funken
        neu("anim_flame", "Phoenix Flame", "Roaring flames and sparks", 0xF59E0B, 0xFDE047, 0.3f, 0.5f, (x, y, t, p) -> {
            float wirbel = fbm(x * 4f, y * 3f + t * 0.9f, 3);
            float f = fbm(x * 5f + wirbel * 0.8f, y * 3.6f + t * 1.25f, 4);
            float hitze = y * 1.25f - 0.35f + (f - 0.5f) * 1.25f;
            hitze = Math.max(0, Math.min(1, hitze));
            int c = verlauf(hitze, 0x0A0303, 0x2B0505, 0x7F1D1D, 0xDC2626, 0xF97316, 0xFBBF24, 0xFEF3C7);
            p.farbe(c);
            p.leuchte(c, glatt(0.35f, 0.9f, hitze));
            float funke = aufsteigend(x, y, t, 8, 0.14f, 0.008f, 91, false);
            p.mische(0xFFE08A, funke);
            p.leuchte(0xFFD166, funke);
        });

        // --- Prism: holografisch schillernd mit Rautenmuster
        neu("anim_prism", "Prism", "Holographic shimmer", 0xE5E7EB, 0xFFFFFF, 0.8f, 1.2f, (x, y, t, p) -> {
            float w = fbm(x * 2.5f + t * 0.05f, y * 2.5f - t * 0.03f, 3);
            float hue = x * 0.6f + y * 0.5f + w * 0.9f + t * 0.06f;
            int c = hsv(hue, 0.38f, 1f);
            p.farbe(c);
            p.hell(0.82f + 0.18f * (float) Math.sin(w * 12f + t * 0.8f));
            float u = x * 6f + y * 4f, v = x * 6f - y * 4f;
            float lu = 0.5f - Math.abs(frac(u) - 0.5f), lv = 0.5f - Math.abs(frac(v) - 0.5f);
            float kante = glatt(0.06f, 0.0f, Math.min(lu, lv));
            p.mische(0xFFFFFF, kante * 0.4f);
            float schill = (float) Math.pow(0.5f + 0.5f * Math.sin((x - y) * 14f + t * 1.4f + w * 6f), 6);
            p.leuchte(hsv(hue + 0.33f, 0.35f, 1f), kante * 0.3f + schill * 0.35f);
        });

        // --- Royal: Samt mit goldener Ornamentik
        neu("anim_royal", "Royal", "Velvet with golden ornaments", 0xD4AF37, 0xFFF1B8, 0.55f, 1.0f, (x, y, t, p) -> {
            float stoff = fbm(x * 18f, y * 10f, 3);
            p.farbe(verlauf(stoff, 0x3D0612, 0x5E0B1E, 0x7A1028));
            float glanz = 0.5f + 0.5f * (float) Math.sin(x * 9f - t * 0.6f + y * 2f);
            p.mische(0x9B1B3A, glanz * 0.25f);
            // Ornament: gespiegeltes Muster in Kacheln
            float kx = Math.abs(frac(x / BREITE * 2f) - 0.5f), ky = frac(y * 3.2f) - 0.5f;
            float r = (float) Math.sqrt(kx * kx + ky * ky), a = (float) Math.atan2(ky, kx);
            float rose = 0.28f * Math.abs((float) Math.cos(2 * a)) + 0.05f;
            float linie = glatt(0.025f, 0.008f, Math.abs(r - rose));
            float kreis = glatt(0.02f, 0.008f, Math.abs(r - 0.06f));
            float ranke = glatt(0.02f, 0.006f, Math.abs(ky - 0.12f * (float) Math.sin(kx * 18f)) + Math.max(0, 0.35f - kx) * 0f) * glatt(0.32f, 0.38f, kx);
            float gold = Math.max(Math.max(linie, kreis), ranke);
            if (gold > 0) {
                float metall = 0.75f + 0.25f * (float) Math.sin((x + y) * 60f + t * 2f);
                int gc = verlauf(metall, 0x8A6A1C, 0xD4AF37, 0xFFE9A3);
                p.mische(gc, gold);
                p.leuchte(0xFFD86B, gold * 0.22f * metall);
            }
            // Wappen oben
            float wx = (x - BREITE / 2) / 0.09f, wy = (y - 0.2f) / 0.1f;
            boolean schild = Math.abs(wx) < 1f - Math.max(0, wy) * 0.6f && wy > -0.9f && wy < 1f;
            if (schild) {
                p.farbe(verlauf(glatt(-1, 1, wy + wx * 0.3f), 0xFFE9A3, 0xD4AF37, 0x8A6A1C));
                boolean v = Math.abs(Math.abs(wx) - (0.55f - wy) * 0.42f) < 0.13f && wy > -0.6f && wy < 0.55f;
                if (v) p.farbe(0x5E0B1E);
                p.leuchte(0xFFE38A, 0.2f);
            }
        });
    }
}
