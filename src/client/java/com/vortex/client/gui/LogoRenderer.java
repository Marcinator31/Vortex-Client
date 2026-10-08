package com.vortex.client.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/**
 * Das Vortex-Logo als Sprite (Rueckfall fuer Glatt.logo und fuer das alte Menue).
 *
 * Seit 4.28 ist es Logo A "Faltung": zwei Klingen, getrennt als Bild
 *   logo_a_l   linke Klinge (Lila)
 *   logo_a_r   rechte Klinge (Blau)
 * Beim Oeffnen kommen sie von links oben und rechts oben und falten sich
 * zum V. logo (beide zusammen, unbewegt) bleibt fuer kleine Stellen und als
 * Rueckfall, falls das Bewegen einmal nicht geht.
 */
public final class LogoRenderer {

    private static final Identifier L = Identifier.fromNamespaceAndPath("vortexclient", "logo_a_l");
    private static final Identifier R = Identifier.fromNamespaceAndPath("vortexclient", "logo_a_r");
    private static final Identifier GANZ = Identifier.fromNamespaceAndPath("vortexclient", "logo");

    /** Einmal gescheitert -> ab dann das unbewegte Bild. */
    private static boolean nurStatisch = false;

    private LogoRenderer() {}

    /**
     * @param g        Kantenlaenge in Pixeln
     * @param alpha    Deckkraft 0..1
     * @param oeffnen  Fortschritt des Oeffnens 0..1 (fuer das Zusammenfalten)
     */
    public static void zeichne(GuiGraphicsExtractor ctx, int x, int y, int g, float alpha, float oeffnen) {
        if (alpha <= 0.01f) return;
        float o = clamp(oeffnen);
        float e = 1f - (1f - o) * (1f - o) * (1f - o);
        if (nurStatisch) {
            statisch(ctx, x, y, g, alpha);
            return;
        }
        var p = ctx.pose();
        p.pushMatrix();
        try {
            p.translate(x + g / 2f, y + g / 2f);
            float s = 0.86f + 0.14f * e;
            p.scale(s, s);
            int h = g / 2;
            float weg = (1f - e) * g * 0.16f;
            p.pushMatrix();
            p.translate(-weg, -weg * 0.7f);
            ctx.blitSprite(RenderPipelines.GUI_TEXTURED, L, -h, -h, g, g, alpha);
            p.popMatrix();
            p.pushMatrix();
            p.translate(weg, -weg * 0.7f);
            ctx.blitSprite(RenderPipelines.GUI_TEXTURED, R, -h, -h, g, g, alpha);
            p.popMatrix();
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
