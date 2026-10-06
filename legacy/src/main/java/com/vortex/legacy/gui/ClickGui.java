package com.vortex.legacy.gui;

import com.mojang.blaze3d.platform.GlStateManager;
import com.vortex.legacy.VortexLegacy;
import com.vortex.legacy.core.BoolSetting;
import com.vortex.legacy.core.ClientSettings;
import com.vortex.legacy.core.ColorSetting;
import com.vortex.legacy.core.Config;
import com.vortex.legacy.core.KeySetting;
import com.vortex.legacy.core.ModeSetting;
import com.vortex.legacy.core.Module;
import com.vortex.legacy.core.ModuleManager;
import com.vortex.legacy.core.NumberSetting;
import com.vortex.legacy.core.Setting;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.screen.Screen;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/**
 * Mod-Menue (Right Shift) im Vortex-Stil: Kategorien links, Module als
 * Karten mit Schalter rechts, Suche oben. Klick auf eine Karte schaltet,
 * Rechtsklick / Zahnrad oeffnet die Einstellungen.
 */
public class ClickGui extends Screen {
    private static Module.Category lastCat = Module.Category.HUD;
    private static final int SETTINGS_TAB = -1;

    private int tab = lastCat.ordinal();          // Kategorie oder SETTINGS_TAB
    private Module open;                          // Einstellungen dieses Moduls (oder null)
    private String search = "";
    private float scroll, scrollTarget, maxScroll;
    private final long openedAt = System.currentTimeMillis();
    private final Map<Object, float[]> hover = new HashMap<Object, float[]>();
    private long lastFrame;
    private float dt;
    private KeySetting listening;
    private Setting dragging;

    // Fensterflaeche (in render berechnet)
    private float px, py, pw, ph;
    private static final float SIDE = 108, HEAD = 34, R = 9;

    @Override public boolean shouldPauseGame() { return false; }

    private float anim(Object k, boolean on, float speed) {
        float[] v = hover.get(k);
        if (v == null) { v = new float[]{ on ? 1 : 0 }; hover.put(k, v); }
        v[0] += ((on ? 1 : 0) - v[0]) * (1 - (float) Math.exp(-dt * speed));
        return v[0];
    }

    private List<Module> shownModules() {
        List<Module> l = new ArrayList<Module>();
        if (!search.isEmpty()) {
            for (Module m : ModuleManager.INSTANCE.all())
                if (m.getName().toLowerCase().contains(search.toLowerCase()) || m.getDescription().toLowerCase().contains(search.toLowerCase())) l.add(m);
            return l;
        }
        if (tab < 0) return l;
        return ModuleManager.INSTANCE.byCategory(Module.Category.values()[tab]);
    }

    // ------------------------------------------------------------------ Zeichnen

    @Override
    public void render(int mx, int my, float delta) {
        long now = System.currentTimeMillis();
        dt = lastFrame == 0 ? 0 : Math.min(0.1f, (now - lastFrame) / 1000f);
        lastFrame = now;
        float t = Math.min(1f, (now - openedAt) / 180f);
        float ease = 1 - (1 - t) * (1 - t) * (1 - t);

        Render2D.rect(0, 0, width, height, Render2D.alpha(0x99000000, ease));
        pw = Math.min(width - 24, 470);
        ph = Math.min(height - 24, 300);
        px = (width - pw) / 2f;
        py = (height - ph) / 2f;

        GlStateManager.pushMatrix();
        float sc = 0.96f + 0.04f * ease;
        GlStateManager.translate(width / 2f, height / 2f, 0);
        GlStateManager.scale(sc, sc, 1);
        GlStateManager.translate(-width / 2f, -height / 2f, 0);

        Render2D.shadow(px, py, pw, ph, R, 12, Render2D.alpha(0x70000000, ease));
        Render2D.round(px, py, pw, ph, R, Render2D.alpha(Theme.BG, ease));
        Render2D.roundOutline(px, py, pw, ph, R, 1, Render2D.alpha(Theme.LINE, ease));

        drawSidebar(mx, my, ease);
        drawHeader(mx, my, ease);

        float cx = px + SIDE + 10, cy = py + HEAD + 8, cw = pw - SIDE - 20, ch = ph - HEAD - 16;
        Render2D.scissor(cx - 2, cy - 2, cw + 4, ch + 4);
        scroll += (scrollTarget - scroll) * (1 - (float) Math.exp(-dt * 16));
        if (open != null) drawSettings(open.getSettings(), open, cx, cy - scroll, cw, mx, my, ease);
        else if (tab == SETTINGS_TAB && search.isEmpty()) drawSettings(ClientSettings.INSTANCE.all(), null, cx, cy - scroll, cw, mx, my, ease);
        else drawModules(cx, cy - scroll, cw, ch, mx, my, ease);
        Render2D.endScissor();

        GlStateManager.popMatrix();
        super.render(mx, my, delta);
    }

    private void drawSidebar(int mx, int my, float a) {
        Render2D.round(px, py, SIDE, ph, R, Render2D.alpha(0x30000000, a));
        // Logo
        Render2D.textScaled("VORTEX", px + 12, py + 12, 1.25f, Render2D.alpha(0xFFFFFFFF, a), true);
        Render2D.text("1.8.9", px + 12, py + 25, Render2D.alpha(Theme.DIM, a), false);
        float y = py + 44;
        for (Module.Category c : Module.Category.values()) {
            boolean sel = search.isEmpty() && open == null && tab == c.ordinal() || (open != null && open.getCategory() == c && tab == c.ordinal());
            y = sideItem(c.label, y, sel, mx, my, a, c);
        }
        y += 6;
        Render2D.rect(px + 10, y, SIDE - 20, 1, Render2D.alpha(Theme.LINE, a));
        y += 7;
        y = sideItem("Settings", y, tab == SETTINGS_TAB && open == null, mx, my, a, "settings");
        // HUD-Editor unten
        float by = py + ph - 26;
        boolean ov = mx >= px + 8 && mx <= px + SIDE - 8 && my >= by && my <= by + 18;
        float h = anim("hudedit", ov, 14);
        Render2D.round(px + 8, by, SIDE - 16, 18, 5, Render2D.alpha(Render2D.mix(Theme.accent(), Theme.accent2(), h * 0.5f), a));
        Render2D.textCentered("Edit HUD", px + SIDE / 2f, by + 5, Render2D.alpha(0xFFFFFFFF, a), true);
    }

    private float sideItem(String label, float y, boolean sel, int mx, int my, float a, Object key) {
        boolean ov = mx >= px + 6 && mx <= px + SIDE - 6 && my >= y && my <= y + 17;
        float h = anim(key, ov || sel, 14);
        if (h > 0.01f) Render2D.round(px + 6, y, SIDE - 12, 17, 5, Render2D.alpha(sel ? Render2D.alpha(Theme.accent(), 0.28f) : 0x18FFFFFF, a * h));
        if (sel) Render2D.round(px + 6, y + 4, 2.5f, 9, 1.2f, Render2D.alpha(Theme.accent(), a));
        Render2D.text(label, px + 15, y + 5, Render2D.alpha(sel ? 0xFFFFFFFF : Render2D.mix(Theme.TEXT2, 0xFFFFFFFF, h), a), false);
        return y + 19;
    }

    private void drawHeader(int mx, int my, float a) {
        float hx = px + SIDE + 10, hy = py + 9, hw = pw - SIDE - 20;
        String title = open != null ? open.getName() : !search.isEmpty() ? "Search" : tab == SETTINGS_TAB ? "Client Settings" : Module.Category.values()[tab].label;
        if (open != null) {
            boolean ov = mx >= hx && mx <= hx + 14 && my >= hy && my <= hy + 16;
            Render2D.text("←", hx + 2, hy + 4, Render2D.alpha(ov ? 0xFFFFFFFF : Theme.TEXT2, a), false);
            hx += 16;
        }
        Render2D.textScaled(title, hx, hy + 3, 1.15f, Render2D.alpha(0xFFFFFFFF, a), true);
        // Suchfeld
        float sw = 110, sx = px + pw - 10 - sw;
        Render2D.round(sx, hy, sw, 16, 5, Render2D.alpha(0x40000000, a));
        Render2D.roundOutline(sx, hy, sw, 16, 5, 1, Render2D.alpha(search.isEmpty() ? Theme.LINE : Render2D.alpha(Theme.accent(), 0.6f), a));
        boolean blink = (System.currentTimeMillis() / 500) % 2 == 0;
        String s = search.isEmpty() ? "Search modules..." : search + (blink ? "_" : "");
        Render2D.text(Render2D.trim(s, (int) sw - 10), sx + 6, hy + 4, Render2D.alpha(search.isEmpty() ? Theme.DIM : 0xFFFFFFFF, a), false);
        Render2D.rect(px + SIDE + 10, py + HEAD, hw, 1, Render2D.alpha(Theme.LINE, a));
    }

    private static final float CARD_H = 40, GAP = 6;

    private void drawModules(float x, float y, float w, float h, int mx, int my, float a) {
        List<Module> list = shownModules();
        int cols = w > 300 ? 3 : 2;
        float cw = (w - GAP * (cols - 1)) / cols;
        for (int i = 0; i < list.size(); i++) {
            Module m = list.get(i);
            float cx = x + (i % cols) * (cw + GAP), cy = y + (i / cols) * (CARD_H + GAP);
            boolean ov = mx >= cx && mx <= cx + cw && my >= cy && my <= cy + CARD_H && my >= py + HEAD && my <= py + ph;
            float hv = anim(m, ov, 14);
            float on = anim("on:" + m.getName(), m.isEnabled(), 12);
            Render2D.round(cx, cy, cw, CARD_H, 6, Render2D.alpha(Render2D.mix(Theme.CARD, Theme.CARD_HOVER, hv), a));
            if (on > 0.01f) Render2D.roundOutline(cx, cy, cw, CARD_H, 6, 1, Render2D.alpha(Theme.accent(), a * on * 0.8f));
            Render2D.text(Render2D.trim(m.getName(), (int) cw - 34), cx + 8, cy + 8, Render2D.alpha(0xFFFFFFFF, a), false);
            Render2D.text(Render2D.trim(m.getDescription(), (int) cw - 14), cx + 8, cy + 23, Render2D.alpha(Theme.DIM, a), false);
            // Schalter
            float sx = cx + cw - 25, sy = cy + 7;
            Render2D.round(sx, sy, 18, 10, 5, Render2D.alpha(Render2D.mix(0x40FFFFFF, Theme.accent(), on), a));
            Render2D.circle(sx + 5 + on * 8, sy + 5, 3.5f, Render2D.alpha(0xFFFFFFFF, a));
            // "..." fuer Einstellungen (oder Rechtsklick)
            Render2D.text("\u2022\u2022\u2022", cx + cw - 15, cy + CARD_H - 11, Render2D.alpha(Render2D.mix(Theme.DIM, 0xFFFFFFFF, hv), a), false);
        }
        float total = ((list.size() + cols - 1) / cols) * (CARD_H + GAP);
        maxScroll = Math.max(0, total - h);
        if (list.isEmpty()) Render2D.textCentered("Nothing found", x + w / 2, y + 30, Render2D.alpha(Theme.DIM, a), false);
    }

    private static final float ROW = 22;

    private void drawSettings(List<Setting> settings, Module m, float x, float y, float w, int mx, int my, float a) {
        float yy = y;
        if (m != null) {
            Render2D.text(m.getDescription(), x, yy + 2, Render2D.alpha(Theme.TEXT2, a), false);
            yy += 16;
            yy = row(m.key, x, yy, w, mx, my, a, "Keybind");
        }
        for (Setting s : settings) {
            if (!s.isVisible()) continue;
            if (m instanceof com.vortex.legacy.hud.HudModule && (s.getName().equals("X") || s.getName().equals("Y"))) continue;
            yy = row(s, x, yy, w, mx, my, a, s.getName());
        }
        maxScroll = Math.max(0, yy + scroll - y - (ph - HEAD - 16));
    }

    private float row(Setting s, float x, float y, float w, int mx, int my, float a, String label) {
        boolean ov = mx >= x && mx <= x + w && my >= y && my <= y + ROW - 2;
        float hv = anim(s, ov, 14);
        Render2D.round(x, y, w, ROW - 2, 5, Render2D.alpha(Render2D.mix(0x22FFFFFF, 0x33FFFFFF, hv), a));
        Render2D.text(label, x + 7, y + 6, Render2D.alpha(0xFFFFFFFF, a), false);
        float rx = x + w - 7;
        if (s instanceof BoolSetting) {
            float on = anim("b:" + System.identityHashCode(s), ((BoolSetting) s).get(), 12);
            Render2D.round(rx - 18, y + 5, 18, 10, 5, Render2D.alpha(Render2D.mix(0x40FFFFFF, Theme.accent(), on), a));
            Render2D.circle(rx - 13 + on * 8, y + 10, 3.5f, Render2D.alpha(0xFFFFFFFF, a));
        } else if (s instanceof NumberSetting) {
            NumberSetting n = (NumberSetting) s;
            float sw = Math.min(110, w * 0.4f), sx = rx - sw;
            float f = (float) ((n.get() - n.getMin()) / (n.getMax() - n.getMin()));
            Render2D.round(sx, y + 8, sw, 4, 2, Render2D.alpha(0x40FFFFFF, a));
            Render2D.round(sx, y + 8, Math.max(4, sw * f), 4, 2, Render2D.alpha(Theme.accent(), a));
            Render2D.circle(sx + sw * f, y + 10, 4, Render2D.alpha(0xFFFFFFFF, a));
            String v = n.getStep() >= 1 ? String.valueOf(n.getInt()) : String.format("%.2f", n.get());
            Render2D.text(v, sx - 6 - Render2D.width(v), y + 6, Render2D.alpha(Theme.TEXT2, a), false);
            if (dragging == s && Mouse.isButtonDown(0)) n.set(n.getMin() + (n.getMax() - n.getMin()) * Math.max(0, Math.min(1, (mx - sx) / sw)));
        } else if (s instanceof ModeSetting) {
            String v = "< " + ((ModeSetting) s).get() + " >";
            Render2D.text(v, rx - Render2D.width(v), y + 6, Render2D.alpha(Render2D.mix(Theme.TEXT2, Theme.accent(), hv), a), false);
        } else if (s instanceof ColorSetting) {
            int c = ((ColorSetting) s).get();
            float sx = rx - 14 * PALETTE.length;
            for (int i = 0; i < PALETTE.length; i++) {
                int pc = PALETTE[i] | (c & 0xFF000000);
                Render2D.round(sx + i * 14, y + 4, 11, 11, 3, Render2D.alpha(pc | 0xFF000000, a));
                if ((pc & 0xFFFFFF) == (c & 0xFFFFFF)) Render2D.roundOutline(sx + i * 14 - 1.5f, y + 2.5f, 14, 14, 4, 1, Render2D.alpha(0xFFFFFFFF, a));
            }
        } else if (s instanceof KeySetting) {
            KeySetting k = (KeySetting) s;
            String v = listening == k ? "Press a key..." : k.label();
            float bw = Render2D.width(v) + 12;
            Render2D.round(rx - bw, y + 3, bw, 14, 4, Render2D.alpha(listening == k ? Render2D.alpha(Theme.accent(), 0.5f) : 0x40000000, a));
            Render2D.text(v, rx - bw + 6, y + 6, Render2D.alpha(0xFFFFFFFF, a), false);
        }
        return y + ROW;
    }

    private static final int[] PALETTE = { 0xFFFFFF, 0x8B5CF6, 0x3B82F6, 0x22C55E, 0xF5B942, 0xEF4444, 0xEC4899, 0x06B6D4 };

    // ------------------------------------------------------------------ Eingabe

    @Override
    protected void mouseClicked(int mx, int my, int button) {
        float t = 1;
        // Sidebar
        float y = py + 44;
        for (Module.Category c : Module.Category.values()) {
            if (mx >= px + 6 && mx <= px + SIDE - 6 && my >= y && my <= y + 17) { tab = c.ordinal(); lastCat = c; open = null; search = ""; scrollTarget = scroll = 0; return; }
            y += 19;
        }
        y += 13;
        if (mx >= px + 6 && mx <= px + SIDE - 6 && my >= y && my <= y + 17) { tab = SETTINGS_TAB; open = null; search = ""; scrollTarget = scroll = 0; return; }
        float by = py + ph - 26;
        if (mx >= px + 8 && mx <= px + SIDE - 8 && my >= by && my <= by + 18) { client.setScreen(new HudEditorScreen(this)); return; }
        // Zurueck
        if (open != null && mx >= px + SIDE + 10 && mx <= px + SIDE + 26 && my >= py + 9 && my <= py + 25) { open = null; scrollTarget = scroll = 0; return; }

        float cx = px + SIDE + 10, cy = py + HEAD + 8 - scroll, cw = pw - SIDE - 20;
        if (my < py + HEAD || my > py + ph) return;
        if (open != null || (tab == SETTINGS_TAB && search.isEmpty())) {
            List<Setting> list = new ArrayList<Setting>();
            if (open != null) { cy += 16; list.add(open.key); }
            for (Setting s : open != null ? open.getSettings() : ClientSettings.INSTANCE.all()) {
                if (!s.isVisible()) continue;
                if (open instanceof com.vortex.legacy.hud.HudModule && (s.getName().equals("X") || s.getName().equals("Y"))) continue;
                list.add(s);
            }
            for (Setting s : list) {
                if (my >= cy && my <= cy + ROW - 2 && mx >= cx && mx <= cx + cw) { clickSetting(s, mx, cx + cw - 7, button); Config.markDirty(); return; }
                cy += ROW;
            }
            return;
        }
        List<Module> list = shownModules();
        int cols = cw > 300 ? 3 : 2;
        float w = (cw - GAP * (cols - 1)) / cols;
        for (int i = 0; i < list.size(); i++) {
            float x = cx + (i % cols) * (w + GAP), yy = cy + (i / cols) * (CARD_H + GAP);
            if (mx >= x && mx <= x + w && my >= yy && my <= yy + CARD_H) {
                Module m = list.get(i);
                boolean gear = mx >= x + w - 16 && my >= yy + CARD_H - 16;
                if (button == 1 || gear) { open = m; scrollTarget = scroll = 0; return; }
                m.toggle();
                Config.markDirty();
                return;
            }
        }
    }

    private void clickSetting(Setting s, int mx, float rx, int button) {
        if (s instanceof BoolSetting) ((BoolSetting) s).toggle();
        else if (s instanceof ModeSetting) ((ModeSetting) s).cycle(button == 1 ? -1 : 1);
        else if (s instanceof NumberSetting) dragging = s;
        else if (s instanceof KeySetting) listening = (KeySetting) s;
        else if (s instanceof ColorSetting) {
            float sx = rx - 14 * PALETTE.length;
            int i = (int) ((mx - sx) / 14);
            if (i >= 0 && i < PALETTE.length) { ColorSetting c = (ColorSetting) s; c.set((c.get() & 0xFF000000) | PALETTE[i]); }
        }
    }

    @Override
    protected void mouseReleased(int mx, int my, int button) {
        if (dragging != null) Config.markDirty();
        dragging = null;
    }

    @Override
    public void handleMouse() {
        super.handleMouse();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) scrollTarget = Math.max(0, Math.min(maxScroll, scrollTarget - Math.signum(wheel) * 30));
    }

    @Override
    protected void keyPressed(char c, int key) {
        if (listening != null) {
            listening.set(key == Keyboard.KEY_ESCAPE || key == Keyboard.KEY_BACK ? 0 : key);
            listening = null;
            Config.markDirty();
            return;
        }
        if (key == Keyboard.KEY_ESCAPE) {
            if (open != null) { open = null; return; }
            Config.save();
            client.setScreen(null);
            return;
        }
        if (key == Keyboard.KEY_BACK) { if (!search.isEmpty()) search = search.substring(0, search.length() - 1); scrollTarget = 0; return; }
        if (c >= 32 && c < 127 && search.length() < 24) { search += c; open = null; scrollTarget = 0; }
    }

    @Override
    public void removed() {
        Config.save();
    }

    /** Fuer den Test: direkt ein Modul oeffnen. */
    public ClickGui openModule(Module m) { this.open = m; return this; }
    public ClickGui showTab(int t) { this.tab = t; return this; }
    static { VortexLegacy.LOG.debug("ClickGui ready"); }
}
