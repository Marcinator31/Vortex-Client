package com.vortex.client.musik;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;

/**
 * Der Song ueber dem Kopf als kleine Karte: Album-Cover links, daneben Titel
 * und Kuenstler, darunter der Fortschritt in Spotify-Gruen. Dreht sich wie ein
 * Namensschild immer zur Kamera.
 *
 * Ohne Cover (z. B. Song aus der Spotify-App ohne Anmeldung) steht dort eine
 * gruene Note.
 */
public final class KopfKarte {
    private KopfKarte() {}

    private static final int LICHT = 0xF000F0, GRUEN = 0xFF1ED760;

    public static void zeichne(Player p, PoseStack m, SubmitNodeCollector q, CameraRenderState cam, double hoehe) {
        Song s = MusikDienst.kopfSong(p);
        if (s == null || cam == null || cam.orientation == null) return;
        Font font = Minecraft.getInstance().font;
        String titel = kurz(font, s.titel(), 120);
        String unter = kurz(font, s.kuenstler(), 120) + (MusikDienst.istZiel(p) ? "  ◀ you" : "");
        int tw = Math.max(font.width(titel), font.width(unter));
        final float c = 20, rand = 3;
        final float w = rand + c + 5 + tw + 6, h = c + rand * 2;
        final float x0 = -w / 2f, y0 = -h / 2f;
        final float anteil = s.dauer() > 0 ? Math.max(0, Math.min(1, s.anteil())) : -1;
        Identifier cover = Cover.von(s.cover());

        m.pushPose();
        m.translate(0.0, hoehe, 0.0);
        m.mulPose(cam.orientation);
        m.scale(0.025f, -0.025f, 0.025f);
        final float tx = x0 + rand + c + 5;
        q.submitCustomGeometry(m, RenderTypes.textBackground(), (pose, vc) -> {
            rechteck(pose, vc, x0, y0, x0 + w, y0 + h, 0xA00B0814);
            rechteck(pose, vc, x0, y0, x0 + 1.2f, y0 + h, GRUEN);               // gruene Kante links
            if (anteil >= 0) {
                rechteck(pose, vc, tx, y0 + h - 4.5f, x0 + w - 6, y0 + h - 3.5f, 0x40FFFFFF);
                rechteck(pose, vc, tx, y0 + h - 4.5f, tx + (x0 + w - 6 - tx) * anteil, y0 + h - 3.5f, GRUEN);
            }
            if (cover == null) rechteck(pose, vc, x0 + rand, y0 + rand, x0 + rand + c, y0 + rand + c, 0xFF1B3A26);
        });
        if (cover != null) {
            q.submitCustomGeometry(m, RenderTypes.textPolygonOffset(cover), (pose, vc) -> bild(pose, vc, x0 + rand, y0 + rand, x0 + rand + c, y0 + rand + c));
        }
        var text = q.order(1);
        if (cover == null) {
            text.submitText(m, x0 + rand + c / 2f - font.width("♫") / 2f, y0 + rand + 6, Component.literal("♫").withColor(GRUEN & 0xFFFFFF).getVisualOrderText(),
                    false, Font.DisplayMode.POLYGON_OFFSET, LICHT, 0xFFFFFFFF, 0, 0);
        }
        text.submitText(m, tx, y0 + rand + 0.5f, Component.literal(titel).getVisualOrderText(), false, Font.DisplayMode.POLYGON_OFFSET, LICHT, 0xFFFFFFFF, 0, 0);
        text.submitText(m, tx, y0 + rand + 10f, Component.literal(unter).getVisualOrderText(), false, Font.DisplayMode.POLYGON_OFFSET, LICHT,
                0xFF000000 | MusikDienst.kopfFarbe(), 0, 0);
        m.popPose();
    }

    private static String kurz(Font font, String t, int max) {
        if (t == null) return "";
        if (font.width(t) <= max) return t;
        while (t.length() > 1 && font.width(t + "…") > max) t = t.substring(0, t.length() - 1);
        return t + "…";
    }

    private static void rechteck(PoseStack.Pose p, VertexConsumer vc, float x0, float y0, float x1, float y1, int farbe) {
        vc.addVertex(p, x0, y0, 0).setColor(farbe).setLight(LICHT);
        vc.addVertex(p, x0, y1, 0).setColor(farbe).setLight(LICHT);
        vc.addVertex(p, x1, y1, 0).setColor(farbe).setLight(LICHT);
        vc.addVertex(p, x1, y0, 0).setColor(farbe).setLight(LICHT);
    }

    private static void bild(PoseStack.Pose p, VertexConsumer vc, float x0, float y0, float x1, float y1) {
        vc.addVertex(p, x0, y0, 0).setColor(0xFFFFFFFF).setUv(0, 0).setLight(LICHT);
        vc.addVertex(p, x0, y1, 0).setColor(0xFFFFFFFF).setUv(0, 1).setLight(LICHT);
        vc.addVertex(p, x1, y1, 0).setColor(0xFFFFFFFF).setUv(1, 1).setLight(LICHT);
        vc.addVertex(p, x1, y0, 0).setColor(0xFFFFFFFF).setUv(1, 0).setLight(LICHT);
    }
}
