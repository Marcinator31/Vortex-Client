package com.vortex.client.gui.menu;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Kleine Pixel-Symbole fuer die Menue-Leiste, direkt mit fill() gezeichnet --
 * keine Texturen, damit es in jeder Minecraft-Version gleich aussieht.
 * '#' = Pixel, alles andere = leer. Ein Pixel ist ein GUI-Pixel.
 */
public final class MenuSymbole {
    private MenuSymbole() {}

    /** Funkwellen: Welt fuer Freunde oeffnen. */
    public static final String[] HOST = {
            "######....",
            "......##..",
            "####....#.",
            "....##...#",
            "##....#..#",
            "##....#..#",
    };
    /** Zwei Personen: Freunde. */
    public static final String[] SOCIAL = {
            ".##....##.",
            ".##....##.",
            "..........",
            "####..####",
            "####..####",
    };
    /** T-Shirt: Skin-Garderobe. */
    public static final String[] WARDROBE = {
            "###..###",
            "########",
            "########",
            ".######.",
            ".######.",
            ".######.",
    };
    /** Bilderrahmen mit Bergen: Screenshots. */
    public static final String[] PICTURES = {
            "##########",
            "#........#",
            "#.....#..#",
            "#..#.###.#",
            "#.########",
            "##########",
    };
    /** Zwei Regler: Einstellungen. */
    public static final String[] SETTINGS = {
            "...#......",
            "##########",
            "...#......",
            "......#...",
            "##########",
            "......#...",
    };
    /** Kopf mit Schultern: Konto. */
    public static final String[] ACCOUNT = {
            "...####...",
            "...####...",
            "...####...",
            "..........",
            ".########.",
            "##########",
    };

    public static int breite(String[] s) { return s.length == 0 ? 0 : s[0].length(); }
    public static int hoehe(String[] s) { return s.length; }

    public static void zeichnen(GuiGraphicsExtractor g, String[] s, int x, int y, int farbe) {
        for (int r = 0; r < s.length; r++) {
            String zeile = s[r];
            int c = 0;
            while (c < zeile.length()) {
                if (zeile.charAt(c) != '#') { c++; continue; }
                // zusammenhaengende Pixel einer Zeile in einem fill()
                int ende = c;
                while (ende < zeile.length() && zeile.charAt(ende) == '#') ende++;
                g.fill(x + c, y + r, x + ende, y + r + 1, farbe);
                c = ende;
            }
        }
    }
}
