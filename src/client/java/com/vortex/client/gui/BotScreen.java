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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Eigene Seite fuer die Bots.
 *
 * Bots sind keine Module, die man nebenbei an- und ausknipst: man stellt sie
 * einmal ein und laesst sie dann laufen. In der schmalen Modulspalte waren
 * die Einstellungsnamen abgeschnitten ("Golden Apple Bel..") und man sah
 * nicht, was der Bot gerade tut.
 *
 * Hier bekommt jeder Bot eine grosse Karte:
 *   - Name gross, daneben ein deutlicher Start/Stop-Knopf
 *   - die Beschreibung (ModuleInfo, dieselben Texte wie im Tooltip)
 *   - eine Statuszeile, wenn der Bot eine liefert (siehe status())
 *   - ALLE Einstellungen, immer offen, mit vollem Namen
 *
 * Die Einstellungen werden ueber dieselben Aufrufe geaendert wie in PanelGui.
 */
public class BotScreen extends Screen {

    private static final int KARTE_MAX = 300;
    private static final int KARTE_MIN = 210;
    private static final int ABSTAND = 12;
    private static final int OBEN = 40;
    private static final int KOPF_H = 36;
    private static final int ZEILE_H = 18;
    private static final int ZAHL_H = 24;
    private static final int AUSWAHL_H = 18;

    private final Screen parent;

    private int mx, my;
    private long letzteZeit = 0;
    private float oeffnen = 0f;
    private float dtJetzt = 0.016f;
    private float lauf = 0f, laufZiel = 0f;

    private final Map<Setting, Float> einstAnim = new HashMap<>();
    private final Map<Module, Float> anAnim = new HashMap<>();

    private NumberSetting zieht = null;
    private int ziehX, ziehB;

    private Module resetFrage = null;
    private long resetFrageZeit = 0;

    private enum Art { AN, BOOL, NUM, MODUS, FARBE, TASTE, AUSWAHL, RESET }

    private static final class Treffer {
        final int x, y, w, h;
        final Art art;
        final Module modul;
        final Setting einst;
        Treffer(int x, int y, int w, int h, Art art, Module m, Setting s) {
            this.x = x; this.y = y; this.w = w; this.h = h;
            this.art = art; this.modul = m; this.einst = s;
        }
        boolean in(double px, double py) {
            return px >= x && px < x + w && py >= y && py < y + h;
        }
    }
    private final List<Treffer> treffer = new ArrayList<>();

    public BotScreen(Screen parent) {
        super(Component.literal("Bots"));
        this.parent = parent;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(parent);
    }

    @Override
    public void removed() {
        com.vortex.client.core.ConfigManager.save();
        super.removed();
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
        dtJetzt = dt;
        oeffnen = weich(oeffnen, 1f, 10f, dt);
        float a = oeffnen;
        treffer.clear();
        tasteAufnehmen();

        ctx.fill(0, 0, this.width, this.height, VortexStyle.fade(0xAA000000, a));

        // Kopfzeile
        String titel = "Bots";
        var p = ctx.pose();
        p.pushMatrix();
        p.translate(this.width / 2f - this.font.width(titel), 10f);
        p.scale(2f, 2f);
        ctx.text(this.font, Component.literal(titel), 0, 0, VortexStyle.fade(VortexStyle.TEXT, a), false);
        p.popMatrix();
        VortexStyle.akzentLinie(ctx, this.width / 2 - 30, 30, 60, a);

        List<Module> bots = ModuleManager.INSTANCE.getByCategory(Module.Category.BOTS);
        if (bots.isEmpty()) {
            String t = "No bots installed -- they come with the Vortex Plus Addon.";
            ctx.text(this.font, Component.literal(t), this.width / 2 - this.font.width(t) / 2,
                    this.height / 2, VortexStyle.fade(VortexStyle.TEXT_DIM, a), false);
            super.extractRenderState(ctx, mouseX, mouseY, delta);
            return;
        }

        // --- Raster: so viele Karten nebeneinander, wie in Mindestbreite passen
        int verfuegbar = this.width - 24;
        int spalten = Math.max(1, Math.min(bots.size(), (verfuegbar + ABSTAND) / (KARTE_MIN + ABSTAND)));
        int kb = Math.min(KARTE_MAX, (verfuegbar - (spalten - 1) * ABSTAND) / spalten);
        int reiheB = spalten * kb + (spalten - 1) * ABSTAND;
        int x0 = (this.width - reiheB) / 2;

        // Hoehen vorab, damit Reihen buendig liegen und der Bildlauf stimmt
        int[] hoehe = new int[bots.size()];
        for (int i = 0; i < bots.size(); i++) hoehe[i] = kartenHoehe(bots.get(i), kb);
        int gesamt = 0;
        for (int r = 0; r * spalten < bots.size(); r++) {
            int hMax = 0;
            for (int c = 0; c < spalten && r * spalten + c < bots.size(); c++) {
                hMax = Math.max(hMax, hoehe[r * spalten + c]);
            }
            gesamt += hMax + ABSTAND;
        }
        int sichtbar = this.height - OBEN - 8;
        float maxLauf = Math.max(0, gesamt - sichtbar);
        if (laufZiel > maxLauf) laufZiel = maxLauf;
        lauf = weich(lauf, laufZiel, 16f, dt);

        ctx.enableScissor(0, OBEN, this.width, this.height - 4);
        int y = OBEN + 4 - (int) lauf;
        int reiheH = 0;
        for (int i = 0; i < bots.size(); i++) {
            int c = i % spalten;
            if (c == 0 && i > 0) { y += reiheH + ABSTAND; reiheH = 0; }
            int x = x0 + c * (kb + ABSTAND);
            zeichneKarte(ctx, bots.get(i), x, y, kb, hoehe[i], a);
            reiheH = Math.max(reiheH, hoehe[i]);
        }
        ctx.disableScissor();

        if (maxLauf > 0) {
            int spur = sichtbar - 8;
            int balken = Math.max(20, (int) (spur * (sichtbar / (float) gesamt)));
            int by = OBEN + 4 + (int) ((spur - balken) * (lauf / maxLauf));
            ctx.fill(this.width - 5, by, this.width - 3, by + balken,
                    VortexStyle.fade(VortexStyle.akzent(0.5f), a * 0.7f));
        }

        super.extractRenderState(ctx, mouseX, mouseY, delta);
    }

    private List<String> beschreibung(Module m, int breite) {
        List<String> zeilen = new ArrayList<>();
        String text = ModuleInfo.get(m.getName());
        if (text == null || text.isBlank()) return zeilen;
        StringBuilder z = new StringBuilder();
        for (String wort : text.split(" ")) {
            String probe = z.length() == 0 ? wort : z + " " + wort;
            if (this.font.width(probe) > breite && z.length() > 0) {
                zeilen.add(z.toString());
                z = new StringBuilder(wort);
            } else {
                z = new StringBuilder(probe);
            }
        }
        if (z.length() > 0) zeilen.add(z.toString());
        return zeilen;
    }

    private int kartenHoehe(Module m, int kb) {
        int h = KOPF_H;
        h += beschreibung(m, kb - 20).size() * 10 + 4;
        if (status(m) != null) h += 14;
        h += 8;
        if (m instanceof com.vortex.client.module.HasOwnScreen) h += AUSWAHL_H + 4;
        for (Setting s : m.getSettings()) {
            if (s == m.getEnabledSetting()) continue;
            h += (s instanceof NumberSetting) ? ZAHL_H : ZEILE_H;
        }
        h += ZEILE_H + 6;   // Reset
        return h;
    }

    private void zeichneKarte(GuiGraphicsExtractor ctx, Module m, int x, int y, int w, int h, float a) {
        if (y > this.height || y + h < OBEN) return;
        boolean an = m.isEnabled();
        float av = weich(anAnim.getOrDefault(m, an ? 1f : 0f), an ? 1f : 0f, 12f, dtJetzt);
        anAnim.put(m, av);

        VortexStyle.schatten(ctx, x, y, w, h, a);
        roundRect(ctx, x, y, w, h, VortexStyle.fade(VortexStyle.WINDOW, a * 0.97f));
        // Kopf: laeuft der Bot, leuchtet er im Akzentverlauf
        roundRect(ctx, x, y, w, KOPF_H, VortexStyle.fade(VortexStyle.BAR, a));
        verlaufBand(ctx, x + 2, y + 1, w - 4, KOPF_H - 2,
                VortexStyle.fade(VortexStyle.akzent(0f), a * (0.10f + 0.35f * av)),
                VortexStyle.fade(VortexStyle.akzent(1f), a * 0.04f));
        VortexStyle.akzentLinie(ctx, x + 3, y + KOPF_H - 2, w - 6, a * (0.4f + 0.6f * av));

        // Name, anderthalbfach
        var p = ctx.pose();
        p.pushMatrix();
        p.translate(x + 10f, y + 10f);
        p.scale(1.5f, 1.5f);
        String name = kuerzen(m.getName(), (int) ((w - 90) / 1.5f));
        ctx.text(this.font, Component.literal(name), 0, 0, VortexStyle.fade(0xFFFFFFFF, a), false);
        p.popMatrix();

        // Start/Stop-Knopf
        String knopf = an ? "STOP" : "START";
        int kbB = 56, kbH = 20;
        int kx = x + w - kbB - 8, ky = y + (KOPF_H - kbH) / 2;
        boolean kHov = mx >= kx && mx < kx + kbB && my >= ky && my < ky + kbH;
        int grund = an ? VortexStyle.mix(0xFF3A1420, 0xFFEF4444, kHov ? 0.55f : 0.35f)
                       : VortexStyle.mix(VortexStyle.CARD, VortexStyle.akzent(0.5f), kHov ? 0.75f : 0.5f);
        roundRect(ctx, kx, ky, kbB, kbH, VortexStyle.fade(grund, a));
        ctx.text(this.font, Component.literal(knopf), kx + kbB / 2 - this.font.width(knopf) / 2, ky + 6,
                VortexStyle.fade(0xFFFFFFFF, a), false);
        treffer.add(new Treffer(kx, ky, kbB, kbH, Art.AN, m, null));

        int cy = y + KOPF_H + 6;
        for (String l : beschreibung(m, w - 20)) {
            ctx.text(this.font, Component.literal(l), x + 10, cy, VortexStyle.fade(VortexStyle.TEXT_DIM, a), false);
            cy += 10;
        }
        cy += 4;
        String st = status(m);
        if (st != null) {
            // Punkt: gruen, wenn er laeuft
            ctx.fill(x + 10, cy + 2, x + 15, cy + 7, VortexStyle.fade(an ? 0xFF22C55E : 0xFF6B6480, a));
            ctx.text(this.font, Component.literal(kuerzen(st, w - 30)), x + 19, cy,
                    VortexStyle.fade(an ? VortexStyle.TEXT : VortexStyle.TEXT_DIM, a), false);
            cy += 14;
        }
        ctx.fill(x + 8, cy + 2, x + w - 8, cy + 3, VortexStyle.fade(VortexStyle.LINE, a));
        cy += 8;

        int ix = x + 10, iw = w - 20;
        if (m instanceof com.vortex.client.module.HasOwnScreen hos) {
            boolean hov = mx >= ix && mx < ix + iw && my >= cy && my < cy + AUSWAHL_H;
            roundRect(ctx, ix, cy, iw, AUSWAHL_H, VortexStyle.fade(hov
                    ? VortexStyle.mix(VortexStyle.CARD, VortexStyle.akzent(0.5f), 0.35f) : VortexStyle.CARD, a));
            ctx.text(this.font, Component.literal(kuerzen(hos.screenButtonLabel(), iw - 20)), ix + 6, cy + 5,
                    VortexStyle.fade(VortexStyle.TEXT, a), false);
            ctx.text(this.font, Component.literal(">"), ix + iw - 9, cy + 5,
                    VortexStyle.fade(VortexStyle.akzent(0.5f), a), false);
            treffer.add(new Treffer(ix, cy, iw, AUSWAHL_H, Art.AUSWAHL, m, null));
            cy += AUSWAHL_H + 4;
        }

        for (Setting s : m.getSettings()) {
            if (s == m.getEnabledSetting()) continue;
            cy = zeichneEinstellung(ctx, m, s, ix, cy, iw, a);
        }
        zeichneReset(ctx, m, ix, cy + 2, iw, a);
    }

    private int zeichneEinstellung(GuiGraphicsExtractor ctx, Module m, Setting s,
                                   int x, int y, int w, float a) {
        int dim = VortexStyle.fade(VortexStyle.mix(VortexStyle.TEXT_DIM, VortexStyle.TEXT, 0.45f), a);
        int txt = VortexStyle.fade(VortexStyle.TEXT, a);
        int akz = VortexStyle.fade(VortexStyle.akzent(0.5f), a);
        int zh = (s instanceof NumberSetting) ? ZAHL_H : ZEILE_H;
        if (mx >= x && mx < x + w && my >= y && my < y + zh) {
            ctx.fill(x - 3, y, x + w + 3, y + zh, VortexStyle.fade(VortexStyle.HOV, a * 0.7f));
        }

        if (s instanceof BooleanSetting b) {
            ctx.text(this.font, Component.literal(kuerzen(s.getName(), w - 30)), x + 2, y + 5, dim, false);
            float an = weich(einstAnim.getOrDefault(s, b.get() ? 1f : 0f), b.get() ? 1f : 0f, 16f, dtJetzt);
            einstAnim.put(s, an);
            int bx = x + w - 22;
            ctx.fill(bx, y + 5, bx + 22, y + 13,
                    VortexStyle.fade(VortexStyle.mix(VortexStyle.TRACK, VortexStyle.akzent(0.5f), an), a));
            int kx = bx + 1 + (int) (an * 12f);
            ctx.fill(kx, y + 4, kx + 8, y + 14, VortexStyle.fade(VortexStyle.mix(0xFFB8B2CC, 0xFFFFFFFF, an), a));
            treffer.add(new Treffer(x, y, w, zh, Art.BOOL, m, s));

        } else if (s instanceof NumberSetting n) {
            String wert = zahl(n);
            int wertB = this.font.width(wert);
            ctx.text(this.font, Component.literal(kuerzen(s.getName(), w - wertB - 10)), x + 2, y + 3, dim, false);
            ctx.text(this.font, Component.literal(wert), x + w - wertB, y + 3, txt, false);
            int ty = y + 15;
            ctx.fill(x + 2, ty, x + w, ty + 3, VortexStyle.fade(VortexStyle.TRACK, a));
            double ziel = (n.get() - n.getMin()) / Math.max(1e-9, n.getMax() - n.getMin());
            ziel = Math.max(0, Math.min(1, ziel));
            float pz = weich(einstAnim.getOrDefault(s, (float) ziel), (float) ziel,
                    zieht == n ? 40f : 14f, dtJetzt);
            einstAnim.put(s, pz);
            int fx = x + 2 + (int) ((w - 2) * pz);
            verlaufBand(ctx, x + 2, ty, fx - x - 2, 3,
                    VortexStyle.fade(VortexStyle.akzent(0f), a), VortexStyle.fade(VortexStyle.akzent(1f), a));
            ctx.fill(fx - 2, ty - 3, fx + 3, ty + 6, VortexStyle.fade(0xFFFFFFFF, a));
            treffer.add(new Treffer(x + 2, y, w - 2, zh, Art.NUM, m, s));

        } else if (s instanceof ModeSetting mode) {
            String wert = "< " + mode.get() + " >";
            wert = kuerzen(wert, Math.max(40, w / 2));
            int wertB = this.font.width(wert);
            ctx.text(this.font, Component.literal(kuerzen(s.getName(), w - wertB - 10)), x + 2, y + 5, dim, false);
            ctx.text(this.font, Component.literal(wert), x + w - wertB, y + 5, akz, false);
            treffer.add(new Treffer(x, y, w, zh, Art.MODUS, m, s));

        } else if (s instanceof ColorSetting c) {
            ctx.text(this.font, Component.literal(kuerzen(s.getName(), w - 20)), x + 2, y + 5, dim, false);
            int bx = x + w - 14;
            ctx.fill(bx, y + 3, bx + 12, y + 15, VortexStyle.fade(0xFF000000, a));
            ctx.fill(bx + 1, y + 4, bx + 11, y + 14, VortexStyle.fade(c.get() | 0xFF000000, a));
            treffer.add(new Treffer(x, y, w, zh, Art.FARBE, m, s));

        } else if (s instanceof KeySetting k) {
            boolean lauscht = k.isListening();
            String wert = lauscht ? "press a key..." : (k.isBound() ? k.getKeyName() : "None");
            wert = kuerzen(wert, Math.max(30, w / 2));
            int kappeB = this.font.width(wert) + 10;
            int kx = x + w - kappeB;
            ctx.fill(kx, y + 2, kx + kappeB, y + zh - 2, VortexStyle.fade(lauscht
                    ? VortexStyle.mix(VortexStyle.CARD, VortexStyle.akzent(0.5f), 0.45f) : VortexStyle.CARD, a));
            ctx.fill(kx, y + zh - 3, kx + kappeB, y + zh - 2,
                    VortexStyle.fade(lauscht ? VortexStyle.akzent(0.5f) : VortexStyle.LINE, a));
            ctx.text(this.font, Component.literal(wert), kx + 5, y + 5, k.isBound() || lauscht ? txt : dim, false);
            ctx.text(this.font, Component.literal(kuerzen(s.getName(), w - kappeB - 10)), x + 2, y + 5, dim, false);
            treffer.add(new Treffer(x, y, w, zh, Art.TASTE, m, s));
        }
        return y + zh;
    }

    private void zeichneReset(GuiGraphicsExtractor ctx, Module m, int x, int y, int w, float a) {
        boolean frage = resetFrage == m && System.currentTimeMillis() - resetFrageZeit < 3000;
        String text = frage ? "Click again to reset" : "Reset to default";
        int tw = this.font.width(text);
        int bx = x + w - tw - 8;
        boolean hov = mx >= bx && mx < x + w && my >= y && my < y + ZEILE_H;
        if (hov || frage) {
            ctx.fill(bx, y + 1, x + w, y + ZEILE_H - 1, VortexStyle.fade(
                    frage ? VortexStyle.mix(VortexStyle.CARD, 0xFFEF4444, 0.35f) : VortexStyle.HOV, a));
        }
        ctx.text(this.font, Component.literal(text), bx + 4, y + 5,
                VortexStyle.fade(frage ? 0xFFFF8A8A : (hov ? VortexStyle.TEXT : VortexStyle.TEXT_DIM), a), false);
        treffer.add(new Treffer(bx, y, x + w - bx, ZEILE_H, Art.RESET, m, null));
    }

    // ======================================================================
    // Status des Bots
    // ======================================================================

    private static final Map<Class<?>, java.lang.reflect.Method> STATUS = new HashMap<>();

    /**
     * Statuszeile des Bots, oder null.
     *
     * Ein Bot liefert sie ueber eine oeffentliche Methode "getStatus()" --
     * per Reflexion abgefragt, damit das Addon auch gegen einen aelteren
     * Client ohne gemeinsame Schnittstelle baut.
     */
    private static String status(Module m) {
        try {
            java.lang.reflect.Method meth;
            if (STATUS.containsKey(m.getClass())) {
                meth = STATUS.get(m.getClass());
            } else {
                try {
                    meth = m.getClass().getMethod("getStatus");
                    if (meth.getReturnType() != String.class) meth = null;
                } catch (NoSuchMethodException e) {
                    meth = null;
                }
                STATUS.put(m.getClass(), meth);
            }
            if (meth == null) return null;
            Object o = meth.invoke(m);
            return o == null ? null : o.toString();
        } catch (Throwable t) {
            return null;
        }
    }

    // ======================================================================
    // Bedienung
    // ======================================================================

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent click, boolean doubled) {
        if (super.mouseClicked(click, doubled)) return true;
        if (my < OBEN) return false;
        int knopf = click.button();
        for (int i = treffer.size() - 1; i >= 0; i--) {
            Treffer t = treffer.get(i);
            if (!t.in(mx, my)) continue;
            switch (t.art) {
                case AN:
                    t.modul.toggle();
                    return true;
                case BOOL: {
                    BooleanSetting b = (BooleanSetting) t.einst;
                    if (b == t.modul.getEnabledSetting()) t.modul.toggle();
                    else b.toggle();
                    return true;
                }
                case NUM:
                    zieht = (NumberSetting) t.einst;
                    ziehX = t.x;
                    ziehB = t.w;
                    schieber(zieht, mx);
                    return true;
                case MODUS:
                    if (knopf == 1) zurueck((ModeSetting) t.einst);
                    else ((ModeSetting) t.einst).cycle();
                    return true;
                case FARBE:
                    Minecraft.getInstance().gui.setScreen(new ColorPickerScreen(this, (ColorSetting) t.einst));
                    return true;
                case AUSWAHL:
                    if (t.modul instanceof com.vortex.client.module.HasOwnScreen hos) {
                        Minecraft.getInstance().gui.setScreen(hos.createScreen(this));
                    }
                    return true;
                case TASTE:
                    ((KeySetting) t.einst).setListening(true);
                    return true;
                case RESET: {
                    long jetztMs = System.currentTimeMillis();
                    if (resetFrage == t.modul && jetztMs - resetFrageZeit < 3000) {
                        for (Setting s : t.modul.getSettings()) {
                            if (s == t.modul.getEnabledSetting() || s == t.modul.getToggleKey()) continue;
                            s.resetToDefault();
                            einstAnim.remove(s);
                        }
                        com.vortex.client.core.ConfigManager.save();
                        resetFrage = null;
                    } else {
                        resetFrage = t.modul;
                        resetFrageZeit = jetztMs;
                    }
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent click, double dx, double dy) {
        if (zieht != null) {
            schieber(zieht, mx);
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
        laufZiel = Math.max(0f, laufZiel - (float) vertical * 28f);
        return true;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return lauschend() == null;
    }

    private void schieber(NumberSetting n, int mausX) {
        if (ziehB <= 0) return;
        float q = (mausX - ziehX) / (float) ziehB;
        if (q < 0f) q = 0f;
        if (q > 1f) q = 1f;
        double roh = n.getMin() + q * (n.getMax() - n.getMin());
        double schritt = n.getStep();
        if (schritt > 0) roh = Math.round(roh / schritt) * schritt;
        roh = Math.round(roh * 1000.0) / 1000.0;
        if (roh < n.getMin()) roh = n.getMin();
        if (roh > n.getMax()) roh = n.getMax();
        n.set(roh);
    }

    private void zurueck(ModeSetting mode) {
        int start = mode.getIndex();
        int anzahl = 0;
        do { mode.cycle(); anzahl++; } while (mode.getIndex() != start && anzahl < 64);
        if (anzahl <= 1) return;
        for (int i = 0; i < anzahl - 1; i++) mode.cycle();
    }

    private KeySetting lauschend() {
        for (Module m : ModuleManager.INSTANCE.getModules()) {
            for (Setting s : m.getSettings()) {
                if (s instanceof KeySetting k && k.isListening()) return k;
            }
        }
        return null;
    }

    private void tasteAufnehmen() {
        KeySetting k = lauschend();
        if (k == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (com.mojang.blaze3d.platform.InputConstants.isKeyDown(
                mc.getWindow(), org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE)) {
            k.setKeyCode(org.lwjgl.glfw.GLFW.GLFW_KEY_UNKNOWN);
            k.setListening(false);
            com.vortex.client.core.ConfigManager.save();
            return;
        }
        for (int code = org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE;
             code <= org.lwjgl.glfw.GLFW.GLFW_KEY_LAST; code++) {
            if (com.mojang.blaze3d.platform.InputConstants.isKeyDown(mc.getWindow(), code)) {
                k.setKeyCode(code);
                k.setListening(false);
                com.vortex.client.core.ConfigManager.save();
                return;
            }
        }
    }

    // ======================================================================
    // Hilfen
    // ======================================================================

    private String zahl(NumberSetting n) {
        double v = n.get();
        if (n.getStep() >= 1 && v == Math.rint(v)) return String.valueOf((long) v);
        return String.format(Locale.ROOT, "%.2f", v);
    }

    private String kuerzen(String s, int max) {
        if (this.font.width(s) <= max) return s;
        while (s.length() > 1 && this.font.width(s + "..") > max) s = s.substring(0, s.length() - 1);
        return s + "..";
    }

    private static float weich(float ist, float ziel, float tempo, float dt) {
        float f = 1f - (float) Math.exp(-tempo * dt);
        float neu = ist + (ziel - ist) * f;
        return Math.abs(ziel - neu) < 0.005f ? ziel : neu;
    }

    private void roundRect(GuiGraphicsExtractor ctx, int x, int y, int w, int h, int c) {
        if (w <= 0 || h <= 0) return;
        int r = Math.min(2, Math.min(w / 2, h / 2));
        ctx.fill(x, y + r, x + w, y + h - r, c);
        ctx.fill(x + r, y, x + w - r, y + r, c);
        ctx.fill(x + r, y + h - r, x + w - r, y + h, c);
        if (r >= 2) {
            ctx.fill(x + 1, y + 1, x + r, y + r, c);
            ctx.fill(x + w - r, y + 1, x + w - 1, y + r, c);
            ctx.fill(x + 1, y + h - r, x + r, y + h - 1, c);
            ctx.fill(x + w - r, y + h - r, x + w - 1, y + h - 1, c);
        }
    }

    private static void verlaufBand(GuiGraphicsExtractor ctx, int x, int y, int w, int h, int von, int bis) {
        if (w <= 0 || h <= 0) return;
        int baender = Math.max(1, (w + 5) / 6);
        for (int b = 0; b < baender; b++) {
            int ax = x + w * b / baender, bx = x + w * (b + 1) / baender;
            if (bx <= ax) continue;
            ctx.fill(ax, y, bx, y + h, VortexStyle.mix(von, bis, (b + 0.5f) / baender));
        }
    }
}
