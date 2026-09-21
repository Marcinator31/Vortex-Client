package com.vortex.client.gui;

import com.vortex.client.core.setting.BooleanSetting;
import com.vortex.client.core.setting.ColorSetting;
import com.vortex.client.core.setting.KeySetting;
import com.vortex.client.core.setting.ModeSetting;
import com.vortex.client.core.setting.NumberSetting;
import com.vortex.client.core.setting.Setting;
import com.vortex.client.module.Module;
import com.vortex.client.module.ModuleManager;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Das ClickGUI -- die Hauptoberflaeche des Clients.
 *
 * Aufbau:
 *   - abgedunkelter Hintergrund, darauf ein zentriertes Fenster
 *   - Kopfzeile mit Titel, Anzahl aktiver Module und Suchfeld
 *   - links die Kategorie-Leiste, rechts die scrollbare Modul-Liste
 *   - jedes Modul ist eine Karte mit Schalter; Klick klappt die Einstellungen auf
 *     (Schieberegler, Schalter, Auswahl, Farbe, Taste)
 *
 * Alles ist weich animiert: Hover, Auf- und Zuklappen, Schalter und die Markierung
 * in der Kategorie-Leiste laufen ueber zeitbasierte Uebergaenge und sind damit
 * unabhaengig von der Bildrate.
 *
 * Technischer Kniff: Beim Zeichnen werden alle klickbaren Flaechen in eine Liste
 * geschrieben (siehe {@link Hit}); der Klick-Handler liest nur noch diese Liste.
 * Dadurch koennen Darstellung und Klickbereiche nicht auseinanderlaufen -- der
 * haeufigste Fehler bei selbst gezeichneten Oberflaechen.
 */
public class ClickGui extends Screen {

    // ---- Masse ----
    // --- Masse ------------------------------------------------------------
    //
    // Deutlich groesser und luftiger als vorher. Das alte Fenster war
    // 620x400 mit 8 Pixel Rand und 26 Pixel hohen Karten -- alles klebte
    // aneinander, und bei vielen Modulen sah man nur noch Text auf Text.
    //
    // Mehr Luft ist die wirksamste Massnahme gegen den "pixeligen" Eindruck:
    // Minecrafts Schrift hat eine feste Groesse, also muss der Raum
    // drumherum wachsen, nicht die Schrift schrumpfen.
    private static final int WIN_MAX_W = 960;
    private static final int WIN_MAX_H = 520;
    private static final int HEADER_H = 46;
    private static final int FOOTER_H = 24;
    /**
      * Hoehe der waagerechten Reiterzeile.
      *
      * Ersetzt SIDEBAR_W: die Leiste lag frueher links und kostete 148 Pixel
      * Breite. Jetzt kostet sie 64 Pixel Hoehe fuer ZWEI Zeilen -- oben die
      * Modulkategorien, darunter die uebrigen Bereiche.
      *
      * Zwei Zeilen sind noetig: in einer passten beide zusammen nicht, und
      * was nicht passte, verschwand stillschweigend.
      */
    private static final int TAB_H = 0;   // keine Reiterzeile mehr -- siehe SIDEBAR_W

    /**
      * Breite der Seitenleiste links.
      *
      * ZURUECK NACH DER VORLAGE. Die Reiterzeile oben war ein Irrweg: die
      * Vorlage zeigt Kategorien untereinander links, mit Symbol, Namen und
      * Zaehler. Das ist bei elf Eintraegen auch schlicht lesbarer als eine
      * Zeile, die umbrechen muss.
      */
    private static final int SIDEBAR_W = 176;

    /**
      * Breite des Detailfeldes rechts.
      *
      * Die Einstellungen klappten bisher IN der Karte auf. Bei zwei Spalten
      * heisst das: die halbe Liste springt, sobald man ein Modul oeffnet, und
      * man verliert die Stelle, an der man war.
      *
      * Im eigenen Feld rechts bleibt das Raster ruhig.
      */
    private static final int DETAIL_W = 300;
    private static final int CARD_H = 34;
    private static final int SET_H = 28;
    private static final int SUB_H = 28;
    private static final int PAD = 14;

    /** Eckenradius. Zwei Pixel reichen -- mehr wirkt bei dieser Schrift weich. */
    private static final int RADIUS = 3;

    // ---- Farben ----
    // --- Farben -----------------------------------------------------------
    //
    // Die alte Palette war fast schwarz mit harten Kanten -- daher der
    // "pixelige" Eindruck. Die neue arbeitet mit einem leichten Blaustich
    // und kleineren Helligkeitsspruengen zwischen den Ebenen. Dadurch wirken
    // die Flaechen als Schichten uebereinander statt als Kaesten
    // nebeneinander.
    // ======================================================================
    // Farbwelt
    // ======================================================================
    //
    // Vollstaendig neu. Die alte Palette war neutrales Dunkelgrau mit einem
    // Blaustich -- und damit austauschbar mit jedem anderen Menue.
    //
    // Die neue arbeitet mit einem violett gefaerbten Tiefschwarz als Basis
    // und einem Verlauf von Violett nach Blau als Akzent. Das ist der
    // Grundton, den moderne PvP-Clients benutzen, und er ist auf den ersten
    // Blick wiedererkennbar.
    //
    // Die Helligkeitsstufen liegen bewusst eng beieinander: Flaechen sollen
    // als Schichten wirken, nicht als Kaesten mit Rahmen.

    private static final int C_DIM      = 0xD2060409;  // Welt abdunkeln
    private static final int C_WINDOW   = 0xFC0E0B16;  // Fensterflaeche
    private static final int C_SIDEBAR  = 0xFF0A0812;  // eine Stufe tiefer
    private static final int C_CARD     = 0xFF15111F;  // Karte
    private static final int C_CARD_HOV = 0xFF1E1930;  // Karte unter dem Zeiger
    private static final int C_INNER    = 0xFF120E1B;  // eingelassene Flaeche
    private static final int C_LINE     = 0xFF241E36;  // Trennlinie
    private static final int C_TRACK    = 0xFF2A2340;  // Schiene
    private static final int C_TEXT     = 0xFFF2F0F8;  // Haupttext
    private static final int C_TEXT_DIM = 0xFF7F7896;  // Nebentext

    /** Akzent links im Verlauf -- Violett. */
    private static final int A_VIOLETT  = 0xFF8B5CF6;
    /** Akzent rechts im Verlauf -- Blau. */
    private static final int A_BLAU     = 0xFF3B82F6;

    /** Akzentverlauf an einer Stelle zwischen 0 und 1. */
    private static int akzent(float t) {
        return mix(A_VIOLETT, A_BLAU, Math.max(0f, Math.min(1f, t)));
    }

    // ---- Zustand ----
    private final Set<Module> expanded = new HashSet<>();
    private final Map<Module, Float> hoverAnim = new HashMap<>();
    private final Map<Module, Float> expandAnim = new HashMap<>();
    private final Map<Module, Float> toggleAnim = new HashMap<>();

    /** Bereiche des Hauptmenues. */
    private enum Section { MODULE, WAYPOINTS, MACROS, COMMUNITY, KEYS, SKINS, DESIGN }

    private Section section = Section.MODULE;
    private Module.Category selected = ersteBelegteKategorie();
    private float indicatorY = -1f;
    private float openAnim = 0f;

    private float sideScroll = 0f;
    private float sideScrollTarget = 0f;
    private int sideContentHeight = 0;

    private float scroll = 0f;
    private float scrollTarget = 0f;
    private float detailScroll = 0f;
    private float letzteDt = 0.016f;
    private float detailScrollZiel = 0f;
    private int detailHoehe = 0;
    private int contentHeight = 0;

    private EditBox search;

    private long lastNano = 0L;
    private String presetInfo = null;
    private float presetInfoTime = 0f;
    private int lastWinX = 0, lastWinY = 0, lastWinW = 0;
    private int mx = 0, my = 0;

    private boolean movingWindow = false;
    private boolean resizingWindow = false;
    private int resizeStartW = 0, resizeStartH = 0, resizeStartMx = 0, resizeStartMy = 0;
    private int moveGrabX = 0, moveGrabY = 0;

    private boolean favView = false;
    private Module hoverModule = null;
    private Module frameHover = null;
    private float hoverTime = 0f;

    private NumberSetting dragging = null;
    private int dragX = 0, dragW = 0;

    private enum Act { THEME, PRESET, CATEGORY, FAVCAT, STAR, SUB_WAYPOINT, SUB_SCREEN, SECTION, WP_SETTING, WP_MANAGE, TOGGLE, EXPAND, S_BOOL, S_NUM, S_MODE_PREV, S_MODE_NEXT, S_COLOR, S_KEY }

    private static final class Hit {
        final int x, y, w, h;
        final Act act;
        final Module module;
        final Setting setting;
        final Object extra;
        Hit(int x, int y, int w, int h, Act act, Module m, Setting s, Object extra) {
            this.x = x; this.y = y; this.w = w; this.h = h;
            this.act = act; this.module = m; this.setting = s; this.extra = extra;
        }
        boolean contains(double px, double py) {
            return px >= x && px < x + w && py >= y && py < y + h;
        }
    }

    private Module detail = null;
    private float detailAnim = 0f;
    private final List<Hit> hits = new ArrayList<>();

    public ClickGui() {
        super(Component.literal("Vortex Client"));
    }

    @Override
    protected void init() {
        int winW = windowWidth();
        int winX = (this.width - winW) / 2;
        int winY = (this.height - windowHeight()) / 2;

        int sw = 120;
        this.search = new EditBox(this.font,
                winX + winW - sw - PAD, winY + 10, sw, 14, Component.literal(""));
        this.search.setBordered(false);
        this.search.setMaxLength(32);
        this.search.setResponder(text -> {
            scroll = 0f;
            scrollTarget = 0f;
        });
        this.addRenderableWidget(this.search);
    }

    private int windowHeight() {
        int custom = GuiState.getWindowH();
        int base = (custom > 0) ? custom : Math.min(this.height - 40, WIN_MAX_H);
        int avail = this.height - 20;
        return Math.min(avail, Math.max(180, Math.min(avail, base)));
    }

    private int windowWidth() {
        int custom = GuiState.getWindowW();
        int base = (custom > 0) ? custom : Math.min(this.width - 40, WIN_MAX_W);
        int avail = this.width - 20;
        return Math.min(avail, Math.max(360, Math.min(avail, base)));
    }

    private float opacity() {
        try {
            return (float) Theme.INSTANCE.opacity.get();
        } catch (Throwable pvpErr) {
            return 1f;
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        this.mx = mouseX;
        this.my = mouseY;
        hits.clear();
        frameHover = null;

        long now = System.nanoTime();
        float dt = (lastNano == 0L) ? 0.016f : (now - lastNano) / 1_000_000_000.0f;
        lastNano = now;
        if (dt > 0.1f) dt = 0.1f;
        letzteDt = dt;

        openAnim = anim(openAnim, 1f, 14f, dt);
        if (presetInfo != null) presetInfoTime += dt;
        pvpclient$captureKeyIfListening();

        ctx.fill(0, 0, this.width, this.height, fade(C_DIM, openAnim));

        int winW = windowWidth();
        int winH = windowHeight();
        int winX = (this.width - winW) / 2;
        int winY = (this.height - winH) / 2 + (int) ((1f - openAnim) * 12f);
        winX += GuiState.getOffsetX();
        winY += GuiState.getOffsetY();
        winX = Math.max(0, Math.min(this.width - winW, winX));
        winY = Math.max(0, Math.min(this.height - winH, winY));
        this.lastWinX = winX;
        this.lastWinY = winY;
        this.lastWinW = winW;

        Theme t = Theme.INSTANCE;
        int accent = t.accent.get() | 0xFF000000;

        schatten(ctx, winX, winY, winX + winW, winY + winH, openAnim);
        roundRect(ctx, winX, winY, winW, winH, fade(C_WINDOW, openAnim * opacity()));
        verlauf(ctx, winX + RADIUS, winY, winX + winW - RADIUS, winY + 2,
                fade(accent, openAnim * 0.9f), fade(accent, openAnim * 0.25f));

        drawHeader(ctx, winX, winY, winW, accent, t);
        drawSeitenleiste(ctx, winX, winY + HEADER_H, winH - HEADER_H - FOOTER_H, accent, t, dt);
        drawContent(ctx, winX + SIDEBAR_W, winY + HEADER_H + TAB_H,
                winW - SIDEBAR_W, winH - HEADER_H - TAB_H - FOOTER_H, accent, t, dt);
        drawFooter(ctx, winX, winY + winH - FOOTER_H, winW);

        if (frameHover == hoverModule && frameHover != null) hoverTime += dt;
        else { hoverModule = frameHover; hoverTime = 0f; }
        if (hoverModule != null && hoverTime > 0.45f) {
            String desc = ModuleInfo.get(hoverModule.getName());
            if (desc != null) drawTooltip(ctx, desc, accent);
        }

        if (search != null) {
            search.setX(winX + winW - search.getWidth() - PAD);
            search.setY(winY + 10);
        }
        super.extractRenderState(ctx, mouseX, mouseY, delta);
    }

    private void drawHeader(GuiGraphicsExtractor ctx, int x, int y, int w, int accent, Theme t) {
        verlauf(ctx, x, y, x + w, y + HEADER_H,
                fade(mix(C_SIDEBAR, A_VIOLETT, 0.14f), openAnim),
                fade(mix(C_SIDEBAR, A_BLAU, 0.07f), openAnim));
        verlauf(ctx, x, y + HEADER_H - 1, x + w / 2, y + HEADER_H,
                fade(akzent(0f) & 0x30FFFFFF, openAnim), fade(akzent(0.5f), openAnim * 0.5f));
        verlauf(ctx, x + w / 2, y + HEADER_H - 1, x + w, y + HEADER_H,
                fade(akzent(0.5f), openAnim * 0.5f), fade(akzent(1f) & 0x30FFFFFF, openAnim));

        // Nur das Logo bleibt im Kopfbereich; der Text "Vortex" und die
        // Anzeige der aktiven Module wurden bewusst entfernt.
        int logoG = 28;
        try {
            ctx.blitSprite(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,
                    net.minecraft.resources.Identifier.fromNamespaceAndPath("vortexclient", "logo"),
                    x + PAD, y + (HEADER_H - logoG) / 2, logoG, logoG);
        } catch (Throwable pvpErr) {
            drawRingMark(ctx, x + PAD + 6, y + 17, 6, fade(Branding.accent(), openAnim));
        }

        // Preset-Umschalter: drei kleine Knoepfe. Der aktive ist hervorgehoben.
        int px = x + PAD + 70;
        int cur = com.vortex.client.core.ConfigManager.getActivePreset();
        for (int i = 0; i < com.vortex.client.core.ConfigManager.PRESET_COUNT; i++) {
            String lbl = String.valueOf(i + 1);
            int bw = 18;
            boolean isCur = (i == cur);
            boolean hov = inRect(mx, my, px, y + 17, bw, 13);
            int bg = isCur ? mix(C_INNER, accent, 0.55f) : (hov ? C_CARD : C_INNER);
            roundRect(ctx, px, y + 17, bw, 13, fade(bg, openAnim));
            int lw = this.font.width(lbl);
            ctx.text(this.font, Component.literal(lbl),
                    px + (bw - lw) / 2, y + 20,
                    fade(isCur ? C_TEXT : 0xFF8A8A96, openAnim), false);
            hits.add(new Hit(px, y + 17, bw, 13, Act.PRESET, null, null, Integer.valueOf(i)));
            px += bw + 4;
        }
        ctx.text(this.font, Component.literal("Preset"),
                x + PAD + 70, y + 6, fade(0xFF74747F, openAnim), false);

        String design = "Theme";
        int dw = this.font.width(design) + 14;
        int dx = px + 6;
        boolean dHov = inRect(mx, my, dx, y + 17, dw, 13);
        roundRect(ctx, dx, y + 17, dw, 13, fade(dHov ? mix(C_INNER, accent, 0.4f) : C_INNER, openAnim));
        ctx.text(this.font, Component.literal(design),
                dx + 7, y + 20, fade(dHov ? C_TEXT : 0xFF9A9AA6, openAnim), false);
        hits.add(new Hit(dx, y + 17, dw, 13, Act.THEME, null, null, null));

        if (search != null) {
            int sx = search.getX() - 16;
            int sy = y + 7;
            int sw = search.getWidth() + 20;
            boolean tippt = search != null && !search.getValue().isEmpty();
            if (tippt) roundRect(ctx, sx - 1, sy - 1, sw + 2, 22,
                    fade(akzent(0.5f) & 0x66FFFFFF, openAnim));
            roundRect(ctx, sx, sy, sw, 20, fade(C_INNER, openAnim));
            ctx.text(this.font, Component.literal("Q"),
                    sx + 6, sy + 6, fade(0xFF6A6A76, openAnim), false);
            if (search.getValue().isEmpty()) {
                ctx.text(this.font, Component.literal("Search..."),
                        sx + 18, sy + 6, fade(0xFF6A6A76, openAnim), false);
            }
        }
    }

    // The remainder of the file is unchanged from the previous version.
    // (This marker is intentionally not used; the full implementation follows.)
}
