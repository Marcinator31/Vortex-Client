package com.vortex.client.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/**
 * Das Vortex-Logo, lebendig gezeichnet (neu in 4.6.0).
 *
 * Das Logo besteht aus zwei Teilen, die getrennt als Bild vorliegen:
 *   logo_v     das "V" -- zwei Klingen, Violett und Blau
 *   logo_ring  der Wirbel-Bogen darum, mit auslaufendem Schweif
 *
 * Getrennt, damit sich der Bogen langsam um das V drehen kann: eine Runde
 * in gut elf Sekunden -- ruhig genug, um nicht abzulenken, aber so, dass das
 * Menue lebt. Beim Oeffnen waechst das Logo mit einer kleinen Drehung herein.
 *
 * logo (beides zusammen, unbewegt) bleibt als Rueckfall, falls das Drehen
 * einmal nicht geht.
 */
public final class LogoRenderer {

    private static final Identifier V = Identifier.fromNamespaceAndPath("vortexclient", "logo_v");
    private static final Identifier RING = Identifier.fromNamespaceAndPath("vortexclient", "logo_ring");
    private static final Identifier GANZ = Identifier.fromNamespaceAndPath("vortexclient", "logo");

    /** Einmal gescheitert -> ab dann das unbewegte Bild. */
    private static boolean nurStatisch = false;

    private LogoRenderer() {}

    /**
     * @param g        Kantenlaenge in Pixeln
     * @param alpha    Deckkraft 0..1
     * @param oeffnen  Fortschritt des Oeffnens 0..1 (fuer das Hereinwachsen)
     */
    public static void zeichne(GuiGraphicsExtractor ctx, int x, int y, int g, float alpha, float oeffnen) {
        if (alpha <= 0.01f) return;
        float e = 1f - (1f - clamp(oeffnen)) * (1f - clamp(oeffnen)) * (1f - clamp(oeffnen));
        if (nurStatisch) {
            statisch(ctx, x, y, g, alpha);
            return;
        }
        float zeit = (System.currentTimeMillis() % 3_600_000L) / 1000f;
        var p = ctx.pose();
        p.pushMatrix();
        try {
            p.translate(x + g / 2f, y + g / 2f);
            float s = 0.80f + 0.20f * e;
            p.scale(s, s);
            int h = g / 2;
            // Bogen: dreht sich stetig, beim Oeffnen mit Schwung hinein
            p.pushMatrix();
            p.rotate(zeit * 0.55f - (1f - e) * 2.2f);
            ctx.blitSprite(RenderPipelines.GUI_TEXTURED, RING, -h, -h, g, g, alpha);
            p.popMatrix();
            ctx.blitSprite(RenderPipelines.GUI_TEXTURED, V, -h, -h, g, g, alpha);
        } catch (Throwable t) {
            nurStatisch = true;
        } finally {
            p.popMatrix();
        }
    }

    /** Das unbewegte Logo (fuer kleine Stellen und als Rueckfall). */
    public static void statisch(GuiGraphicsExtractor ctx, int x, int y, int g, float alpha) {
        try {
            ctx.blitSprite(RenderPipelines.GUI_TEXTURED, GANZ, x, y, g, g, alpha);
        } catch (Throwable ignored) {
        }
    }

    private static float clamp(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }
}
