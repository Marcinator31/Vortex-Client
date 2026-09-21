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

        int cx = this.width / 2;
        int cy = this.height / 2 - 40 + gleiten;

        // --- Logo mit Schein ----------------------------------------------
        int logo = 72;
        for (int i = 1; i <= 4; i++) {
            int al = (int) (22f / i * a);
            rund(ctx, cx - logo / 2 - i * 3, cy - logo / 2 - i * 3,
                    logo + i * 6, logo + i * 6, (al << 24) | (VIOLETT & 0x00FFFFFF), 12);
        }
        try {
            ctx.blitSprite(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,
                    LOGO, cx - logo / 2, cy - logo / 2, logo, logo);
        } catch (Throwable ignored) {
            // Sprite fehlt: dann wenigstens der Schriftzug.
        }

        // --- Schriftzug ----------------------------------------------------
        // Buchstaben mit Abstand gesetzt -- wirkt kraeftiger als die
        // normale Laufweite, ohne die Schrift skalieren zu muessen.
        String name = "V O R T E X";
        int nw = this.font.width(name);
        ctx.text(this.font, Component.literal(name),
                cx - nw / 2, cy + logo / 2 + 12, fade(C_TEXT, a), false);
        String zeile = "CLIENT";
        int zw = this.font.width(zeile);
        ctx.text(this.font, Component.literal(zeile),
                cx - zw / 2, cy + logo / 2 + 24, fade(C_DIMTXT, a), false);

        // --- Drei Hauptknoepfe --------------------------------------------
        int by = cy + logo / 2 + 48;
        int modsB = 120, seitB = 90, h = 30, luecke = 12;

        kNeustart = knopf(ctx, cx - modsB / 2 - luecke - seitB, by, seitB, h,
                "\u21BB  Restart", false, a);
        kMods = knopf(ctx, cx - modsB / 2, by, modsB, h, "MODS", true, a);
        kGarderobe = knopf(ctx, cx + modsB / 2 + luecke, by, seitB, h,
                "Wardrobe  \u2692", false, a);

        // --- HUD-Editor, kleiner darunter ---------------------------------
        int hudB = 140;
        kHud = knopf(ctx, cx - hudB / 2, by + h + 12, hudB, 22,
                "Edit HUD layout", false, a);

        // --- Fusszeile -------------------------------------------------------
        // Nur ESC: Rechtsshift oeffnet den Bildschirm, schliesst ihn aber nicht.
        String hinweis = "ESC to close";
        int hw = this.font.width(hinweis);
        ctx.text(this.font, Component.literal(hinweis),
                cx - hw / 2, this.height - 20, fade(C_DIMTXT, a * 0.7f), false);

        if (fehler != null) {
            int fw = this.font.width(fehler);
            ctx.text(this.font, Component.literal(fehler),
                    cx - fw / 2, by + h + 44, fade(0xFFF87171, a), false);
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

        if (in(kMods)) { mc.gui.setScreen(new ClickGui()); return true; }
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
}
