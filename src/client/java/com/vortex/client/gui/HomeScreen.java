package com.vortex.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * Startbildschirm des Clients.
 *
 * Oeffnet sich mit Rechtsshift VOR der Moduluebersicht -- wie bei grossen
 * PvP-Clients. In der Mitte das Logo, darunter die drei Hauptwege:
 *
 *   links   Spiel neu starten (mit Rueckfrage)
 *   Mitte   Mods -- oeffnet das ClickGUI
 *   rechts  Skin-Garderobe
 *
 * Darunter der HUD-Editor, um die Anzeigen am Bildschirmrand zu verschieben.
 *
 * ZUR BEDIENUNG: Nichts hiervon ist neu. Mods, Garderobe, HUD-Editor und
 * Neustart gab es schon; dieser Bildschirm ordnet sie nur an einer Stelle an,
 * statt direkt in die lange Modulliste zu springen.
 */
public class HomeScreen extends Screen {

    // --- Farben: dieselbe Welt wie im ClickGUI ------------------------------
    private static final int C_DIM     = 0xD2060409;
    private static final int C_CARD    = 0xFF15111F;
    private static final int C_CARD_HV = 0xFF1E1930;
    private static final int C_LINE    = 0xFF241E36;
    private static final int C_TEXT    = 0xFFF2F0F8;
    private static final int C_DIMTXT  = 0xFF7F7896;
    private static final int VIOLETT   = 0xFF8B5CF6;
    private static final int BLAU      = 0xFF3B82F6;

    private static final Identifier LOGO =
            Identifier.fromNamespaceAndPath("vortexclient", "logo");

    private int mx, my;
    private float dtLetzt = 0.016f;
    private float oeffnen = 0f;
    private long letzteZeit = 0;

    /** Laeuft gerade die Rueckfrage zum Neustart? */
    private boolean frageNeustart = false;
    /** Fehlertext, falls der Neustart scheiterte. */
    private String fehler = null;

    // Klickflaechen, im Zeichnen gesetzt -- so koennen sie nie von der
    // gezeichneten Flaeche abweichen.
    private int[] kMods, kGarderobe, kNeustart, kHud, kJa, kNein;
    private int[] kWaypoints, kMacros, kKeys, kTheme, kBots;

    public HomeScreen() {
        super(Component.literal("Vortex Client"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        this.mx = mouseX;
        this.my = mouseY;

        long jetzt = System.nanoTime();
        float dt = letzteZeit == 0 ? 0f : Math.min(0.1f, (jetzt - letzteZeit) / 1e9f);
        letzteZeit = jetzt;
        dtLetzt = dt;
        // Weiches Einblenden mit exponentieller Annaeherung -- dieselbe Form
        // wie im ClickGUI, damit beide Bildschirme gleich wirken.
        oeffnen += (1f - oeffnen) * (1f - (float) Math.exp(-9f * dt));
        if (1f - oeffnen < 0.005f) oeffnen = 1f;
        float a = oeffnen;
        // Inhalt gleitet beim Oeffnen von unten herein. Die Strecke schrumpft
        // mit dem Einblenden, endet also sanft statt abrupt.
        int gleiten = (int) ((1f - a) * 18f);

        ctx.fill(0, 0, this.width, this.height, fade(C_DIM, a));

        // --- Aufbau: Logo links, Menue rechts daneben ----------------------
        //
        // VOELLIG ANDERS ALS VORHER. Das Kachelraster wirkte wie eine
        // Webseite: alles gleich gross, alles gleich wichtig, nichts fuehrte
        // den Blick.
        //
        // Jetzt steht links gross das Logo mit dem Namen, rechts daneben eine
        // ruhige Liste. Der Blick geht von links nach rechts, und die
        // Reihenfolge der Eintraege sagt, was wichtig ist -- Mods oben.
        int logo = 84;
        int spalte = 220;                       // Breite der Menueliste
        int block = logo + 36 + spalte;         // Logo + Abstand + Liste
        int bx = this.width / 2 - block / 2;
        int by = this.height / 2 - 92 + gleiten;

        // Logo mit Schein
        int ly = by + 20;
        for (int i = 1; i <= 4; i++) {
            int al = (int) (20f / i * a);
            rund(ctx, bx - i * 3, ly - i * 3, logo + i * 6, logo + i * 6,
                    (al << 24) | (VIOLETT & 0x00FFFFFF), 14);
        }
        try {
            ctx.blitSprite(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,
                    LOGO, bx, ly, logo, logo);
        } catch (Throwable ignored) { }

        String zeile = "Vortex Client";
        ctx.text(this.font, Component.literal(zeile),
                bx, ly + logo + 12, fade(C_TEXT, a), false);
        String unter = "Right Shift";
        ctx.text(this.font, Component.literal(unter),
                bx, ly + logo + 24, fade(C_DIMTXT, a * 0.8f), false);

        // --- Menueliste rechts ---------------------------------------------
        //
        // Eintraege statt Kacheln: eine Zeile je Bereich, mit einem Balken
        // links, der beim Ueberfahren erscheint. Das ist ruhiger als acht
        // gleich grosse Kaesten und laesst sich mit einem Blick lesen.
        int lx = bx + logo + 36;
        int ly2 = by;
        int zh = 26;

        kMods      = eintrag(ctx, lx, ly2, spalte, zh, "Mods", true, a);  ly2 += zh + 4;
        kBots      = eintrag(ctx, lx, ly2, spalte, zh, "Bots", false, a); ly2 += zh + 4;
        ly2 += 6;
        trenner(ctx, lx, ly2, spalte, a); ly2 += 10;

        kWaypoints = eintrag(ctx, lx, ly2, spalte, zh, "Waypoints", false, a); ly2 += zh + 4;
        kMacros    = eintrag(ctx, lx, ly2, spalte, zh, "Macros", false, a);    ly2 += zh + 4;
        kGarderobe = eintrag(ctx, lx, ly2, spalte, zh, "Wardrobe", false, a);  ly2 += zh + 4;
        kKeys      = eintrag(ctx, lx, ly2, spalte, zh, "Keybinds", false, a);  ly2 += zh + 4;
        kHud       = eintrag(ctx, lx, ly2, spalte, zh, "HUD Editor", false, a);ly2 += zh + 4;
        ly2 += 6;
        trenner(ctx, lx, ly2, spalte, a); ly2 += 10;

        kNeustart  = eintragWarnung(ctx, lx, ly2, spalte, zh, "Restart Game", a);
        kTheme     = null;

        int cx = this.width / 2;
        int ky = ly2, kH = zh;

        // --- Fusszeile -------------------------------------------------------
        // Nur ESC: Rechtsshift oeffnet den Bildschirm, schliesst ihn aber nicht.
        String hinweis = "ESC to close";
        int hw = this.font.width(hinweis);
        ctx.text(this.font, Component.literal(hinweis),
                cx - hw / 2, this.height - 20, fade(C_DIMTXT, a * 0.7f), false);

        if (fehler != null) {
            int fw = this.font.width(fehler);
            ctx.text(this.font, Component.literal(fehler),
                    cx - fw / 2, ky + kH + 14, fade(0xFFF87171, a), false);
        }

        // --- Rueckfrage ueber allem ----------------------------------------
        if (frageNeustart) zeichneRueckfrage(ctx, a);
        else { kJa = null; kNein = null; }
    }

    /**
     * Rueckfrage vor dem Neustart.
     *
     * Ein Neustart schliesst das Spiel -- ein versehentlicher Klick darf das
     * nicht ausloesen. Deshalb diese zweite Stufe.
     */
    private void zeichneRueckfrage(GuiGraphicsExtractor ctx, float a) {
        ctx.fill(0, 0, this.width, this.height, fade(0xB0000000, a));
        int w = 260, h = 96;
        int x = this.width / 2 - w / 2, y = this.height / 2 - h / 2;
        rund(ctx, x, y, w, h, fade(C_CARD, a), 6);
        ctx.fill(x + 6, y, x + w - 6, y + 1, fade(mix(C_CARD, C_TEXT, 0.1f), a));

        String t1 = "Restart your game?";
        ctx.text(this.font, Component.literal(t1),
                this.width / 2 - this.font.width(t1) / 2, y + 16, fade(C_TEXT, a), false);
        String t2 = "Minecraft closes and starts again.";
        ctx.text(this.font, Component.literal(t2),
                this.width / 2 - this.font.width(t2) / 2, y + 30, fade(C_DIMTXT, a), false);

        kNein = knopf(ctx, x + 16, y + h - 36, 104, 24, "Cancel", false, a);
        kJa = knopf(ctx, x + w - 120, y + h - 36, 104, 24, "Restart", true, a);
    }

    /** Zeichnet einen Knopf und liefert seine Klickflaeche. */
    private int[] knopf(GuiGraphicsExtractor ctx, int x, int y, int w, int h,
                        String text, boolean haupt, float a) {
        boolean hov = mx >= x && mx < x + w && my >= y && my < y + h;
        if (haupt) {
            // Hauptknopf im Verlauf, mit Schein.
            for (int i = 1; i <= 3; i++) {
                int al = (int) ((hov ? 40f : 24f) / i * a);
                rund(ctx, x - i, y - i, w + i * 2, h + i * 2,
                        (al << 24) | (mix(VIOLETT, BLAU, 0.5f) & 0x00FFFFFF), 4);
            }
            // Ecken einzeln (wegen der Rundung), die Mitte in Baendern --
            // pixelweise waeren das ueber hundert Aufrufe je Bild.
            for (int i = 0; i < 3; i++) {
                int ein = 3 - i;
                int c1 = mix(VIOLETT, BLAU, i / (float) w);
                int c2 = mix(VIOLETT, BLAU, (w - 1 - i) / (float) w);
                if (hov) { c1 = mix(c1, 0xFFFFFFFF, 0.12f); c2 = mix(c2, 0xFFFFFFFF, 0.12f); }
                ctx.fill(x + i, y + ein, x + i + 1, y + h - ein, fade(c1, a));
                ctx.fill(x + w - 1 - i, y + ein, x + w - i, y + h - ein, fade(c2, a));
            }
            int mw = w - 6, baender = Math.max(1, (mw + 7) / 8);
            for (int b = 0; b < baender; b++) {
                int ax = x + 3 + mw * b / baender, bx = x + 3 + mw * (b + 1) / baender;
                int c = mix(VIOLETT, BLAU, ((ax + bx) / 2f - x) / w);
                if (hov) c = mix(c, 0xFFFFFFFF, 0.12f);
                ctx.fill(ax, y, bx, y + h, fade(c, a));
            }
        } else {
            rund(ctx, x, y, w, h, fade(hov ? C_CARD_HV : C_CARD, a), 4);
            ctx.fill(x + 4, y, x + w - 4, y + 1,
                    fade(mix(C_CARD, C_TEXT, hov ? 0.16f : 0.08f), a));
        }
        int tw = this.font.width(text);
        ctx.text(this.font, Component.literal(text),
                x + (w - tw) / 2, y + (h - 8) / 2,
                fade(haupt || hov ? 0xFFFFFFFF : C_TEXT, a), false);
        return new int[]{x, y, w, h};
    }

    private boolean in(int[] k) {
        return k != null && mx >= k[0] && mx < k[0] + k[2] && my >= k[1] && my < k[1] + k[3];
    }

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
                    // Der Neustart prueft jetzt selbst, ob der neue Prozess
                    // ueberlebt. Scheitert er, bleibt das Spiel offen und wir
                    // zeigen den Grund -- statt abzustuerzen.
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

        // Das klassische Spaltenmenue: jede Kategorie eine Spalte, alles
        // auf einen Blick. Das bisherige Menue (ClickGui) bleibt im Code
        // erhalten, wird hier aber nicht mehr geoeffnet.
        if (in(kMods)) { mc.gui.setScreen(new PanelGui()); return true; }
        // Alle Unterseiten bekommen diesen Bildschirm als Eltern -- ESC fuehrt
        // also hierher zurueck, nicht ins Spiel.
        if (in(kWaypoints)) { mc.gui.setScreen(new WaypointScreen(this)); return true; }
        if (in(kMacros))    { mc.gui.setScreen(new MacroScreen(this)); return true; }
        if (in(kKeys))      { mc.gui.setScreen(new KeyListScreen(this)); return true; }
        if (in(kBots)) {
            mc.gui.setScreen(new PanelGui(com.vortex.client.module.Module.Category.BOTS));
            return true;
        }
        if (in(kGarderobe)) { mc.gui.setScreen(new SkinScreen(this)); return true; }
        if (in(kHud)) { mc.gui.setScreen(new HudEditorScreen()); return true; }
        if (in(kNeustart)) { frageNeustart = true; fehler = null; return true; }
        return false;
    }

    // --- Hilfen --------------------------------------------------------------

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

    private static int fade(int argb, float f) {
        if (f >= 1f) return argb;
        if (f <= 0f) return argb & 0x00FFFFFF;
        int al = (int) (((argb >>> 24) & 0xFF) * f);
        return (al << 24) | (argb & 0x00FFFFFF);
    }

    private static int mix(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int aa = (a >>> 24) & 0xFF, ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int ba = (b >>> 24) & 0xFF, br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        return ((int) (aa + (ba - aa) * t) << 24) | ((int) (ar + (br - ar) * t) << 16)
                | ((int) (ag + (bg - ag) * t) << 8) | (int) (ab + (bb - ab) * t);
    }

    /**
     * Knopf fuer den Neustart, farblich abgesetzt.
     *
     * Der einzige Knopf, der das Spiel beendet -- deshalb ein roetlicher
     * Hauch beim Ueberfahren. Er sieht sonst aus wie die anderen, damit das
     * Raster ruhig bleibt; die Warnung kommt erst, wenn man ihn ansteuert.
     */
    private int[] knopfWarnung(GuiGraphicsExtractor ctx, int x, int y, int w, int h,
                               String text, float a) {
        boolean hov = mx >= x && mx < x + w && my >= y && my < y + h;
        rund(ctx, x, y, w, h, fade(hov ? mix(C_CARD, 0xFFB91C1C, 0.30f) : C_CARD, a), 4);
        ctx.fill(x + 4, y, x + w - 4, y + 1,
                fade(mix(C_CARD, hov ? 0xFFF87171 : C_TEXT, hov ? 0.4f : 0.08f), a));
        int tw = this.font.width(text);
        ctx.text(this.font, Component.literal(text),
                x + (w - tw) / 2, y + (h - 8) / 2,
                fade(hov ? 0xFFFCA5A5 : C_TEXT, a), false);
        return new int[]{x, y, w, h};
    }


    /**
     * Eine Menuezeile.
     *
     * Links ein Balken, der beim Ueberfahren einblendet und beim Hauptweg
     * dauerhaft leuchtet. Das fuehrt den Blick die Liste entlang, ohne dass
     * jede Zeile einen Kasten braucht.
     */
    private int[] eintrag(GuiGraphicsExtractor ctx, int x, int y, int w, int h,
                          String text, boolean haupt, float a) {
        boolean hov = mx >= x && mx < x + w && my >= y && my < y + h;
        if (hov || haupt) {
            rund(ctx, x, y, w, h, fade(hov ? C_CARD_HV : C_CARD, a), 4);
        }
        // Balken links
        if (haupt) {
            for (int i = 0; i < 3; i++) {
                ctx.fill(x, y + 4 + i, x + 3, y + h - 4 - i,
                        fade(mix(VIOLETT, BLAU, i / 3f), a));
            }
        } else if (hov) {
            ctx.fill(x, y + 6, x + 2, y + h - 6, fade(mix(VIOLETT, BLAU, 0.5f), a * 0.8f));
        }
        ctx.text(this.font, Component.literal(text), x + 14, y + (h - 8) / 2,
                fade(haupt ? 0xFFFFFFFF : (hov ? C_TEXT : C_DIMTXT), a), false);
        // Pfeil rechts, nur beim Ueberfahren
        if (hov || haupt) {
            ctx.text(this.font, Component.literal(">"), x + w - 14, y + (h - 8) / 2,
                    fade(haupt ? 0xFFFFFFFF : mix(VIOLETT, BLAU, 0.5f), a), false);
        }
        return new int[]{x, y, w, h};
    }

    /** Wie eintrag, aber roetlich -- der einzige Weg, der das Spiel beendet. */
    private int[] eintragWarnung(GuiGraphicsExtractor ctx, int x, int y, int w, int h,
                                 String text, float a) {
        boolean hov = mx >= x && mx < x + w && my >= y && my < y + h;
        if (hov) {
            rund(ctx, x, y, w, h, fade(mix(C_CARD, 0xFFB91C1C, 0.28f), a), 4);
            ctx.fill(x, y + 6, x + 2, y + h - 6, fade(0xFFF87171, a));
        }
        ctx.text(this.font, Component.literal(text), x + 14, y + (h - 8) / 2,
                fade(hov ? 0xFFFCA5A5 : C_DIMTXT, a), false);
        return new int[]{x, y, w, h};
    }

    /** Duenne Trennlinie, zu den Raendern hin auslaufend. */
    private void trenner(GuiGraphicsExtractor ctx, int x, int y, int w, float a) {
        int halb = w / 2;
        for (int i = 0; i < halb; i += 4) {
            ctx.fill(x + i, y, x + i + 4, y + 1, fade(C_LINE, a * (i / (float) halb)));
            ctx.fill(x + w - i - 4, y, x + w - i, y + 1, fade(C_LINE, a * (i / (float) halb)));
        }
    }

}
