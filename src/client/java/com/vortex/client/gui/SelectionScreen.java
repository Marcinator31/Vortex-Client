package com.vortex.client.gui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Gemeinsame Grundlage der Auswahl-Menues (Mobs, Bloecke, Entities).
 *
 * Alle drei zeigen dasselbe: eine lange Liste von Eintraegen mit Symbol und
 * Namen, von denen beliebig viele an- oder abgewaehlt werden koennen. Statt das
 * dreimal zu schreiben, steckt die komplette Darstellung und Bedienung hier --
 * die drei Menues liefern nur noch ihre Eintraege und sagen, wie ein Eintrag
 * an- und abgeschaltet wird.
 *
 * Der Stil entspricht dem ClickGUI: Fenster mit Kopfzeile, Suchfeld, weich
 * animierte Karten, Scrollbalken. Gerade bei ueber hundert Entity-Typen macht
 * die Suche den Unterschied.
 */
public abstract class SelectionScreen extends Screen {

    /** Ein auswaehlbarer Eintrag. */
    protected static final class Entry {
        final ItemStack icon;
        final String id;
        final String name;
        final String search; // klein geschrieben, fuer die Suche
        Entry(Item icon, String id, String name) {
            this.icon = new ItemStack(icon);
            this.id = id;
            this.name = name;
            this.search = (name + " " + id).toLowerCase(Locale.ROOT);
        }
    }

    // ---- Masse ----
    private static final int WIN_MAX_W = 620;
    private static final int WIN_MAX_H = 400;
    private static final int HEADER_H = 46;
    private static final int FOOTER_H = 20;
    private static final int CELL_H = 30;
    private static final int PAD = 8;

    // ---- Farben (wie im ClickGUI) ----
    private static final int C_DIM      = 0xB4000000;
    private static final int C_WINDOW   = 0xF21B1B21;
    private static final int C_BAR      = 0xFF16161B;
    private static final int C_CARD     = 0xFF24242B;
    private static final int C_CARD_HOV = 0xFF2E2E38;
    private static final int C_INNER    = 0xFF1C1C22;
    private static final int C_LINE     = 0xFF31313A;

    private final Screen parent;
    private final String title;

    protected final List<Entry> entries = new ArrayList<>();
    private final Map<String, Float> hoverAnim = new HashMap<>();
    private final Map<String, Float> selAnim = new HashMap<>();

    private EditBox search;
    private float openAnim = 0f;
    private float scroll = 0f;
    private float scrollTarget = 0f;
    private int contentHeight = 0;
    private long lastNano = 0L;
    private int mx = 0, my = 0;

    // Klickflaechen, beim Zeichnen gefuellt (siehe ClickGui -- gleiches Prinzip).
    private static final class Hit {
        final int x, y, w, h;
        final String id;
        Hit(int x, int y, int w, int h, String id) {
            this.x = x; this.y = y; this.w = w; this.h = h; this.id = id;
        }
        boolean contains(double px, double py) {
            return px >= x && px < x + w && py >= y && py < y + h;
        }
    }
    private final List<Hit> hits = new ArrayList<>();
    private Hit clearButton = null;

    protected SelectionScreen(Screen parent, String title) {
        super(Component.literal(title));
        this.parent = parent;
        this.title = title;
    }

    // ---- Von den drei Menues zu liefern ----

    /** Eintraege aufbauen (wird einmal beim Oeffnen gerufen). */
    protected abstract void buildEntries();

    /** Ist dieser Eintrag ausgewaehlt? */
    protected abstract boolean isOn(String id);

    /** Auswahl umschalten. */
    protected abstract void toggle(String id);

    /** Gesamte Auswahl leeren. */
    protected abstract void clearAll();

    /** Beschriftung, was die Auswahl bewirkt (Kopfzeile). */
    protected abstract String hint();

    // ---------------------------------------------------------------- Aufbau

    @Override
    protected void init() {
        if (entries.isEmpty()) {
            buildEntries();
            entries.sort((a, b) -> a.name.compareToIgnoreCase(b.name));
        }
        int winW = Math.min(this.width - 40, WIN_MAX_W);
        int winX = (this.width - winW) / 2;
        int winY = (this.height - windowHeight()) / 2;

        int sw = 150;
        this.search = new EditBox(this.font,
                winX + winW - sw - PAD, winY + 26, sw, 14, Component.literal(""));
        this.search.setBordered(false);
        this.search.setMaxLength(48);
        this.addRenderableWidget(this.search);
    }

    private int windowHeight() {
        return Math.min(this.height - 40, WIN_MAX_H);
    }

    // -------------------------------------------------------------- Zeichnen

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        this.mx = mouseX;
        this.my = mouseY;
        hits.clear();
        clearButton = null;

        long now = System.nanoTime();
        float dt = (lastNano == 0L) ? 0.016f : (now - lastNano) / 1_000_000_000.0f;
        lastNano = now;
        if (dt > 0.1f) dt = 0.1f;
        openAnim = anim(openAnim, 1f, 14f, dt);

        ctx.fill(0, 0, this.width, this.height, fade(C_DIM, openAnim));

        int winW = Math.min(this.width - 40, WIN_MAX_W);
        int winH = windowHeight();
        int winX = (this.width - winW) / 2;
        int winY = (this.height - winH) / 2 + (int) ((1f - openAnim) * 12f);

        Theme t = Theme.INSTANCE;
        int accent = t.accent.get() | 0xFF000000;

        roundRect(ctx, winX, winY, winW, winH, fade(C_WINDOW, openAnim));
        ctx.fill(winX, winY, winX + winW, winY + 1, fade(accent, openAnim * 0.9f));

        drawHeader(ctx, winX, winY, winW, accent, t);
        drawGrid(ctx, winX, winY + HEADER_H, winW, winH - HEADER_H - FOOTER_H, accent, t, dt);
        drawFooter(ctx, winX, winY + winH - FOOTER_H, winW);

        if (search != null) {
            search.setX(winX + winW - search.getWidth() - PAD);
            search.setY(winY + 26);
        }
        super.extractRenderState(ctx, mouseX, mouseY, delta);
    }

    private void drawHeader(GuiGraphicsExtractor ctx, int x, int y, int w, int accent, Theme t) {
        ctx.fill(x, y, x + w, y + HEADER_H, fade(C_BAR, openAnim));
        ctx.fill(x, y + HEADER_H - 1, x + w, y + HEADER_H, fade(C_LINE, openAnim));

        // Zurueck-Pfeil + Titel.
        boolean backHov = inRect(mx, my, x + PAD, y + 8, 16, 16);
        ctx.text(this.font, Component.literal("<"),
                x + PAD + 4, y + 12, fade(backHov ? accent : 0xFF9A9AA6, openAnim));
        hits.add(new Hit(x + PAD, y + 8, 16, 16, "\0back"));

        ctx.text(this.font, Component.literal(title),
                x + PAD + 22, y + 11, fade(t.text.get(), openAnim));

        int on = countSelected();
        ctx.text(this.font,
                Component.literal(on + " ausgewaehlt  \u00B7  " + hint()),
                x + PAD + 22, y + 28, fade(t.textDim.get(), openAnim), false);

        // "Clear selection" -- nur wenn es etwas zu leeren gibt.
        if (on > 0) {
            String lbl = "Clear selection";
            int lw = this.font.width(lbl) + 12;
            int bx = x + w - lw - PAD;
            int by = y + 7;
            boolean hov = inRect(mx, my, bx, by, lw, 14);
            roundRect(ctx, bx, by, lw, 14, fade(hov ? mix(C_INNER, accent, 0.3f) : C_INNER, openAnim));
            ctx.text(this.font, Component.literal(lbl),
                    bx + 6, by + 3, fade(t.text.get(), openAnim), false);
            clearButton = new Hit(bx, by, lw, 14, "\0clear");
        }

        // Suchfeld-Rahmen.
        if (search != null) {
            int sx = search.getX() - 16, sy = y + 23, sw = search.getWidth() + 20;
            roundRect(ctx, sx, sy, sw, 20, fade(C_INNER, openAnim));
            ctx.text(this.font, Component.literal("Q"),
                    sx + 6, sy + 6, fade(0xFF6A6A76, openAnim), false);
            if (search.getValue().isEmpty()) {
                ctx.text(this.font, Component.literal("Search..."),
                        sx + 18, sy + 6, fade(0xFF6A6A76, openAnim), false);
            }
        }
    }

    private void drawGrid(GuiGraphicsExtractor ctx, int x, int y, int w, int h,
                          int accent, Theme t, float dt) {
        scroll = anim(scroll, scrollTarget, 18f, dt);
        ctx.enableScissor(x, y, x + w, y + h);

        List<Entry> list = filtered();
        int inner = w - PAD * 2 - 6;
        int cols = Math.max(1, inner / 190);
        int cellW = (inner - (cols - 1) * 6) / cols;

        int i = 0;
        int cy0 = y + PAD - (int) scroll;
        for (Entry e : list) {
            int col = i % cols;
            int row = i / cols;
            int cx = x + PAD + col * (cellW + 6);
            int cy = cy0 + row * (CELL_H + 6);
            i++;

            if (cy + CELL_H < y || cy > y + h) continue; // ausserhalb -> ueberspringen

            boolean on = isOn(e.id);
            boolean hov = inRect(mx, my, cx, cy, cellW, CELL_H);

            float hv = hoverAnim.getOrDefault(e.id, 0f);
            hv = anim(hv, hov ? 1f : 0f, 14f, dt);
            hoverAnim.put(e.id, hv);

            float sv = selAnim.getOrDefault(e.id, on ? 1f : 0f);
            sv = anim(sv, on ? 1f : 0f, 14f, dt);
            selAnim.put(e.id, sv);

            int bg = mix(mix(C_CARD, C_CARD_HOV, hv), mix(C_CARD, accent, 0.35f), sv);
            roundRect(ctx, cx, cy, cellW, CELL_H, bg);
            if (sv > 0.01f) {
                ctx.fill(cx, cy + 4, cx + 2, cy + CELL_H - 4, fade(accent, sv));
            }

            try {
                ctx.item(e.icon, cx + 8, cy + 7);
            } catch (Throwable pvpErr) {
                com.vortex.client.core.Errors.report("SelectionScreen", pvpErr);
            }

            // Namen kuerzen, wenn er nicht passt.
            String name = e.name;
            int maxW = cellW - 34;
            if (this.font.width(name) > maxW) {
                while (name.length() > 1 && this.font.width(name + "..") > maxW) {
                    name = name.substring(0, name.length() - 1);
                }
                name = name + "..";
            }
            ctx.text(this.font, Component.literal(name),
                    cx + 30, cy + 11, on ? t.text.get() : 0xFFB4B4C0, false);

            hits.add(new Hit(cx, cy, cellW, CELL_H, e.id));
        }

        int rows = (list.size() + cols - 1) / cols;
        contentHeight = rows * (CELL_H + 6) + PAD * 2;
        ctx.disableScissor();

        if (contentHeight > h) {
            int trackH = h - 8;
            int barH = Math.max(24, (int) (trackH * (h / (float) contentHeight)));
            float p = scroll / Math.max(1f, contentHeight - h);
            if (p < 0f) p = 0f;
            if (p > 1f) p = 1f;
            int barY = y + 4 + (int) ((trackH - barH) * p);
            ctx.fill(x + w - 5, y + 4, x + w - 3, y + 4 + trackH, 0x30FFFFFF);
            ctx.fill(x + w - 5, barY, x + w - 3, barY + barH, mix(accent, 0xFFFFFFFF, 0.15f));
        }

        if (list.isEmpty()) {
            String msg = "No results";
            ctx.text(this.font, Component.literal(msg),
                    x + (w - this.font.width(msg)) / 2, y + h / 2 - 4,
                    0xFF6A6A76, false);
        }
    }

    private void drawFooter(GuiGraphicsExtractor ctx, int x, int y, int w) {
        ctx.fill(x, y, x + w, y + FOOTER_H, fade(C_BAR, openAnim));
        ctx.fill(x, y, x + w, y + 1, fade(C_LINE, openAnim));
        ctx.text(this.font,
                Component.literal("Click to toggle   ·   Type to search   ·   ESC to go back"),
                x + PAD, y + 6, fade(0xFF74747F, openAnim), false);
    }

    // ---------------------------------------------------------------- Eingabe

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent click, boolean doubled) {
        if (super.mouseClicked(click, doubled)) return true;

        if (clearButton != null && clearButton.contains(mx, my)) {
            clearAll();
            selAnim.clear();
            return true;
        }
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit hit = hits.get(i);
            if (!hit.contains(mx, my)) continue;
            if ("\0back".equals(hit.id)) {
                this.onClose();
            } else {
                toggle(hit.id);
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY,
                                 double horizontal, double vertical) {
        int h = windowHeight() - HEADER_H - FOOTER_H;
        scrollTarget -= (float) vertical * 36f;
        float max = contentHeight - h;
        if (max < 0f) max = 0f;
        if (scrollTarget < 0f) scrollTarget = 0f;
        if (scrollTarget > max) scrollTarget = max;
        return true;
    }

    // ----------------------------------------------------------- Hilfsmittel

    private List<Entry> filtered() {
        String q = (search == null) ? "" : search.getValue().trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) return entries;
        List<Entry> out = new ArrayList<>();
        for (Entry e : entries) {
            if (e.search.contains(q)) out.add(e);
        }
        return out;
    }

    private int countSelected() {
        int n = 0;
        for (Entry e : entries) {
            if (isOn(e.id)) n++;
        }
        return n;
    }

    private boolean inRect(int px, int py, int x, int y, int w, int h) {
        return px >= x && px < x + w && py >= y && py < y + h;
    }

    private static float anim(float cur, float target, float speed, float dt) {
        float f = speed * dt;
        if (f > 1f) f = 1f;
        return cur + (target - cur) * f;
    }

    private void roundRect(GuiGraphicsExtractor ctx, int x, int y, int w, int h, int color) {
        if (w <= 0 || h <= 0) return;
        ctx.fill(x + 1, y, x + w - 1, y + h, color);
        ctx.fill(x, y + 1, x + 1, y + h - 1, color);
        ctx.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
    }

    private static int fade(int argb, float f) {
        if (f >= 1f) return argb;
        if (f <= 0f) return argb & 0x00FFFFFF;
        int a = (int) (((argb >>> 24) & 0xFF) * f);
        return (a << 24) | (argb & 0x00FFFFFF);
    }

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

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(parent);
    }
}
