package com.vortex.client.cosmetics;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Die Huete -- aus farbigen Quadern im Minecraft-Pixelmass gebaut (ein Pixel
 * = 1/16 Block, wie beim Spielerkopf). Keine Texturen: so sehen sie in jeder
 * Version gleich aus und lassen sich leicht ergaenzen.
 *
 * Koordinaten je Quader in Pixeln, Mitte des Kopfes = 0:
 *   x links/rechts, z vorne(-)/hinten(+), y nach OBEN ab der Kopfoberkante.
 * Der Kopf ist 8 x 8 Pixel gross (x und z von -4 bis 4).
 */
public final class Huete {
    private Huete() {}

    /** Ein Quader: von (x0,y0,z0) bis (x1,y1,z1), Farbe ARGB, leuchtet = immer hell. */
    public record Quader(float x0, float y0, float z0, float x1, float y1, float z1, int farbe, boolean leuchtet) {}

    public record Hut(String id, String name, List<Quader> teile) {}

    private static final Map<String, Hut> ALLE = new LinkedHashMap<>();

    public static Map<String, Hut> alle() { return ALLE; }
    public static Hut get(String id) { return id == null ? null : ALLE.get(id); }

    // ---- Bauhilfen -----------------------------------------------------------

    private static final class Bau {
        final List<Quader> q = new ArrayList<>();
        Bau box(float x0, float y0, float z0, float x1, float y1, float z1, int farbe) {
            q.add(new Quader(x0, y0, z0, x1, y1, z1, farbe, false));
            return this;
        }
        Bau licht(float x0, float y0, float z0, float x1, float y1, float z1, int farbe) {
            q.add(new Quader(x0, y0, z0, x1, y1, z1, farbe, true));
            return this;
        }
        /** Zentrierter Block mit Breite b (x und z) von Hoehe y0 bis y1. */
        Bau mitte(float b, float y0, float y1, int farbe) {
            return box(-b / 2, y0, -b / 2, b / 2, y1, b / 2, farbe);
        }
        /** Rahmen (nur der Rand) mit Aussenbreite b und Dicke d. */
        Bau ring(float b, float d, float y0, float y1, int farbe, boolean leuchtet) {
            float a = b / 2, i = a - d;
            List<Quader> r = List.of(
                    new Quader(-a, y0, -a, a, y1, -i, farbe, leuchtet),
                    new Quader(-a, y0, i, a, y1, a, farbe, leuchtet),
                    new Quader(-a, y0, -i, -i, y1, i, farbe, leuchtet),
                    new Quader(i, y0, -i, a, y1, i, farbe, leuchtet));
            q.addAll(r);
            return this;
        }
        void fertig(String id, String name) { ALLE.put(id, new Hut(id, name, List.copyOf(q))); }
    }

    static {
        // Zylinder: breite Krempe, rotes Band, hoher schwarzer Koerper
        new Bau()
                .mitte(11, 0, 0.75f, 0xFF1A1A1F)
                .mitte(7, 0.75f, 2, 0xFFB3202A)
                .mitte(7, 2, 7.5f, 0xFF1F1F26)
                .mitte(7.4f, 7.5f, 8, 0xFF2A2A33)
                .fertig("top_hat", "Top Hat");

        // Krone: goldener Reif mit Zacken und Edelsteinen
        Bau krone = new Bau().ring(9, 1, 0, 2, 0xFFF2C230, false);
        for (float[] z : new float[][] { {-4.5f, -4.5f}, {3.5f, -4.5f}, {-4.5f, 3.5f}, {3.5f, 3.5f}, {-0.5f, -4.5f}, {-0.5f, 3.5f}, {-4.5f, -0.5f}, {3.5f, -0.5f} }) {
            krone.box(z[0], 2, z[1], z[0] + 1, 3.5f, z[1] + 1, 0xFFF7D046);
        }
        krone.licht(-0.75f, 0.5f, -4.75f, 0.75f, 1.75f, -4.4f, 0xFFE0234E)    // Rubin vorne
                .licht(-4.75f, 0.5f, -0.75f, -4.4f, 1.75f, 0.75f, 0xFF2F7DE1)  // Saphir links
                .licht(4.4f, 0.5f, -0.75f, 4.75f, 1.75f, 0.75f, 0xFF2F7DE1)    // Saphir rechts
                .fertig("crown", "Crown");

        // Partyhut: gestreifter Kegel mit Bommel
        new Bau()
                .mitte(7, 0, 1.5f, 0xFF8B5CF6)
                .mitte(6, 1.5f, 3, 0xFF3B82F6)
                .mitte(4.5f, 3, 4.5f, 0xFF8B5CF6)
                .mitte(3, 4.5f, 6, 0xFF3B82F6)
                .mitte(1.6f, 6, 7.5f, 0xFF8B5CF6)
                .mitte(2.4f, 7.5f, 9.5f, 0xFFF5F5FA)
                .fertig("party_hat", "Party Hat");

        // Heiligenschein: leuchtender Ring ueber dem Kopf
        new Bau().ring(8.5f, 1.25f, 3, 4, 0xFFFFE070, true).fertig("halo", "Halo");

        // Katzenohren
        new Bau()
                .box(-4, 0, -1.5f, -1.5f, 1.5f, 0.5f, 0xFF2B2B33).box(-3.5f, 1.5f, -1.25f, -2, 3, 0.25f, 0xFF2B2B33)
                .box(-3.25f, 0.5f, -1.6f, -2.25f, 2.25f, -1.4f, 0xFFF0A3C0)
                .box(1.5f, 0, -1.5f, 4, 1.5f, 0.5f, 0xFF2B2B33).box(2, 1.5f, -1.25f, 3.5f, 3, 0.25f, 0xFF2B2B33)
                .box(2.25f, 0.5f, -1.6f, 3.25f, 2.25f, -1.4f, 0xFFF0A3C0)
                .fertig("cat_ears", "Cat Ears");

        // Wikingerhelm: Kappe mit zwei gebogenen Hoernern
        new Bau()
                .mitte(9, -1, 1, 0xFF8A8F99)
                .mitte(8, 1, 2.5f, 0xFFA3A9B4)
                .mitte(4, 2.5f, 3.25f, 0xFFA3A9B4)
                .box(-6, 0, -1, -4.5f, 1.5f, 1, 0xFFEDE3C8).box(-7, 1, -0.75f, -5.75f, 3.5f, 0.75f, 0xFFEDE3C8).box(-7.5f, 3.5f, -0.5f, -6.5f, 5.5f, 0.5f, 0xFFD9CCA8)
                .box(4.5f, 0, -1, 6, 1.5f, 1, 0xFFEDE3C8).box(5.75f, 1, -0.75f, 7, 3.5f, 0.75f, 0xFFEDE3C8).box(6.5f, 3.5f, -0.5f, 7.5f, 5.5f, 0.5f, 0xFFD9CCA8)
                .fertig("viking", "Viking Helmet");

        // Weihnachtsmuetze: weisser Rand, roter Zipfel nach hinten, Bommel
        new Bau()
                .mitte(9, 0, 1.5f, 0xFFF5F5FA)
                .mitte(7.5f, 1.5f, 3.5f, 0xFFC8202B)
                .box(-2.5f, 3.5f, -1, 2.5f, 5, 4, 0xFFC8202B)
                .box(-1.5f, 4.5f, 3, 1.5f, 6, 6, 0xFFC8202B)
                .box(-1.25f, 4, 5.5f, 1.25f, 6.5f, 8, 0xFFF5F5FA)
                .fertig("santa", "Santa Hat");

        // Vortex-Kappe: Basecap in Vortex-Violett mit blauem Schirm
        new Bau()
                .mitte(8.5f, 0, 2.5f, 0xFF7C4DE8)
                .mitte(6, 2.5f, 3.25f, 0xFF8B5CF6)
                .box(-4.25f, 0, -8, 4.25f, 0.6f, -4.25f, 0xFF3B82F6)
                .licht(-1, 1, -4.4f, 1, 2, -4.25f, 0xFFFFFFFF)
                .fertig("vortex_cap", "Vortex Cap");
    }
}
