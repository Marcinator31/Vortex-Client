package com.vortex.client.gui;

import com.vortex.client.gui.glatt.Glatt;
import com.vortex.client.gui.glatt.Glatt.Schrift;
import com.vortex.client.gui.glatt.Symbole.Symbol;
import com.vortex.client.module.Module;
import com.vortex.client.module.ModuleManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Startbildschirm des Clients (Rechtsshift).
 *
 * AB 4.17: KOMPLETT NEU, OHNE PIXEL
 *   Vorher wurde alles in GUI-Pixeln gezeichnet: Ecken als Treppen, Symbole
 *   aus 7x7 Punkten, Minecrafts Pixelschrift -- bei GUI-Groesse 3 sah man
 *   jeden einzelnen Pixel. Jetzt kommt alles aus {@link Glatt}: Flaechen,
 *   Rahmen, Schatten und Symbole werden in echten Bildschirmpixeln mit
 *   weichen Kanten gezeichnet, der Text in der Schrift "Inter".
 *
 * AUFBAU
 *   - Kopf: Logo, Name, Version; rechts ein Schliessen-Knopf
 *   - "Mods" als grosse Karte mit Anzahl der aktiven Module
 *   - acht Kacheln (Symbol, Titel, kurze Info darunter)
 *   - optional die Beta-Test-Zeile
 *   - Fuss: Settings, Community, Restart
 *   Bei kleinen Fenstern wird es kompakter (2 Spalten, niedrigere Kacheln);
 *   passt es dann immer noch nicht, wird das Ganze verkleinert.
 *
 * BEWEGUNG
 *   Einblenden mit leichtem Aufsteigen, Kacheln gestaffelt; beim Ueberfahren
 *   blenden Farbe und Rahmen weich um. Da jetzt in echten Pixeln gezeichnet
 *   wird, ruckelt dabei nichts mehr um ganze GUI-Pixel.
 */
public class HomeScreen extends Screen {

    // --- Farben ------------------------------------------------------------------
    private static final int FELD = 0xF0100D18;
    private static final int KARTE = 0xFF19151F;
    private static final int TEXT = 0xFFF4F2FA;
    private static final int TEXT_LEISE = 0xFF948DAA;
    private static final int KANTE = 0x1CFFFFFF;
    private static final int ROT = 0xFFF87171;

    // --- Zustand -------------------------------------------------------------------
    private int mx, my;
    private long letzteZeit = 0;
    private float oeffnen = 0f;
    private float seit = 0f;

    private boolean frageNeustart = false;
    private float frageA = 0f;
    private String fehler = null;
    private float[] kJa, kNein;

    private record Hit(float x, float y, float w, float h, Runnable run) {}
    private final java.util.List<Hit> hits = new java.util.ArrayList<>();
    private final java.util.Map<String, Float> hov = new java.util.HashMap<>();

    /** Verkleinerung, falls das Fenster zu klein ist (1 = normal). */
    private float zoom = 1f, zoomCx, zoomCy;

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
        long jetzt = System.nanoTime();
        float dt = letzteZeit == 0 ? 0.016f : Math.min(0.1f, (jetzt - letzteZeit) / 1e9f);
        letzteZeit = jetzt;
        oeffnen = weich(oeffnen, 1f, 9f, dt);
        seit += dt;
        float a = oeffnen;

        // Welt hinter dem Menue weichzeichnen und abdunkeln
        try { ctx.blurBeforeThisStratum(); } catch (Throwable ignored) { }
        ctx.fill(0, 0, this.width, this.height, VortexStyle.fade(0x8A06040B, a));

        hits.clear();
        Masse m = masse();
        zoom = m.zoom;
        zoomCx = this.width / 2f;
        zoomCy = this.height / 2f;
        // Mauskoordinaten in den (evtl. verkleinerten) Raum umrechnen
        this.mx = Math.round((mouseX - zoomCx) / zoom + zoomCx);
        this.my = Math.round((mouseY - zoomCy) / zoom + zoomCy);

        var p = ctx.pose();
        p.pushMatrix();
        try {
            if (zoom < 0.999f) {
                p.translate(zoomCx, zoomCy);
                p.scale(zoom, zoom);
                p.translate(-zoomCx, -zoomCy);
            }
            zeichneDashboard(ctx, m, a, dt);
        } finally {
            p.popMatrix();
        }

        // --- Rueckfrage ueber allem ------------------------------------------
        frageA = weich(frageA, frageNeustart ? 1f : 0f, 14f, dt);
        if (frageA > 0.01f) {
            ctx.nextStratum();
            this.mx = mouseX;
            this.my = mouseY;
            zeichneRueckfrage(ctx, a * frageA);
        } else {
            kJa = null;
            kNein = null;
        }
    }

    /** Alle Masse aus der Fenstergroesse. */
    private record Masse(float w, float h, int spalten, float kachelH, boolean kompakt, boolean mitBeta, float zoom) {}

    private Masse masse() {
        boolean mitBeta = betaDaten()[1] > 0;
        float verfW = this.width - 24, verfH = this.height - 20;
        boolean kompakt = verfH < 330;
        int spalten = verfW >= 400 ? 4 : 2;
        float kachelH = kompakt ? 38 : 50;
        float w = Math.min(verfW, spalten == 4 ? 500 : 300);
        float h = hoehe(spalten, kachelH, kompakt, mitBeta);
        float zoom = 1f;
        if (h > verfH) zoom = Math.max(0.55f, verfH / h);
        if (w < 260) zoom = Math.min(zoom, Math.max(0.55f, verfW / 260f));
        if (zoom < 1f) w = Math.min(Math.max(w, 260), (spalten == 4 ? 500 : 300));
        return new Masse(w, h, spalten, kachelH, kompakt, mitBeta, zoom);
    }

    private static float hoehe(int spalten, float kachelH, boolean kompakt, boolean mitBeta) {
        int reihen = 8 / spalten;
        float h = 16;                         // Rand oben
        h += kompakt ? 26 : 32;               // Kopf
        h += kompakt ? 10 : 14;
        h += kompakt ? 42 : 54;               // Mods-Karte
        h += kompakt ? 10 : 12;
        h += 14;                              // Abschnittsname
        h += reihen * kachelH + (reihen - 1) * 8;
        if (mitBeta) h += 8 + 30;
        h += 14 + 1 + 10 + 22 + 12;           // Trennlinie + Fuss
        return h;
    }

    private void zeichneDashboard(GuiGraphicsExtractor ctx, Masse m, float a, float dt) {
        Minecraft mc = Minecraft.getInstance();
        int akzent = Theme.INSTANCE.accent.get() | 0xFF000000;
        int akzent2 = Glatt.mix(akzent, VortexStyle.VIOLETT, 0.55f);
        int akzentHell = Glatt.mix(akzent, 0xFFFFFFFF, 0.35f);

        float W = m.w, H = m.h;
        float x0 = (this.width - W) / 2f;
        float y0 = (this.height - H) / 2f + (1f - a) * 10f;

        // --- Licht hinter dem Feld, Schatten, Feld -------------------------------
        float puls = (float) (0.5 + 0.5 * Math.sin(seit * 0.6));
        Glatt.licht(ctx, x0 + W * 0.15f, y0 + H * 0.1f, Math.max(W, H) * 0.75f, Glatt.alpha(akzent2, a * (0.20f + 0.06f * puls)));
        Glatt.licht(ctx, x0 + W * 0.9f, y0 + H * 0.95f, Math.max(W, H) * 0.6f, Glatt.alpha(akzent, a * (0.14f + 0.05f * (1 - puls))));
        Glatt.schatten(ctx, x0, y0 + 4, W, H, 16, 22, Glatt.alpha(0xFF000000, a * 0.55f));
        Glatt.rund(ctx, x0, y0, W, H, 16, Glatt.alpha(FELD, a));
        Glatt.rahmen(ctx, x0, y0, W, H, 16, 1, Glatt.alpha(KANTE, a));

        float pad = 16;
        float ix = x0 + pad, iw = W - 2 * pad;
        float cy = y0 + 16;

        // --- Kopf ------------------------------------------------------------------
        float logoG = m.kompakt ? 22 : 28;
        Glatt.logo(ctx, ix, cy, logoG, a, a);
        float tx = ix + logoG + 10;
        if (m.kompakt) {
            Glatt.text(ctx, "Vortex Client", tx, cy + 4, Glatt.alpha(TEXT, a), Schrift.TITEL);
        } else {
            Glatt.text(ctx, "Vortex Client", tx, cy + 1, Glatt.alpha(TEXT, a), Schrift.TITEL);
            String unter = (VERSION.isEmpty() ? "" : "v" + VERSION + "  ·  ") + "Right Shift menu";
            Glatt.text(ctx, unter, tx, cy + 18, Glatt.alpha(TEXT_LEISE, a), Schrift.NORMAL);
        }
        // Schliessen
        float zs = 24, zx = x0 + W - pad - zs, zy = cy + (logoG - zs) / 2f;
        float hz = hover("zu", over(zx, zy, zs, zs), dt);
        Glatt.kreis(ctx, zx + zs / 2, zy + zs / 2, zs, Glatt.alpha(0xFFFFFFFF, a * (0.04f + 0.08f * hz)));
        Glatt.symbol(ctx, Symbol.KREUZ, zx + 6, zy + 6, 12, Glatt.alpha(Glatt.mix(TEXT_LEISE, TEXT, hz), a));
        hits.add(new Hit(zx, zy, zs, zs, this::onClose));
        cy += (m.kompakt ? 26 : 32) + (m.kompakt ? 10 : 14);

        // --- Mods-Karte --------------------------------------------------------------
        int aktiv = 0, alle = 0, bots = 0;
        for (Module mod : ModuleManager.INSTANCE.getModules()) {
            if (mod.getCategory() == Module.Category.BOTS) { bots++; continue; }
            alle++;
            if (mod.isEnabled()) aktiv++;
        }
        float mh = m.kompakt ? 42 : 54;
        float st0 = einblenden(0);
        float al0 = a * st0;
        float hm = hover("mods", over(ix, cy, iw, mh), dt);
        int grund = Glatt.mix(Glatt.mix(KARTE, akzent2, 0.30f), akzent, 0.12f + 0.14f * hm);
        Glatt.schatten(ctx, ix, cy + 3, iw, mh, 13, 10, Glatt.alpha(Glatt.mix(0xFF000000, akzent, 0.4f), al0 * (0.25f + 0.25f * hm)));
        Glatt.rund(ctx, ix, cy, iw, mh, 13, Glatt.alpha(grund, al0));
        // Glanz: weicher Lichtfleck links oben in der Karte
        Glatt.licht(ctx, ix + mh * 0.6f, cy + mh * 0.3f, mh * 1.3f, Glatt.alpha(akzentHell, al0 * (0.16f + 0.10f * hm)));
        Glatt.rahmen(ctx, ix, cy, iw, mh, 13, 1, Glatt.alpha(Glatt.mix(0x33FFFFFF, akzentHell, hm), al0 * (0.6f + 0.4f * hm)));
        float chip = m.kompakt ? 28 : 34;
        float chx = ix + (mh - chip) / 2f, chy = cy + (mh - chip) / 2f;
        Glatt.rund(ctx, chx, chy, chip, chip, 10, Glatt.alpha(Glatt.mix(akzent, 0xFFFFFFFF, 0.08f + 0.1f * hm), al0));
        float sg = chip * 0.56f;
        Glatt.symbol(ctx, Symbol.RASTER, chx + (chip - sg) / 2f, chy + (chip - sg) / 2f, sg, Glatt.alpha(0xFFFFFFFF, al0));
        float mtx = chx + chip + 12;
        if (m.kompakt) {
            Glatt.text(ctx, "Mods", mtx, cy + mh / 2f - 9, Glatt.alpha(0xFFFFFFFF, al0), Schrift.FETT);
            Glatt.text(ctx, aktiv + " of " + alle + " active", mtx, cy + mh / 2f + 2, Glatt.alpha(0xFFD9D4EE, al0), Schrift.NORMAL);
        } else {
            Glatt.text(ctx, "Mods", mtx, cy + 12, Glatt.alpha(0xFFFFFFFF, al0), Schrift.TITEL);
            Glatt.text(ctx, aktiv + " of " + alle + " active", mtx, cy + 31, Glatt.alpha(0xFFD9D4EE, al0), Schrift.NORMAL);
        }
        // rechts: "Open" mit Pfeil, der beim Ueberfahren ein Stueck nach rechts gleitet
        float pfx = ix + iw - 14 - 12 + 2 * hm;
        Glatt.symbol(ctx, Symbol.PFEIL_RECHTS, pfx, cy + (mh - 12) / 2f, 12, Glatt.alpha(Glatt.mix(0xFFCFC8E8, 0xFFFFFFFF, hm), al0));
        Glatt.textRechts(ctx, "Open", pfx - 4, cy + (mh - 9) / 2f + 0.5f, Glatt.alpha(Glatt.mix(0xFFCFC8E8, 0xFFFFFFFF, hm), al0), Schrift.FETT);
        hits.add(new Hit(ix, cy, iw, mh, () -> mc.gui.setScreen(new PanelGui())));
        cy += mh + (m.kompakt ? 10 : 12);

        // --- Kacheln -------------------------------------------------------------------
        Glatt.text(ctx, "TOOLS", ix + 2, cy + 1, Glatt.alpha(0xFF6F6888, a * einblenden(1)), Schrift.FETT);
        cy += 14;
        int neu = com.vortex.client.social.Social.data() == null ? 0 : com.vortex.client.social.Social.unreadTotal();
        Object[][] kacheln = {
            // {Schluessel, Titel, Untertitel, Symbol, Aktion}
            {"hud", "HUD Editor", "Move elements", Symbol.HUD, (Runnable) () -> mc.gui.setScreen(new HudEditorScreen())},
            {"presets", "Presets", presetKurz(), Symbol.REGLER, (Runnable) () -> mc.gui.setScreen(new PresetScreen(this))},
            {"waypoints", "Waypoints", anzahl(() -> com.vortex.client.waypoint.WaypointManager.all().size(), "marker"), Symbol.NADEL, (Runnable) () -> mc.gui.setScreen(new WaypointScreen(this))},
            {"macros", "Macros", anzahl(() -> com.vortex.client.macro.MacroManager.all().size(), "macro"), Symbol.BLITZ, (Runnable) () -> mc.gui.setScreen(new MacroScreen(this))},
            {"friends", "Friends", neu > 0 ? neu + " unread" : freundeKurz(), Symbol.LEUTE, (Runnable) () -> mc.gui.setScreen(new FriendsScreen(this))},
            {"wardrobe", "Wardrobe", "Skins, capes", Symbol.HEMD, (Runnable) () -> mc.gui.setScreen(new SkinScreen(this))},
            {"bots", "Bots", bots > 0 ? bots + " bots" : "Plus Addon", Symbol.ROBOTER, (Runnable) () -> mc.gui.setScreen(new BotScreen(this))},
            {"keys", "Keybinds", "All keys", Symbol.TASTATUR, (Runnable) () -> mc.gui.setScreen(new KeyListScreen(this))},
        };
        float gap = 8;
        int sp = m.spalten;
        float kw = (iw - (sp - 1) * gap) / sp;
        float kh = m.kachelH;
        for (int i = 0; i < kacheln.length; i++) {
            Object[] k = kacheln[i];
            float kx = ix + (i % sp) * (kw + gap);
            float ky = cy + (i / sp) * (kh + gap);
            float al = a * einblenden(2 + i);
            String key = (String) k[0];
            float h = hover(key, over(kx, ky, kw, kh), dt);
            kachel(ctx, kx, ky, kw, kh, (String) k[1], (String) k[2], (Symbol) k[3], akzent, akzentHell, h, al, m.kompakt);
            if (key.equals("friends") && neu > 0) {
                String z = neu > 9 ? "9+" : String.valueOf(neu);
                float bw = Math.max(14, Glatt.breite(z, Schrift.FETT) + 8);
                Glatt.rund(ctx, kx + kw - bw - 6, ky + 6, bw, 14, 7, Glatt.alpha(VortexStyle.VIOLETT, al));
                Glatt.textMitte(ctx, z, kx + kw - 6 - bw / 2f, ky + 9, Glatt.alpha(0xFFFFFFFF, al), Schrift.FETT);
            }
            hits.add(new Hit(kx, ky, kw, kh, (Runnable) k[4]));
        }
        int reihen = 8 / sp;
        cy += reihen * kh + (reihen - 1) * gap;

        // --- Beta-Test (nur wenn der Launcher eine Checkliste mitgibt) ---------------
        if (m.mitBeta) {
            cy += 8;
            int[] beta = betaDaten();
            int gruen = 0xFF4ADE80;
            float h = hover("beta", over(ix, cy, iw, 30), dt);
            float al = a * einblenden(10);
            Glatt.rund(ctx, ix, cy, iw, 30, 10, Glatt.alpha(Glatt.mix(KARTE, gruen, 0.07f + 0.08f * h), al));
            Glatt.rahmen(ctx, ix, cy, iw, 30, 10, 1, Glatt.alpha(Glatt.mix(KANTE, 0x804ADE80, h), al));
            Glatt.symbol(ctx, Symbol.KOLBEN, ix + 10, cy + 8, 14, Glatt.alpha(gruen, al));
            String bt = "Beta test  ·  " + beta[0] + " / " + beta[1] + " checked" + (beta[2] > 0 ? "  ·  " + beta[2] + " bug(s)" : "");
            Glatt.text(ctx, Glatt.kuerzen(bt, (int) (iw - 140), Schrift.NORMAL), ix + 32, cy + 11, Glatt.alpha(TEXT, al), Schrift.NORMAL);
            float bw = 90, bx = ix + iw - bw - 12, by = cy + 13;
            Glatt.rund(ctx, bx, by, bw, 4, 2, Glatt.alpha(0x26FFFFFF, al));
            float voll = bw * beta[0] / Math.max(1, beta[1]);
            if (voll > 0) Glatt.rund(ctx, bx, by, Math.max(4, voll), 4, 2, Glatt.alpha(gruen, al));
            hits.add(new Hit(ix, cy, iw, 30, () -> mc.gui.setScreen(new com.vortex.client.beta.BetaScreen(this))));
            cy += 30;
        }

        // --- Fuss ------------------------------------------------------------------
        cy += 14;
        Glatt.linieW(ctx, ix, ix + iw, cy, Glatt.alpha(0x14FFFFFF, a));
        cy += 11;
        float fa = a * einblenden(11);
        Glatt.text(ctx, "ESC to close", ix + 2, cy + 7, Glatt.alpha(0xFF5F5976, fa), Schrift.NORMAL);
        float rx = ix + iw;
        rx = fussKnopf(ctx, rx, cy, "Restart", Symbol.NEUSTART, true, fa, dt, () -> {
            if (com.vortex.client.core.ClientSettings.INSTANCE.confirmRestart.get()) { frageNeustart = true; fehler = null; }
            else neustarten();
        });
        rx = fussKnopf(ctx, rx - 6, cy, "Community", Symbol.GLOBUS, false, fa, dt, () -> mc.gui.setScreen(new CommunityScreen(this)));
        fussKnopf(ctx, rx - 6, cy, "Settings", Symbol.ZAHNRAD, false, fa, dt, () -> mc.gui.setScreen(new SettingsScreen(this, "Client Settings",
                com.vortex.client.core.ClientSettings.INSTANCE.all())));

        if (fehler != null) {
            Glatt.textMitte(ctx, fehler, this.width / 2f, Math.min(this.height - 12, y0 + H + 8), Glatt.alpha(ROT, a), Schrift.FETT);
        }
    }

    /** Eine Kachel: Symbol-Chip, Titel, Info. */
    private void kachel(GuiGraphicsExtractor ctx, float x, float y, float w, float h, String titel, String info, Symbol sym,
                        int akzent, int akzentHell, float hv, float al, boolean kompakt) {
        Glatt.rund(ctx, x, y, w, h, 11, Glatt.alpha(Glatt.mix(KARTE, Glatt.mix(KARTE, akzent, 0.16f), hv), al));
        Glatt.rahmen(ctx, x, y, w, h, 11, 1, Glatt.alpha(Glatt.mix(KANTE, Glatt.alpha(akzentHell, 0.55f), hv), al));
        float chip = kompakt ? 22 : 26;
        float cx = x + 10, cyy = y + (h - chip) / 2f;
        Glatt.rund(ctx, cx, cyy, chip, chip, 8, Glatt.alpha(Glatt.mix(Glatt.alpha(akzent, 0.16f), akzent, hv), al));
        float sg = chip * 0.58f;
        Glatt.symbol(ctx, sym, cx + (chip - sg) / 2f, cyy + (chip - sg) / 2f, sg, Glatt.alpha(Glatt.mix(akzentHell, 0xFFFFFFFF, hv), al));
        float tx = cx + chip + 9;
        int platz = (int) (x + w - tx - 6);
        if (kompakt || info == null || info.isEmpty()) {
            Glatt.text(ctx, Glatt.kuerzen(titel, platz, Schrift.FETT), tx, y + (h - 9) / 2f, Glatt.alpha(TEXT, al), Schrift.FETT);
        } else {
            Glatt.text(ctx, Glatt.kuerzen(titel, platz, Schrift.FETT), tx, y + h / 2f - 10, Glatt.alpha(TEXT, al), Schrift.FETT);
            Glatt.text(ctx, Glatt.kuerzen(info, platz, Schrift.NORMAL), tx, y + h / 2f + 2, Glatt.alpha(Glatt.mix(TEXT_LEISE, 0xFFC9C3DD, hv), al), Schrift.NORMAL);
        }
    }

    /** Knopf im Fuss mit Symbol, rechtsbuendig an "rechts". Gibt die linke Kante zurueck. */
    private float fussKnopf(GuiGraphicsExtractor ctx, float rechts, float y, String text, Symbol sym, boolean rot,
                            float a, float dt, Runnable run) {
        float h = 22;
        float w = Glatt.breite(text, Schrift.FETT) + 12 + 6 + 20;
        float x = rechts - w;
        float hv = hover("f:" + text, over(x, y, w, h), dt);
        int farbe = rot ? Glatt.mix(0xFFB98A93, 0xFFFCA5A5, hv) : Glatt.mix(0xFFA9A2BF, TEXT, hv);
        int grund = rot ? Glatt.alpha(0xFFF87171, 0.06f + 0.12f * hv) : Glatt.alpha(0xFFFFFFFF, 0.035f + 0.06f * hv);
        Glatt.rund(ctx, x, y, w, h, 11, Glatt.alpha(grund, a));
        Glatt.symbol(ctx, sym, x + 10, y + 5, 12, Glatt.alpha(farbe, a));
        Glatt.text(ctx, text, x + 28, y + 6.5f, Glatt.alpha(farbe, a), Schrift.FETT);
        hits.add(new Hit(x, y, w, h, run));
        return x;
    }

    // ======================================================================
    // Rueckfrage vor dem Neustart
    // ======================================================================

    private void zeichneRueckfrage(GuiGraphicsExtractor ctx, float a) {
        ctx.fill(0, 0, this.width, this.height, VortexStyle.fade(0x99000000, a));
        float w = Math.min(280, this.width - 20), h = 128;
        float x = (this.width - w) / 2f, y = (this.height - h) / 2f + (1f - a) * 10f;
        Glatt.schatten(ctx, x, y + 4, w, h, 16, 20, Glatt.alpha(0xFF000000, a * 0.6f));
        Glatt.rund(ctx, x, y, w, h, 16, Glatt.alpha(0xFA120E1A, a));
        Glatt.rahmen(ctx, x, y, w, h, 16, 1, Glatt.alpha(KANTE, a));

        float chip = 30;
        Glatt.rund(ctx, x + (w - chip) / 2f, y + 14, chip, chip, 10, Glatt.alpha(0x2EF87171, a));
        Glatt.symbol(ctx, Symbol.NEUSTART, x + (w - 16) / 2f, y + 21, 16, Glatt.alpha(ROT, a));
        Glatt.textMitte(ctx, "Restart your game?", this.width / 2f, y + 52, Glatt.alpha(TEXT, a), Schrift.FETT);
        Glatt.textMitte(ctx, "Minecraft closes and starts again.", this.width / 2f, y + 66, Glatt.alpha(TEXT_LEISE, a), Schrift.NORMAL);

        float kb = (w - 16 * 2 - 10) / 2f, ky = y + h - 16 - 24;
        kNein = knopf(ctx, x + 16, ky, kb, 24, "Cancel", false, a);
        kJa = knopf(ctx, x + w - 16 - kb, ky, kb, 24, "Restart", true, a);
    }

    private float[] knopf(GuiGraphicsExtractor ctx, float x, float y, float w, float h, String text, boolean warnung, float a) {
        boolean hv = mx >= x && mx < x + w && my >= y && my < y + h;
        if (warnung) {
            Glatt.rund(ctx, x, y, w, h, 12, Glatt.alpha(hv ? 0xFFDC2626 : 0xFFB91C1C, a));
        } else {
            Glatt.rund(ctx, x, y, w, h, 12, Glatt.alpha(hv ? 0x1FFFFFFF : 0x0FFFFFFF, a));
            Glatt.rahmen(ctx, x, y, w, h, 12, 1, Glatt.alpha(0x24FFFFFF, a));
        }
        Glatt.textMitte(ctx, text, x + w / 2f, y + (h - 9) / 2f + 0.5f, Glatt.alpha(warnung || hv ? 0xFFFFFFFF : TEXT, a), Schrift.FETT);
        return new float[]{x, y, w, h};
    }

    // ======================================================================
    // Daten
    // ======================================================================

    private static final String VERSION = version();

    private static String version() {
        try {
            return net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer("vortexclient")
                    .map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("").replaceAll("\\+.*$", "");
        } catch (Throwable e) { return ""; }
    }

    private static int[] betaDaten() {
        try {
            return com.vortex.client.beta.BetaTest.aktiv() ? com.vortex.client.beta.BetaTest.fortschritt() : new int[]{0, 0, 0};
        } catch (Throwable e) {
            return new int[]{0, 0, 0};
        }
    }

    private static String presetKurz() {
        try { return com.vortex.client.core.ConfigManager.presetName(com.vortex.client.core.ConfigManager.getActivePreset()); }
        catch (Throwable e) { return "Setups"; }
    }

    private static String freundeKurz() {
        var c = com.vortex.client.social.Social.conn();
        if (c != com.vortex.client.social.Social.Conn.ONLINE || com.vortex.client.social.Social.data() == null) return "Chat, invites";
        int on = 0;
        for (var f : com.vortex.client.social.Social.friends()) {
            String st = com.vortex.client.social.Social.str(com.vortex.client.social.Social.obj(f, "presence"), "state");
            if (!st.isEmpty() && !st.equals("offline")) on++;
        }
        return on + " online";
    }

    private static String anzahl(java.util.function.IntSupplier n, String wort) {
        try { int z = n.getAsInt(); return z == 0 ? "No " + wort + "s yet" : z + " " + wort + (z == 1 ? "" : "s"); }
        catch (Throwable e) { return ""; }
    }

    // ======================================================================
    // Bedienung
    // ======================================================================

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent click, boolean doubled) {
        if (super.mouseClicked(click, doubled)) return true;
        if (click.button() != 0) return false;
        int rohX = (int) click.x(), rohY = (int) click.y();

        // Rueckfrage hat Vorrang: solange sie offen ist, zaehlen nur ihre beiden Knoepfe.
        if (frageNeustart) {
            if (in(kJa, rohX, rohY)) {
                frageNeustart = false;
                neustarten();
                return true;
            }
            if (in(kNein, rohX, rohY)) { frageNeustart = false; return true; }
            return true;
        }

        float zx = (rohX - zoomCx) / zoom + zoomCx, zy = (rohY - zoomCy) / zoom + zoomCy;
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit h = hits.get(i);
            if (zx >= h.x && zx < h.x + h.w && zy >= h.y && zy < h.y + h.h) { h.run.run(); return true; }
        }
        return false;
    }

    private void neustarten() {
        try {
            com.vortex.client.util.GameRestarter.restart();
        } catch (Throwable err) {
            // Der Neustart prueft selbst, ob der neue Prozess ueberlebt.
            // Scheitert er, bleibt das Spiel offen und zeigt den Grund.
            fehler = "Restart failed: " + (err.getMessage() == null ? err.getClass().getSimpleName() : err.getMessage());
            com.vortex.client.core.Errors.report("HomeScreen.restart", err);
        }
    }

    // ======================================================================
    // Hilfen
    // ======================================================================

    private float hover(String key, boolean over, float dt) {
        float v = weich(hov.getOrDefault(key, 0f), over ? 1f : 0f, 16f, dt);
        hov.put(key, v);
        return v;
    }

    private boolean over(float x, float y, float w, float h) {
        return !frageNeustart && mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private static boolean in(float[] k, int x, int y) {
        return k != null && x >= k[0] && x < k[0] + k[2] && y >= k[1] && y < k[1] + k[3];
    }

    /** Sanftes, gestaffeltes Einblenden (nur Deckkraft). */
    private float einblenden(int index) {
        float t = clamp01((seit - 0.03f - index * 0.022f) / 0.22f);
        return t * t * (3f - 2f * t);
    }

    /** Weiche, bildratenunabhaengige Annaeherung -- wie ueberall im Client. */
    private static float weich(float ist, float ziel, float tempo, float dt) {
        float f = 1f - (float) Math.exp(-tempo * dt);
        float neu = ist + (ziel - ist) * f;
        return Math.abs(ziel - neu) < 0.002f ? ziel : neu;
    }

    private static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }
}
