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
 * AB 4.9.1: DASHBOARD (normale Fenstergroessen)
 *   Kopf mit Logo, Version und Live-Werten (Name, FPS, Ping, Server, Uhrzeit),
 *   darunter Kacheln mit Live-Infos (Mods aktiv, Freunde online, aktives
 *   Preset, Anzahl Waypoints/Macros ...), rechts Schnellschalter fuer
 *   beliebte Module und die Freunde, die gerade online sind. Kacheln gleiten
 *   gestaffelt herein, heben sich beim Ueberfahren und bekommen eine
 *   Akzentkante. Die alte Liste bleibt fuer sehr kleine Fenster.
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

    /** Schnellschalter: diese Module, soweit vorhanden. */
    private static final String[] SCHNELL = {
        "Fullbright", "Zoom", "Toggle Sprint", "Freelook", "Keystrokes", "Motion Blur", "Hitbox", "Damage Numbers"
    };

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

        dashboard = this.width >= 440 && this.height >= 250;
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

    private void zeichneDashboard(GuiGraphicsExtractor ctx, float a, float dt) {
        Minecraft mc = Minecraft.getInstance();
        int accent = Theme.INSTANCE.accent.get() | 0xFF000000;

        // Schwebende Funken im Hintergrund
        for (int i = 0; i < 26; i++) {
            float sx = ((i * 97) % 100) / 100f, sp = 0.015f + (i % 5) * 0.006f;
            float py = 1f - ((seit * sp + (i * 37 % 100) / 100f) % 1f);
            int px = (int) (sx * this.width + Math.sin(seit * 0.6f + i) * 6);
            int pyy = (int) (py * this.height);
            int c = VortexStyle.fade(i % 2 == 0 ? VortexStyle.VIOLETT : VortexStyle.BLAU, a * (0.25f + 0.25f * (float) Math.sin(seit + i)));
            ctx.fill(px, pyy, px + 1 + i % 2, pyy + 1 + i % 2, c);
        }

        int W = Math.min(this.width - 24, 680), H = Math.min(this.height - 24, 390);
        int x0 = (this.width - W) / 2, y0 = (this.height - H) / 2 + (int) ((1f - a) * 14f);
        VortexStyle.schatten(ctx, x0, y0, W, H, a);
        rund(ctx, x0, y0, W, H, VortexStyle.fade(0xF20E0B16, a), 8);
        VortexStyle.akzentLinie(ctx, x0 + 8, y0, W - 16, a);

        // --- Kopf ------------------------------------------------------------
        zeichneLogo(ctx, x0 + 14, y0 + 9, 26, a);
        ctx.text(this.font, Component.literal("Vortex Client"), x0 + 48, y0 + 11, VortexStyle.fade(VortexStyle.TEXT, a), false);
        String sub = (VERSION.isEmpty() ? "" : "v" + VERSION + "  ·  ") + "Minecraft " + com.vortex.client.social.Social.mcVersion();
        ctx.text(this.font, Component.literal(sub), x0 + 48, y0 + 23, VortexStyle.fade(VortexStyle.TEXT_DIM, a), false);

        java.util.List<String[]> chips = new java.util.ArrayList<>();   // {Text, Farbe}
        if (mc.player != null) chips.add(new String[]{mc.player.getName().getString(), "name"});
        chips.add(new String[]{mc.getFps() + " FPS", mc.getFps() >= 60 ? "ok" : mc.getFps() >= 30 ? "warn" : "bad"});
        int ping = ping(mc);
        if (ping >= 0) chips.add(new String[]{ping + " ms", ping <= 80 ? "ok" : ping <= 150 ? "warn" : "bad"});
        chips.add(new String[]{wo(mc), "dim"});
        chips.add(new String[]{java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")), "dim"});
        int cx = x0 + W - 14;
        for (String[] c : chips) {
            String t = cut(c[0], 110);
            int w = this.font.width(t) + (c[1].equals("name") ? 18 : 12);
            cx -= w;
            rund(ctx, cx, y0 + 12, w, 16, VortexStyle.fade(VortexStyle.INNER, a), 4);
            int col = switch (c[1]) { case "ok" -> 0xFF7EE2A0; case "warn" -> 0xFFF5B942; case "bad" -> 0xFFFF8080; case "dim" -> 0xFFB9B2CC; default -> VortexStyle.TEXT; };
            int tx = cx + 6;
            if (c[1].equals("name")) { ctx.fill(cx + 6, y0 + 18, cx + 10, y0 + 22, VortexStyle.fade(0xFF22C55E, a)); tx += 6; }
            ctx.text(this.font, Component.literal(t), tx, y0 + 16, VortexStyle.fade(col, a), false);
            cx -= 5;
        }
        ctx.fill(x0 + 10, y0 + 44, x0 + W - 10, y0 + 45, VortexStyle.fade(VortexStyle.LINE, a));

        // --- Masse -------------------------------------------------------------
        int bodyTop = y0 + 54, bodyBottom = y0 + H - 32;
        int rcW = Math.max(150, Math.min(190, W / 4 + 10));
        int rcX = x0 + W - 12 - rcW;
        int tx0 = x0 + 12, tW = rcX - 10 - tx0;
        // Beta-Test: eigene Zeile, solange der Launcher eine Checkliste mitgibt.
        int[] beta = com.vortex.client.beta.BetaTest.aktiv() ? com.vortex.client.beta.BetaTest.fortschritt() : new int[]{0, 0, 0};
        boolean mitBeta = beta[1] > 0;
        int gap = 7, cols = 3, rows = mitBeta ? 5 : 4;
        int tileW = (tW - (cols - 1) * gap) / cols;
        int tileH = (bodyBottom - bodyTop - (rows - 1) * gap) / rows;

        // --- Kacheln ------------------------------------------------------------
        int aktiv = 0, alle = 0, hudAn = 0, tasten = 0, bots = 0;
        for (Module m : ModuleManager.INSTANCE.getModules()) {
            if (m.getCategory() == Module.Category.BOTS) { bots++; continue; }
            alle++;
            if (m.isEnabled()) aktiv++;
            if (m.isEnabled() && m instanceof com.vortex.client.hud.HudElement) hudAn++;
            try { if (m.getToggleKey().getKeyCode() != org.lwjgl.glfw.GLFW.GLFW_KEY_UNKNOWN) tasten++; } catch (Throwable ignored) { }
        }
        final int fAktiv = aktiv, fAlle = alle;
        Minecraft m0 = mc;
        Object[][] kacheln = {
            // {Schluessel, Titel, Untertitel, Symbol, Spalten, Aktion}
            {"mods", "Mods", aktiv + " of " + alle + " active", MODS, 2, (Runnable) () -> m0.gui.setScreen(new PanelGui())},
            {"friends", "Friends", freundeText(), FRIENDS, 1, (Runnable) () -> m0.gui.setScreen(new FriendsScreen(this))},
            {"hud", "HUD Editor", hudAn + " elements on screen", HUD, 1, (Runnable) () -> m0.gui.setScreen(new HudEditorScreen())},
            {"presets", "Presets", presetText(), PRESETS, 1, (Runnable) () -> m0.gui.setScreen(new PresetScreen(this))},
            {"waypoints", "Waypoints", anzahl(() -> com.vortex.client.waypoint.WaypointManager.all().size(), "waypoint"), WAYPOINTS, 1, (Runnable) () -> m0.gui.setScreen(new WaypointScreen(this))},
            {"macros", "Macros", anzahl(() -> com.vortex.client.macro.MacroManager.all().size(), "macro"), MACROS, 1, (Runnable) () -> m0.gui.setScreen(new MacroScreen(this))},
            {"wardrobe", "Wardrobe", "Skins and capes", WARDROBE, 1, (Runnable) () -> m0.gui.setScreen(new SkinScreen(this))},
            {"keys", "Keybinds", tasten + " module keys", KEYS, 1, (Runnable) () -> m0.gui.setScreen(new KeyListScreen(this))},
            {"community", "Community", "Shared presets and macros", COMMUNITY, 1, (Runnable) () -> m0.gui.setScreen(new CommunityScreen(this))},
            {"bots", "Bots", bots > 0 ? bots + " bots ready" : "Needs the Plus Addon", BOTS, 1, (Runnable) () -> m0.gui.setScreen(new BotScreen(this))},
            {"theme", "Theme", "Colours of the client", -1, 1, (Runnable) () -> m0.gui.setScreen(new ThemeScreen(this))},
        };
        if (mitBeta) {
            Object[][] mehr = java.util.Arrays.copyOf(kacheln, kacheln.length + 1);
            mehr[kacheln.length] = new Object[]{"beta", "Beta test", beta[0] + " of " + beta[1] + " checked"
                    + (beta[2] > 0 ? "  \u00b7  " + beta[2] + " bug(s) open" : "  \u00b7  check what works, report what doesn't"),
                    -2, 3, (Runnable) () -> m0.gui.setScreen(new com.vortex.client.beta.BetaScreen(this))};
            kacheln = mehr;
        }
        int col = 0, row = 0, idx = 0;
        for (Object[] k : kacheln) {
            int span = (Integer) k[4];
            if (col + span > cols) { col = 0; row++; }
            int x = tx0 + col * (tileW + gap), y = bodyTop + row * (tileH + gap);
            int w = tileW * span + gap * (span - 1);
            float st = clamp01((seit - 0.06f - idx * 0.03f) / 0.3f);
            float e = 1f - (1f - st) * (1f - st) * (1f - st);
            int yy = y + (int) ((1f - e) * 8f);
            float al = a * e;
            String key = (String) k[0];
            boolean ov = over(x, y, w, tileH);
            float h = hover(key, ov, dt);
            boolean haupt = key.equals("mods");
            int grund = haupt ? VortexStyle.mix(VortexStyle.CARD, accent, 0.22f + 0.18f * h)
                              : VortexStyle.mix(VortexStyle.CARD, accent, 0.05f + 0.17f * h);
            int lift = Math.round(-2f * h);
            rund(ctx, x, yy + lift, w, tileH, VortexStyle.fade(grund, al), 5);
            // Obere Kante im Akzent, waechst beim Ueberfahren
            int kw = (int) ((w - 10) * (haupt ? 1f : h));
            if (kw > 0) ctx.fill(x + 5, yy + lift, x + 5 + kw, yy + lift + 1, VortexStyle.fade(haupt ? VortexStyle.akzent(0.2f) : accent, al));
            int sym = (Integer) k[3];
            int icoCol = VortexStyle.fade(h > 0.5f || haupt ? 0xFFFFFFFF : VortexStyle.mix(accent, 0xFFFFFFFF, 0.25f), al);
            boolean flach = tileH < 46;
            int ix = x + 10, iy = yy + lift + (flach ? (tileH - 14) / 2 : 10);
            if (sym >= 0) symbol2(ctx, SYMBOLE[sym], ix, iy, icoCol);
            else if (sym == -2) {
                // Haken-Symbol fuer den Beta-Test
                ctx.fill(ix, iy, ix + 14, iy + 14, VortexStyle.fade(0x404ADE80, al));
                ctx.fill(ix + 3, iy + 7, ix + 5, iy + 9, icoCol);
                ctx.fill(ix + 5, iy + 9, ix + 7, iy + 11, icoCol);
                ctx.fill(ix + 7, iy + 7, ix + 9, iy + 9, icoCol);
                ctx.fill(ix + 9, iy + 5, ix + 11, iy + 7, icoCol);
                ctx.fill(ix + 11, iy + 3, ix + 13, iy + 5, icoCol);
            }
            else palette(ctx, ix, iy, al);
            String titel = (String) k[1], unter = (String) k[2];
            if (flach) {
                ctx.text(this.font, Component.literal(titel), ix + 22, yy + lift + (tileH - 8) / 2 - 4, VortexStyle.fade(VortexStyle.TEXT, al), false);
                ctx.text(this.font, Component.literal(cut(unter, w - 40)), ix + 22, yy + lift + (tileH - 8) / 2 + 6, VortexStyle.fade(0xFF9A93B0, al), false);
            } else {
                ctx.text(this.font, Component.literal(titel), ix, yy + lift + tileH - 26, VortexStyle.fade(VortexStyle.TEXT, al), false);
                ctx.text(this.font, Component.literal(cut(unter, w - 20)), ix, yy + lift + tileH - 14, VortexStyle.fade(0xFF9A93B0, al), false);
            }
            if (haupt && !flach) {
                // Anteil aktiver Module als Balken
                int bw = Math.min(160, w - 40), bx = x + w - bw - 12, by = yy + lift + tileH - 12;
                ctx.fill(bx, by, bx + bw, by + 3, VortexStyle.fade(0x40FFFFFF, al));
                int fill = fAlle == 0 ? 0 : bw * fAktiv / fAlle;
                for (int i = 0; i < fill; i += 2) ctx.fill(bx + i, by, bx + Math.min(i + 2, fill), by + 3, VortexStyle.fade(VortexStyle.akzent((float) i / bw), al));
                String open = "Open  >";
                ctx.text(this.font, Component.literal(open), x + w - 12 - this.font.width(open), yy + lift + 10, VortexStyle.fade(0xFFE9E3FF, al * (0.6f + 0.4f * h)), false);
            }
            if (key.equals("friends")) {
                int neu = com.vortex.client.social.Social.data() == null ? 0 : com.vortex.client.social.Social.unreadTotal();
                if (neu > 0) {
                    String b = neu > 99 ? "99+" : String.valueOf(neu);
                    int bw = this.font.width(b) + 8;
                    rund(ctx, x + w - bw - 8, yy + lift + 8, bw, 12, VortexStyle.fade(VortexStyle.VIOLETT, al), 6);
                    ctx.text(this.font, Component.literal(b), x + w - bw - 4, yy + lift + 10, VortexStyle.fade(0xFFFFFFFF, al), false);
                }
            }
            hits.add(new Hit(x, y, w, tileH, (Runnable) k[5]));
            col += span;
            idx++;
        }

        // --- Rechte Spalte: Schnellschalter ----------------------------------------
        java.util.List<Module> schnell = new java.util.ArrayList<>();
        for (String n : SCHNELL) for (Module m : ModuleManager.INSTANCE.getModules()) if (m.getName().equalsIgnoreCase(n)) { schnell.add(m); break; }
        int zeile = 17;
        int c1H = 20 + schnell.size() * zeile + 4;
        int cy = bodyTop;
        rund(ctx, rcX, cy, rcW, c1H, VortexStyle.fade(VortexStyle.CARD, a), 5);
        ctx.text(this.font, Component.literal("QUICK TOGGLES"), rcX + 10, cy + 7, VortexStyle.fade(VortexStyle.akzent(0.3f), a), false);
        int ry = cy + 20;
        for (Module m : schnell) {
            boolean ov = over(rcX + 4, ry, rcW - 8, zeile);
            float h = hover("q:" + m.getName(), ov, dt);
            if (h > 0.01f) rund(ctx, rcX + 4, ry, rcW - 8, zeile, VortexStyle.fade(VortexStyle.HOV, a * h), 3);
            ctx.text(this.font, Component.literal(cut(m.getName(), rcW - 50)), rcX + 10, ry + 5, VortexStyle.fade(m.isEnabled() ? VortexStyle.TEXT : 0xFF9A93B0, a), false);
            float an = hover("s:" + m.getName(), m.isEnabled(), dt);
            int sx = rcX + rcW - 32, sy = ry + 4;
            rund(ctx, sx, sy, 20, 10, VortexStyle.fade(VortexStyle.mix(0xFF2A2340, accent, an), a), 5);
            int knob = sx + 1 + (int) (10 * an);
            rund(ctx, knob, sy + 1, 8, 8, VortexStyle.fade(0xFFFFFFFF, a), 4);
            final Module mod = m;
            hits.add(new Hit(rcX + 4, ry, rcW - 8, zeile, () -> { mod.toggle(); com.vortex.client.core.ConfigManager.save(); }));
            ry += zeile;
        }

        // --- Rechte Spalte: Freunde ----------------------------------------------------
        int c2Y = cy + c1H + 7, c2H = bodyBottom - c2Y;
        if (c2H > 40) {
            boolean ov2 = over(rcX, c2Y, rcW, c2H);
            float h2 = hover("friendsCard", ov2, dt);
            rund(ctx, rcX, c2Y, rcW, c2H, VortexStyle.fade(VortexStyle.mix(VortexStyle.CARD, accent, 0.06f * h2), a), 5);
            ctx.text(this.font, Component.literal("FRIENDS ONLINE"), rcX + 10, c2Y + 7, VortexStyle.fade(VortexStyle.akzent(0.6f), a), false);
            var conn = com.vortex.client.social.Social.conn();
            int fy = c2Y + 21;
            if (conn != com.vortex.client.social.Social.Conn.ONLINE || com.vortex.client.social.Social.data() == null) {
                String t = conn == com.vortex.client.social.Social.Conn.DISABLED ? "Start from the launcher" : conn == com.vortex.client.social.Social.Conn.CONNECTING ? "Connecting..." : "Not connected";
                ctx.text(this.font, Component.literal(t), rcX + 10, fy, VortexStyle.fade(VortexStyle.TEXT_DIM, a), false);
            } else {
                int n = 0, gezeigt = 0;
                int platz = Math.max(0, (c2Y + c2H - 6 - fy) / 20);
                for (var f : com.vortex.client.social.Social.friends()) {
                    var p = com.vortex.client.social.Social.obj(f, "presence");
                    String state = com.vortex.client.social.Social.str(p, "state");
                    if (state.isEmpty() || state.equals("offline")) continue;
                    n++;
                    if (gezeigt >= platz) continue;
                    gezeigt++;
                    int dot = state.equals("playing") ? 0xFF60A5FA : 0xFF22C55E;
                    String mode = com.vortex.client.social.Social.str(p, "mode");
                    if (mode.equals("dnd")) dot = 0xFFEF4444; else if (mode.equals("away")) dot = 0xFFF5B942;
                    ctx.fill(rcX + 10, fy + 3, rcX + 14, fy + 7, VortexStyle.fade(dot, a));
                    ctx.text(this.font, Component.literal(cut(com.vortex.client.social.Social.label(f, com.vortex.client.social.Social.str(f, "name")), rcW - 30)), rcX + 19, fy, VortexStyle.fade(VortexStyle.TEXT, a), false);
                    ctx.text(this.font, Component.literal(cut(com.vortex.client.social.Social.presenceText(p, false), rcW - 30)), rcX + 19, fy + 9, VortexStyle.fade(0xFF8A83A0, a), false);
                    fy += 20;
                }
                if (n == 0) ctx.text(this.font, Component.literal("Nobody online right now"), rcX + 10, fy, VortexStyle.fade(VortexStyle.TEXT_DIM, a), false);
                else if (n > gezeigt) ctx.text(this.font, Component.literal("+" + (n - gezeigt) + " more"), rcX + 10, fy, VortexStyle.fade(VortexStyle.TEXT_DIM, a), false);
            }
            hits.add(new Hit(rcX, c2Y, rcW, c2H, () -> Minecraft.getInstance().gui.setScreen(new FriendsScreen(this))));
        }

        // --- Fuss ----------------------------------------------------------------------
        int fy = y0 + H - 24;
        ctx.fill(x0 + 10, fy - 4, x0 + W - 10, fy - 3, VortexStyle.fade(VortexStyle.LINE, a));
        ctx.text(this.font, Component.literal("Right Shift opens this menu  ·  ESC closes"), x0 + 14, fy + 5, VortexStyle.fade(VortexStyle.TEXT_DIM, a * 0.8f), false);
        String rs = "Restart Game";
        int rw = this.font.width(rs) + 20, rx = x0 + W - 12 - rw;
        boolean rov = over(rx, fy, rw, 18);
        float rh = hover("restart", rov, dt);
        rund(ctx, rx, fy, rw, 18, VortexStyle.fade(VortexStyle.mix(VortexStyle.INNER, 0xFFB91C1C, 0.15f + 0.4f * rh), a), 4);
        ctx.text(this.font, Component.literal(rs), rx + 10, fy + 5, VortexStyle.fade(VortexStyle.mix(0xFFFCA5A5, 0xFFFFFFFF, rh), a), false);
        hits.add(new Hit(rx, fy, rw, 18, () -> { frageNeustart = true; fehler = null; }));
        if (fehler != null) {
            ctx.text(this.font, Component.literal(fehler), (this.width - this.font.width(fehler)) / 2, Math.min(this.height - 11, y0 + H + 4), VortexStyle.fade(0xFFF87171, a), false);
        }
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

    private static String wo(Minecraft mc) {
        if (mc.level == null) return "Main menu";
        if (mc.isLocalServer()) return "Singleplayer";
        var sd = mc.getCurrentServer();
        if (sd == null) return "Multiplayer";
        return sd.name != null && !sd.name.isBlank() ? sd.name : sd.ip;
    }

    private static String freundeText() {
        var c = com.vortex.client.social.Social.conn();
        if (c == com.vortex.client.social.Social.Conn.DISABLED) return "Start from the launcher";
        if (c != com.vortex.client.social.Social.Conn.ONLINE || com.vortex.client.social.Social.data() == null) return "Connecting...";
        int on = 0;
        for (var f : com.vortex.client.social.Social.friends()) {
            String s = com.vortex.client.social.Social.str(com.vortex.client.social.Social.obj(f, "presence"), "state");
            if (!s.isEmpty() && !s.equals("offline")) on++;
        }
        return on + " online  ·  " + com.vortex.client.social.Social.friends().size() + " friends";
    }

    private static String presetText() {
        try { return "Active: " + com.vortex.client.core.ConfigManager.presetName(com.vortex.client.core.ConfigManager.getActivePreset()); }
        catch (Throwable e) { return "Save and switch setups"; }
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

    /** Symbol fuer "Theme": vier Farbfelder im Vortex-Verlauf. */
    private static void palette(GuiGraphicsExtractor ctx, int x, int y, float a) {
        ctx.fill(x, y, x + 6, y + 6, VortexStyle.fade(VortexStyle.VIOLETT, a));
        ctx.fill(x + 8, y, x + 14, y + 6, VortexStyle.fade(VortexStyle.akzent(0.5f), a));
        ctx.fill(x, y + 8, x + 6, y + 14, VortexStyle.fade(VortexStyle.BLAU, a));
        ctx.fill(x + 8, y + 8, x + 14, y + 14, VortexStyle.fade(0xFFE9E3FF, a));
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
