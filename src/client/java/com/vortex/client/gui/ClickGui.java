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

    /**
     * Akzentverlauf an einer Stelle zwischen 0 und 1.
     *
     * Ein einzelner Farbwert wirkt flach. Der Verlauf gibt Balken, Schaltern
     * und dem ausgewaehlten Reiter Tiefe, ohne dass man dafuer zeichnen muss.
     */
    private static int akzent(float t) {
        return mix(A_VIOLETT, A_BLAU, Math.max(0f, Math.min(1f, t)));
    }

    // ---- Zustand ----
    private final Set<Module> expanded = new HashSet<>();
    private final Map<Module, Float> hoverAnim = new HashMap<>();
    private final Map<Module, Float> expandAnim = new HashMap<>();
    private final Map<Module, Float> toggleAnim = new HashMap<>();

    /**
     * Bereiche des Hauptmenues.
     *
     * Frueher zeigte die Leiste ausschliesslich Modul-Kategorien. Mit Waypoints,
     * Design und Skins gibt es aber Dinge, die keine Module sind -- die gehoeren
     * nicht in die Kategorie-Liste, sondern gleichberechtigt daneben.
     */
    private enum Section { MODULE, WAYPOINTS, MACROS, COMMUNITY, KEYS, SKINS, DESIGN }

    private Section section = Section.MODULE;
    private Module.Category selected = ersteBelegteKategorie();
    private float indicatorY = -1f;
    private float openAnim = 0f;

    // Scrollen der Kategorie-Leiste. Bei kleinem Fenster oder vielen Eintraegen
    // passt sonst nicht alles hinein -- die unteren Bereiche waren schlicht
    // nicht erreichbar.
    private float sideScroll = 0f;
    private float sideScrollTarget = 0f;
    private int sideContentHeight = 0;

    private float scroll = 0f;
    private float scrollTarget = 0f;
    /** Bildlauf im Detailfeld rechts -- eigener Wert, eigene Grenze. */
    private float detailScroll = 0f;
    /** Zeit des letzten Bildes -- fuer Animationen ausserhalb des Hauptpfads. */
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

    // Fenster verschieben: Position kommt aus GuiState (ueberlebt Schliessen).
    private boolean movingWindow = false;
    // Fenstergroesse ziehen (Griff unten rechts).
    private boolean resizingWindow = false;
    private int resizeStartW = 0, resizeStartH = 0, resizeStartMx = 0, resizeStartMy = 0;
    private int moveGrabX = 0, moveGrabY = 0;

    // Zeigt gerade die Favoriten-Liste statt einer Kategorie?
    private boolean favView = false;

    // Fuer den Hinweistext: wie lange steht die Maus schon auf derselben Karte?
    private Module hoverModule = null;
    private Module frameHover = null;
    private float hoverTime = 0f;

    private NumberSetting dragging = null;
    private int dragX = 0, dragW = 0;

    // ---- Klickflaechen (beim Zeichnen gefuellt) ----
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

    /** Modul, dessen Einstellungen rechts stehen. null = kein Feld. */
    private Module detail = null;
    private float detailAnim = 0f;

    private final List<Hit> hits = new ArrayList<>();

    /**
     * Oeffnet das ClickGUI direkt in einer bestimmten Kategorie.
     *
     * Fuer den Startbildschirm: dort gibt es einen eigenen Knopf "Bots", der
     * ohne Umweg in die Bot-Module fuehren soll.
     */
    public ClickGui(Module.Category start) {
        this();
        if (start != null && hatModule(start)) {
            this.selected = start;
            this.section = Section.MODULE;
        }
    }

    public ClickGui() {
        super(Component.literal("Vortex Client"));
    }

    // ---------------------------------------------------------------- Aufbau

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
        // Bei Texteingabe nach oben scrollen -- sonst liegen die Treffer
        // oberhalb des sichtbaren Bereichs, und es sieht aus, als haette die
        // Suche nichts gefunden.
        this.search.setResponder(text -> {
            scroll = 0f;
            scrollTarget = 0f;
        });

        this.addRenderableWidget(this.search);
    }

    /**
     * Fensterhoehe: entweder die vom Nutzer gezogene oder der Standard.
     * Immer so begrenzt, dass sie auf den Bildschirm passt.
     */
    private int windowHeight() {
        int custom = GuiState.getWindowH();
        int base = (custom > 0) ? custom : Math.min(this.height - 40, WIN_MAX_H);
        // The lower bound must never beat the screen: on a very small display
        // Math.max would otherwise hand back 180 even when only 140 are there,
        // and the window would reach past the edge.
        int avail = this.height - 20;
        return Math.min(avail, Math.max(180, Math.min(avail, base)));
    }

    /** Fensterbreite -- analog zur Hoehe. */
    private int windowWidth() {
        int custom = GuiState.getWindowW();
        int base = (custom > 0) ? custom : Math.min(this.width - 40, WIN_MAX_W);
        int avail = this.width - 20;
        return Math.min(avail, Math.max(360, Math.min(avail, base)));
    }

    /** Deckkraft aus dem Design -- macht das Fenster auf Wunsch durchsichtig. */
    private float opacity() {
        try {
            return 1f;   // feste Deckkraft -- das Theme ist nicht mehr einstellbar
        } catch (Throwable pvpErr) {
            return 1f;
        }
    }

    // -------------------------------------------------------------- Zeichnen

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

        // Kein renderBackground() -- das loest in 1.21.11 einen Blur aus, der pro
        // Bild nur einmal erlaubt ist. Stattdessen selbst abdunkeln.
        ctx.fill(0, 0, this.width, this.height, fade(C_DIM, openAnim));

        int winW = windowWidth();
        int winH = windowHeight();
        int winX = (this.width - winW) / 2;
        int winY = (this.height - winH) / 2 + (int) ((1f - openAnim) * 12f);
        // Vom Nutzer verschobene Position dazurechnen, aber im Bild halten.
        winX += GuiState.getOffsetX();
        winY += GuiState.getOffsetY();
        winX = Math.max(0, Math.min(this.width - winW, winX));
        winY = Math.max(0, Math.min(this.height - winH, winY));
        this.lastWinX = winX;
        this.lastWinY = winY;
        this.lastWinW = winW;

        Theme t = Theme.INSTANCE;
        // Akzent fest aus der Palette, nicht mehr aus dem Theme.
        //
        // Das Theme laesst sich nicht mehr einstellen. Hatte jemand dort
        // frueher eine eigene Farbe gewaehlt, waere sie sonst fuer immer
        // geblieben -- ohne Weg zurueck. So sieht das Menue bei allen gleich
        // aus, genau wie es gestaltet ist.
        int accent = akzent(0.5f);

        // openAnim blendet ein, opacity() ist die eingestellte Durchsichtigkeit.
        // Schatten zuerst: er liegt unter dem Fenster und hebt es vom
        // Spielgeschehen ab. Das ersetzt den harten Rahmen von frueher.
        schatten(ctx, winX, winY, winX + winW, winY + winH, openAnim);
        roundRect(ctx, winX, winY, winW, winH, fade(C_WINDOW, openAnim * opacity()));
        // Akzentlinie oben, in der Breite verlaufend -- sie gibt dem Fenster
        // einen Anfang, ohne einen harten Rahmen zu ziehen.
        verlauf(ctx, winX + RADIUS, winY, winX + winW - RADIUS, winY + 2,
                fade(accent, openAnim * 0.9f), fade(accent, openAnim * 0.25f));

        drawHeader(ctx, winX, winY, winW, accent, t);
        drawSeitenleiste(ctx, winX, winY + HEADER_H, winH - HEADER_H - FOOTER_H, accent, t, dt);
        // Volle Breite -- die Seitenleiste ist weg. Und die Hoehe um die
        // Reiterzeile verringert, sonst laeuft der Inhalt in die Fusszeile.
        drawContent(ctx, winX + SIDEBAR_W, winY + HEADER_H + TAB_H,
                winW - SIDEBAR_W, winH - HEADER_H - TAB_H - FOOTER_H, accent, t, dt);
        drawFooter(ctx, winX, winY + winH - FOOTER_H, winW);

        // Hinweistext: erscheint, wenn die Maus kurz auf einer Karte steht.
        if (frameHover == hoverModule && frameHover != null) {
            hoverTime += dt;
        } else {
            hoverModule = frameHover;
            hoverTime = 0f;
        }
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
        // Kopf mit Verlauf statt einer flachen Flaeche.
        //
        // Links ein Hauch Akzentfarbe, nach rechts auslaufend. Das gibt dem
        // Fenster einen Anfang und bindet das Zeichen links optisch ein --
        // vorher stand es auf einer gleichmaessig dunklen Platte.
        // Kopf im Akzentverlauf, sehr zurueckhaltend beigemischt.
        //
        // Links Violett, rechts Blau -- derselbe Verlauf wie bei Reitern und
        // Schaltern. Dadurch haengt die Oberflaeche farblich zusammen, statt
        // aus einzelnen gefaerbten Teilen zu bestehen.
        verlauf(ctx, x, y, x + w, y + HEADER_H,
                fade(mix(C_SIDEBAR, A_VIOLETT, 0.14f), openAnim),
                fade(mix(C_SIDEBAR, A_BLAU, 0.07f), openAnim));

        // Trennlinie als Verlauf statt harter Kante: in der Mitte kraeftig,
        // zu den Raendern hin auslaufend.
        verlauf(ctx, x, y + HEADER_H - 1, x + w / 2, y + HEADER_H,
                fade(akzent(0f) & 0x30FFFFFF, openAnim), fade(akzent(0.5f), openAnim * 0.5f));
        verlauf(ctx, x + w / 2, y + HEADER_H - 1, x + w, y + HEADER_H,
                fade(akzent(0.5f), openAnim * 0.5f), fade(akzent(1f) & 0x30FFFFFF, openAnim));

        // The mark: a hollow ring, the same shape as the icon and as the
        // waypoint markers. Red once the addon is installed, so which of the
        // two you are running is visible at a glance rather than a surprise.
        // DAS ECHTE LOGO statt des gepixelten Rings.
        //
        // Gezeichnet ueber blitSprite -- derselbe Weg, den HudRenderer fuer
        // die Effekt-Symbole benutzt, also im Projekt belegt. Das Bild liegt
        // unter textures/gui/sprites/logo.png und ist eine Kopie von
        // icon.png.
        //
        // Faellt das Laden aus, bleibt der Ring als Rueckfall -- lieber ein
        // einfaches Zeichen als eine leere Stelle.
        // NUR DAS LOGO -- kein Schriftzug, keine Zaehlung daneben.
        //
        // Die Zahl der aktiven Module steht bereits unten in der
        // Seitenleiste. Doppelt angezeigt lenkt sie nur ab, und der Name des
        // Clients steckt ohnehin im Logo.
        int logoG = 34;
        try {
            ctx.blitSprite(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,
                    net.minecraft.resources.Identifier.fromNamespaceAndPath("vortexclient", "logo"),
                    x + PAD, y + (HEADER_H - logoG) / 2, logoG, logoG);
        } catch (Throwable pvpErr) {
            drawRingMark(ctx, x + PAD + 6, y + 17, 6, fade(Branding.accent(), openAnim));
        }



        // Rueckmeldung nach einem Preset-Wechsel, blendet nach 3 Sekunden aus.
        if (presetInfo != null && presetInfoTime < 3f) {
            int iw = this.font.width(presetInfo);
            float alpha = (presetInfoTime > 2f) ? (3f - presetInfoTime) : 1f;
            ctx.text(this.font, Component.literal(presetInfo),
                    x + (w - iw) / 2, y + 19, fade(accent, alpha * openAnim), false);
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

        // Knopf zum Design-Menue (Farben der Oberflaeche).
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
            // Suchfeld als eingelassene Pille.
            //
            // Beim Tippen leuchtet der Rand im Akzent auf -- so sieht man,
            // dass das Feld den Fokus hat, ohne einen dicken Rahmen.
            boolean tippt = search != null && !search.getValue().isEmpty();
            if (tippt) {
                roundRect(ctx, sx - 1, sy - 1, sw + 2, 22,
                        fade(akzent(0.5f) & 0x66FFFFFF, openAnim));
            }
            roundRect(ctx, sx, sy, sw, 20, fade(C_INNER, openAnim));
            ctx.text(this.font, Component.literal("Q"),
                    sx + 6, sy + 6, fade(0xFF6A6A76, openAnim), false);
            if (search.getValue().isEmpty()) {
                ctx.text(this.font, Component.literal("Search..."),
                        sx + 18, sy + 6, fade(0xFF6A6A76, openAnim), false);
            }
        }
    }

    /**
     * Waagerechte Reiterleiste.
     *
     * ERSETZT DIE SEITENLEISTE. Die stand links und nahm 148 Pixel Breite --
     * Platz, der dem eigentlichen Inhalt fehlte, waehrend die Leiste selbst
     * meist halb leer war.
     *
     * Jetzt liegen die Kategorien in einer Zeile unter dem Kopf. Links die
     * Modulkategorien, rechts die uebrigen Bereiche (Waypoints, Macros und
     * so weiter) etwas gedaempft -- so sieht man auf einen Blick, was Module
     * sind und was nicht.
     *
     * Der Gewinn: die volle Fensterbreite steht dem Raster zur Verfuegung,
     * und die Oberflaeche wird breit statt hoch.
     */
    private void drawTabs(GuiGraphicsExtractor ctx, int x, int y, int w,
                          int accent, Theme t, float dt) {
        ctx.fill(x, y, x + w, y + TAB_H, fade(C_SIDEBAR, openAnim));
        ctx.fill(x, y + TAB_H - 1, x + w, y + TAB_H, fade(C_LINE, openAnim));

        boolean searching = search != null && !search.getValue().isEmpty();
        int cx = x + PAD;
        int ty = y + 4;

        // --- Favoriten ----------------------------------------------------
        if (GuiState.hasFavorites()) {
            String txt = "* " + GuiState.getFavorites().size();
            int bw = this.font.width(txt) + 20;
            boolean isSel = !searching && favView;
            boolean hov = inRect(mx, my, cx, ty, bw, NAV_H);
            reiter(ctx, cx, ty, bw, txt, isSel, hov, accent, t, dt);
            hits.add(new Hit(cx, ty, bw, NAV_H, Act.FAVCAT, null, null, null));
            cx += bw + 6;
        }

        // --- Modulkategorien ----------------------------------------------
        for (Module.Category cat : Module.Category.values()) {
            // Leere Kategorien nicht anzeigen -- ein Reiter ins Nichts sieht
            // nach einem Fehler aus.
            if (!hatModule(cat)) continue;

            int on = 0, total = 0;
            for (Module m : ModuleManager.INSTANCE.getByCategory(cat)) {
                total++;
                if (m.isEnabled()) on++;
            }
            String txt = pretty(cat.name()) + "  " + on + "/" + total;
            int bw = this.font.width(txt) + 20;
            // Passt die Kategorie nicht mehr, wird umgebrochen -- nicht
            // weggelassen. Eine Kategorie, die es nicht gibt, kann man auch
            // nicht anwaehlen.
            if (cx + bw > x + w - PAD) {
                cx = x + PAD;
                ty += NAV_H + 4;
            }
            boolean isSel = !searching && !favView
                    && section == Section.MODULE && cat == selected;
            boolean hov = inRect(mx, my, cx, ty, bw, NAV_H);

            reiter(ctx, cx, ty, bw, txt, isSel, hov, accent, t, dt);
            hits.add(new Hit(cx, ty, bw, NAV_H, Act.CATEGORY, null, null, cat));
            cx += bw + 6;
        }

        // --- Uebrige Bereiche: EIGENE ZEILE --------------------------------
        //
        // HIER LAG EIN SCHWERER FEHLER.
        //
        // Sie standen vorher rechts in DERSELBEN Zeile wie die Kategorien.
        // Zusammen brauchen beide rund 944 Pixel, verfuegbar sind 872 -- die
        // Schleife brach also ab, und Waypoints und Macros verschwanden
        // einfach. Ohne Meldung, ohne Hinweis: die Bereiche waren schlicht
        // nicht mehr erreichbar.
        //
        // Zwei Zeilen loesen das dauerhaft. Und wenn doch einmal etwas nicht
        // passt, wird umgebrochen statt weggelassen -- ein Bedienelement darf
        // nie stillschweigend verschwinden.
        int by = y + NAV_H + 6;
        int bx = x + PAD;
        Object[][] bereiche = {
            {"Waypoints", Section.WAYPOINTS}, {"Macros", Section.MACROS},
            {"Skins", Section.SKINS}, {"Keys", Section.KEYS},
            {"Theme", Section.DESIGN}
        };
        for (Object[] b : bereiche) {
            String label = (String) b[0];
            Section sec = (Section) b[1];
            int bw = this.font.width(label) + 18;
            if (bx + bw > x + w - PAD) {       // umbrechen statt weglassen
                bx = x + PAD;
                by += NAV_H + 4;
            }
            boolean isSel = !searching && !favView && section == sec;
            boolean hov = inRect(mx, my, bx, by, bw, NAV_H);
            reiter(ctx, bx, by, bw, label, isSel, hov, accent, t, dt);
            hits.add(new Hit(bx, by, bw, NAV_H, Act.SECTION, null, null, sec));
            bx += bw + 6;
        }
    }

    /**
     * Ein einzelner Reiter.
     *
     * Der ausgewaehlte bekommt einen Balken UNTEN statt einer Flaeche --
     * das ist die uebliche Form bei waagerechten Reitern und wirkt ruhiger
     * als ein gefuellter Kasten.
     */
    /**
     * Ein Reiter als Pille.
     *
     * NEU: der ausgewaehlte Reiter ist eine gefuellte Pille im Akzentverlauf
     * mit einem Schein darunter, nicht mehr ein Strich am unteren Rand. Das
     * ist die Form, die moderne Clients benutzen, und der Zustand ist auch
     * auf einen schnellen Blick eindeutig.
     *
     * Der Verlauf laeuft ueber die Pille von Violett nach Blau -- dadurch
     * wirkt sie plastisch, ohne dass ein Rahmen noetig waere.
     */
    private void reiter(GuiGraphicsExtractor ctx, int x, int y, int w, String text,
                        boolean isSel, boolean hov, int accent, Theme t, float dt) {
        if (isSel) {
            // Schein unter der Pille -- gibt ihr Hoehe.
            for (int i = 1; i <= 3; i++) {
                int a = (int) (26f / i * openAnim);
                roundRect(ctx, x - i, y - i, w + i * 2, NAV_H + i * 2,
                        (a << 24) | (akzent(0.5f) & 0x00FFFFFF));
            }
            verlaufRund(ctx, x, y, w, NAV_H,
                    fade(akzent(0f), openAnim), fade(akzent(1f), openAnim));
        } else if (hov) {
            roundRect(ctx, x, y, w, NAV_H, fade(C_CARD_HOV, openAnim * 0.9f));
        }
        int col = isSel ? 0xFFFFFFFF : (hov ? C_TEXT : C_TEXT_DIM);
        int tw = this.font.width(text);
        ctx.text(this.font, Component.literal(text),
                x + (w - tw) / 2, y + (NAV_H - 8) / 2, fade(col, openAnim), false);
    }

    /** Abgerundetes Rechteck mit waagerechtem Verlauf. */
    /**
     * Abgerundetes Rechteck mit waagerechtem Verlauf.
     *
     * Nur die wenigen Spalten an den Ecken werden einzeln gezeichnet, weil
     * dort die Rundung entsteht. Die Mitte in Baendern -- das war vorher
     * pixelweise und machte bei vielen Schaltern und Kacheln den Grossteil
     * der Zeichenlast aus.
     */
    private void verlaufRund(GuiGraphicsExtractor ctx, int x, int y, int w, int h,
                             int von, int bis) {
        if (w <= 0 || h <= 0) return;
        int r = Math.min(RADIUS, Math.min(w / 2, h / 2));
        // Ecken: einzeln, wegen der Rundung
        for (int i = 0; i < r; i++) {
            int ein = r - i;
            ctx.fill(x + i, y + ein, x + i + 1, y + h - ein, mix(von, bis, i / (float) w));
            int rx = x + w - 1 - i;
            ctx.fill(rx, y + ein, rx + 1, y + h - ein, mix(von, bis, (w - 1 - i) / (float) w));
        }
        // Mitte: in Baendern
        int mw = w - 2 * r;
        if (mw <= 0) return;
        int baender = Math.max(1, (mw + 7) / 8);
        for (int b = 0; b < baender; b++) {
            int ax = x + r + (int) ((long) mw * b / baender);
            int bx = x + r + (int) ((long) mw * (b + 1) / baender);
            if (bx <= ax) continue;
            float t = (r + ((ax - x - r) + (bx - x - r)) / 2f) / w;
            ctx.fill(ax, y, bx, y + h, mix(von, bis, t));
        }
    }

    private void drawContent(GuiGraphicsExtractor ctx, int x, int y, int w, int h,
                             int accent, Theme t, float dt) {
        scroll = anim(scroll, scrollTarget, 18f, dt);

        // Bereiche ausserhalb der Module haben eigene Inhalte.
        boolean searchingNow = search != null && !search.getValue().isEmpty();
        if (!searchingNow && !favView && section != Section.MODULE) {
            drawSectionContent(ctx, x, y, w, h, accent, t);
            return;
        }

        // Ist das Detailfeld offen, bekommt das Raster weniger Breite.
        // detailAnim blendet den Uebergang ein, damit die Karten nicht
        // springen.
        detailAnim = anim(detailAnim, detail != null ? 1f : 0f, 14f, dt);
        int feldB = (int) (DETAIL_W * detailAnim);
        int rasterB = w - feldB;

        ctx.enableScissor(x, y, x + rasterB, y + h);

        List<Module> list = visibleModules();

        // Leerer Zustand.
        //
        // Findet die Suche nichts, blieb hier bisher eine leere Flaeche --
        // und man weiss nicht, ob die Suche nichts fand oder die Oberflaeche
        // haengt. Eine Zeile Text beantwortet das.
        if (list.isEmpty()) {
            String txt = (search != null && !search.getValue().isEmpty())
                    ? "Nothing found for \"" + search.getValue() + "\""
                    : "Nothing here yet";
            int tw = this.font.width(txt);
            ctx.text(this.font, Component.literal(txt),
                    x + (w - tw) / 2, y + h / 2 - 12,
                    fade(C_TEXT_DIM, openAnim), false);
            String hinweis = "Right Shift closes this window";
            int hw = this.font.width(hinweis);
            ctx.text(this.font, Component.literal(hinweis),
                    x + (w - hw) / 2, y + h / 2 + 2,
                    fade(mix(C_TEXT_DIM, C_LINE, 0.4f), openAnim), false);
            ctx.disableScissor();
            return;
        }

        // --- ZWEISPALTIGES RASTER -----------------------------------------
        //
        // Die Module standen bisher in EINER langen Spalte. Bei 58 Modulen
        // bedeutet das endloses Scrollen, und die halbe Fensterbreite blieb
        // leer -- der Hauptgrund, warum die Oberflaeche voll und unuebersicht-
        // lich wirkte.
        //
        // Zwei Spalten halbieren die Hoehe. Weil ausgeklappte Module
        // unterschiedlich hoch sind, bekommt jede Spalte ihr EIGENES cy: die
        // naechste Karte kommt immer in die Spalte, die gerade kuerzer ist.
        // So entstehen keine Luecken.
        // EINE Spalte, wie in der Vorlage. Zwei Spalten waren dicht, aber
        // die Zeilen wurden so schmal, dass Beschreibung und Steuerung sich
        // draengten. Eine breite Zeile je Modul liest sich ruhiger.
        int spalten = 1;
        int luecke = 10;
        int cw = (rasterB - PAD * 2 - 4 - (spalten - 1) * luecke) / spalten;
        int[] spaltenY = new int[spalten];
        for (int i = 0; i < spalten; i++) spaltenY[i] = y + PAD - (int) scroll;

        for (Module m : list) {
            // Kuerzeste Spalte waehlen.
            int sp = 0;
            for (int i = 1; i < spalten; i++) {
                if (spaltenY[i] < spaltenY[sp]) sp = i;
            }
            int cx = x + PAD + sp * (cw + luecke);
            int cy = spaltenY[sp];
            float ex = expandAnim.getOrDefault(m, 0f);
            ex = anim(ex, expanded.contains(m) ? 1f : 0f, 12f, dt);
            expandAnim.put(m, ex);

            // Karten haben jetzt eine feste Hoehe -- das Aufklappen passiert
            // im Detailfeld.
            int cardH = CARD_H;
            boolean visible = (cy + cardH >= y) && (cy <= y + h);

            boolean hov = visible && inRect(mx, my, cx, cy, cw, CARD_H);
            if (hov) frameHover = m;
            float hv = hoverAnim.getOrDefault(m, 0f);
            hv = anim(hv, hov ? 1f : 0f, 14f, dt);
            hoverAnim.put(m, hv);

            float on = toggleAnim.getOrDefault(m, m.isEnabled() ? 1f : 0f);
            on = anim(on, m.isEnabled() ? 1f : 0f, 14f, dt);
            toggleAnim.put(m, on);

            if (visible) {
                roundRect(ctx, cx, cy, cw, cardH, mix(C_CARD, C_CARD_HOV, hv));
                // Beim Ueberfahren ein zarter Schatten: die Karte hebt sich
                // vom Raster ab, statt nur heller zu werden. Das macht den
                // Unterschied zwischen "markiert" und "angehoben".
                if (hv > 0.05f) {
                    schatten(ctx, cx, cy, cx + cw, cy + cardH, hv * openAnim * 0.7f);
                }
                // Lichtkante oben.
                //
                // Eine Zeile, die um eine Spur heller ist als die Karte. Das
                // laesst die Flaeche wirken, als faele Licht von oben darauf
                // -- der Unterschied zwischen "Farbflaeche" und "Koerper".
                // Kostet nichts und ist der wirksamste einzelne Handgriff
                // gegen den flachen Eindruck.
                ctx.fill(cx + RADIUS, cy, cx + cw - RADIUS, cy + 1,
                        fade(mix(C_CARD, C_TEXT, 0.10f + 0.06f * hv), openAnim));

                // SYMBOLKACHEL LINKS, wie in der Vorlage.
                //
                // Ein abgerundetes Quadrat mit dem Anfangsbuchstaben des
                // Moduls. Laeuft das Modul, faerbt sich die Kachel im
                // Akzentverlauf -- der Zustand ist so schon am linken Rand
                // ablesbar, bevor man den Schalter sucht.
                int kx = cx + 8, ky = cy + (CARD_H - 22) / 2;
                if (on > 0.01f) {
                    verlaufRund(ctx, kx, ky, 22, 22,
                            fade(mix(C_INNER, akzent(0f), 0.55f * on), openAnim),
                            fade(mix(C_INNER, akzent(1f), 0.55f * on), openAnim));
                } else {
                    roundRect(ctx, kx, ky, 22, 22, fade(C_INNER, openAnim));
                }
                String buchstabe = m.getName().substring(0, 1).toUpperCase();
                int bw = this.font.width(buchstabe);
                ctx.text(this.font, Component.literal(buchstabe),
                        kx + (22 - bw) / 2, ky + 7,
                        fade(on > 0.5f ? 0xFFFFFFFF : C_TEXT_DIM, openAnim), false);

                // Gewaehltes Modul: violetter Rahmen, wie in der Vorlage.
                if (detail == m) {
                    int rc = fade(akzent(0.4f), openAnim);
                    ctx.fill(cx + RADIUS, cy, cx + cw - RADIUS, cy + 1, rc);
                    ctx.fill(cx + RADIUS, cy + cardH - 1, cx + cw - RADIUS, cy + cardH, rc);
                    ctx.fill(cx, cy + RADIUS, cx + 1, cy + cardH - RADIUS, rc);
                    ctx.fill(cx + cw - 1, cy + RADIUS, cx + cw, cy + cardH - RADIUS, rc);
                }

                // ZWEIZEILIG: Name oben, Kurzbeschreibung darunter.
                //
                // Die Beschreibung stand frueher nur im Tooltip -- man musste
                // also auf jedes Modul zeigen, um zu wissen, was es tut. Bei
                // 58 Modulen ist das unbrauchbar. Jetzt steht sie direkt da,
                // wofuer die groessere Kartenhoehe den Platz schafft.
                // Name kraeftig, Beschreibung gedaempft -- klare Rangfolge.
                ctx.text(this.font, Component.literal(m.getName()),
                        cx + 38, cy + 7, m.isEnabled() ? C_TEXT : C_TEXT_DIM);

                String kurz = kurzInfo(m, cw - 116);
                if (kurz != null) {
                    ctx.text(this.font, Component.literal(kurz),
                            cx + 38, cy + 19,
                            fade(mix(C_TEXT_DIM, C_LINE, 0.25f), openAnim), false);
                }

                if (hasContent(m)) {
                    ctx.text(this.font, Component.literal(ex > 0.5f ? "-" : "+"),
                            cx + cw - 50, cy + 13, C_TEXT_DIM, false);
                }
                // Stern zum Anpinnen (leuchtet, wenn das Modul Favorit ist).
                boolean fav = GuiState.isFavorite(m.getName());
                boolean starHov = inRect(mx, my, cx + cw - 66, cy + 10, 14, 14);
                ctx.text(this.font, Component.literal("*"),
                        cx + cw - 62, cy + 13,
                        fav ? accent : (starHov ? C_TEXT : 0xFF4A5164), false);
                drawSwitch(ctx, cx + cw - 36, cy + 12, on, accent);
            }

            // WICHTIG: Klickflaechen nur registrieren, wenn die Karte wirklich im
            // sichtbaren Bereich liegt. Sonst koennte man durch die Kopfzeile oder
            // die Fussleiste hindurch auf weggescrollte Karten klicken.
            if (visible) {
                hits.add(new Hit(cx, cy, cw - 40, CARD_H, Act.EXPAND, m, null, null));
                hits.add(new Hit(cx + cw - 38, cy + 9, 30, 16, Act.TOGGLE, m, null, null));
                hits.add(new Hit(cx + cw - 66, cy + 10, 14, 14, Act.STAR, m, null, null));
            }

            // Die Einstellungen stehen jetzt RECHTS im Detailfeld, nicht mehr
            // in der Karte. Dadurch bleiben alle Karten gleich hoch und das
            // Raster springt nicht mehr beim Oeffnen.

            // Nur die benutzte Spalte weiterschieben.
            spaltenY[sp] = cy + cardH + luecke;
        }

        // Hoehe ist die der LAENGSTEN Spalte -- sonst laesst sich das Ende
        // der laengeren Spalte nicht erreichen.
        int maxY = spaltenY[0];
        for (int i = 1; i < spalten; i++) maxY = Math.max(maxY, spaltenY[i]);
        contentHeight = (maxY + (int) scroll) - (y + PAD) + PAD;

        // ZUERST den Beschneidungsbereich des Rasters schliessen.
        //
        // HIER LAG DER FEHLER: das Detailfeld wurde davor gezeichnet,
        // waehrend der Bereich noch auf x..x+rasterB begrenzt war. Das Feld
        // liegt aber RECHTS davon -- es wurde also vollstaendig
        // weggeschnitten.
        //
        // Folge: keine Einstellungen sichtbar, und die Oberflaeche sah aus
        // wie vorher, weil die auffaelligste Neuerung unsichtbar blieb.
        ctx.disableScissor();

        // --- Detailfeld rechts --------------------------------------------
        if (feldB > 4 && detail != null) {
            zeichneDetail(ctx, x + rasterB, y, feldB, h, accent, t);
        }

        if (contentHeight > h) {
            int trackH = h - 8;
            int barH = Math.max(24, (int) (trackH * (h / (float) contentHeight)));
            float p = scroll / Math.max(1f, contentHeight - h);
            if (p < 0f) p = 0f;
            if (p > 1f) p = 1f;
            int barY = y + 4 + (int) ((trackH - barH) * p);
            ctx.fill(x + w - 4, y + 4, x + w - 2, y + 4 + trackH, 0x30FFFFFF);
            // Abgerundet und etwas breiter -- der Zwei-Pixel-Strich von
            // vorher sah aus wie ein Zeichenfehler.
            roundRect(ctx, x + w - 6, barY, 4, barH,
                    fade(mix(accent, C_TEXT, 0.15f), openAnim));
        }

        if (list.isEmpty()) {
            String msg = "No results";
            ctx.text(this.font, Component.literal(msg),
                    x + (w - this.font.width(msg)) / 2, y + h / 2 - 4,
                    0xFF6A6A76, false);
        }
    }

    /**
     * Inhalt fuer Bereiche, die keine Module sind.
     *
     * Waypoints bekommen ihre Einstellungen direkt hier (dieselben Bedien-
     * elemente wie bei Modulen) plus einen Knopf zur Verwaltung. Skins und
     * Design oeffnen ihre eigenen Bildschirme.
     */
    private void drawSectionContent(GuiGraphicsExtractor ctx, int x, int y, int w, int h,
                                    int accent, Theme t) {
        int cx = x + PAD;
        int cw = w - PAD * 2 - 4;
        // Scrollversatz beruecksichtigen -- sonst sind untere Einstellungen
        // (Randbreite, Tastenbelegungen) schlicht nicht erreichbar.
        int cy = y + PAD - (int) scroll;
        int cyStart = cy;

        ctx.enableScissor(x, y, x + w, y + h);

        switch (section) {
            case WAYPOINTS: {
                var wp = com.vortex.client.waypoint.WaypointSettings.INSTANCE;
                int count = com.vortex.client.waypoint.WaypointManager.all().size();

                // Einzahl und Mehrzahl waren beide "markers" -- ein alter
                // Tippfehler, der nie auffiel, weil beide Zweige gleich waren.
                cy = seitenKopf(ctx, cx, cy, "Waypoints",
                        count + (count == 1 ? " marker" : " markers"), t);
                cy = aktionsZeile(ctx, cx, cy, cw, "Manage markers",
                        mx, my, accent, t, Act.WP_MANAGE, null);

                trennlinie(ctx, cx, cy, cw);
                cy += GAP;

                for (Setting st : wp.getSettings()) {
                    // Nur zeichnen, was im Fenster liegt -- spart Arbeit und
                    // verhindert Klickflaechen ausserhalb.
                    if (cy + SET_H > y && cy < y + h) {
                        drawSetting(ctx, null, st, cx, cy, cw, accent, t);
                    }
                    cy += SET_H;
                }
                break;
            }
            case MACROS: {
                int n = com.vortex.client.macro.MacroManager.all().size();
                cy = seitenKopf(ctx, cx, cy, "Macros",
                        "Record clicks and keys, edit the timing, bind a key", t);
                cy = aktionsZeile(ctx, cx, cy, cw, "Open macro editor",
                        mx, my, accent, t, Act.SECTION, "openMacros");
                ctx.text(this.font,
                        Component.literal(n == 0 ? "No macros yet"
                                                 : n + (n == 1 ? " saved" : " saved")),
                        cx, cy, C_TEXT_DIM, false);
                break;
            }
            case COMMUNITY: {
                ctx.text(this.font, Component.literal("Community"),
                        cx, cy, C_TEXT);
                ctx.text(this.font,
                        Component.literal("Macros and presets shared by other players"),
                        cx, cy + 11, C_TEXT_DIM, false);
                cy += GAP + 8;
                cy = aktionsZeile(ctx, cx, cy, cw, "Browse shared macros",
                        mx, my, accent, t, Act.SECTION, "openCommunity");
                ctx.text(this.font,
                        Component.literal("Share your own on the website"),
                        cx, cy, C_TEXT_DIM, false);
                break;
            }
            case KEYS: {
                cy = seitenKopf(ctx, cx, cy, "Keys",
                        "Every assigned key in one list, conflicts marked", t);
                cy = aktionsZeile(ctx, cx, cy, cw, "Open key list",
                        mx, my, accent, t, Act.SECTION, "openKeys");
                break;
            }
            case SKINS: {
                cy = seitenKopf(ctx, cx, cy, "Skins",
                        "Wardrobe, player name lookup, your own files", t);
                cy = aktionsZeile(ctx, cx, cy, cw, "Open skin wardrobe",
                        mx, my, accent, t, Act.SECTION, "openSkins");
                break;
            }
            case DESIGN: {
                cy = seitenKopf(ctx, cx, cy, "Theme",
                        "Customise the interface colours", t);
                cy = aktionsZeile(ctx, cx, cy, cw, "Open theme editor",
                        mx, my, accent, t, Act.THEME, null);
                break;
            }
            default:
                break;
        }
        ctx.disableScissor();

        // Gesamthoehe fuer den Scrollbereich merken.
        contentHeight = (cy - cyStart) + PAD * 2;

        if (contentHeight > h) {
            int trackH = h - 8;
            int barH = Math.max(24, (int) (trackH * (h / (float) contentHeight)));
            float p = scroll / Math.max(1f, contentHeight - h);
            if (p < 0f) p = 0f;
            if (p > 1f) p = 1f;
            int barY = y + 4 + (int) ((trackH - barH) * p);
            ctx.fill(x + w - 4, y + 4, x + w - 2, y + 4 + trackH, 0x30FFFFFF);
            ctx.fill(x + w - 4, barY, x + w - 2, barY + barH,
                    mix(accent, C_TEXT, 0.15f));
        }
    }

    /** Knopf, der ein Auswahl-Menue oeffnet (Mobs / Bloecke / Entities). */
    private int drawSubButton(GuiGraphicsExtractor ctx, Module m, int cx, int sy, int cw,
                              int accent, Theme t) {
        // Beschriftung und Bildschirm kommen vom Modul selbst.
        if (!(m instanceof com.vortex.client.module.HasOwnScreen hos)) {
            return sy;
        }
        String label = hos.screenButtonLabel();
        Act act = Act.SUB_SCREEN;

        int bx = cx + 8;
        int bw = cw - 16;
        boolean hov = inRect(mx, my, bx, sy, bw, 18);
        roundRect(ctx, bx, sy, bw, 18, hov ? mix(C_INNER, accent, 0.28f) : C_INNER);
        ctx.text(this.font, Component.literal(label),
                bx + 8, sy + 5, C_TEXT, false);
        ctx.text(this.font, Component.literal(">"),
                bx + bw - 12, sy + 5, accent, false);
        hits.add(new Hit(bx, sy, bw, 18, act, m, null, null));
        return sy + SUB_H;
    }

    /** Eine Einstellungs-Zeile, je nach Typ unterschiedlich dargestellt. */
    private void drawSetting(GuiGraphicsExtractor ctx, Module m, Setting s,
                             int x, int y, int w, int accent, Theme t) {
        String name = s.getName();

        if (s instanceof BooleanSetting b) {
            ctx.text(this.font, Component.literal(name), x, y + 6,
                    b.get() ? C_TEXT : C_TEXT_DIM, false);
            drawSwitch(ctx, x + w - 24, y + 4, b.get() ? 1f : 0f, accent);
            hits.add(new Hit(x, y, w, SET_H, Act.S_BOOL, m, s, null));

        } else if (s instanceof NumberSetting n) {
            ctx.text(this.font, Component.literal(name), x, y + 1,
                    C_TEXT_DIM, false);
            String val = fmt(n.get());
            int vw = this.font.width(val);
            ctx.text(this.font, Component.literal(val), x + w - vw, y + 1, accent, false);

            int ty = y + 13;
            double span = n.getMax() - n.getMin();
            float p = (span <= 0) ? 0f : (float) ((n.get() - n.getMin()) / span);
            if (p < 0f) p = 0f;
            if (p > 1f) p = 1f;
            ctx.fill(x, ty, x + w, ty + 3, C_TRACK);
            ctx.fill(x, ty, x + (int) (w * p), ty + 3, accent);
            int kx = x + (int) (w * p);
            ctx.fill(kx - 2, ty - 2, kx + 3, ty + 5, C_TEXT);
            hits.add(new Hit(x, y + 7, w, 13, Act.S_NUM, m, s, null));

        } else if (s instanceof ModeSetting mode) {
            ctx.text(this.font, Component.literal(name), x, y + 6,
                    C_TEXT_DIM, false);
            String val = mode.get();
            int vw = this.font.width(val);
            int rightX = x + w;
            ctx.text(this.font, Component.literal("<"),
                    rightX - vw - 22, y + 6, 0xFF9A9AA6, false);
            ctx.text(this.font, Component.literal(val),
                    rightX - vw - 10, y + 6, accent, false);
            ctx.text(this.font, Component.literal(">"),
                    rightX - 6, y + 6, 0xFF9A9AA6, false);
            hits.add(new Hit(rightX - vw - 26, y, 14, SET_H, Act.S_MODE_PREV, m, s, null));
            hits.add(new Hit(rightX - 10, y, 14, SET_H, Act.S_MODE_NEXT, m, s, null));

        } else if (s instanceof ColorSetting c) {
            ctx.text(this.font, Component.literal(name), x, y + 6,
                    C_TEXT_DIM, false);
            roundRect(ctx, x + w - 26, y + 4, 24, 12, 0xFF000000);
            roundRect(ctx, x + w - 25, y + 5, 22, 10, c.get() | 0xFF000000);
            hits.add(new Hit(x, y, w, SET_H, Act.S_COLOR, m, s, null));

        } else if (s instanceof KeySetting k) {
            ctx.text(this.font, Component.literal(name), x, y + 6,
                    C_TEXT_DIM, false);
            String val = k.isListening() ? "Press a key" : k.getKeyName();
            int vw = this.font.width(val);
            roundRect(ctx, x + w - vw - 10, y + 3, vw + 8, 14,
                    k.isListening() ? mix(C_INNER, accent, 0.4f) : C_INNER);
            ctx.text(this.font, Component.literal(val),
                    x + w - vw - 6, y + 6, k.isListening() ? accent : C_TEXT, false);
            hits.add(new Hit(x, y, w, SET_H, Act.S_KEY, m, s, null));

        } else {
            ctx.text(this.font, Component.literal(name), x, y + 6,
                    C_TEXT_DIM, false);
        }
    }

    /** Kleiner Hinweiskasten neben der Maus, mit Umbruch bei Bedarf. */
    private void drawTooltip(GuiGraphicsExtractor ctx, String text, int accent) {
        int maxW = 210;
        java.util.List<String> lines = new java.util.ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String word : text.split(" ")) {
            String test = cur.length() == 0 ? word : cur + " " + word;
            if (this.font.width(test) > maxW && cur.length() > 0) {
                lines.add(cur.toString());
                cur = new StringBuilder(word);
            } else {
                cur = new StringBuilder(test);
            }
        }
        if (cur.length() > 0) lines.add(cur.toString());

        int w = 0;
        for (String l : lines) w = Math.max(w, this.font.width(l));
        w += 12;
        int h = lines.size() * 10 + 8;

        int tx = mx + 12;
        int ty = my + 12;
        if (tx + w > this.width) tx = this.width - w - 2;
        if (ty + h > this.height) ty = this.height - h - 2;

        roundRect(ctx, tx, ty, w, h, 0xF00E0E12);
        ctx.fill(tx, ty, tx + w, ty + 1, accent);
        int ly = ty + 5;
        for (String l : lines) {
            ctx.text(this.font, Component.literal(l), tx + 6, ly, 0xFFD0D0DA, false);
            ly += 10;
        }
    }

    /**
     * Schalter.
     *
     * Etwas groesser als vorher (26x13 statt 22x11) und mit rundem Knopf.
     * Der Knauf war ein hartes Rechteck -- bei einem Element, das man staendig
     * ansieht, faellt so etwas am meisten auf.
     *
     * Im ausgeschalteten Zustand liegt der Knauf leicht gedaempft, im
     * eingeschalteten hell: so erkennt man den Zustand auch ohne Farbe, was
     * bei einem selbst gewaehlten Akzent wichtig ist.
     */
    /**
     * Schalter.
     *
     * NEU: 28x14 mit Akzentverlauf im eingeschalteten Zustand und einem
     * weichen Schein darum. Der Knauf ist rund und wirft einen kleinen
     * Schatten.
     *
     * Der Zustand ist damit dreifach ablesbar -- Farbe, Position des Knaufs
     * und Schein. Das ist wichtig, weil der Akzent frei waehlbar ist und
     * nicht bei jeder Wahl gut sichtbar bleibt.
     */
    private void drawSwitch(GuiGraphicsExtractor ctx, int x, int y, float on, int accent) {
        if (on > 0.02f) {
            // Schein nur im Ein-Zustand, mit dem Uebergang eingeblendet.
            for (int i = 1; i <= 3; i++) {
                int a = (int) (30f / i * on * openAnim);
                roundRect(ctx, x - i, y - i, 28 + i * 2, 14 + i * 2,
                        (a << 24) | (akzent(0.5f) & 0x00FFFFFF));
            }
        }
        roundRect(ctx, x, y, 28, 14, fade(C_TRACK, openAnim));
        if (on > 0.02f) {
            verlaufRund(ctx, x, y, 28, 14,
                    fade(akzent(0f), on * openAnim), fade(akzent(1f), on * openAnim));
        }

        int kx = x + 2 + (int) (on * 14f);
        // Schatten unter dem Knauf.
        ctx.fill(kx + 1, y + 11, kx + 9, y + 12, fade(0x40000000, openAnim));
        // Knauf: Mittelblock plus schmalere Zeilen -- wirkt rund.
        int knauf = fade(mix(0xFFD8D4E8, 0xFFFFFFFF, on), openAnim);
        ctx.fill(kx, y + 3, kx + 10, y + 11, knauf);
        ctx.fill(kx + 1, y + 2, kx + 9, y + 3, knauf);
        ctx.fill(kx + 1, y + 11, kx + 9, y + 12, knauf);
    }

    private void drawFooter(GuiGraphicsExtractor ctx, int x, int y, int w) {
        ctx.fill(x, y, x + w, y + FOOTER_H, fade(C_SIDEBAR, openAnim * opacity()));
        ctx.fill(x, y, x + w, y + 1, fade(C_LINE, openAnim));

        // Griff unten rechts zum Groesserziehen -- drei kurze Schraegstriche.
        int gx = x + w - 12, gy = y + FOOTER_H - 12;
        boolean gHov = inRect(mx, my, gx - 2, gy - 2, 14, 14);
        int gc = gHov ? akzent(0.5f) : C_TEXT_DIM;
        for (int i = 0; i < 3; i++) {
            int o = i * 4;
            ctx.fill(gx + 8 - o, gy + 8, gx + 10 - o, gy + 10, gc);
            ctx.fill(gx + 8, gy + 8 - o, gx + 10, gy + 10 - o, gc);
        }
        ctx.text(this.font,
                Component.literal("Click to expand   ·   Toggle on the right   ·   ESC to close"),
                x + PAD, y + 5, fade(0xFF74747F, openAnim), false);
    }

    // ---------------------------------------------------------------- Eingabe

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent click, boolean doubled) {
        if (super.mouseClicked(click, doubled)) return true;

        int button = click.button();

        // Griff unten rechts gepackt -> Groesse aendern.
        int gx = lastWinX + lastWinW - 14;
        int gy = lastWinY + windowHeight() - 14;
        if (mx >= gx && my >= gy && mx <= lastWinX + lastWinW && my <= lastWinY + windowHeight()) {
            resizingWindow = true;
            resizeStartW = lastWinW;
            resizeStartH = windowHeight();
            resizeStartMx = mx;
            resizeStartMy = my;
            return true;
        }

        // Kopfzeile gepackt -> Fenster verschieben. Erst pruefen, nachdem die
        // Knoepfe dort (Preset/Design) ihre Chance hatten -- das erledigt die
        // Hit-Schleife weiter unten, deshalb hier nur der freie Bereich.
        if (my >= lastWinY && my < lastWinY + HEADER_H
                && mx >= lastWinX && mx < lastWinX + lastWinW) {
            boolean onWidget = false;
            for (Hit h : hits) {
                if (h.contains(mx, my)) { onWidget = true; break; }
            }
            if (!onWidget && (search == null || !search.isMouseOver(mx, my))) {
                movingWindow = true;
                moveGrabX = mx - lastWinX;
                moveGrabY = my - lastWinY;
                return true;
            }
        }
        // Rueckwaerts pruefen: spaeter Gezeichnetes liegt oben.
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit hit = hits.get(i);
            if (!hit.contains(mx, my)) continue;

            switch (hit.act) {
                case SECTION:
                    // Skin-Garderobe hat einen eigenen Bildschirm.
                    if ("openSkins".equals(hit.extra)) {
                    // Schliessen-Knopf des Detailfeldes.
                    if ("closeDetail".equals(hit.extra)) {
                        detail = null;
                        break;
                    }
                        Minecraft.getInstance().gui.setScreen(new SkinScreen(this));
                        break;
                    }
                    if ("openMacros".equals(hit.extra)) {
                        Minecraft.getInstance().gui.setScreen(new MacroScreen(this));
                        break;
                    }
                    if ("openKeys".equals(hit.extra)) {
                        Minecraft.getInstance().gui.setScreen(new KeyListScreen(this));
                        break;
                    }
                    if ("openCommunity".equals(hit.extra)) {
                        Minecraft.getInstance().gui.setScreen(new CommunityScreen(this));
                        break;
                    }
                    section = (Section) hit.extra;
                    favView = false;
                    scrollTarget = 0f;
                    scroll = 0f;
                    if (search != null) search.setValue("");
                    break;
                case WP_MANAGE:
                    Minecraft.getInstance().gui.setScreen(new WaypointScreen(this));
                    break;
                case FAVCAT:
                    favView = true;
                    section = Section.MODULE;
                    scrollTarget = 0f;
                    scroll = 0f;
                    if (search != null) search.setValue("");
                    break;
                case STAR:
                    GuiState.toggleFavorite(hit.module.getName());
                    // Letzten Favoriten entfernt -> zurueck zur Kategorie-Ansicht.
                    if (favView && !GuiState.hasFavorites()) favView = false;
                    break;
                case THEME:
                    Minecraft.getInstance().gui.setScreen(new ThemeScreen(this));
                    break;
                case PRESET: {
                    // Wechselt das Preset: sichert den aktuellen Stand und laedt
                    // den anderen Satz. Aufgeklappte Karten schliessen, damit die
                    // Anzeige zu den neuen Werten passt.
                    int target = ((Integer) hit.extra).intValue();
                    com.vortex.client.core.ConfigManager.switchTo(target);
                    expanded.clear();
                    expandAnim.clear();
                    toggleAnim.clear();
                    // Kurze Rueckmeldung im Chat -- sonst ist beim Wechsel auf ein
                    // leeres Preset nicht erkennbar, ob etwas passiert ist.
                    presetInfo = "Preset " + (target + 1) + ": "
                            + countEnabled() + " modules active";
                    presetInfoTime = 0f;
                    break;
                }
                case CATEGORY:
                    detail = null;
                    favView = false;
                    section = Section.MODULE;
                    selected = (Module.Category) hit.extra;
                    scrollTarget = 0f;
                    scroll = 0f;
                    if (search != null) search.setValue("");
                    break;
                case TOGGLE:
                    hit.module.toggle();
                    break;
                case EXPAND:
                    // Rechtsklick schaltet weiterhin um -- das ist der
                    // schnellste Weg und soll sich nicht aendern.
                    if (button == 1 || !hasContent(hit.module)) {
                        hit.module.toggle();
                    } else if (detail == hit.module) {
                        detail = null;          // nochmal klicken schliesst
                    } else {
                        detail = hit.module;    // Einstellungen rechts oeffnen
                        // An den Anfang: sonst steht das neue Modul mitten
                        // in der Liste, weil die Position vom vorigen bleibt.
                        detailScroll = 0f;
                        detailScrollZiel = 0f;
                    }
                    break;
                case SUB_SCREEN:
                    // Das Modul erzeugt seinen Bildschirm selbst.
                    if (hit.module instanceof com.vortex.client.module.HasOwnScreen hos2) {
                        Minecraft.getInstance().gui.setScreen(hos2.createScreen(this));
                    }
                    break;
                case SUB_WAYPOINT:
                    Minecraft.getInstance().gui.setScreen(new WaypointScreen(this));
                    break;
                case S_BOOL:
                    handleBool(hit.module, (BooleanSetting) hit.setting);
                    break;
                case S_NUM:
                    dragging = (NumberSetting) hit.setting;
                    dragX = hit.x;
                    dragW = hit.w;
                    applySlider(dragging, mx);
                    break;
                case S_MODE_PREV:
                    cycleBack((ModeSetting) hit.setting);
                    break;
                case S_MODE_NEXT:
                    ((ModeSetting) hit.setting).cycle();
                    break;
                case S_COLOR:
                    openColor(hit.module, (ColorSetting) hit.setting);
                    break;
                case S_KEY:
                    ((KeySetting) hit.setting).setListening(true);
                    break;
                default:
                    break;
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent click, double dx, double dy) {
        if (resizingWindow) {
            // Neue Groesse aus der Mausbewegung -- die Grenzen setzt
            // windowWidth()/windowHeight() selbst.
            GuiState.setWindowSize(resizeStartW + (mx - resizeStartMx),
                                   resizeStartH + (my - resizeStartMy));
            return true;
        }
        if (movingWindow) {
            // Versatz zur Bildschirmmitte speichern (unabhaengig von der Aufloesung).
            int winW = Math.min(this.width - 40, WIN_MAX_W);
            int winH = windowHeight();
            int baseX = (this.width - winW) / 2;
            int baseY = (this.height - winH) / 2;
            GuiState.setOffset((mx - moveGrabX) - baseX, (my - moveGrabY) - baseY);
            return true;
        }
        if (dragging != null) {
            applySlider(dragging, mx);
            return true;
        }
        return super.mouseDragged(click, dx, dy);
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent click) {
        if (movingWindow || resizingWindow) {
            // Position und Groesse gleich sichern.
            com.vortex.client.core.ConfigManager.save();
        }
        movingWindow = false;
        resizingWindow = false;
        dragging = null;
        return super.mouseReleased(click);
    }

    /** Mausposition auf den Wertebereich umrechnen (inkl. Schrittweite). */
    private void applySlider(NumberSetting n, int mouseX) {
        if (dragW <= 0) return;
        float p = (mouseX - dragX) / (float) dragW;
        if (p < 0f) p = 0f;
        if (p > 1f) p = 1f;
        double raw = n.getMin() + p * (n.getMax() - n.getMin());
        double step = n.getStep();
        if (step > 0) raw = Math.round(raw / step) * step;
        raw = Math.round(raw * 1000.0) / 1000.0;
        if (raw < n.getMin()) raw = n.getMin();
        if (raw > n.getMax()) raw = n.getMax();
        n.set(raw);
    }

    private void handleBool(Module m, BooleanSetting b) {
        // Einstellungen koennen auch OHNE Modul auftreten (z.B. Waypoints, die
        // ein eigener Bereich sind und kein Modul mehr). Dann einfach umschalten.
        if (m == null) {
            b.toggle();
            return;
        }
        if (m instanceof com.vortex.client.module.modules.GlobalHudColorModule ghc) {
            if (b == ghc.apply) { ghc.applyToAll(); return; }
            if (b == ghc.reset) { ghc.resetToWhite(); return; }
        }
        if (b == m.getEnabledSetting()) {
            m.toggle();
        } else {
            b.toggle();
        }
    }

    private void openColor(Module m, ColorSetting c) {
        if (m != null && m instanceof com.vortex.client.module.modules.GlobalHudColorModule ghc
                && c == ghc.color) {
            Minecraft.getInstance()
                    .gui.setScreen(new ColorPickerScreen(this, c, ghc::applyToAll));
            return;
        }
        Minecraft.getInstance().gui.setScreen(new ColorPickerScreen(this, c));
    }

    /**
     * Eine Option zurueck. ModeSetting kann nur vorwaerts schalten, also wird so
     * lange weitergeschaltet, bis der Vorgaenger erreicht ist.
     */
    private void cycleBack(ModeSetting mode) {
        int start = mode.getIndex();
        int count = 0;
        // Erst die Anzahl der Optionen bestimmen.
        do {
            mode.cycle();
            count++;
        } while (mode.getIndex() != start && count < 64);
        if (count <= 1) return;
        // Jetzt bis zum Vorgaenger schalten.
        for (int i = 0; i < count - 1; i++) {
            mode.cycle();
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY,
                                 double horizontal, double vertical) {
        // DIE REITERZEILE MUSS ABGEZOGEN WERDEN.
        //
        // Hier stand nur Kopf und Fuss. Der Inhaltsbereich ist aber seit dem
        // Umbau zusaetzlich um TAB_H kleiner -- also hielt die Begrenzung
        // den sichtbaren Bereich fuer 64 Pixel groesser als er ist und
        // stoppte das Scrollen zu frueh. Die letzten Module blieben
        // unerreichbar.
        //
        // Genau dieselbe Rechnung wie beim Zeichnen, sonst laufen die beiden
        // wieder auseinander.
        int h = windowHeight() - HEADER_H - TAB_H - FOOTER_H;

        // Zeigt die Maus auf die Seitenleiste? Dann diese scrollen.
        if (mx < lastWinX + SIDEBAR_W) {
            leisteScrollZiel -= (float) vertical * 28f;
            float lmax = Math.max(0f, leisteHoehe - h + 70);
            if (leisteScrollZiel < 0f) leisteScrollZiel = 0f;
            if (leisteScrollZiel > lmax) leisteScrollZiel = lmax;
            return true;
        }

        // ZEIGT DIE MAUS AUF DAS DETAILFELD? Dann dieses scrollen.
        //
        // Ohne diese Zuordnung lief das Rad immer auf das Raster, und das
        // Feld liess sich gar nicht bewegen -- der Bildlauf darin waere
        // wirkungslos geblieben.
        if (detail != null && mx >= lastWinX + lastWinW - DETAIL_W) {
            detailScrollZiel -= (float) vertical * 32f;
            float dmax = Math.max(0f, detailHoehe - h);
            if (detailScrollZiel < 0f) detailScrollZiel = 0f;
            if (detailScrollZiel > dmax) detailScrollZiel = dmax;
            return true;
        }

        scrollTarget -= (float) vertical * 32f;
        float max = contentHeight - h;
        if (max < 0f) max = 0f;
        if (scrollTarget < 0f) scrollTarget = 0f;
        if (scrollTarget > max) scrollTarget = max;
        return true;
    }

    // ----------------------------------------------------------- Hilfsmittel

    /** Aktuell sichtbare Module: Suchtreffer, sonst die gewaehlte Kategorie. */
    private List<Module> visibleModules() {
        String q = (search == null) ? "" : search.getValue().trim().toLowerCase(Locale.ROOT);
        List<Module> out = new ArrayList<>();
        if (!q.isEmpty()) {
            for (Module m : ModuleManager.INSTANCE.getModules()) {
                if (m.getName().toLowerCase(Locale.ROOT).contains(q)) out.add(m);
            }
        } else if (favView) {
            // Angepinnte Module in der Reihenfolge des Anpinnens.
            for (String name : GuiState.getFavorites()) {
                for (Module m : ModuleManager.INSTANCE.getModules()) {
                    if (m.getName().equals(name)) {
                        out.add(m);
                        break;
                    }
                }
            }
        } else {
            out.addAll(ModuleManager.INSTANCE.getByCategory(selected));
        }
        return out;
    }

    /** Hoehe des aufgeklappten Bereichs. */
    private int extraHeight(Module m) {
        int h = 6;
        if (m instanceof com.vortex.client.module.HasOwnScreen) {
            h += SUB_H;
        }
        for (Setting s : m.getSettings()) {
            if (s == m.getEnabledSetting()) continue;
            h += SET_H;
        }
        return h;
    }

    /** Hoehe des Auswahl-Knopfes (0, wenn das Modul keinen hat). */
    private int subHeight(Module m) {
        if (m instanceof com.vortex.client.module.HasOwnScreen) {
            return SUB_H;
        }
        return 0;
    }

    private boolean hasContent(Module m) {
        return extraHeight(m) > 6;
    }

    /**
     * Category name as shown in the sidebar.
     *
     * Plain capitalisation turned HUD into "Hud" and PVP into "Pvp", which
     * reads like a typo. Acronyms keep their form; everything else gets the
     * normal treatment, so a category added later needs no change here.
     */
    /**
     * Draws the ring mark.
     *
     * Built from horizontal strips, the same way the waypoint markers are: only
     * the two edge pieces of each row are filled, so the middle stays open.
     */
    private static void drawRingMark(GuiGraphicsExtractor ctx, int cx, int cy, int r, int color) {
        int inner = Math.max(1, r - 2);
        for (int dy = -r; dy <= r; dy++) {
            int outerHalf = rowHalf(dy, r);
            if (outerHalf <= 0) continue;
            int innerHalf = rowHalf(dy, inner);
            int py = cy + dy;
            if (innerHalf <= 0) {
                ctx.fill(cx - outerHalf, py, cx + outerHalf, py + 1, color);
            } else {
                ctx.fill(cx - outerHalf, py, cx - innerHalf, py + 1, color);
                ctx.fill(cx + innerHalf, py, cx + outerHalf, py + 1, color);
            }
        }
    }

    /** Half width of a circle row at distance dy from the centre. */
    private static int rowHalf(int dy, int radius) {
        double t2 = (double) dy / radius;
        double v = 1.0 - t2 * t2;
        if (v <= 0) return 0;
        return (int) Math.round(Math.sqrt(v) * radius);
    }

    private static String pretty(String name) {
        if (name == null || name.isEmpty()) return "";
        switch (name) {
            case "HUD": return "HUD";
            case "PVP": return "PvP";
            default:
                String s = name.toLowerCase(Locale.ROOT);
                return Character.toUpperCase(s.charAt(0)) + s.substring(1);
        }
    }

    private static String fmt(double v) {
        if (Math.abs(v - Math.rint(v)) < 1.0e-6) return String.valueOf((int) Math.rint(v));
        return String.valueOf(Math.round(v * 100.0) / 100.0);
    }

    /** Anzahl aktuell eingeschalteter Module. */
    private int countEnabled() {
        int n = 0;
        for (Module m : ModuleManager.INSTANCE.getModules()) {
            if (m.isEnabled()) n++;
        }
        return n;
    }

    private boolean inRect(int px, int py, int x, int y, int w, int h) {
        return px >= x && px < x + w && py >= y && py < y + h;
    }

    /** Zeitbasierter Uebergang -- unabhaengig von der Bildrate. */
    /**
     * Weiche Annaeherung an einen Zielwert.
     *
     * VORHER: cur + (target - cur) * (speed * dt), gekappt bei 1.
     * Das ist nur bei hoher Bildrate weich. Sinkt sie, wird speed * dt
     * schnell groesser als 1 -- die Bewegung SPRINGT dann in einem Schritt
     * ans Ziel. Bei jedem kleinen Einbruch der Bildrate ruckelte dadurch
     * alles, was sich bewegt: Bildlauf, Einblenden, Hervorhebungen.
     *
     * JETZT: 1 - e^(-speed * dt). Das ist die exakte Form einer
     * gleichmaessigen Annaeherung. Sie ist bei 30 und bei 240 Bildern pro
     * Sekunde gleich schnell, wird nie groesser als 1 und springt nie.
     */
    private static float anim(float cur, float target, float speed, float dt) {
        float f = 1f - (float) Math.exp(-speed * dt);
        float neu = cur + (target - cur) * f;
        // Winzigen Rest abschneiden, sonst naehert sich der Wert ewig an und
        // die Oberflaeche zeichnet Bewegung, die man nicht mehr sieht.
        if (Math.abs(target - neu) < 0.01f) return target;
        return neu;
    }

    /** Rechteck mit leicht abgerundet wirkenden Ecken. */
    /**
     * Rechteck mit abgerundeten Ecken.
     *
     * Die alte Fassung schnitt nur EINEN Pixel ab -- das sieht man kaum, und
     * die Oberflaeche wirkte weiterhin kastig. Jetzt wird der Radius
     * treppenfoermig ausgespart, was bei drei Pixeln schon deutlich weicher
     * aussieht.
     *
     * Kleine Flaechen bekommen automatisch einen kleineren Radius, damit
     * Schalter und Schieber nicht rund werden.
     */
    private void roundRect(GuiGraphicsExtractor ctx, int x, int y, int w, int h, int color) {
        if (w <= 0 || h <= 0) return;
        int r = Math.min(RADIUS, Math.min(w / 2, h / 2));
        if (r <= 0) { ctx.fill(x, y, x + w, y + h, color); return; }

        // Mittelblock in voller Hoehe, dann oben und unten eingerueckt.
        ctx.fill(x, y + r, x + w, y + h - r, color);
        ctx.fill(x + r, y, x + w - r, y + r, color);
        ctx.fill(x + r, y + h - r, x + w - r, y + h, color);

        // Ecken treppenfoermig auffuellen.
        for (int i = 0; i < r; i++) {
            int ein = r - i - 1;
            ctx.fill(x + ein, y + i, x + r, y + i + 1, color);
            ctx.fill(x + w - r, y + i, x + w - ein, y + i + 1, color);
            ctx.fill(x + ein, y + h - i - 1, x + r, y + h - i, color);
            ctx.fill(x + w - r, y + h - i - 1, x + w - ein, y + h - i, color);
        }
    }

    /** Deckkraft einer Farbe skalieren (fuers Einblenden). */
    private static int fade(int argb, float f) {
        if (f >= 1f) return argb;
        if (f <= 0f) return argb & 0x00FFFFFF;
        int a = (int) (((argb >>> 24) & 0xFF) * f);
        return (a << 24) | (argb & 0x00FFFFFF);
    }

    /** Zwei Farben mischen (t = 0 -> a, t = 1 -> b). */
    private static int mix(int a, int b, float t) {
        if (t < 0f) t = 0f;
        if (t > 1f) t = 1f;
        int aa = (a >>> 24) & 0xFF, ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int ba = (b >>> 24) & 0xFF, br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        int al = (int) (aa + (ba - aa) * t);
        int r  = (int) (ar + (br - ar) * t);
        int g  = (int) (ag + (bg - ag) * t);
        int bl = (int) (ab + (bb - ab) * t);
        return (al << 24) | (r << 16) | (g << 8) | bl;
    }

    /**
     * Wartet ein KeySetting auf eine Taste, wird GLFW direkt abgefragt und die
     * erste gedrueckte Taste uebernommen. Escape bricht ab.
     */
    /** The setting waiting for a key, or null. */
    private KeySetting pvpclient$listeningSetting() {
        for (Module m : ModuleManager.INSTANCE.getModules()) {
            for (Setting s : m.getSettings()) {
                if (s instanceof KeySetting k && k.isListening()) return k;
            }
        }
        // Areas outside the modules count too. Without this the waypoint keys
        // could not be assigned at all -- you clicked, and nothing happened.
        for (Setting s : com.vortex.client.waypoint.WaypointSettings
                .INSTANCE.getSettings()) {
            if (s instanceof KeySetting k && k.isListening()) return k;
        }
        return null;
    }

    /**
     * Escape must not close the menu while a key is being assigned.
     *
     * This was the whole bug: Minecraft closes a screen on Escape before
     * anything else gets a look, so the handling that clears a binding never
     * ran -- the menu simply shut and the key stayed as it was.
     *
     * Blocking the close for that moment lets the existing handling see the
     * key and clear it. Deliberately done this way rather than through the
     * key event, whose new argument type does not expose what it holds.
     */
    @Override
    public boolean shouldCloseOnEsc() {
        return pvpclient$listeningSetting() == null;
    }

    private void pvpclient$captureKeyIfListening() {
        KeySetting listening = pvpclient$listeningSetting();
        if (listening == null) return;

        Minecraft mc = Minecraft.getInstance();

        // Escape CLEARS the binding instead of just cancelling.
        //
        // Cancelling left you stuck: once a key was assigned there was no way
        // to get rid of it again, only to swap it for another one. Escape is
        // the obvious "I want none of it" key, and it can never be a sensible
        // binding itself, since it closes the menu.
        if (com.mojang.blaze3d.platform.InputConstants.isKeyDown(
                mc.getWindow(), org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE)) {
            listening.setKeyCode(org.lwjgl.glfw.GLFW.GLFW_KEY_UNKNOWN);
            listening.setListening(false);
            com.vortex.client.core.ConfigManager.save();
            return;
        }
        for (int code = org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE;
             code <= org.lwjgl.glfw.GLFW.GLFW_KEY_LAST; code++) {
            if (com.mojang.blaze3d.platform.InputConstants.isKeyDown(mc.getWindow(), code)) {
                listening.setKeyCode(code);
                listening.setListening(false);
                com.vortex.client.core.ConfigManager.save();
                return;
            }
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void removed() {
        com.vortex.client.core.ConfigManager.save();
        super.removed();
    }

    /** Gibt es ueberhaupt ein Modul in dieser Kategorie? */
    private boolean hatModule(Module.Category cat) {
        for (Module m : com.vortex.client.module.ModuleManager.INSTANCE.getModules()) {
            if (m.getCategory() == cat) return true;
        }
        return false;
    }

    /** Erste Kategorie mit Modulen -- sonst oeffnet das Menue leer. */
    private static Module.Category ersteBelegteKategorie() {
        for (Module.Category cat : Module.Category.values()) {
            // Eine nur vom Start erreichbare Kategorie darf nicht die
            // Vorauswahl sein -- sonst oeffnet das Mod-Menue auf einer Seite,
            // die in der Leiste gar nicht steht.
            if (nurVomStart(cat)) continue;
            for (Module m : com.vortex.client.module.ModuleManager.INSTANCE.getModules()) {
                if (m.getCategory() == cat) return cat;
            }
        }
        return Module.Category.values()[0];
    }


    // ======================================================================
    // Zeichenhilfen
    // ======================================================================
    //
    // Minecraft kann nur Rechtecke fuellen. Runde Ecken entstehen, indem man
    // die Ecken aussparrt: ein breites Rechteck in der Mitte, ein schmales
    // oben und unten. Bei zwei bis drei Pixeln reicht das voellig und nimmt
    // der Oberflaeche das Kastige.


    /**
     * Weicher Schatten unter einer Flaeche.
     *
     * Vier immer blassere Rahmen. Das trennt die Ebenen optisch, ohne eine
     * harte Linie zu ziehen -- genau das, was die alte Oberflaeche flach und
     * gedraengt wirken liess.
     */
    private static void schatten(GuiGraphicsExtractor ctx, int x, int y, int x2, int y2,
                                 float staerke) {
        for (int i = 1; i <= 4; i++) {
            int a = (int) (36 * staerke / i);
            if (a <= 0) continue;
            int c = (a << 24);
            ctx.fill(x - i, y - i, x2 + i, y - i + 1, c);
            ctx.fill(x - i, y2 + i - 1, x2 + i, y2 + i, c);
            ctx.fill(x - i, y - i, x - i + 1, y2 + i, c);
            ctx.fill(x2 + i - 1, y - i, x2 + i, y2 + i, c);
        }
    }

    /** Waagerechter Verlauf zwischen zwei Farben. */
    private static void verlauf(GuiGraphicsExtractor ctx, int x, int y, int x2, int y2,
                                int von, int bis) {
        int w = x2 - x;
        if (w <= 0) return;
        // IN BAENDERN STATT PIXELWEISE.
        //
        // Hier lag der Scroll-Ruckler: der Verlauf zeichnete eine Spalte je
        // Pixel, jede als eigener Aufruf. Der Kopf allein war 960 Aufrufe,
        // zusammen mit Trennlinie, Pillen, Kacheln und Schaltern ueber 3000
        // pro Bild. Beim Scrollen wird alles neu gezeichnet -- daher der
        // Eindruck von 5 Bildern pro Sekunde.
        //
        // Baender von 8 Pixeln sehen fuer das Auge genauso aus: die
        // Farbstufen zwischen zwei Baendern sind kleiner, als man
        // unterscheiden kann. Rund siebenmal weniger Aufrufe.
        int baender = Math.max(1, Math.min(w, (w + 7) / 8));
        for (int b = 0; b < baender; b++) {
            int ax = x + (int) ((long) w * b / baender);
            int bx = x + (int) ((long) w * (b + 1) / baender);
            if (bx <= ax) continue;
            float t = (b + 0.5f) / baender;
            ctx.fill(ax, y, bx, y2, mix(von, bis, t));
        }
    }


    /**
     * Kurzbeschreibung fuer die Modulkarte.
     *
     * Nimmt den ersten Satz aus ModuleInfo und kuerzt ihn auf die verfuegbare
     * Breite. Passt nichts mehr, wird mit Auslassungspunkten abgeschnitten --
     * ein abgehackter Satz ist schlimmer als gar keiner.
     */
    private String kurzInfo(Module m, int maxBreite) {
        try {
            String info = ModuleInfo.get(m.getName());
            if (info == null || info.isBlank()) return null;
            int punkt = info.indexOf('.');
            String satz = (punkt > 8) ? info.substring(0, punkt) : info;
            if (this.font.width(satz) <= maxBreite) return satz;
            // Wortweise kuerzen, damit nicht mitten im Wort abgeschnitten wird.
            String[] worte = satz.split(" ");
            StringBuilder b = new StringBuilder();
            for (String w : worte) {
                String test = b.length() == 0 ? w : b + " " + w;
                if (this.font.width(test + "...") > maxBreite) break;
                b.setLength(0);
                b.append(test);
            }
            if (b.length() == 0) return null;
            return b + "...";
        } catch (Throwable pvpErr) {
            return null;
        }
    }


    // ======================================================================
    // Raster fuer die Unterseiten
    // ======================================================================
    //
    // Die Unterseiten -- Macros, Waypoints, Skins, Theme, Keys, Community --
    // waren ueber Jahre gewachsen und benutzten jeweils eigene Abstaende:
    // mal 26, mal 28 Pixel, Zeilen fest auf 20 hoch. Nebeneinander sah das
    // unruhig aus, und beim Vergroessern des Fensters passte nichts mehr
    // zusammen.
    //
    // Diese beiden Helfer legen ein gemeinsames Raster fest. Jede Seite
    // benutzt sie, also aendert sich das Aussehen ueberall gleichzeitig,
    // wenn man hier eine Zahl anpasst.

    /** Hoehe einer Aktionszeile. */
    private static final int ROW_H = 26;
    /** Abstand zwischen zwei Bloecken. */
    private static final int GAP = 12;
    /**
     * Hoehe eines Eintrags in der Seitenleiste.
     *
     * Die Leiste ist von 108 auf 148 Pixel gewachsen -- dann muessen die
     * Eintraege mitwachsen, sonst wirken sie verloren in der Breite.
     */
    private static final int NAV_H = 26;

    /**
     * Ueberschrift mit Unterzeile.
     *
     * @return das neue cy, also die Stelle direkt darunter
     */
    private int seitenKopf(GuiGraphicsExtractor ctx, int cx, int cy,
                           String titel, String unterzeile, Theme t) {
        ctx.text(this.font, Component.literal(titel), cx, cy, C_TEXT);
        if (unterzeile != null) {
            ctx.text(this.font, Component.literal(unterzeile),
                    cx, cy + 12, C_TEXT_DIM, false);
            return cy + 12 + GAP + 8;
        }
        return cy + GAP + 8;
    }

    /**
     * Anklickbare Zeile mit Pfeil rechts.
     *
     * Registriert die Klickflaeche gleich mit -- vorher stand sie an jeder
     * Stelle einzeln im Code, und bei Aenderungen an der Hoehe vergass man
     * leicht eine davon.
     *
     * @return das neue cy
     */
    private int aktionsZeile(GuiGraphicsExtractor ctx, int cx, int cy, int cw,
                             String beschriftung, int mx, int my,
                             int accent, Theme t, Act aktion, String schluessel) {
        boolean hov = inRect(mx, my, cx, cy, cw, ROW_H);
        roundRect(ctx, cx, cy, cw, ROW_H,
                hov ? mix(C_INNER, accent, 0.35f) : C_INNER);
        // Akzentstreifen links beim Ueberfahren -- dasselbe Signal wie bei
        // den Modulkarten, damit sich die Oberflaeche einheitlich anfuehlt.
        if (hov) roundRect(ctx, cx, cy + 5, 3, ROW_H - 10, accent);
        ctx.text(this.font, Component.literal(beschriftung),
                cx + 12, cy + (ROW_H - 8) / 2, C_TEXT, false);
        ctx.text(this.font, Component.literal(">"),
                cx + cw - 16, cy + (ROW_H - 8) / 2, hov ? accent : C_TEXT_DIM, false);
        hits.add(new Hit(cx, cy, cw, ROW_H, aktion, null, null, schluessel));
        return cy + ROW_H + 8;
    }


    /**
     * Weiche Trennlinie.
     *
     * Vorher eine harte Ein-Pixel-Linie ueber die volle Breite. Ein Verlauf
     * zu beiden Seiten hin wirkt ruhiger und trennt trotzdem deutlich.
     */
    private void trennlinie(GuiGraphicsExtractor ctx, int x, int y, int w) {
        int halb = w / 2;
        verlauf(ctx, x, y, x + halb, y + 1, C_LINE & 0x00FFFFFF, C_LINE);
        verlauf(ctx, x + halb, y, x + w, y + 1, C_LINE, C_LINE & 0x00FFFFFF);
    }


    /**
     * Detailfeld rechts: die Einstellungen des gewaehlten Moduls.
     *
     * WARUM NICHT MEHR IN DER KARTE: bei zwei Spalten sprang beim Aufklappen
     * die halbe Liste, und man verlor die Stelle, an der man war. Hier bleibt
     * das Raster ruhig, und die Einstellungen haben mehr Platz als in einer
     * halbbreiten Karte.
     */
    private void zeichneDetail(GuiGraphicsExtractor ctx, int x, int y, int w, int h,
                               int accent, Theme t) {
        Module m = detail;
        if (m == null) return;

        ctx.fill(x, y, x + w, y + h, fade(C_SIDEBAR, openAnim));
        ctx.fill(x, y, x + 1, y + h, fade(C_LINE, openAnim));

        ctx.enableScissor(x, y, x + w, y + h);

        // BILDLAUF IM FELD.
        //
        // Vorher brach die Schleife ab, sobald der Platz zu Ende war -- bei
        // Modulen mit vielen Einstellungen, etwa Armor, sah man nur die
        // erste und kam an die uebrigen nicht heran.
        // Echte Bildzeit statt eines festen Werts. Mit 0.05 lief der
        // Bildlauf bei 144 Bildern pro Sekunde dreimal zu schnell und bei 30
        // zu langsam -- in beiden Faellen sprunghaft statt weich.
        detailScroll = anim(detailScroll, detailScrollZiel, 16f, letzteDt);
        int cy = y + PAD - (int) detailScroll;
        int cyStart = cy;

        // Kopf: Name, Zustand, Schliessen
        ctx.text(this.font, Component.literal(m.getName()), x + PAD, cy, C_TEXT);
        boolean zuHov = inRect(mx, my, x + w - PAD - 12, cy - 2, 14, 14);
        ctx.text(this.font, Component.literal("x"), x + w - PAD - 10, cy,
                zuHov ? C_TEXT : C_TEXT_DIM, false);
        hits.add(new Hit(x + w - PAD - 12, cy - 2, 14, 14, Act.SECTION, null, null, "closeDetail"));
        cy += 14;

        String info = ModuleInfo.get(m.getName());
        if (info != null && !info.isBlank()) {
            for (net.minecraft.util.FormattedCharSequence z
                    : this.font.split(Component.literal(info), w - PAD * 2)) {
                ctx.text(this.font, z, x + PAD, cy, C_TEXT_DIM, false);
                cy += 10;
            }
        }
        cy += 6;
        trennlinie(ctx, x + PAD, cy, w - PAD * 2);
        cy += GAP;

        // Eigener Auswahlbildschirm, falls das Modul einen hat
        cy = drawSubButton(ctx, m, x + PAD, cy, w - PAD * 2, accent, t);

        int nr = 0;
        for (Setting st : m.getSettings()) {
            if (st == m.getEnabledSetting()) continue;
            // Nicht abbrechen, nur ueberspringen: sonst wird die Gesamthoehe
            // falsch gemessen und man kann nie bis ans Ende scrollen.
            boolean sichtbar = (cy + SET_H > y) && (cy < y + h);

            // Jede zweite Zeile minimal abgesetzt.
            //
            // Bei einem Modul mit acht Einstellungen verschwimmen die Zeilen
            // sonst ineinander, und man greift beim Schieben den falschen
            // Regler. Der Unterschied ist bewusst winzig -- er soll fuehren,
            // nicht auffallen.
            if (sichtbar) {
                if (nr % 2 == 1) {
                    roundRect(ctx, x + PAD - 4, cy - 2, w - PAD * 2 + 8, SET_H,
                            fade(mix(C_SIDEBAR, C_TEXT, 0.03f), openAnim));
                }
                drawSetting(ctx, m, st, x + PAD, cy, w - PAD * 2, accent, t);
            }
            cy += SET_H;
            nr++;
        }

        // Hat das Modul gar keine Einstellungen, sagt es das -- statt einer
        // leeren Flaeche, bei der man raetselt, ob etwas fehlt.
        if (nr == 0) {
            ctx.text(this.font, Component.literal("No settings"),
                    x + PAD, cy, fade(C_TEXT_DIM, openAnim), false);
        }

        // Gesamthoehe fuers Scrollen merken.
        detailHoehe = (cy - cyStart) + PAD * 2;
        ctx.disableScissor();

        // Bildlaufbalken, sobald es mehr gibt als hineinpasst.
        if (detailHoehe > h) {
            int spurH = h - 8;
            int balkenH = Math.max(24, (int) (spurH * (h / (float) detailHoehe)));
            float pos = detailScroll / Math.max(1f, detailHoehe - h);
            if (pos < 0f) pos = 0f;
            if (pos > 1f) pos = 1f;
            int balkenY = y + 4 + (int) ((spurH - balkenH) * pos);
            roundRect(ctx, x + w - 6, balkenY, 4, balkenH,
                    fade(mix(accent, C_TEXT, 0.15f), openAnim));
        }
    }


    // ======================================================================
    // Seitenleiste nach der Vorlage
    // ======================================================================

    /** Hoehe eines Eintrags in der Seitenleiste. */
    // 26 statt 30: bei zwoelf Eintraegen plus Zaehlkarte lief die Leiste
    // mit 30 ueber den unteren Rand -- die letzten Bereiche waeren wieder
    // unerreichbar gewesen, derselbe Fehler wie frueher bei den Reitern.
    private static final int LEISTE_H = 26;
    private float leisteScroll = 0f;
    private float leisteScrollZiel = 0f;
    private int leisteHoehe = 0;

    /**
     * Symbol je Kategorie.
     *
     * Minecraft zeichnet keine Vektorsymbole, also werden sie aus Zeichen
     * der Standardschrift gebaut. Das ist bewusst schlicht -- lieber ein
     * klares Zeichen als ein unscharfes Bild.
     */
    private static String symbol(String name) {
        switch (name.toUpperCase()) {
            case "HUD":         return "\u25A3";   // Rahmen mit Punkt
            case "PVP":         return "\u2694";   // gekreuzte Schwerter
            case "CHEATS":      return "\u2620";   // Totenkopf
            case "PERFORMANCE": return "\u26A1";   // Blitz
            case "MISC":        return "\u2699";   // Zahnrad
            case "BOTS":        return "\u2699";
            case "WAYPOINTS":   return "\u2691";   // Fahne
            case "MACROS":      return "\u25B6";   // Abspielen
            case "SKINS":       return "\u263A";   // Gesicht
            case "KEYS":        return "\u2328";   // Tastatur
            case "THEME":       return "\u25D0";   // Halbkreis
            default:            return "\u25C6";
        }
    }

    /**
     * Seitenleiste links.
     *
     * Aufbau wie in der Vorlage:
     *   - Modulkategorien untereinander, je mit Symbol, Name und Zaehler
     *   - darunter die uebrigen Bereiche (Waypoints, Macros ...)
     *   - ganz unten die Gesamtzahl aktiver Module mit Fortschrittsbalken
     *
     * Der gewaehlte Eintrag ist eine gefuellte Pille im Akzentverlauf mit
     * leichtem Schein -- klar erkennbar, ohne zu schreien.
     */
    private void drawSeitenleiste(GuiGraphicsExtractor ctx, int x, int y, int h,
                                  int accent, Theme t, float dt) {
        ctx.fill(x, y, x + SIDEBAR_W, y + h, fade(C_SIDEBAR, openAnim));
        ctx.fill(x + SIDEBAR_W - 1, y, x + SIDEBAR_W, y + h, fade(C_LINE, openAnim));

        boolean searching = search != null && !search.getValue().isEmpty();
        int ex = x + 10, ew = SIDEBAR_W - 20;

        // Auf kleinen Bildschirmen passt nicht alles hinein. Dann wird
        // gescrollt statt abgeschnitten -- ein Eintrag, den man nicht
        // erreicht, ist schlimmer als einer, zu dem man scrollen muss.
        leisteScroll = anim(leisteScroll, leisteScrollZiel, 18f, dt);
        ctx.enableScissor(x, y, x + SIDEBAR_W, y + h);
        int cy = y + 12 - (int) leisteScroll;
        int cyAnfang = cy;

        // --- Favoriten ------------------------------------------------------
        if (GuiState.hasFavorites()) {
            boolean sel = !searching && favView;
            cy = leistenEintrag(ctx, ex, cy, ew, "", "Favorites",
                    String.valueOf(GuiState.getFavorites().size()), sel, t,
                    new Hit(ex, cy, ew, LEISTE_H, Act.FAVCAT, null, null, null));
        }

        // --- Modulkategorien -------------------------------------------------
        for (Module.Category cat : Module.Category.values()) {
            if (!hatModule(cat)) continue;
            // Bots erreicht man ueber die eigene Kachel auf dem Startbildschirm.
            // Hier zusaetzlich aufgefuehrt, stand es doppelt da -- und das
            // Mod-Menue soll nur die Kategorien zeigen, die nicht schon auf
            // dem Start liegen.
            if (nurVomStart(cat)) continue;
            int on = 0, total = 0;
            for (Module m : ModuleManager.INSTANCE.getByCategory(cat)) {
                total++;
                if (m.isEnabled()) on++;
            }
            boolean sel = !searching && !favView
                    && section == Section.MODULE && cat == selected;
            cy = leistenEintrag(ctx, ex, cy, ew, symbol(cat.name()), pretty(cat.name()),
                    on + "/" + total, sel, t,
                    new Hit(ex, cy, ew, LEISTE_H, Act.CATEGORY, null, null, cat));
        }

        // Waypoints, Macros, Wardrobe, Keys und Theme stehen jetzt auf dem
        // STARTBILDSCHIRM, nicht mehr hier. Das ClickGUI zeigt nur noch
        // Module -- so ist die Leiste kuerzer und eindeutig: hier stellt man
        // Module ein, alles andere erreicht man vom Start aus.

        leisteHoehe = (cy - cyAnfang) + 12;
        ctx.disableScissor();

        // --- Gesamtzaehlung unten, wie in der Vorlage ------------------------
        int aktiv = 0, alle = 0;
        for (Module m : ModuleManager.INSTANCE.getModules()) {
            alle++;
            if (m.isEnabled()) aktiv++;
        }
        int by = y + h - 58;
        if (by > cy + 8) {
            roundRect(ctx, ex, by, ew, 46, fade(C_CARD, openAnim));
            ctx.fill(ex + RADIUS, by, ex + ew - RADIUS, by + 1,
                    fade(mix(C_CARD, C_TEXT, 0.08f), openAnim));
            ctx.text(this.font, Component.literal("Active modules"),
                    ex + 10, by + 8, fade(C_TEXT_DIM, openAnim), false);
            ctx.text(this.font, Component.literal(aktiv + " / " + alle),
                    ex + 10, by + 20, fade(C_TEXT, openAnim), false);
            // Fortschrittsbalken
            int bw = ew - 20;
            roundRect(ctx, ex + 10, by + 34, bw, 4, fade(C_TRACK, openAnim));
            int voll = (alle == 0) ? 0 : (int) (bw * (aktiv / (float) alle));
            if (voll > 2) {
                verlaufRund(ctx, ex + 10, by + 34, voll, 4,
                        fade(akzent(0f), openAnim), fade(akzent(1f), openAnim));
            }
        }
    }

    /**
     * Ein Eintrag der Seitenleiste.
     *
     * Die Klickflaeche wird hier mitgegeben und registriert -- so kann sie
     * nie von der gezeichneten Flaeche abweichen. Genau dieses
     * Auseinanderlaufen hatte frueher Macros und Waypoints unerreichbar
     * gemacht.
     */
    private int leistenEintrag(GuiGraphicsExtractor ctx, int x, int y, int w,
                               String sym, String name, String zahl,
                               boolean sel, Theme t, Hit treffer) {
        boolean hov = inRect(mx, my, x, y, w, LEISTE_H);
        if (sel) {
            for (int i = 1; i <= 3; i++) {
                int a = (int) (22f / i * openAnim);
                roundRect(ctx, x - i, y - i, w + i * 2, LEISTE_H + i * 2,
                        (a << 24) | (akzent(0.5f) & 0x00FFFFFF));
            }
            verlaufRund(ctx, x, y, w, LEISTE_H,
                    fade(akzent(0f), openAnim), fade(akzent(1f), openAnim));
        } else if (hov) {
            roundRect(ctx, x, y, w, LEISTE_H, fade(C_CARD_HOV, openAnim));
        }
        int txt = sel ? 0xFFFFFFFF : (hov ? C_TEXT : C_TEXT_DIM);
        int mitte = y + (LEISTE_H - 8) / 2;
        // Kennzeichen als gezeichnetes Quadrat statt als Sonderzeichen.
        //
        // Die Symbole waren Unicode-Zeichen, die Minecrafts Standardschrift
        // nicht hat -- sie kamen aus einer Ersatzschrift und sahen unscharf
        // und fremd aus. Ein gezeichnetes Quadrat ist pixelgenau und passt
        // zur Kachel in den Modulzeilen.
        int kq = y + LEISTE_H / 2 - 2;
        ctx.fill(x + 12, kq, x + 16, kq + 4,
                fade(sel ? 0xFFFFFFFF : akzent(0.3f), openAnim));
        ctx.text(this.font, Component.literal(name), x + 26, mitte, fade(txt, openAnim), false);
        if (!zahl.isEmpty()) {
            int zw = this.font.width(zahl);
            ctx.text(this.font, Component.literal(zahl), x + w - zw - 10, mitte,
                    fade(sel ? 0xFFFFFFFF : C_TEXT_DIM, openAnim), false);
        }
        hits.add(treffer);
        return y + LEISTE_H + 3;
    }


    /**
     * Kategorien, die nur ueber den Startbildschirm erreichbar sind.
     *
     * Sie stehen NICHT in der Seitenleiste, bleiben aber voll nutzbar: die
     * Kachel auf dem Start oeffnet das Mod-Menue direkt in dieser Kategorie,
     * mit Detailfeld und allen Einstellungen. Auch die Suche findet ihre
     * Module weiterhin.
     */
    private static boolean nurVomStart(Module.Category cat) {
        return cat == Module.Category.BOTS;
    }

}
