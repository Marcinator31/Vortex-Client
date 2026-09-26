package com.vortex.client.gui;

import com.vortex.client.core.setting.BooleanSetting;
import com.vortex.client.core.setting.ColorSetting;
import com.vortex.client.core.setting.KeySetting;
import com.vortex.client.core.setting.ModeSetting;
import com.vortex.client.core.setting.NumberSetting;
import com.vortex.client.core.setting.Setting;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Eine Liste beliebiger Einstellungen in einem eigenen Fenster.
 *
 * WARUM ES DAS GIBT: Die Waypoint-Einstellungen -- darunter der Hauptschalter
 * "Show", Tracer, Beschriftungen, Tastenbelegungen -- standen nur im alten
 * Menue. Seit das Spaltenmenue es ersetzt hat, kam man an keine davon mehr
 * heran. Dieses Fenster zeigt jede Setting-Liste an, egal woher sie kommt.
 *
 * Bedienung wie im Spaltenmenue: Schalter anklicken, Regler ziehen, Modus mit
 * Links/Rechts durchschalten, Farbe oeffnet den Farbwaehler, Taste anklicken
 * und die neue Taste druecken (ESC = keine Taste).
 */
public class SettingsScreen extends Screen {

    private static final int ZEILE_H = 18;
    private static final int KOPF_H = 30;

    private final Screen parent;
    private final List<Setting> settings;

    private float oeffnen = 0f;
    private long letzteZeit = 0L;
    private float dt = 0.016f;
    private int mx, my;
    private float lauf = 0f, laufZiel = 0f;
    private int winX, winY, winW, winH;

    private NumberSetting zieht = null;
    private int ziehX, ziehB;
    private KeySetting lauscht = null;

    private final Map<Setting, Float> anim = new HashMap<>();

    private enum Art { BOOL, NUM, MODUS, FARBE, TASTE }

    private record Treffer(int x, int y, int w, int h, Art art, Setting s) {
        boolean in(double px, double py) {
            return px >= x && px < x + w && py >= y && py < y + h;
        }
    }

    private final List<Treffer> treffer = new ArrayList<>();

    public SettingsScreen(Screen parent, String titel, List<Setting> settings) {
        super(Component.literal(titel));
        this.parent = parent;
        this.settings = settings;
    }

    @Override
    protected void init() {
        winW = Math.min(this.width - 20, 300);
        winH = Math.min(this.height - 20, KOPF_H + settings.size() * ZEILE_H + 12);
        winX = (this.width - winW) / 2;
        winY = (this.height - winH) / 2;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        mx = mouseX;
        my = mouseY;
        long jetzt = System.nanoTime();
        dt = letzteZeit == 0L ? 0.016f : Math.min(0.1f, (jetzt - letzteZeit) / 1e9f);
        letzteZeit = jetzt;
        oeffnen += (1f - oeffnen) * (1f - (float) Math.exp(-14f * dt));
        lauf += (laufZiel - lauf) * (1f - (float) Math.exp(-18f * dt));
        float a = oeffnen;

        pruefeTaste();
        treffer.clear();

        ctx.fill(0, 0, this.width, this.height, VortexStyle.fade(VortexStyle.DIM, a));
        VortexStyle.schatten(ctx, winX, winY, winW, winH, a);
        ctx.fill(winX, winY, winX + winW, winY + winH, VortexStyle.fade(VortexStyle.WINDOW, a));
        ctx.fill(winX, winY, winX + winW, winY + KOPF_H, VortexStyle.fade(VortexStyle.BAR, a));
        VortexStyle.akzentLinie(ctx, winX + 4, winY + KOPF_H - 2, winW - 8, a);

        boolean zurueckHov = in(winX + 6, winY + 6, 18, 18);
        ctx.text(this.font, Component.literal("<"), winX + 12, winY + 11,
                VortexStyle.fade(zurueckHov ? VortexStyle.akzent(0.5f) : VortexStyle.TEXT_DIM, a), false);
        ctx.text(this.font, this.title, winX + 28, winY + 11, VortexStyle.fade(VortexStyle.TEXT, a), false);

        int oben = winY + KOPF_H + 4;
        int unten = winY + winH - 4;
        int inhalt = settings.size() * ZEILE_H;
        float maxLauf = Math.max(0, inhalt - (unten - oben));
        if (laufZiel > maxLauf) laufZiel = maxLauf;

        ctx.enableScissor(winX, oben, winX + winW, unten);
        int y = oben - (int) lauf;
        for (Setting s : settings) {
            if (y + ZEILE_H > oben && y < unten) {
                zeile(ctx, s, winX + 12, y, winW - 24, a);
            }
            y += ZEILE_H;
        }
        ctx.disableScissor();

        super.extractRenderState(ctx, mouseX, mouseY, delta);
    }

    private void zeile(GuiGraphicsExtractor ctx, Setting s, int x, int y, int w, float a) {
        int dim = VortexStyle.fade(VortexStyle.TEXT_DIM, a);
        int txt = VortexStyle.fade(VortexStyle.TEXT, a);
        int akz = VortexStyle.fade(VortexStyle.akzent(0.5f), a);
        if (mx >= x - 4 && mx < x + w + 4 && my >= y && my < y + ZEILE_H) {
            ctx.fill(x - 4, y, x + w + 4, y + ZEILE_H, VortexStyle.fade(VortexStyle.HOV, a * 0.7f));
        }
        int ty = y + 5;

        if (s instanceof BooleanSetting b) {
            ctx.text(this.font, Component.literal(kuerzen(s.getName(), w - 30)), x, ty, dim, false);
            float an = weich(anim.getOrDefault(s, b.get() ? 1f : 0f), b.get() ? 1f : 0f, 16f);
            anim.put(s, an);
            int bx = x + w - 18;
            ctx.fill(bx, y + 6, bx + 18, y + 12,
                    VortexStyle.fade(VortexStyle.mix(VortexStyle.TRACK, VortexStyle.akzent(0.5f), an), a));
            int kx = bx + 1 + (int) (an * 10f);
            ctx.fill(kx, y + 5, kx + 7, y + 13,
                    VortexStyle.fade(VortexStyle.mix(0xFFB8B2CC, 0xFFFFFFFF, an), a));
            treffer.add(new Treffer(x, y, w, ZEILE_H, Art.BOOL, s));

        } else if (s instanceof NumberSetting n) {
            String wert = zahl(n);
            int wb = this.font.width(wert);
            ctx.text(this.font, Component.literal(kuerzen(s.getName(), w / 2 - 6)), x, ty, dim, false);
            ctx.text(this.font, Component.literal(wert), x + w - wb, ty, txt, false);
            int sx = x + w / 2, sb = w / 2 - wb - 8;
            if (sb > 10) {
                double ziel = (n.get() - n.getMin()) / Math.max(1e-9, n.getMax() - n.getMin());
                ziel = Math.max(0, Math.min(1, ziel));
                float p = weich(anim.getOrDefault(s, (float) ziel), (float) ziel, zieht == n ? 40f : 14f);
                anim.put(s, p);
                ctx.fill(sx, y + 8, sx + sb, y + 10, VortexStyle.fade(VortexStyle.TRACK, a));
                int fx = sx + (int) (sb * p);
                ctx.fill(sx, y + 8, fx, y + 10, akz);
                ctx.fill(fx - 2, y + 6, fx + 2, y + 12, VortexStyle.fade(0xFFFFFFFF, a));
                treffer.add(new Treffer(sx, y, sb, ZEILE_H, Art.NUM, s));
            }

        } else if (s instanceof ModeSetting m) {
            String wert = kuerzen(m.get(), w / 2);
            int wb = this.font.width(wert);
            ctx.text(this.font, Component.literal(kuerzen(s.getName(), w - wb - 10)), x, ty, dim, false);
            ctx.text(this.font, Component.literal(wert), x + w - wb, ty, akz, false);
            treffer.add(new Treffer(x, y, w, ZEILE_H, Art.MODUS, s));

        } else if (s instanceof ColorSetting c) {
            ctx.text(this.font, Component.literal(kuerzen(s.getName(), w - 24)), x, ty, dim, false);
            int bx = x + w - 12;
            ctx.fill(bx, y + 4, bx + 12, y + 14, VortexStyle.fade(0xFF000000, a));
            ctx.fill(bx + 1, y + 5, bx + 11, y + 13, VortexStyle.fade(c.get() | 0xFF000000, a));
            treffer.add(new Treffer(x, y, w, ZEILE_H, Art.FARBE, s));

        } else if (s instanceof KeySetting k) {
            boolean hoert = lauscht == k;
            String wert = hoert ? "..." : (k.isBound() ? kuerzen(k.getKeyName(), w / 2 - 8) : "None");
            int kb = this.font.width(wert) + 8;
            int kx = x + w - kb;
            ctx.fill(kx, y + 2, kx + kb, y + ZEILE_H - 2,
                    VortexStyle.fade(hoert ? VortexStyle.mix(VortexStyle.CARD, VortexStyle.akzent(0.5f), 0.45f)
                                           : VortexStyle.CARD, a));
            ctx.text(this.font, Component.literal(wert), kx + 4, ty, k.isBound() || hoert ? txt : dim, false);
            ctx.text(this.font, Component.literal(kuerzen(s.getName(), w - kb - 8)), x, ty, dim, false);
            treffer.add(new Treffer(x, y, w, ZEILE_H, Art.TASTE, s));
        }
    }

    // ------------------------------------------------------------------

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent click, boolean doubled) {
        if (super.mouseClicked(click, doubled)) return true;
        if (in(winX + 6, winY + 6, 18, 18)) {
            onClose();
            return true;
        }
        int knopf = click.button();
        for (Treffer t : treffer) {
            if (!t.in(mx, my)) continue;
            switch (t.art()) {
                case BOOL -> ((BooleanSetting) t.s()).toggle();
                case NUM -> {
                    zieht = (NumberSetting) t.s();
                    ziehX = t.x();
                    ziehB = t.w();
                    schieber(mx);
                }
                case MODUS -> {
                    ModeSetting m = (ModeSetting) t.s();
                    if (knopf == 1) {
                        int start = m.getIndex(), n = 0;
                        do { m.cycle(); n++; } while (m.getIndex() != start && n < 64);
                        for (int i = 0; i < n - 1; i++) m.cycle();
                    } else {
                        m.cycle();
                    }
                }
                case FARBE -> Minecraft.getInstance().gui.setScreen(
                        new ColorPickerScreen(this, (ColorSetting) t.s()));
                case TASTE -> lauscht = (KeySetting) t.s();
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent click, double dx, double dy) {
        if (zieht != null) {
            schieber(mx);
            return true;
        }
        return super.mouseDragged(click, dx, dy);
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent click) {
        zieht = null;
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        laufZiel = Math.max(0f, laufZiel - (float) vertical * 24f);
        return true;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return lauscht == null;
    }

    @Override
    public void onClose() {
        com.vortex.client.core.ConfigManager.save();
        Minecraft.getInstance().gui.setScreen(parent);
    }

    /** Naechste gedrueckte Taste uebernehmen -- wie im Spaltenmenue. */
    private void pruefeTaste() {
        if (lauscht == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (com.mojang.blaze3d.platform.InputConstants.isKeyDown(
                mc.getWindow(), org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE)) {
            lauscht.setKeyCode(org.lwjgl.glfw.GLFW.GLFW_KEY_UNKNOWN);
            lauscht = null;
            return;
        }
        for (int code = org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE;
             code <= org.lwjgl.glfw.GLFW.GLFW_KEY_LAST; code++) {
            if (com.mojang.blaze3d.platform.InputConstants.isKeyDown(mc.getWindow(), code)) {
                lauscht.setKeyCode(code);
                lauscht = null;
                return;
            }
        }
    }

    private void schieber(int mausX) {
        if (zieht == null || ziehB <= 0) return;
        float p = Math.max(0f, Math.min(1f, (mausX - ziehX) / (float) ziehB));
        double roh = zieht.getMin() + p * (zieht.getMax() - zieht.getMin());
        double schritt = zieht.getStep();
        if (schritt > 0) roh = Math.round(roh / schritt) * schritt;
        roh = Math.round(roh * 1000.0) / 1000.0;
        zieht.set(Math.max(zieht.getMin(), Math.min(zieht.getMax(), roh)));
    }

    private boolean in(int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private float weich(float ist, float ziel, float tempo) {
        float f = 1f - (float) Math.exp(-tempo * dt);
        float neu = ist + (ziel - ist) * f;
        return Math.abs(ziel - neu) < 0.005f ? ziel : neu;
    }

    private String zahl(NumberSetting n) {
        double v = n.get();
        if (n.getStep() >= 1 && v == Math.rint(v)) return String.valueOf((long) v);
        return String.format(Locale.ROOT, "%.2f", v);
    }

    private String kuerzen(String s, int max) {
        if (max <= 8) return "";
        if (this.font.width(s) <= max) return s;
        while (s.length() > 1 && this.font.width(s + "..") > max) s = s.substring(0, s.length() - 1);
        return s + "..";
    }
}
