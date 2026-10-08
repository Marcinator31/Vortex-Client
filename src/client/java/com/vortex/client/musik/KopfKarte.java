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
        String titel = kurz(font, s.titel(), 140);
        String unter = kurz(font, s.kuenstler(), 140) + (MusikDienst.istZiel(p) ? "  ◀ you" : "");
        final boolean zeit = s.dauer() > 0;
        String links = zeit ? Song.zeit(s.jetzt()) : "", rechts = zeit ? Song.zeit(s.dauer()) : "";
        // Aufbau wie die Karte im HUD: Cover links (darunter "Spotify"),
        // rechts Titel, Kuenstler, Fortschritt mit Zeiten
        final float pad = 4, c = 30;
        final float tw = Math.max(110, Math.max(font.width(titel), font.width(unter)));
        final float w = pad + c + 6 + tw + pad + 1, h = pad + c + 8 + pad;
        final float x0 = -w / 2f, y0 = -h / 2f;
        final float tx = x0 + pad + c + 6, rx = tx + tw;
        final float anteil = zeit ? Math.max(0, Math.min(1, s.anteil())) : -1;
        final float by = y0 + pad + 24;
        Identifier cover = Cover.von(s.cover());

        m.pushPose();
        m.translate(0.0, hoehe, 0.0);
        m.mulPose(cam.orientation);
        m.scale(0.025f, -0.025f, 0.025f);
        q.submitCustomGeometry(m, RenderTypes.textBackground(), (pose, vc) -> {
            rechteck(pose, vc, x0, y0, x0 + w, y0 + h, 0xD00B0814);
            rechteck(pose, vc, x0, y0, x0 + w, y0 + 1, 0x30FFFFFF);
            if (anteil >= 0) {
                rechteck(pose, vc, tx, by, rx, by + 1.5f, 0x40FFFFFF);
                rechteck(pose, vc, tx, by, tx + (rx - tx) * anteil, by + 1.5f, GRUEN);
            }
            if (cover == null) rechteck(pose, vc, x0 + pad, y0 + pad, x0 + pad + c, y0 + pad + c, 0xFF1B3A26);
        });
        if (cover != null) {
            q.submitCustomGeometry(m, RenderTypes.textPolygonOffset(cover), (pose, vc) -> bild(pose, vc, x0 + pad, y0 + pad, x0 + pad + c, y0 + pad + c));
        }
        var text = q.order(1);
        if (cover == null) {
            text.submitText(m, x0 + pad + c / 2f - font.width("♫") / 2f, y0 + pad + c / 2f - 4, Component.literal("♫").withColor(GRUEN & 0xFFFFFF).getVisualOrderText(),
                    false, Font.DisplayMode.POLYGON_OFFSET, LICHT, 0xFFFFFFFF, 0, 0);
        }
        text.submitText(m, tx, y0 + pad + 1, Component.literal(titel).getVisualOrderText(), false, Font.DisplayMode.POLYGON_OFFSET, LICHT, 0xFFFFFFFF, 0, 0);
        text.submitText(m, tx, y0 + pad + 12, Component.literal(unter).getVisualOrderText(), false, Font.DisplayMode.POLYGON_OFFSET, LICHT,
                0xFF000000 | MusikDienst.kopfFarbe(), 0, 0);
        // Kleinere Schrift: Zeiten unter dem Balken, "Spotify" unter dem Cover
        m.pushPose();
        m.translate(0f, 0f, 0f);
        m.scale(0.7f, 0.7f, 0.7f);
        float k = 1 / 0.7f;
        if (zeit) {
            text.submitText(m, tx * k, (by + 3.5f) * k, Component.literal(links).getVisualOrderText(), false, Font.DisplayMode.POLYGON_OFFSET, LICHT, 0xFFB9B3CC, 0, 0);
            text.submitText(m, rx * k - font.width(rechts), (by + 3.5f) * k, Component.literal(rechts).getVisualOrderText(), false, Font.DisplayMode.POLYGON_OFFSET, LICHT, 0xFFB9B3CC, 0, 0);
        }
        String marke = "♫ Spotify";
        text.submitText(m, (x0 + pad + c / 2f) * k - font.width(marke) / 2f, (y0 + pad + c + 2f) * k, Component.literal(marke).getVisualOrderText(),
                false, Font.DisplayMode.POLYGON_OFFSET, LICHT, GRUEN, 0, 0);
        m.popPose();
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
