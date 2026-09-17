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
    private static final int WIN_MAX_W = 780;
    private static final int WIN_MAX_H = 470;
    private static final int HEADER_H = 46;
    private static final int FOOTER_H = 24;
    private static final int SIDEBAR_W = 148;
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
    private static final int C_DIM      = 0xC8070910;  // Hintergrund abdunkeln
    private static final int C_WINDOW   = 0xFA151821;  // Fensterflaeche
    private static final int C_SIDEBAR  = 0xFF11141C;  // eine Stufe tiefer
    private static final int C_CARD     = 0xFF1E2230;  // Karte
    private static final int C_CARD_HOV = 0xFF272C3C;  // Karte unter dem Zeiger
    private static final int C_INNER    = 0xFF181C27;  // eingelassene Flaeche
    private static final int C_LINE     = 0xFF2A3040;  // Trennlinie, weicher
    private static final int C_TRACK    = 0xFF323949;  // Schieber-Schiene
    private static final int C_TEXT     = 0xFFE8ECF5;  // Haupttext
    private static final int C_TEXT_DIM = 0xFF8A93A8;  // Nebentext

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

    private final List<Hit> hits = new ArrayList<>();

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
            return (float) Theme.INSTANCE.opacity.get();
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
        int accent = t.accent.get() | 0xFF000000;

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
        drawSidebar(ctx, winX, winY + HEADER_H, winH - HEADER_H - FOOTER_H, accent, t, dt);
        drawContent(ctx, winX + SIDEBAR_W, winY + HEADER_H,
                winW - SIDEBAR_W, winH - HEADER_H - FOOTER_H, accent, t, dt);
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
        ctx.fill(x, y, x + w, y + HEADER_H, fade(C_SIDEBAR, openAnim));
        ctx.fill(x, y + HEADER_H - 1, x + w, y + HEADER_H, fade(C_LINE, openAnim));

        // The mark: a hollow ring, the same shape as the icon and as the
        // waypoint markers. Red once the addon is installed, so which of the
        // two you are running is visible at a glance rather than a surprise.
        int markColor = fade(Branding.accent(), openAnim);
        drawRingMark(ctx, x + PAD + 6, y + 17, 6, markColor);

        ctx.text(this.font, Component.literal(Branding.title()),
                x + PAD + 17, y + 8, fade(t.text.get(), openAnim));

        int active = 0;
        for (Module m : ModuleManager.INSTANCE.getModules()) {
            if (m.isEnabled()) active++;
        }
        ctx.text(this.font, Component.literal(active + " active"),
                x + PAD + 9, y + 19, fade(t.textDim.get(), openAnim), false);

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
            roundRect(ctx, sx, sy, sw, 20, fade(C_INNER, openAnim));
            ctx.text(this.font, Component.literal("Q"),
                    sx + 6, sy + 6, fade(0xFF6A6A76, openAnim), false);
            if (search.getValue().isEmpty()) {
                ctx.text(this.font, Component.literal("Search..."),
                        sx + 18, sy + 6, fade(0xFF6A6A76, openAnim), false);
            }
        }
    }

    private void drawSidebar(GuiGraphicsExtractor ctx, int x, int y, int h,
                             int accent, Theme t, float dt) {
        ctx.fill(x, y, x + SIDEBAR_W, y + h, fade(C_SIDEBAR, openAnim));
        ctx.fill(x + SIDEBAR_W - 1, y, x + SIDEBAR_W, y + h, fade(C_LINE, openAnim));

        sideScroll = anim(sideScroll, sideScrollTarget, 18f, dt);

        boolean searching = search != null && !search.getValue().isEmpty();
        int cy = y + PAD - (int) sideScroll;
        int cyStart = cy;

        // Nur innerhalb der Leiste zeichnen.
        ctx.enableScissor(x, y, x + SIDEBAR_W, y + h);

        // Favoriten ganz oben -- nur wenn welche angepinnt sind.
        if (GuiState.hasFavorites()) {
            boolean isSel = !searching && favView;
            boolean hov = inRect(mx, my, x + 6, cy, SIDEBAR_W - 12, NAV_H);
            int bg = isSel ? mix(C_CARD, accent, 0.18f) : (hov ? C_CARD : 0);
            if ((bg >>> 24) != 0) {
                roundRect(ctx, x + 6, cy, SIDEBAR_W - 12, NAV_H, fade(bg, openAnim));
            }
            ctx.text(this.font, Component.literal("* Favourites"),
                    x + 16, cy + 7,
                    fade(isSel ? t.text.get() : t.textDim.get(), openAnim), false);
            String badge = String.valueOf(GuiState.getFavorites().size());
            int bw0 = this.font.width(badge);
            ctx.text(this.font, Component.literal(badge),
                    x + SIDEBAR_W - 12 - bw0, cy + 7, fade(accent, openAnim), false);
            hits.add(new Hit(x + 6, cy, SIDEBAR_W - 12, NAV_H, Act.FAVCAT, null, null, null));
            if (isSel) {
                if (indicatorY < 0) indicatorY = cy;
                indicatorY = anim(indicatorY, cy, 16f, dt);
            }
            cy += NAV_H + 4;
        }

        for (Module.Category cat : Module.Category.values()) {
            // Leere Kategorien nicht anzeigen: die Cheats liegen im Addon,
            // ohne Addon ist die Kategorie leer -- ein Reiter ins Nichts
            // sieht nach einem Fehler aus.
            if (!hatModule(cat)) continue;

            boolean isSel = !searching && !favView
                    && section == Section.MODULE && cat == selected;
            boolean hov = inRect(mx, my, x + 6, cy, SIDEBAR_W - 12, NAV_H);

            if (isSel) {
                if (indicatorY < 0) indicatorY = cy;
                indicatorY = anim(indicatorY, cy, 16f, dt);
            }

            int bg = isSel ? mix(C_CARD, accent, 0.18f) : (hov ? C_CARD : 0);
            if ((bg >>> 24) != 0) {
                roundRect(ctx, x + 6, cy, SIDEBAR_W - 12, NAV_H, fade(bg, openAnim));
            }

            ctx.text(this.font, Component.literal(pretty(cat.name())),
                    x + 16, cy + 7, fade(isSel ? t.text.get() : t.textDim.get(), openAnim), false);

            int on = 0, total = 0;
            for (Module m : ModuleManager.INSTANCE.getByCategory(cat)) {
                total++;
                if (m.isEnabled()) on++;
            }
            String badge = on + "/" + total;
            int bw = this.font.width(badge);
            ctx.text(this.font, Component.literal(badge),
                    x + SIDEBAR_W - 12 - bw, cy + 7,
                    fade(on > 0 ? accent : 0xFF5A5A66, openAnim), false);

            hits.add(new Hit(x + 6, cy, SIDEBAR_W - 12, NAV_H, Act.CATEGORY, null, null, cat));
            cy += NAV_H + 4;
        }

        // Trennlinie: darunter stehen Bereiche, die keine Module sind.
        cy += 4;
        ctx.fill(x + 12, cy, x + SIDEBAR_W - 12, cy + 1, fade(C_LINE, openAnim));
        cy += 8;

        cy = drawSectionEntry(ctx, x, cy, "Waypoints", Section.WAYPOINTS,
                String.valueOf(com.vortex.client.waypoint.WaypointManager.all().size()),
                accent, t, dt, searching);
        cy = drawSectionEntry(ctx, x, cy, "Macros", Section.MACROS,
                String.valueOf(com.vortex.client.macro.MacroManager.all().size()),
                accent, t, dt, searching);
        cy = drawSectionEntry(ctx, x, cy, "Community", Section.COMMUNITY, null,
                accent, t, dt, searching);
        cy = drawSectionEntry(ctx, x, cy, "Keys", Section.KEYS, null,
                accent, t, dt, searching);
        cy = drawSectionEntry(ctx, x, cy, "Skins", Section.SKINS, null,
                accent, t, dt, searching);
        cy = drawSectionEntry(ctx, x, cy, "Theme", Section.DESIGN, null,
                accent, t, dt, searching);

        if (!searching && indicatorY >= 0) {
            ctx.fill(x + 2, (int) indicatorY + 4, x + 4, (int) indicatorY + 18,
                    fade(accent, openAnim));
        }
        ctx.disableScissor();

        sideContentHeight = (cy - cyStart) + PAD;

        // Hinweis, dass es weitergeht.
        if (sideContentHeight > h) {
            int barH = Math.max(20, (int) ((h - 8) * (h / (float) sideContentHeight)));
            float p = sideScroll / Math.max(1f, sideContentHeight - h);
            if (p < 0f) p = 0f;
            if (p > 1f) p = 1f;
            int barY = y + 4 + (int) ((h - 8 - barH) * p);
            ctx.fill(x + SIDEBAR_W - 3, barY, x + SIDEBAR_W - 1, barY + barH,
                    fade(mix(accent, C_TEXT, 0.2f), openAnim));
        }
    }

    /** Eine Zeile in der Leiste fuer einen Bereich ausserhalb der Module. */
    private int drawSectionEntry(GuiGraphicsExtractor ctx, int x, int cy, String label,
                                 Section sec, String badge, int accent, Theme t,
                                 float dt, boolean searching) {
        boolean isSel = !searching && !favView && section == sec;
        boolean hov = inRect(mx, my, x + 6, cy, SIDEBAR_W - 12, NAV_H);
        int bg = isSel ? mix(C_CARD, accent, 0.18f) : (hov ? C_CARD : 0);
        if ((bg >>> 24) != 0) {
            roundRect(ctx, x + 6, cy, SIDEBAR_W - 12, NAV_H, fade(bg, openAnim));
        }
        ctx.text(this.font, Component.literal(label), x + 16, cy + 7,
                fade(isSel ? t.text.get() : t.textDim.get(), openAnim), false);
        if (badge != null) {
            int bw = this.font.width(badge);
            ctx.text(this.font, Component.literal(badge),
                    x + SIDEBAR_W - 12 - bw, cy + 7, fade(accent, openAnim), false);
        }
        hits.add(new Hit(x + 6, cy, SIDEBAR_W - 12, NAV_H, Act.SECTION, null, null, sec));
        if (isSel) {
            if (indicatorY < 0) indicatorY = cy;
            indicatorY = anim(indicatorY, cy, 16f, dt);
        }
        return cy + 26;
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

        ctx.enableScissor(x, y, x + w, y + h);

        List<Module> list = visibleModules();
        int cx = x + PAD;
        int cw = w - PAD * 2 - 4;
        int cy = y + PAD - (int) scroll;

        for (Module m : list) {
            float ex = expandAnim.getOrDefault(m, 0f);
            ex = anim(ex, expanded.contains(m) ? 1f : 0f, 12f, dt);
            expandAnim.put(m, ex);

            int extra = extraHeight(m);
            int cardH = CARD_H + (int) (extra * ex);
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

                // Aktiv-Streifen links, jetzt ueber die volle Kartenhoehe und
                // abgerundet -- vorher ein harter Strich von 5 bis 21.
                if (on > 0.01f) {
                    roundRect(ctx, cx, cy + 6, 3, CARD_H - 12, fade(accent, on));
                }

                // ZWEIZEILIG: Name oben, Kurzbeschreibung darunter.
                //
                // Die Beschreibung stand frueher nur im Tooltip -- man musste
                // also auf jedes Modul zeigen, um zu wissen, was es tut. Bei
                // 58 Modulen ist das unbrauchbar. Jetzt steht sie direkt da,
                // wofuer die groessere Kartenhoehe den Platz schafft.
                ctx.text(this.font, Component.literal(m.getName()),
                        cx + 14, cy + 7, m.isEnabled() ? t.text.get() : C_TEXT_DIM);

                String kurz = kurzInfo(m, cw - 90);
                if (kurz != null) {
                    ctx.text(this.font, Component.literal(kurz),
                            cx + 14, cy + 19, C_TEXT_DIM, false);
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

            if (ex > 0.01f) {
                int inner = (int) (extra * ex);
                if (visible) {
                    ctx.fill(cx + 8, cy + CARD_H, cx + cw - 8, cy + CARD_H + 1, C_LINE);
                }
                ctx.enableScissor(cx, cy + CARD_H, cx + cw, cy + CARD_H + inner);

                int sy = cy + CARD_H + 4;
                int clipTop = Math.max(y, cy + CARD_H);
                int clipBottom = Math.min(y + h, cy + CARD_H + inner);
                if (sy + 18 > clipTop && sy < clipBottom) {
                    sy = drawSubButton(ctx, m, cx, sy, cw, accent, t);
                } else {
                    sy += subHeight(m);
                }
                for (Setting s : m.getSettings()) {
                    if (s == m.getEnabledSetting()) continue;
                    // Nur zeichnen (und klickbar machen), was im Fenster liegt.
                    if (sy + SET_H > clipTop && sy < clipBottom) {
                        drawSetting(ctx, m, s, cx + 8, sy, cw - 16, accent, t);
                    }
                    sy += SET_H;
                }
                ctx.disableScissor();
            }

            cy += cardH + 6;
        }

        contentHeight = (cy + (int) scroll) - (y + PAD) + PAD;
        ctx.disableScissor();

        if (contentHeight > h) {
            int trackH = h - 8;
            int barH = Math.max(24, (int) (trackH * (h / (float) contentHeight)));
            float p = scroll / Math.max(1f, contentHeight - h);
            if (p < 0f) p = 0f;
            if (p > 1f) p = 1f;
            int barY = y + 4 + (int) ((trackH - barH) * p);
            ctx.fill(x + w - 4, y + 4, x + w - 2, y + 4 + trackH, 0x30FFFFFF);
            ctx.fill(x + w - 4, barY, x + w - 2, barY + barH, mix(accent, C_TEXT, 0.15f));
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
                        cx, cy, t.textDim.get(), false);
                break;
            }
            case COMMUNITY: {
                ctx.text(this.font, Component.literal("Community"),
                        cx, cy, t.text.get());
                ctx.text(this.font,
                        Component.literal("Macros and presets shared by other players"),
                        cx, cy + 11, t.textDim.get(), false);
                cy += GAP + 8;
                cy = aktionsZeile(ctx, cx, cy, cw, "Browse shared macros",
                        mx, my, accent, t, Act.SECTION, "openCommunity");
                ctx.text(this.font,
                        Component.literal("Share your own on the website"),
                        cx, cy, t.textDim.get(), false);
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
                bx + 8, sy + 5, t.text.get(), false);
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
                    b.get() ? t.text.get() : t.textDim.get(), false);
            drawSwitch(ctx, x + w - 24, y + 4, b.get() ? 1f : 0f, accent);
            hits.add(new Hit(x, y, w, SET_H, Act.S_BOOL, m, s, null));

        } else if (s instanceof NumberSetting n) {
            ctx.text(this.font, Component.literal(name), x, y + 1,
                    t.textDim.get(), false);
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
                    t.textDim.get(), false);
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
                    t.textDim.get(), false);
            roundRect(ctx, x + w - 26, y + 4, 24, 12, 0xFF000000);
            roundRect(ctx, x + w - 25, y + 5, 22, 10, c.get() | 0xFF000000);
            hits.add(new Hit(x, y, w, SET_H, Act.S_COLOR, m, s, null));

        } else if (s instanceof KeySetting k) {
            ctx.text(this.font, Component.literal(name), x, y + 6,
                    t.textDim.get(), false);
            String val = k.isListening() ? "Press a key" : k.getKeyName();
            int vw = this.font.width(val);
            roundRect(ctx, x + w - vw - 10, y + 3, vw + 8, 14,
                    k.isListening() ? mix(C_INNER, accent, 0.4f) : C_INNER);
            ctx.text(this.font, Component.literal(val),
                    x + w - vw - 6, y + 6, k.isListening() ? accent : t.text.get(), false);
            hits.add(new Hit(x, y, w, SET_H, Act.S_KEY, m, s, null));

        } else {
            ctx.text(this.font, Component.literal(name), x, y + 6,
                    t.textDim.get(), false);
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
    private void drawSwitch(GuiGraphicsExtractor ctx, int x, int y, float on, int accent) {
        roundRect(ctx, x, y, 26, 13, mix(C_TRACK, accent, on));
        int kx = x + 2 + (int) (on * 12f);
        // Runder Knauf: Mittelstreifen plus schmalere Zeilen oben und unten.
        int knauf = mix(0xFFCBD2E0, 0xFFFFFFFF, on);
        ctx.fill(kx, y + 3, kx + 9, y + 10, knauf);
        ctx.fill(kx + 1, y + 2, kx + 8, y + 3, knauf);
        ctx.fill(kx + 1, y + 10, kx + 8, y + 11, knauf);
    }

    private void drawFooter(GuiGraphicsExtractor ctx, int x, int y, int w) {
        ctx.fill(x, y, x + w, y + FOOTER_H, fade(C_SIDEBAR, openAnim * opacity()));
        ctx.fill(x, y, x + w, y + 1, fade(C_LINE, openAnim));

        // Griff unten rechts zum Groesserziehen -- drei kurze Schraegstriche.
        int gx = x + w - 12, gy = y + FOOTER_H - 12;
        boolean gHov = inRect(mx, my, gx - 2, gy - 2, 14, 14);
        int gc = gHov ? (Theme.INSTANCE.accent.get() | 0xFF000000) : 0xFF6A6A76;
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
                    if (button == 1 || !hasContent(hit.module)) {
                        hit.module.toggle();
                    } else if (!expanded.add(hit.module)) {
                        expanded.remove(hit.module);
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
        int h = windowHeight() - HEADER_H - FOOTER_H;

        // Zeigt die Maus auf die Leiste? Dann diese scrollen.
        if (mx >= lastWinX && mx < lastWinX + SIDEBAR_W) {
            sideScrollTarget -= (float) vertical * 28f;
            float smax = Math.max(0f, sideContentHeight - h);
            if (sideScrollTarget < 0f) sideScrollTarget = 0f;
            if (sideScrollTarget > smax) sideScrollTarget = smax;
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
    private static float anim(float cur, float target, float speed, float dt) {
        float f = speed * dt;
        if (f > 1f) f = 1f;
        return cur + (target - cur) * f;
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
        for (int i = 0; i < w; i++) {
            ctx.fill(x + i, y, x + i + 1, y2, mix(von, bis, i / (float) w));
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
        ctx.text(this.font, Component.literal(titel), cx, cy, t.text.get());
        if (unterzeile != null) {
            ctx.text(this.font, Component.literal(unterzeile),
                    cx, cy + 12, t.textDim.get(), false);
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
                cx + 12, cy + (ROW_H - 8) / 2, t.text.get(), false);
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

}
