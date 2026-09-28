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
 * AB 4.9.8: RUHIGES DASHBOARD (normale Fenstergroessen)
 *   Kopf, grosser "Mods"-Knopf, acht Kacheln, Fuss mit Community/Neustart.
 *   Bewegung nur als Ueberblenden (siehe zeichneDashboard). Die Liste
 *   darunter bleibt fuer sehr kleine Fenster.
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
    private static final int MODS = 0, BOTS = 1, PRESETS = 2, WAYPOINTS = 3, MACROS = 4,
            WARDROBE = 5, KEYS = 6, HUD = 7, COMMUNITY = 8, FRIENDS = 9, RESTART = 10;
    private static final String[] NAMEN = {
        "Mods", "Bots", "Presets", "Waypoints", "Macros", "Wardrobe", "Keybinds", "HUD Editor",
        "Community", "Friends", "Restart Game"
    };
    /** Anzahl der Eintraege -- ueberall statt einer festen Zahl benutzt. */
    private static final int N = NAMEN.length;

    /**
     * Symbole als 7x7-Punktmuster, eines je Eintrag, in derselben Reihenfolge.
     * '1' = Punkt, '.' = leer.
     */
    private static final String[][] SYMBOLE = {
        { // Mods: vier Kacheln
            "111.111", "111.111", "111.111", ".......", "111.111", "111.111", "111.111" },
        { // Bots: Roboterkopf
            "...1...", ".11111.", ".1.1.1.", ".11111.", ".11111.", ".11111.", ".1...1." },
        { // Presets: drei Schieberegler
            "1......", "1111111", "......1", "...1...", "1111111", ".1.....", "1111111" },
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
        { // Community: zwei Personen
            ".1...1.", "111.111", ".1...1.", ".......", "111.111", "1111111", "1111111" },
        { // Friends: Sprechblase
            "1111111", "1.....1", "1.1.1.1", "1.....1", "1111111", ".11....", ".1....." },
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
    private final int[][] flaeche = new int[N][];
    private int[] kJa, kNein;

    // --- Dashboard ---------------------------------------------------------------
    private record Hit(int x, int y, int w, int h, Runnable run) {}
    private final java.util.List<Hit> hits = new java.util.ArrayList<>();
    private boolean dashboard = false;
    /** Hover-Wert je Kachel/Schalter (0..1), weich animiert. */
    private final java.util.Map<String, Float> hov = new java.util.HashMap<>();


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
        oeffnen = weich(oeffnen, 1f, 11f, dt);
        seit += dt;
        float a = oeffnen;

        // --- Hintergrund ---------------------------------------------------
        ctx.fill(0, 0, this.width, this.height, VortexStyle.fade(0xB0060409, a));
        dashboard = this.width >= 380 && this.height >= 250;
        hits.clear();
        if (dashboard) {
            zeichneDashboard(ctx, a, dt);
        } else {
            zeichneListe(ctx, a, dt);
        }

        // --- Rueckfrage ueber allem ------------------------------------------
        frageA = weich(frageA, frageNeustart ? 1f : 0f, 14f, dt);
        if (frageA > 0.01f) zeichneRueckfrage(ctx, a * frageA);
        else { kJa = null; kNein = null; }
    }

    /** Die schlichte Liste -- nur noch fuer sehr kleine Fenster. */
    private void zeichneListe(GuiGraphicsExtractor ctx, float a, float dt) {
        // --- Masse aus der Fenstergroesse ----------------------------------
        int verfuegbar = this.height - 16;
        boolean kompakt = this.height < 330;
        int logo = kompakt ? 18 : clamp((int) (this.height * 0.12f), 36, 56);
        int kopfH = kompakt ? 24 : logo + 28;
        int gruppenH = kompakt ? 0 : 2 * 14;
        int rest = verfuegbar - 10 - kopfH - 8 - gruppenH - 6 - 10;
        // Bei kleinem Fenster duerfen die Zeilen enger werden -- sonst passen
        // zehn Eintraege nicht mehr in 240 Pixel Hoehe.
        int zeileH = clamp(rest / N - 2, kompakt ? 14 : 16, 24);
        int feldB = Math.min(250, this.width - 16);
        int feldH = 10 + kopfH + 8 + gruppenH + N * (zeileH + 2) + 6 + 10;
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
        int[] ypos = new int[N];
        for (int i = 0; i < N; i++) {
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
            for (int i = 0; i < N; i++) {
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
        for (int i = 0; i < N; i++) {
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
    }

    // ======================================================================
    // Dashboard (4.9.1): Kopf mit Live-Werten, Kacheln, Schnellschalter, Freunde
    // ======================================================================

    private static final String VERSION = version();

    private static String version() {
        try {
            return net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer("vortexclient")
                    .map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("").replaceAll("\\+.*$", "");
        } catch (Throwable e) { return ""; }
    }

    private float hover(String key, boolean over, float dt) {
        float v = weich(hov.getOrDefault(key, 0f), over ? 1f : 0f, 14f, dt);
        hov.put(key, v);
        return v;
    }

    private boolean over(int x, int y, int w, int h) {
        return !frageNeustart && mx >= x && mx < x + w && my >= y && my < y + h;
    }

    /**
     * Dashboard (4.9.8): ruhig und aufgeraeumt.
     *
     * Vorher (4.9.1) war es zu viel: Live-Chips, Schnellschalter, Freundesliste,
     * schwebende Funken und wandernde Lichtflecken -- und Kacheln, die sich beim
     * Ueberfahren um ganze Pixel hoben und so ruckelten. Jetzt:
     *   - Kopf mit Logo, Name, Version und EINER leisen Zeile (FPS, Ping)
     *   - "Mods" als grosser Hauptknopf
     *   - acht gleich grosse Kacheln (2 x 4)
     *   - Fuss: Community, Neustart
     * Bewegung nur noch als Ueberblenden von Farben und Deckkraft -- das ist
     * bei jeder Bildrate glatt, weil sich keine Kante um ganze Pixel verschiebt.
     */
    private void zeichneDashboard(GuiGraphicsExtractor ctx, float a, float dt) {
        Minecraft mc = Minecraft.getInstance();
        int accent = Theme.INSTANCE.accent.get() | 0xFF000000;

        int[] beta = com.vortex.client.beta.BetaTest.aktiv() ? com.vortex.client.beta.BetaTest.fortschritt() : new int[]{0, 0, 0};
        boolean mitBeta = beta[1] > 0;

        int W = Math.min(this.width - 40, 470);
        int kachelH = 40, gap = 6, modsH = 38;
        int H = 14 + 26 + 12 + modsH + gap + 2 * kachelH + gap + (mitBeta ? 22 + gap : 0) + 12 + 16 + 10;
        int x0 = (this.width - W) / 2;
        int y0 = (this.height - H) / 2 + Math.round((1f - a) * 6f);

        VortexStyle.schatten(ctx, x0, y0, W, H, a);
        rund(ctx, x0, y0, W, H, VortexStyle.fade(0xF50E0B16, a), 6);
        VortexStyle.akzentLinie(ctx, x0 + 8, y0, W - 16, a);

        // --- Kopf -------------------------------------------------------------
        int ky = y0 + 14;
        zeichneLogo(ctx, x0 + 14, ky, 24, a);
        ctx.text(this.font, Component.literal("Vortex Client"), x0 + 46, ky + 3, VortexStyle.fade(VortexStyle.TEXT, a), false);
        if (!VERSION.isEmpty()) {
            ctx.text(this.font, Component.literal("v" + VERSION), x0 + 46, ky + 14, VortexStyle.fade(VortexStyle.TEXT_DIM, a), false);
        }
        String info = mc.getFps() + " FPS";
        int ping = ping(mc);
        if (ping >= 0) info += "  \u00b7  " + ping + " ms";
        ctx.text(this.font, Component.literal(info), x0 + W - 14 - this.font.width(info), ky + 8,
                VortexStyle.fade(VortexStyle.TEXT_DIM, a), false);
        int cy = ky + 26 + 6;
        ctx.fill(x0 + 12, cy, x0 + W - 12, cy + 1, VortexStyle.fade(VortexStyle.LINE, a));
        cy += 6;

        // --- Mods (Hauptknopf) ---------------------------------------------------
        int aktiv = 0, alle = 0, bots = 0;
        for (Module m : ModuleManager.INSTANCE.getModules()) {
            if (m.getCategory() == Module.Category.BOTS) { bots++; continue; }
            alle++;
            if (m.isEnabled()) aktiv++;
        }
        int ix = x0 + 12, iw = W - 24;
        float st0 = einblenden(0);
        float hm = hover("mods", over(ix, cy, iw, modsH), dt);
        int grundMods = VortexStyle.mix(VortexStyle.mix(VortexStyle.CARD, accent, 0.28f), accent, 0.22f * hm);
        rund(ctx, ix, cy, iw, modsH, VortexStyle.fade(grundMods, a * st0), 5);
        symbol2(ctx, SYMBOLE[MODS], ix + 12, cy + (modsH - 14) / 2, VortexStyle.fade(0xFFFFFFFF, a * st0));
        ctx.text(this.font, Component.literal("Mods"), ix + 34, cy + 9, VortexStyle.fade(0xFFFFFFFF, a * st0), false);
        ctx.text(this.font, Component.literal(aktiv + " of " + alle + " active"), ix + 34, cy + 21,
                VortexStyle.fade(0xFFD9D2F0, a * st0), false);
        String open = "Open  >";
        ctx.text(this.font, Component.literal(open), ix + iw - 12 - this.font.width(open), cy + (modsH - 8) / 2,
                VortexStyle.fade(VortexStyle.mix(0xFFBDB4DA, 0xFFFFFFFF, hm), a * st0), false);
        hits.add(new Hit(ix, cy, iw, modsH, () -> mc.gui.setScreen(new PanelGui())));
        cy += modsH + gap;

        // --- Kacheln (2 x 4) -----------------------------------------------------
        int neu = com.vortex.client.social.Social.data() == null ? 0 : com.vortex.client.social.Social.unreadTotal();
        Object[][] kacheln = {
            // {Schluessel, Titel, Untertitel, Symbol, Aktion}
            {"hud", "HUD Editor", "Move elements", HUD, (Runnable) () -> mc.gui.setScreen(new HudEditorScreen())},
            {"presets", "Presets", presetKurz(), PRESETS, (Runnable) () -> mc.gui.setScreen(new PresetScreen(this))},
            {"waypoints", "Waypoints", anzahl(() -> com.vortex.client.waypoint.WaypointManager.all().size(), "marker"), WAYPOINTS, (Runnable) () -> mc.gui.setScreen(new WaypointScreen(this))},
            {"macros", "Macros", anzahl(() -> com.vortex.client.macro.MacroManager.all().size(), "macro"), MACROS, (Runnable) () -> mc.gui.setScreen(new MacroScreen(this))},
            {"friends", "Friends", neu > 0 ? neu + " unread" : freundeKurz(), FRIENDS, (Runnable) () -> mc.gui.setScreen(new FriendsScreen(this))},
            {"wardrobe", "Wardrobe", "Skins, capes", WARDROBE, (Runnable) () -> mc.gui.setScreen(new SkinScreen(this))},
            {"bots", "Bots", bots > 0 ? bots + " bots" : "Plus Addon", BOTS, (Runnable) () -> mc.gui.setScreen(new BotScreen(this))},
            {"keys", "Keybinds", "All keys", KEYS, (Runnable) () -> mc.gui.setScreen(new KeyListScreen(this))},
        };
        int cols = 4;
        int kw = (iw - (cols - 1) * gap) / cols;
        for (int i = 0; i < kacheln.length; i++) {
            Object[] k = kacheln[i];
            int kx = ix + (i % cols) * (kw + gap);
            int kyy = cy + (i / cols) * (kachelH + gap);
            float st = einblenden(1 + i);
            float al = a * st;
            String key = (String) k[0];
            float h = hover(key, over(kx, kyy, kw, kachelH), dt);
            rund(ctx, kx, kyy, kw, kachelH, VortexStyle.fade(VortexStyle.mix(VortexStyle.CARD, accent, 0.04f + 0.16f * h), al), 4);
            int ico = VortexStyle.mix(VortexStyle.mix(accent, 0xFFFFFFFF, 0.3f), 0xFFFFFFFF, h);
            symbol(ctx, SYMBOLE[(Integer) k[3]], kx + 9, kyy + 9, VortexStyle.fade(ico, al));
            ctx.text(this.font, Component.literal(cut((String) k[1], kw - 26)), kx + 22, kyy + 9,
                    VortexStyle.fade(VortexStyle.TEXT, al), false);
            ctx.text(this.font, Component.literal(cut((String) k[2], kw - 16)), kx + 9, kyy + 24,
                    VortexStyle.fade(0xFF8E88A6, al), false);
            if (key.equals("friends") && neu > 0) {
                ctx.fill(kx + kw - 9, kyy + 6, kx + kw - 5, kyy + 10, VortexStyle.fade(VortexStyle.VIOLETT, al));
            }
            hits.add(new Hit(kx, kyy, kw, kachelH, (Runnable) k[4]));
        }
        cy += 2 * kachelH + gap + gap;

        // --- Beta-Test (nur wenn der Launcher eine Checkliste mitgibt) ---------------
        if (mitBeta) {
            float h = hover("beta", over(ix, cy, iw, 22), dt);
            float st = einblenden(9);
            rund(ctx, ix, cy, iw, 22, VortexStyle.fade(VortexStyle.mix(VortexStyle.INNER, 0xFF4ADE80, 0.06f + 0.12f * h), a * st), 4);
            String bt = "Beta test  \u00b7  " + beta[0] + " / " + beta[1] + " checked" + (beta[2] > 0 ? "  \u00b7  " + beta[2] + " bug(s)" : "");
            ctx.text(this.font, Component.literal(cut(bt, iw - 110)), ix + 10, cy + 7, VortexStyle.fade(VortexStyle.TEXT, a * st), false);
            int bw = 80, bx = ix + iw - bw - 10, by = cy + 10;
            ctx.fill(bx, by, bx + bw, by + 2, VortexStyle.fade(VortexStyle.TRACK, a * st));
            ctx.fill(bx, by, bx + bw * beta[0] / Math.max(1, beta[1]), by + 2, VortexStyle.fade(0xFF4ADE80, a * st));
            hits.add(new Hit(ix, cy, iw, 22, () -> mc.gui.setScreen(new com.vortex.client.beta.BetaScreen(this))));
            cy += 22 + gap;
        }

        // --- Fuss --------------------------------------------------------------
        cy += 4;
        ctx.fill(x0 + 12, cy, x0 + W - 12, cy + 1, VortexStyle.fade(VortexStyle.LINE, a));
        int fy = cy + 8;
        ctx.text(this.font, Component.literal("ESC to close"), x0 + 14, fy + 4, VortexStyle.fade(VortexStyle.TEXT_DIM, a * 0.7f), false);
        int rx = x0 + W - 12;
        rx = fussKnopf(ctx, rx, fy, "Restart", true, a, dt, () -> { frageNeustart = true; fehler = null; });
        fussKnopf(ctx, rx - 6, fy, "Community", false, a, dt, () -> mc.gui.setScreen(new CommunityScreen(this)));
        if (fehler != null) {
            ctx.text(this.font, Component.literal(fehler), (this.width - this.font.width(fehler)) / 2,
                    Math.min(this.height - 11, y0 + H + 6), VortexStyle.fade(0xFFF87171, a), false);
        }
    }

    /** Kleiner Textknopf im Fuss, rechtsbuendig an x. Gibt die linke Kante zurueck. */
    private int fussKnopf(GuiGraphicsExtractor ctx, int rechts, int y, String text, boolean rot, float a, float dt, Runnable run) {
        int w = this.font.width(text) + 16, x = rechts - w;
        float h = hover("f:" + text, over(x, y, w, 16), dt);
        int grund = rot ? VortexStyle.mix(VortexStyle.INNER, 0xFFB91C1C, 0.12f + 0.4f * h)
                        : VortexStyle.mix(VortexStyle.INNER, VortexStyle.HOV, h);
        rund(ctx, x, y, w, 16, VortexStyle.fade(grund, a), 4);
        int farbe = rot ? VortexStyle.mix(0xFFFCA5A5, 0xFFFFFFFF, h) : VortexStyle.mix(VortexStyle.TEXT_DIM, VortexStyle.TEXT, h);
        ctx.text(this.font, Component.literal(text), x + 8, y + 4, VortexStyle.fade(farbe, a), false);
        hits.add(new Hit(x, y, w, 16, run));
        return x;
    }

    /** Sanftes, gestaffeltes Einblenden (nur Deckkraft, keine Bewegung). */
    private float einblenden(int index) {
        float t = clamp01((seit - 0.04f - index * 0.025f) / 0.22f);
        return t * t * (3f - 2f * t);
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

    private static int ping(Minecraft mc) {
        try {
            if (mc.isLocalServer() || mc.getConnection() == null || mc.player == null) return -1;
            int own = com.vortex.client.hud.PingMeter.get();
            if (own >= 0 && com.vortex.client.hud.PingMeter.age() < 15000) return own;
            var info = mc.getConnection().getPlayerInfo(mc.player.getUUID());
            return info == null ? -1 : info.getLatency();
        } catch (Throwable e) { return -1; }
    }




    private static String anzahl(java.util.function.IntSupplier n, String wort) {
        try { int z = n.getAsInt(); return z == 0 ? "No " + wort + "s yet" : z + " " + wort + (z == 1 ? "" : "s"); }
        catch (Throwable e) { return ""; }
    }

    private String cut(String s, int max) {
        if (max <= 8) return "";
        if (this.font.width(s) <= max) return s;
        String c = s;
        while (c.length() > 1 && this.font.width(c + "..") > max) c = c.substring(0, c.length() - 1);
        return c + "..";
    }

    /** 7x7-Symbol in doppelter Groesse (14x14). */
    private static void symbol2(GuiGraphicsExtractor ctx, String[] muster, int x, int y, int farbe) {
        for (int r = 0; r < muster.length; r++) {
            String z = muster[r];
            int c = 0;
            while (c < z.length()) {
                if (z.charAt(c) != '1') { c++; continue; }
                int start = c;
                while (c < z.length() && z.charAt(c) == '1') c++;
                ctx.fill(x + start * 2, y + r * 2, x + c * 2, y + r * 2 + 2, farbe);
            }
        }
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

        if (dashboard) {
            for (int i = hits.size() - 1; i >= 0; i--) {
                Hit h = hits.get(i);
                if (mx >= h.x && mx < h.x + h.w && my >= h.y && my < h.y + h.h) { h.run.run(); return true; }
            }
            return false;
        }

        // Alle Unterseiten bekommen diesen Bildschirm als Eltern -- ESC fuehrt
        // also hierher zurueck, nicht ins Spiel.
        if (in(flaeche[MODS]))      { mc.gui.setScreen(new PanelGui()); return true; }
        if (in(flaeche[BOTS]))      { mc.gui.setScreen(new BotScreen(this)); return true; }
        if (in(flaeche[WAYPOINTS])) { mc.gui.setScreen(new WaypointScreen(this)); return true; }
        if (in(flaeche[MACROS]))    { mc.gui.setScreen(new MacroScreen(this)); return true; }
        if (in(flaeche[WARDROBE]))  { mc.gui.setScreen(new SkinScreen(this)); return true; }
        if (in(flaeche[KEYS]))      { mc.gui.setScreen(new KeyListScreen(this)); return true; }
        if (in(flaeche[HUD]))       { mc.gui.setScreen(new HudEditorScreen()); return true; }
        if (in(flaeche[PRESETS]))   { mc.gui.setScreen(new PresetScreen(this)); return true; }
        if (in(flaeche[COMMUNITY])) { mc.gui.setScreen(new CommunityScreen(this)); return true; }
        if (in(flaeche[FRIENDS]))   { mc.gui.setScreen(new FriendsScreen(this)); return true; }
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
        // Das neue Logo: der Wirbel-Bogen dreht sich langsam um das V, beim
        // Oeffnen waechst es herein (LogoRenderer).
        LogoRenderer.zeichne(ctx, x, y, g, a, a);
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
