package com.vortex.client.gui;

import com.vortex.client.module.Module;
import com.vortex.client.module.ModuleManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * Startbildschirm des Clients (Rechtsshift).
 *
 * AUFBAU
 *   Ein zentriertes Glasfeld: oben Logo und Name, darunter die Eintraege
 *   in zwei Gruppen -- "Main" (Mods, Bots) und "Tools" (Waypoints, Macros,
 *   Wardrobe, Keybinds, HUD Editor) --, ganz unten abgesetzt der Neustart.
 *   Hinter dem Feld schweben zwei weiche Lichtflecken in Violett und Blau.
 *
 * BEWEGUNG
 *   - Die Eintraege erscheinen beim Oeffnen nacheinander und gleiten von
 *     links herein.
 *   - Die Hervorhebung GLEITET zum Eintrag unter dem Zeiger, statt von
 *     Zeile zu Zeile zu springen.
 *   - Die Lichtflecken bewegen sich langsam -- kaum merklich, aber das Bild
 *     wirkt dadurch lebendig statt eingefroren.
 *
 * GROESSE
 *   Alles wird jedes Bild aus der aktuellen Fenstergroesse berechnet. Bei
 *   kleinem Fenster (Minecraft verkleinert bis auf 320 x 240) wechselt das
 *   Feld in eine kompakte Form: Logo neben dem Namen, keine
 *   Gruppenueberschriften, niedrigere Zeilen.
 *
 * SYMBOLE
 *   Gezeichnet aus 7x7-Punktmustern, nicht aus Sonderzeichen. Sonderzeichen
 *   hat Minecrafts Schrift nicht; sie kamen aus einer Ersatzschrift und
 *   sahen unscharf aus. Gezeichnete Punkte sind pixelgenau.
 */
public class HomeScreen extends Screen {

    private static final Identifier LOGO =
            Identifier.fromNamespaceAndPath("vortexclient", "logo");

    // --- Eintraege ------------------------------------------------------------
    private static final int MODS = 0, BOTS = 1, WAYPOINTS = 2, MACROS = 3,
            WARDROBE = 4, KEYS = 5, HUD = 6, RESTART = 7;
    private static final String[] NAMEN = {
        "Mods", "Bots", "Waypoints", "Macros", "Wardrobe", "Keybinds", "HUD Editor", "Restart Game"
    };

    /**
     * Symbole als 7x7-Punktmuster, eines je Eintrag, in derselben Reihenfolge.
     * '1' = Punkt, '.' = leer.
     */
    private static final String[][] SYMBOLE = {
        { // Mods: vier Kacheln
            "111.111", "111.111", "111.111", ".......", "111.111", "111.111", "111.111" },
        { // Bots: Roboterkopf
            "...1...", ".11111.", ".1.1.1.", ".11111.", ".11111.", ".11111.", ".1...1." },
        { // Waypoints: Kartennadel
            "..111..", ".11111.", "11...11", "11...11", ".11111.", "..111..", "...1..." },
        { // Macros: Abspielen
            ".1.....", ".11....", ".111...", ".1111..", ".111...", ".11....", ".1....." },
        { // Wardrobe: Kleiderbuegel
            "..11...", ".1..1..", "....1..", "...1...", ".11.11.", "1.....1", "1111111" },
        { // Keybinds: Schluessel
            ".......", ".......", "111....", "1.11111", "111.1.1", ".......", "......." },
        { // HUD Editor: Fenster
            "1111111", "1111111", "1.....1", "1.11..1", "1.....1", "1....11", "1111111" },
        { // Restart: Kreispfeil
            "..111.1", ".1...11", "1...111", "1......", "1.....1", ".1...1.", "..111.." },
    };

    // --- Zustand --------------------------------------------------------------
    private int mx, my;
    private long letzteZeit = 0;
    private float oeffnen = 0f;
    private float seit = 0f;

    /** Gleitende Hervorhebung: Lage, Deckkraft, ob schon einmal gesetzt. */
    private float hlY = 0f, hlA = 0f;
    private boolean hlGesetzt = false;
    private int hlZiel = -1;

    private boolean frageNeustart = false;
    private float frageA = 0f;
    private String fehler = null;

    /** Klickflaechen je Eintrag, jedes Bild neu gesetzt. */
    private final int[][] flaeche = new int[8][];
    private int[] kJa, kNein;

    public HomeScreen() {
        super(Component.literal("Vortex Client"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ======================================================================
    // Zeichnen
    // ======================================================================

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        this.mx = mouseX;
        this.my = mouseY;
        long jetzt = System.nanoTime();
        float dt = letzteZeit == 0 ? 0.016f : Math.min(0.1f, (jetzt - letzteZeit) / 1e9f);
        letzteZeit = jetzt;
        oeffnen = weich(oeffnen, 1f, 9f, dt);
        seit += dt;
        float a = oeffnen;

        // --- Hintergrund ---------------------------------------------------
        ctx.fill(0, 0, this.width, this.height, VortexStyle.fade(0xB0060409, a));
        // Zwei weiche Lichtflecken, die sich langsam bewegen.
        float t = seit * 0.35f;
        licht(ctx, (int) (this.width * 0.30f + Math.sin(t) * 18f),
                (int) (this.height * 0.32f + Math.cos(t * 0.8f) * 12f),
                Math.max(60, this.height / 3), VortexStyle.VIOLETT, a);
        licht(ctx, (int) (this.width * 0.70f + Math.cos(t * 0.9f) * 18f),
                (int) (this.height * 0.70f + Math.sin(t * 0.7f) * 12f),
                Math.max(60, this.height / 3), VortexStyle.BLAU, a);

        // --- Masse aus der Fenstergroesse ----------------------------------
        int verfuegbar = this.height - 16;
        boolean kompakt = this.height < 330;
        int logo = kompakt ? 18 : clamp((int) (this.height * 0.12f), 36, 56);
        int kopfH = kompakt ? 24 : logo + 28;
        int gruppenH = kompakt ? 0 : 2 * 14;
        int rest = verfuegbar - 10 - kopfH - 8 - gruppenH - 6 - 10;
        int zeileH = clamp(rest / 8 - 2, 16, 24);
        int feldB = Math.min(250, this.width - 16);
        int feldH = 10 + kopfH + 8 + gruppenH + 8 * (zeileH + 2) + 6 + 10;
        int fx = (this.width - feldB) / 2;
        // Beim Oeffnen gleitet das Feld sanft von unten in seine Lage.
        int fy = (this.height - feldH) / 2 + (int) ((1f - a) * 14f);

        // --- Glasfeld ------------------------------------------------------
        VortexStyle.schatten(ctx, fx, fy, feldB, feldH, a);
        rund(ctx, fx, fy, feldB, feldH, VortexStyle.fade(0xF20E0B16, a), 6);
        VortexStyle.akzentLinie(ctx, fx + 6, fy, feldB - 12, a);
        // Lichtkante direkt unter der Akzentlinie
        ctx.fill(fx + 6, fy + 2, fx + feldB - 6, fy + 3,
                VortexStyle.fade(VortexStyle.mix(0xFF0E0B16, VortexStyle.TEXT, 0.06f), a));

        // --- Kopf ------------------------------------------------------------
        int cy = fy + 10;
        String name = "Vortex Client";
        if (kompakt) {
            int nw = logo + 6 + this.font.width(name);
            int lx = fx + (feldB - nw) / 2;
            zeichneLogo(ctx, lx, cy + (kopfH - logo) / 2 - 2, logo, a);
            ctx.text(this.font, Component.literal(name), lx + logo + 6, cy + (kopfH - 8) / 2 - 2,
                    VortexStyle.fade(VortexStyle.TEXT, a), false);
        } else {
            zeichneLogo(ctx, fx + (feldB - logo) / 2, cy, logo, a);
            ctx.text(this.font, Component.literal(name),
                    fx + (feldB - this.font.width(name)) / 2, cy + logo + 8,
                    VortexStyle.fade(VortexStyle.TEXT, a), false);
        }
        cy += kopfH + 8;

        // --- Eintraege -------------------------------------------------------
        int ex = fx + 10, ew = feldB - 20;
        int[] ypos = new int[8];
        for (int i = 0; i < 8; i++) {
            if (!kompakt && i == MODS) {
                gruppe(ctx, "MAIN", ex, cy, ew, a);
                cy += 14;
            }
            if (!kompakt && i == WAYPOINTS) {
                gruppe(ctx, "TOOLS", ex, cy, ew, a);
                cy += 14;
            }
            if (i == RESTART) cy += 6;
            ypos[i] = cy;
            flaeche[i] = new int[]{ex, cy, ew, zeileH};
            cy += zeileH + 2;
        }

        // Welcher Eintrag liegt unter dem Zeiger?
        int ziel = -1;
        if (!frageNeustart) {
            for (int i = 0; i < 8; i++) {
                if (in(flaeche[i])) { ziel = i; break; }
            }
        }

        // Gleitende Hervorhebung
        if (ziel >= 0) {
            if (!hlGesetzt) { hlY = ypos[ziel]; hlGesetzt = true; }
            hlZiel = ziel;
            hlY = weich(hlY, ypos[ziel], 22f, dt);
        }
        hlA = weich(hlA, ziel >= 0 ? 1f : 0f, 16f, dt);
        if (hlA > 0.01f && hlZiel >= 0) {
            boolean rot = hlZiel == RESTART;
            int grund = rot ? VortexStyle.mix(VortexStyle.CARD, 0xFFB91C1C, 0.30f)
                            : VortexStyle.mix(VortexStyle.CARD, VortexStyle.akzent(0.3f), 0.28f);
            rund(ctx, ex, (int) hlY, ew, zeileH, VortexStyle.fade(grund, a * hlA), 3);
            // Balken links im Akzentverlauf
            int bar = rot ? 0xFFF87171 : VortexStyle.akzent(0.2f);
            ctx.fill(ex, (int) hlY + 4, ex + 2, (int) hlY + zeileH - 4, VortexStyle.fade(bar, a * hlA));
        }

        // Zeilen selbst -- gestaffelt hereingleitend
        int aktiv = 0, alle = 0;
        for (Module m : ModuleManager.INSTANCE.getModules()) {
            if (m.getCategory() == Module.Category.BOTS) continue;
            alle++;
            if (m.isEnabled()) aktiv++;
        }
        for (int i = 0; i < 8; i++) {
            float st = clamp01((seit - 0.08f - i * 0.035f) / 0.28f);
            float e = 1f - (1f - st) * (1f - st) * (1f - st);
            int dx = (int) ((1f - e) * -10f);
            float al = a * e;
            boolean hov = i == ziel;
            boolean rot = i == RESTART;

            int y = ypos[i];
            int symFarbe = rot ? (hov ? 0xFFFCA5A5 : 0xFF9E6B7A)
                               : (hov ? 0xFFFFFFFF : VortexStyle.akzent(0.35f));
            symbol(ctx, SYMBOLE[i], ex + 9 + dx, y + (zeileH - 7) / 2, VortexStyle.fade(symFarbe, al));

            int txt = rot ? (hov ? 0xFFFCA5A5 : VortexStyle.TEXT_DIM)
                          : (hov ? 0xFFFFFFFF : (i == MODS ? VortexStyle.TEXT : VortexStyle.mix(VortexStyle.TEXT_DIM, VortexStyle.TEXT, 0.45f)));
            ctx.text(this.font, Component.literal(NAMEN[i]), ex + 24 + dx, y + (zeileH - 8) / 2,
                    VortexStyle.fade(txt, al), false);

            // Rechts: bei Mods die Zahl aktiver Module, sonst ein Pfeil beim
            // Ueberfahren.
            if (i == MODS) {
                String z = aktiv + "/" + alle;
                ctx.text(this.font, Component.literal(z), ex + ew - 8 - this.font.width(z) + dx,
                        y + (zeileH - 8) / 2, VortexStyle.fade(VortexStyle.akzent(0.5f), al), false);
            } else if (hov && !rot) {
                ctx.text(this.font, Component.literal(">"), ex + ew - 12, y + (zeileH - 8) / 2,
                        VortexStyle.fade(VortexStyle.akzent(0.5f), al * hlA), false);
            }
        }

        // --- Unter dem Feld ------------------------------------------------
        if (fehler != null) {
            String f = fehler;
            ctx.text(this.font, Component.literal(f), (this.width - this.font.width(f)) / 2,
                    Math.min(this.height - 22, fy + feldH + 8), VortexStyle.fade(0xFFF87171, a), false);
        }
        String hinweis = "ESC to close";
        ctx.text(this.font, Component.literal(hinweis), (this.width - this.font.width(hinweis)) / 2,
                this.height - 11, VortexStyle.fade(VortexStyle.TEXT_DIM, a * 0.6f), false);

        // --- Rueckfrage ueber allem ------------------------------------------
        frageA = weich(frageA, frageNeustart ? 1f : 0f, 14f, dt);
        if (frageA > 0.01f) zeichneRueckfrage(ctx, a * frageA);
        else { kJa = null; kNein = null; }
    }

    /**
     * Rueckfrage vor dem Neustart.
     *
     * Ein Neustart schliesst das Spiel -- ein versehentlicher Klick darf das
     * nicht ausloesen. Sie blendet weich ein, statt aufzuploppen.
     */
    private void zeichneRueckfrage(GuiGraphicsExtractor ctx, float a) {
        ctx.fill(0, 0, this.width, this.height, VortexStyle.fade(0xB0000000, a));
        int w = Math.min(250, this.width - 20), h = 92;
        int x = (this.width - w) / 2, y = (this.height - h) / 2 + (int) ((1f - a) * 10f);
        VortexStyle.schatten(ctx, x, y, w, h, a);
        rund(ctx, x, y, w, h, VortexStyle.fade(0xF20E0B16, a), 6);
        ctx.fill(x + 6, y, x + w - 6, y + 2, VortexStyle.fade(0xFFF87171, a * 0.8f));

        String t1 = "Restart your game?";
        ctx.text(this.font, Component.literal(t1), (this.width - this.font.width(t1)) / 2, y + 16,
                VortexStyle.fade(VortexStyle.TEXT, a), false);
        String t2 = "Minecraft closes and starts again.";
        ctx.text(this.font, Component.literal(t2), (this.width - this.font.width(t2)) / 2, y + 30,
                VortexStyle.fade(VortexStyle.TEXT_DIM, a), false);

        int kb = (w - 16 * 2 - 10) / 2;
        kNein = knopf(ctx, x + 16, y + h - 34, kb, 22, "Cancel", false, a);
        kJa = knopf(ctx, x + w - 16 - kb, y + h - 34, kb, 22, "Restart", true, a);
    }

    private int[] knopf(GuiGraphicsExtractor ctx, int x, int y, int w, int h,
                        String text, boolean warnung, float a) {
        boolean hov = mx >= x && mx < x + w && my >= y && my < y + h;
        int grund = warnung
                ? VortexStyle.mix(0xFF3A1418, 0xFFB91C1C, hov ? 0.55f : 0.30f)
                : (hov ? VortexStyle.HOV : VortexStyle.CARD);
        rund(ctx, x, y, w, h, VortexStyle.fade(grund, a), 4);
        int tw = this.font.width(text);
        ctx.text(this.font, Component.literal(text), x + (w - tw) / 2, y + (h - 8) / 2,
                VortexStyle.fade(warnung || hov ? 0xFFFFFFFF : VortexStyle.TEXT, a), false);
        return new int[]{x, y, w, h};
    }

    // ======================================================================
    // Bedienung
    // ======================================================================

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent click, boolean doubled) {
        if (super.mouseClicked(click, doubled)) return true;
        if (click.button() != 0) return false;
        Minecraft mc = Minecraft.getInstance();

        // Rueckfrage hat Vorrang: solange sie offen ist, zaehlen nur ihre
        // beiden Knoepfe.
        if (frageNeustart) {
            if (in(kJa)) {
                frageNeustart = false;
                try {
                    com.vortex.client.util.GameRestarter.restart();
                } catch (Throwable pvpErr) {
                    // Der Neustart prueft selbst, ob der neue Prozess ueberlebt.
                    // Scheitert er, bleibt das Spiel offen und zeigt den Grund.
                    fehler = "Restart failed: "
                            + (pvpErr.getMessage() == null ? pvpErr.getClass().getSimpleName()
                                                           : pvpErr.getMessage());
                    com.vortex.client.core.Errors.report("HomeScreen.restart", pvpErr);
                }
                return true;
            }
            if (in(kNein)) { frageNeustart = false; return true; }
            return true;
        }

        // Alle Unterseiten bekommen diesen Bildschirm als Eltern -- ESC fuehrt
        // also hierher zurueck, nicht ins Spiel.
        if (in(flaeche[MODS]))      { mc.gui.setScreen(new PanelGui()); return true; }
        if (in(flaeche[BOTS]))      { mc.gui.setScreen(new PanelGui(Module.Category.BOTS)); return true; }
        if (in(flaeche[WAYPOINTS])) { mc.gui.setScreen(new WaypointScreen(this)); return true; }
        if (in(flaeche[MACROS]))    { mc.gui.setScreen(new MacroScreen(this)); return true; }
        if (in(flaeche[WARDROBE]))  { mc.gui.setScreen(new SkinScreen(this)); return true; }
        if (in(flaeche[KEYS]))      { mc.gui.setScreen(new KeyListScreen(this)); return true; }
        if (in(flaeche[HUD]))       { mc.gui.setScreen(new HudEditorScreen()); return true; }
        if (in(flaeche[RESTART]))   { frageNeustart = true; fehler = null; return true; }
        return false;
    }

    // ======================================================================
    // Hilfen
    // ======================================================================

    private boolean in(int[] k) {
        return k != null && mx >= k[0] && mx < k[0] + k[2] && my >= k[1] && my < k[1] + k[3];
    }

    private void zeichneLogo(GuiGraphicsExtractor ctx, int x, int y, int g, float a) {
        // Schein hinter dem Logo
        for (int i = 1; i <= 3; i++) {
            int al = (int) (18f / i * a);
            rund(ctx, x - i * 2, y - i * 2, g + i * 4, g + i * 4,
                    (al << 24) | (VortexStyle.VIOLETT & 0x00FFFFFF), Math.max(2, g / 5));
        }
        try {
            ctx.blitSprite(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,
                    LOGO, x, y, g, g);
        } catch (Throwable ignored) {
            // Sprite nicht ladbar -- dann bleibt der Schein allein.
        }
    }

    /** Gruppenueberschrift mit einer duennen Linie bis zum rechten Rand. */
    private void gruppe(GuiGraphicsExtractor ctx, String name, int x, int y, int w, float a) {
        ctx.text(this.font, Component.literal(name), x + 2, y + 3,
                VortexStyle.fade(VortexStyle.mix(VortexStyle.TEXT_DIM, VortexStyle.LINE, 0.25f), a), false);
        int lx = x + 8 + this.font.width(name);
        if (x + w > lx) {
            ctx.fill(lx, y + 7, x + w, y + 8, VortexStyle.fade(VortexStyle.LINE, a * 0.8f));
        }
    }

    /**
     * Zeichnet ein 7x7-Punktmuster.
     *
     * Aufeinanderfolgende Punkte einer Zeile werden zu EINEM Rechteck
     * zusammengefasst -- so sind es je Symbol rund zehn Aufrufe statt bis zu
     * 49.
     */
    private static void symbol(GuiGraphicsExtractor ctx, String[] muster, int x, int y, int farbe) {
        for (int r = 0; r < muster.length; r++) {
            String z = muster[r];
            int c = 0;
            while (c < z.length()) {
                if (z.charAt(c) != '1') { c++; continue; }
                int start = c;
                while (c < z.length() && z.charAt(c) == '1') c++;
                ctx.fill(x + start, y + r, x + c, y + r + 1, farbe);
            }
        }
    }

    /**
     * Weicher Lichtfleck: drei Kreisschichten, jede aus 12 waagerechten
     * Streifen -- 36 Aufrufe, fuer das Auge ein runder, weicher Schein.
     */
    private static void licht(GuiGraphicsExtractor ctx, int cx, int cy, int radius, int farbe, float a) {
        float[] schichten = {1.0f, 0.68f, 0.40f};
        for (float s : schichten) {
            int r = Math.max(4, (int) (radius * s));
            int al = (int) (14 * a);
            int c = (al << 24) | (farbe & 0x00FFFFFF);
            int streifen = 12;
            for (int b = 0; b < streifen; b++) {
                int y0 = cy - r + (2 * r) * b / streifen;
                int y1 = cy - r + (2 * r) * (b + 1) / streifen;
                float ym = (y0 + y1) / 2f - cy;
                int halb = (int) Math.sqrt(Math.max(0f, r * r - ym * ym));
                if (halb > 0 && y1 > y0) ctx.fill(cx - halb, y0, cx + halb, y1, c);
            }
        }
    }

    private static void rund(GuiGraphicsExtractor ctx, int x, int y, int w, int h, int c, int r) {
        if (w <= 0 || h <= 0) return;
        r = Math.min(r, Math.min(w / 2, h / 2));
        ctx.fill(x, y + r, x + w, y + h - r, c);
        ctx.fill(x + r, y, x + w - r, y + r, c);
        ctx.fill(x + r, y + h - r, x + w - r, y + h, c);
        for (int i = 0; i < r; i++) {
            int ein = r - i - 1;
            ctx.fill(x + ein, y + i, x + r, y + i + 1, c);
            ctx.fill(x + w - r, y + i, x + w - ein, y + i + 1, c);
            ctx.fill(x + ein, y + h - i - 1, x + r, y + h - i, c);
            ctx.fill(x + w - r, y + h - i - 1, x + w - ein, y + h - i, c);
        }
    }

    /** Weiche, bildratenunabhaengige Annaeherung -- wie ueberall im Client. */
    private static float weich(float ist, float ziel, float tempo, float dt) {
        float f = 1f - (float) Math.exp(-tempo * dt);
        float neu = ist + (ziel - ist) * f;
        return Math.abs(ziel - neu) < 0.002f ? ziel : neu;
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }
}
