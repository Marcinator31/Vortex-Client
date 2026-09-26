package com.vortex.client.hud;

import com.vortex.client.module.modules.CrosshairModule;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Zeichnet das Fadenkreuz (Modul Crosshair).
 *
 * Alles aus gefuellten Rechtecken, keine Textur -- jede Groesse und Farbe
 * geht ohne Bilddatei. ZWEI DURCHGAENGE: erst alle Umrandungen, dann alle
 * Flaechen. Frueher bekam jedes Stueck seine eigene Umrandung direkt vor sich
 * -- beim Kreis ueberdeckte dann der Rand des naechsten Punkts den vorigen,
 * und der Kreis sah ausgefranst aus.
 */
public final class CrosshairRenderer {

    private CrosshairRenderer() {}

    /** Gesammelte Rechtecke eines Bildes: x, y, b, h. */
    private static final List<int[]> TEILE = new ArrayList<>();

    private static float gapGlatt = 0f;
    private static long letzteZeit = 0;
    private static float letzteStaerke = 1f;
    private static long vollSeit = 0;

    public static void draw(GuiGraphicsExtractor ctx, Minecraft mc, CrosshairModule m) {
        LocalPlayer p = mc.player;
        if (p == null) return;
        int cx = mc.getWindow().getGuiScaledWidth() / 2;
        int cy = mc.getWindow().getGuiScaledHeight() / 2;
        long jetzt = System.currentTimeMillis();
        float dt = letzteZeit == 0 ? 0.016f : Math.min(0.1f, (jetzt - letzteZeit) / 1000f);
        letzteZeit = jetzt;

        // --- Farbe ---------------------------------------------------------
        int farbe = m.color.get();
        if (m.rainbow.get()) {
            float ton = (jetzt % 4000L) / 4000f;
            farbe = (farbe & 0xFF000000) | (hsv(ton) & 0x00FFFFFF);
        }
        int ziel = m.targetMode.getIndex();
        if (ziel > 0 && mc.hitResult instanceof EntityHitResult ehr) {
            if (ziel == 1 || ehr.getEntity() instanceof Player) farbe = m.targetColor.get();
        }

        // --- Angriffsstaerke -------------------------------------------------
        float staerke = p.getAttackStrengthScale(0f);
        if (staerke >= 1f && letzteStaerke < 1f) vollSeit = jetzt;
        letzteStaerke = staerke;

        // --- Dynamischer Abstand -------------------------------------------
        float extra = 0f;
        if (m.dynamicGap.get()) {
            Vec3 v = p.getDeltaMovement();
            double tempo = Math.sqrt(v.x * v.x + v.z * v.z) * 20.0;   // Bloecke pro Sekunde
            extra += (float) Math.min(5.0, tempo * 0.7);
            if (!p.onGround()) extra += 3f;
            extra += (1f - staerke) * 4f;
        }
        gapGlatt += (extra - gapGlatt) * (1f - (float) Math.exp(-14f * dt));
        int gap = m.gap.getInt() + Math.round(gapGlatt);
        int len = m.size.getInt();
        int th = m.thickness.getInt();
        int dot = m.dotSize.getInt();

        // --- Form ------------------------------------------------------------
        TEILE.clear();
        switch (m.style.getIndex()) {
            case 0: kreuz(cx, cy, gap, len, th, true); break;
            case 1: kreuz(cx, cy, gap, len, th, true); punkt(cx, cy, dot); break;
            case 2: punkt(cx, cy, Math.max(dot, th)); break;
            case 3: kreis(cx, cy, gap + len / 2 + 1, th); break;
            case 4: kreis(cx, cy, gap + len / 2 + 1, th); punkt(cx, cy, dot); break;
            case 5: kreuz(cx, cy, gap, len, th, false); break;
            case 6: diagonal(cx, cy, gap, len, th); break;
            case 7: quadrat(cx, cy, gap + len / 2 + 1, th); break;
            case 8: quadrat(cx, cy, gap + len / 2 + 1, th); punkt(cx, cy, dot); break;
            default: kreuz(cx, cy, gap, len, th, true);
        }
        zeichne(ctx, farbe, m.outline.get(), m.outlineColor.get());

        // --- Hitmarker -------------------------------------------------------
        if (m.hitMarker.get()) {
            long alter = CombatTracker.trefferAlter();
            if (alter < 350) {
                float a = 1f - alter / 350f;
                int hm = m.hitMarkerColor.get();
                int al = (int) (((hm >>> 24) & 0xFF) * a);
                TEILE.clear();
                int innen = gap + 3, aussen = gap + 3 + Math.max(3, len / 2 + 1);
                for (int i = innen; i <= aussen; i++) {
                    TEILE.add(new int[]{cx + i, cy + i, 1, 1});
                    TEILE.add(new int[]{cx - i - 1, cy + i, 1, 1});
                    TEILE.add(new int[]{cx + i, cy - i - 1, 1, 1});
                    TEILE.add(new int[]{cx - i - 1, cy - i - 1, 1, 1});
                }
                zeichne(ctx, (al << 24) | (hm & 0x00FFFFFF), m.outline.get(),
                        ((int) (((m.outlineColor.get() >>> 24) & 0xFF) * a) << 24)
                                | (m.outlineColor.get() & 0x00FFFFFF));
            }
        }

        // --- Angriffsanzeige -------------------------------------------------
        int modus = m.indicator.getIndex();
        if (modus > 0) {
            boolean laedt = staerke < 1f;
            long seitVoll = jetzt - vollSeit;
            boolean blitz = m.readyFlash.get() && !laedt && seitVoll < 200;
            if (laedt || blitz || m.indicatorAlways.get()) {
                int ausdehnung = aussenmass(m.style.getIndex(), gap, len, th, dot);
                anzeige(ctx, mc, m, modus, cx, cy, ausdehnung, staerke, blitz ? 1f - seitVoll / 200f : 0f);
            }
        }
    }

    /** Wie weit das Fadenkreuz vom Mittelpunkt reicht -- darunter kommt die Anzeige. */
    private static int aussenmass(int stil, int gap, int len, int th, int dot) {
        switch (stil) {
            case 2: return Math.max(dot, th) + 1;
            case 3: case 4: case 7: case 8: return gap + len / 2 + 1 + th;
            default: return gap + len;
        }
    }

    // ------------------------------------------------------------------
    // Formen
    // ------------------------------------------------------------------

    private static void rechteck(int x, int y, int b, int h) {
        if (b > 0 && h > 0) TEILE.add(new int[]{x, y, b, h});
    }

    /** Kreuz; oben = false gibt die T-Form (ohne Arm nach oben). */
    private static void kreuz(int cx, int cy, int gap, int len, int th, boolean oben) {
        int h = th / 2;
        if (oben) rechteck(cx - h, cy - gap - len, th, len);   // oben
        rechteck(cx - h, cy + gap + (th % 2), th, len);         // unten
        rechteck(cx - gap - len, cy - h, len, th);               // links
        rechteck(cx + gap + (th % 2), cy - h, len, th);          // rechts
    }

    private static void punkt(int cx, int cy, int r) {
        // r = 1 -> 1x1, 2 -> 3x3, 3 -> 5x5 ... immer mittig
        int s = r * 2 - 1;
        rechteck(cx - (r - 1), cy - (r - 1), s, s);
    }

    private static void diagonal(int cx, int cy, int gap, int len, int th) {
        for (int i = gap + 1; i <= gap + len; i++) {
            rechteck(cx + i, cy + i, th, th);
            rechteck(cx - i - th + 1, cy + i, th, th);
            rechteck(cx + i, cy - i - th + 1, th, th);
            rechteck(cx - i - th + 1, cy - i - th + 1, th, th);
        }
    }

    private static void quadrat(int cx, int cy, int r, int th) {
        rechteck(cx - r, cy - r, 2 * r + 1, th);                 // oben
        rechteck(cx - r, cy + r - th + 1, 2 * r + 1, th);        // unten
        rechteck(cx - r, cy - r + th, th, 2 * r + 1 - 2 * th);   // links
        rechteck(cx + r - th + 1, cy - r + th, th, 2 * r + 1 - 2 * th);   // rechts
    }

    /** Kreis aus Punkten; doppelte Punkte werden nur einmal gezeichnet. */
    private static void kreis(int cx, int cy, int r, int th) {
        java.util.Set<Long> schon = new java.util.HashSet<>();
        int schritte = Math.max(24, r * 8);
        for (int i = 0; i < schritte; i++) {
            double w = Math.PI * 2 * i / schritte;
            int px = cx + (int) Math.round(Math.cos(w) * r) - th / 2;
            int py = cy + (int) Math.round(Math.sin(w) * r) - th / 2;
            if (schon.add(((long) px << 32) ^ (py & 0xFFFFFFFFL))) rechteck(px, py, th, th);
        }
    }

    /** Erst alle Umrandungen, dann alle Flaechen. */
    private static void zeichne(GuiGraphicsExtractor ctx, int farbe, boolean rand, int randFarbe) {
        if (rand && (randFarbe >>> 24) != 0) {
            for (int[] t : TEILE) {
                ctx.fill(t[0] - 1, t[1] - 1, t[0] + t[2] + 1, t[1] + t[3] + 1, randFarbe);
            }
        }
        for (int[] t : TEILE) {
            ctx.fill(t[0], t[1], t[0] + t[2], t[1] + t[3], farbe);
        }
    }

    // ------------------------------------------------------------------
    // Angriffsanzeige
    // ------------------------------------------------------------------

    private static void anzeige(GuiGraphicsExtractor ctx, Minecraft mc, CrosshairModule m, int modus,
                                int cx, int cy, int ausdehnung, float staerke, float blitz) {
        int farbe = m.indicatorColor.get();
        if (blitz > 0f) farbe = mischen(farbe, 0xFFFFFFFF, blitz);
        int y = cy + ausdehnung + 4;
        switch (modus) {
            case 1: {   // Bar
                int b = 16, x = cx - b / 2;
                ctx.fill(x - 1, y - 1, x + b + 1, y + 3, 0xB0000000);
                ctx.fill(x, y, x + b, y + 2, 0xFF2E2A3A);
                int voll = Math.round(b * Math.min(1f, staerke));
                if (voll > 0) ctx.fill(x, y, x + voll, y + 2, farbe);
                break;
            }
            case 2: {   // Ring um das Fadenkreuz, faellt im Uhrzeigersinn voll
                int r = ausdehnung + 3;
                TEILE.clear();
                int schritte = Math.max(32, r * 8);
                int bis = Math.round(schritte * Math.min(1f, staerke));
                java.util.Set<Long> schon = new java.util.HashSet<>();
                for (int i = 0; i < bis; i++) {
                    double w = -Math.PI / 2 + Math.PI * 2 * i / schritte;
                    int px = cx + (int) Math.round(Math.cos(w) * r);
                    int py = cy + (int) Math.round(Math.sin(w) * r);
                    if (schon.add(((long) px << 32) ^ (py & 0xFFFFFFFFL))) rechteck(px, py, 1, 1);
                }
                zeichne(ctx, farbe, true, 0x90000000);
                break;
            }
            case 3: {   // Prozent
                String t = String.format(Locale.ROOT, "%d%%", Math.round(Math.min(1f, staerke) * 100));
                int tw = mc.font.width(t);
                ctx.text(mc.font, Component.literal(t), cx - tw / 2, y, farbe, true);
                break;
            }
            default: break;
        }
    }

    private static int mischen(int a, int b, float t) {
        int aa = a >>> 24, ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int ba = b >>> 24, br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        return ((int) (aa + (ba - aa) * t) << 24) | ((int) (ar + (br - ar) * t) << 16)
                | ((int) (ag + (bg - ag) * t) << 8) | (int) (ab + (bb - ab) * t);
    }

    /** Farbton 0..1 -> kraeftige RGB-Farbe. */
    private static int hsv(float h) {
        float r = Math.abs(h * 6f - 3f) - 1f;
        float g = 2f - Math.abs(h * 6f - 2f);
        float b = 2f - Math.abs(h * 6f - 4f);
        r = Math.max(0f, Math.min(1f, r));
        g = Math.max(0f, Math.min(1f, g));
        b = Math.max(0f, Math.min(1f, b));
        return 0xFF000000 | ((int) (r * 255) << 16) | ((int) (g * 255) << 8) | (int) (b * 255);
    }
}
