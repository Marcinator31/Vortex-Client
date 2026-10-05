package com.vortex.client.gui.glatt;

import java.util.ArrayList;
import java.util.List;

/**
 * Vektor-Symbole fuer die Oberflaeche (seit Client 4.17).
 *
 * Jedes Symbol ist auf einem 24x24-Raster beschrieben -- Linien, Boegen,
 * Kreise, Flaechen -- und wird in genau der Pixelgroesse gerastert, in der es
 * gezeichnet wird, mit weichen Kanten. Damit ist es bei jeder GUI-Groesse
 * scharf, statt wie die alten 7x7-Punktmuster aus Kloetzchen zu bestehen.
 */
public final class Symbole {

    private Symbole() {}

    public enum Symbol {
        RASTER, HUD, REGLER, NADEL, BLITZ, LEUTE, HEMD, ROBOTER, TASTATUR, ZAHNRAD, GLOBUS,
        NEUSTART, PFEIL_RECHTS, KREUZ, KOLBEN, WELT, SERVER, WELLEN, BILD, PERSON, AUSGANG, PLUS,
        SUCHE, STERN, HAKEN, NOTE, PLAY, PAUSE, VOR, ZURUECK, KOPFHOERER
    }

    /** Ein Strich-Bauteil: Linienzug (Abstandsfeld) oder Flaeche (Vieleck). */
    private record Teil(float[] p, boolean flaeche, boolean zu, float breite) {}

    private static final class Zeichnung {
        final List<Teil> teile = new ArrayList<>();
        float breite = 2f;

        void zug(float... p) { teile.add(new Teil(p, false, false, breite)); }
        void ring(float... p) { teile.add(new Teil(p, false, true, breite)); }
        void flaeche(float... p) { teile.add(new Teil(p, true, true, 0)); }

        void bogen(float cx, float cy, float r, float von, float bis) { zug(bogenPunkte(cx, cy, r, r, von, bis)); }
        void kreis(float cx, float cy, float r) { ring(bogenPunkte(cx, cy, r, r, 0, 360)); }
        void ellipse(float cx, float cy, float rx, float ry) { ring(bogenPunkte(cx, cy, rx, ry, 0, 360)); }
        void punkt(float cx, float cy, float r) { flaeche(bogenPunkte(cx, cy, r, r, 0, 360)); }

        void rund(float x, float y, float w, float h, float r) { ring(rundPunkte(x, y, w, h, r)); }
        void rundVoll(float x, float y, float w, float h, float r) { flaeche(rundPunkte(x, y, w, h, r)); }

        /** Pfeilspitze an (x, y), die in Richtung (dx, dy) zeigt. */
        void spitze(float x, float y, float dx, float dy, float len) {
            float l = (float) Math.sqrt(dx * dx + dy * dy);
            dx /= l; dy /= l;
            double w = Math.toRadians(42);
            float c = (float) Math.cos(w), s = (float) Math.sin(w);
            float ax = -(dx * c - dy * s) * len, ay = -(dx * s + dy * c) * len;
            float bx = -(dx * c + dy * s) * len, by = -(-dx * s + dy * c) * len;
            zug(x + ax, y + ay, x, y, x + bx, y + by);
        }
    }

    static float[] bogenPunkte(float cx, float cy, float rx, float ry, float von, float bis) {
        int n = Math.max(6, (int) Math.ceil(Math.abs(bis - von) / 7.5f));
        float[] p = new float[(n + 1) * 2];
        for (int i = 0; i <= n; i++) {
            double w = Math.toRadians(von + (bis - von) * i / n);
            p[i * 2] = cx + rx * (float) Math.cos(w);
            p[i * 2 + 1] = cy + ry * (float) Math.sin(w);
        }
        return p;
    }

    static float[] rundPunkte(float x, float y, float w, float h, float r) {
        List<Float> l = new ArrayList<>();
        float[][] ecken = {{x + w - r, y + r, -90}, {x + w - r, y + h - r, 0}, {x + r, y + h - r, 90}, {x + r, y + r, 180}};
        for (float[] e : ecken) {
            float[] b = bogenPunkte(e[0], e[1], r, r, e[2], e[2] + 90);
            for (float v : b) l.add(v);
        }
        float[] p = new float[l.size()];
        for (int i = 0; i < p.length; i++) p[i] = l.get(i);
        return p;
    }

    private static Zeichnung zeichnung(Symbol s) {
        Zeichnung z = new Zeichnung();
        switch (s) {
            case RASTER -> {
                z.rund(3, 3, 7.5f, 7.5f, 2); z.rund(13.5f, 3, 7.5f, 7.5f, 2);
                z.rund(3, 13.5f, 7.5f, 7.5f, 2); z.rund(13.5f, 13.5f, 7.5f, 7.5f, 2);
            }
            case HUD -> {
                z.rund(2.5f, 3.5f, 19, 13.5f, 2.5f);
                z.zug(8, 21, 16, 21); z.zug(12, 17, 12, 21);
                z.rundVoll(5.5f, 6.5f, 5.5f, 3.5f, 1); z.zug(14.5f, 13.5f, 18.5f, 13.5f);
            }
            case REGLER -> {
                z.zug(4, 6, 20, 6); z.zug(4, 12, 20, 12); z.zug(4, 18, 20, 18);
                z.punkt(9, 6, 2.6f); z.punkt(15.5f, 12, 2.6f); z.punkt(7.5f, 18, 2.6f);
            }
            case NADEL -> {
                float[] b = bogenPunkte(12, 10, 7.5f, 7.5f, 145, 395);
                float[] p = new float[b.length + 4];
                System.arraycopy(b, 0, p, 2, b.length);
                p[0] = 12; p[1] = 21.5f; p[p.length - 2] = 12; p[p.length - 1] = 21.5f;
                z.zug(p);
                z.kreis(12, 10, 2.8f);
            }
            case BLITZ -> z.ring(13, 2.5f, 4.5f, 13.5f, 11.5f, 13.5f, 11, 21.5f, 19.5f, 10.5f, 12.5f, 10.5f);
            case LEUTE -> {
                z.kreis(9, 7.5f, 3.6f);
                z.bogen(9, 21, 7, 200, 340);
                z.bogen(16.5f, 7.5f, 3.2f, -75, 85);
                z.zug(17.5f, 14.2f, 19.5f, 15.2f);
                z.bogen(16.5f, 21.5f, 6.5f, 270, 340);
            }
            case HEMD -> {
                // Umriss links oben -> Aermel -> Saum -> rechts oben, dann der
                // Kragen als Bogen von rechts nach links zurueck
                float[] umriss = {8.4f, 2.5f, 4, 4.5f, 1.8f, 9.8f, 5.8f, 11.6f, 5.8f, 21, 18.2f, 21, 18.2f, 11.6f, 22.2f, 9.8f, 20, 4.5f, 15.6f, 2.5f};
                float[] kragen = bogenPunkte(12, 2.5f, 3.6f, 3.2f, 0, 180);
                float[] p = new float[umriss.length + kragen.length];
                System.arraycopy(umriss, 0, p, 0, umriss.length);
                System.arraycopy(kragen, 0, p, umriss.length, kragen.length);
                z.zug(p);
            }
            case ROBOTER -> {
                z.rund(4, 8, 16, 12.5f, 3.5f);
                z.zug(12, 8, 12, 4.5f); z.punkt(12, 3.5f, 1.6f);
                z.punkt(9, 13.8f, 1.6f); z.punkt(15, 13.8f, 1.6f);
                z.zug(1.8f, 12.5f, 1.8f, 16); z.zug(22.2f, 12.5f, 22.2f, 16);
            }
            case TASTATUR -> {
                z.rund(2, 5, 20, 14, 2.5f);
                for (int k = 0; k < 4; k++) z.punkt(6.5f + k * 3.7f, 9.5f, 1.1f);
                for (int k = 0; k < 4; k++) z.punkt(6.5f + k * 3.7f, 12.5f, 1.1f);
                z.zug(8, 15.8f, 16, 15.8f);
            }
            case ZAHNRAD -> {
                z.kreis(12, 12, 3.1f);
                List<Float> l = new ArrayList<>();
                for (int k = 0; k < 8; k++) {
                    float m = k * 45f - 90f;
                    float[][] pt = {{7.4f, m - 18}, {9.9f, m - 9}, {9.9f, m + 9}, {7.4f, m + 18}};
                    for (float[] q : pt) {
                        double w = Math.toRadians(q[1]);
                        l.add(12 + q[0] * (float) Math.cos(w));
                        l.add(12 + q[0] * (float) Math.sin(w));
                    }
                }
                float[] p = new float[l.size()];
                for (int i = 0; i < p.length; i++) p[i] = l.get(i);
                z.breite = 1.8f;
                z.ring(p);
            }
            case GLOBUS -> {
                z.kreis(12, 12, 9.5f); z.ellipse(12, 12, 4.2f, 9.5f); z.zug(2.5f, 12, 21.5f, 12);
            }
            case NEUSTART -> {
                z.bogen(12, 12, 8, 215, 215 + 290);
                double w = Math.toRadians(215);
                float sx = 12 + 8 * (float) Math.cos(w), sy = 12 + 8 * (float) Math.sin(w);
                z.spitze(sx, sy, (float) Math.sin(w), (float) -Math.cos(w), 4.2f);
            }
            case PFEIL_RECHTS -> z.zug(9, 5.5f, 15.5f, 12, 9, 18.5f);
            case KREUZ -> { z.zug(6, 6, 18, 18); z.zug(18, 6, 6, 18); }
            case KOLBEN -> {
                z.zug(9.5f, 3, 9.5f, 9.5f, 4, 19.5f, 4.6f, 21, 19.4f, 21, 20, 19.5f, 14.5f, 9.5f, 14.5f, 3);
                z.zug(7.5f, 3, 16.5f, 3); z.zug(6.6f, 15.5f, 17.4f, 15.5f);
            }
            case WELT -> {
                // Wuerfel (Einzelspieler-Welt)
                z.ring(12, 2.5f, 20.5f, 7, 20.5f, 17, 12, 21.5f, 3.5f, 17, 3.5f, 7);
                z.zug(3.5f, 7, 12, 11.5f, 20.5f, 7); z.zug(12, 11.5f, 12, 21.5f);
            }
            case SERVER -> {
                z.rund(3, 3.5f, 18, 7, 2); z.rund(3, 13.5f, 18, 7, 2);
                z.punkt(7, 7, 1.2f); z.punkt(7, 17, 1.2f);
            }
            case WELLEN -> {
                z.bogen(12, 18, 3, 225, 315); z.bogen(12, 18, 8, 225, 315); z.bogen(12, 18, 13, 230, 310);
                z.punkt(12, 18.5f, 1.4f);
            }
            case BILD -> {
                z.rund(3, 4, 18, 16, 2.5f); z.kreis(9, 9.5f, 1.8f); z.zug(21, 15, 16, 10.5f, 5, 20);
            }
            case PERSON -> { z.kreis(12, 8, 4); z.bogen(12, 22, 8.5f, 205, 335); }
            case AUSGANG -> {
                z.zug(9.5f, 3.5f, 5.5f, 3.5f, 4, 5, 4, 19, 5.5f, 20.5f, 9.5f, 20.5f);
                z.zug(10, 12, 20.5f, 12); z.spitze(20.5f, 12, 1, 0, 4.5f);
            }
            case PLUS -> { z.zug(12, 5, 12, 19); z.zug(5, 12, 19, 12); }
            case SUCHE -> { z.kreis(10.5f, 10.5f, 6.5f); z.zug(15.5f, 15.5f, 20.5f, 20.5f); }
            case STERN -> z.ring(12, 2.5f, 14.8f, 8.8f, 21.5f, 9.4f, 16.4f, 13.8f, 18, 20.6f, 12, 17, 6, 20.6f, 7.6f, 13.8f, 2.5f, 9.4f, 9.2f, 8.8f);
            case HAKEN -> z.zug(5, 12.5f, 10, 17.5f, 19.5f, 7);
            case NOTE -> {
                // Zwei Achtelnoten mit Balken
                z.punkt(7, 18, 3); z.punkt(17, 16, 3);
                z.zug(9.6f, 18, 9.6f, 5.5f, 19.6f, 3.5f, 19.6f, 16);
                z.zug(9.6f, 9, 19.6f, 7);
            }
            case PLAY -> z.flaeche(7, 4.5f, 19.5f, 12, 7, 19.5f);
            case PAUSE -> { z.rundVoll(6, 4.5f, 4.5f, 15, 1.2f); z.rundVoll(13.5f, 4.5f, 4.5f, 15, 1.2f); }
            case VOR -> { z.flaeche(4.5f, 5, 15, 12, 4.5f, 19); z.rundVoll(16, 5, 3.2f, 14, 1); }
            case ZURUECK -> { z.flaeche(19.5f, 5, 9, 12, 19.5f, 19); z.rundVoll(4.8f, 5, 3.2f, 14, 1); }
            case KOPFHOERER -> {
                z.bogen(12, 13, 8, 180, 360);
                z.rundVoll(3, 13, 4.5f, 7.5f, 1.8f); z.rundVoll(16.5f, 13, 4.5f, 7.5f, 1.8f);
            }
        }
        return z;
    }

    /** Symbol in n x n Pixeln (weisse Maske). */
    public static Masken.Maske maske(Symbol s, int n) {
        return Masken.hole("sym_" + s.name() + "_" + n, n, n, false, rasterer(zeichnung(s), n));
    }

    private static Masken.Deckung rasterer(Zeichnung z, int n) {
        float k = n / 24f;
        // Linienstaerke nie unter gut einem Pixel, sonst wird sie fleckig
        float[] dek = new float[n * n];
        for (Teil t : z.teile) {
            float[] p = new float[t.p.length];
            for (int i = 0; i < p.length; i++) p[i] = t.p[i] * k;
            float halb = Math.max(0.6f, t.breite * k / 2f);
            int pn = p.length / 2;
            for (int y = 0; y < n; y++) {
                for (int x = 0; x < n; x++) {
                    float px = x + 0.5f, py = y + 0.5f;
                    float d = Float.MAX_VALUE;
                    int seg = t.zu ? pn : pn - 1;
                    for (int i = 0; i < seg; i++) {
                        int j = (i + 1) % pn;
                        d = Math.min(d, abstandStrecke(px, py, p[i * 2], p[i * 2 + 1], p[j * 2], p[j * 2 + 1]));
                    }
                    float a;
                    if (t.flaeche) {
                        boolean innen = innen(px, py, p);
                        a = innen ? Masken.klemme(0.5f + d) : Masken.klemme(0.5f - d);
                    } else {
                        a = Masken.klemme(halb + 0.5f - d);
                    }
                    int idx = y * n + x;
                    if (a > dek[idx]) dek[idx] = a;
                }
            }
        }
        return (x, y) -> dek[y * n + x];
    }

    private static float abstandStrecke(float px, float py, float ax, float ay, float bx, float by) {
        float vx = bx - ax, vy = by - ay;
        float l2 = vx * vx + vy * vy;
        float t = l2 < 1e-6f ? 0f : Masken.klemme(((px - ax) * vx + (py - ay) * vy) / l2);
        float dx = px - (ax + vx * t), dy = py - (ay + vy * t);
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    private static boolean innen(float px, float py, float[] p) {
        boolean in = false;
        int n = p.length / 2;
        for (int i = 0, j = n - 1; i < n; j = i++) {
            float xi = p[i * 2], yi = p[i * 2 + 1], xj = p[j * 2], yj = p[j * 2 + 1];
            if ((yi > py) != (yj > py) && px < (xj - xi) * (py - yi) / (yj - yi) + xi) in = !in;
        }
        return in;
    }
}
