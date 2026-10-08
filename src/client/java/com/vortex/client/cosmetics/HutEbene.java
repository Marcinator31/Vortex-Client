package com.vortex.client.cosmetics;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;

/**
 * Zeichnet den gewaehlten Hut auf den Kopf eines Spielers -- als eigene
 * Ebene am Spieler-Renderer, damit er jede Kopfbewegung mitmacht (auch
 * beim Schleichen, Schwimmen und in der Inventar-Vorschau).
 *
 * Die Quader werden als farbige Flaechen auf einer weissen Textur gezeichnet.
 * Welcher Spieler gerade dran ist, verraet die Entity-Id im Renderzustand.
 */
public class HutEbene extends RenderLayer<AvatarRenderState, PlayerModel> {
    private static final Identifier WEISS = Identifier.fromNamespaceAndPath("vortexclient", "textures/cosmetics/white.png");
    /** Volle Helligkeit (Block- und Himmelslicht 15) -- fuer leuchtende Teile. */
    private static final int HELL = 0xF000F0;
    /** Der Hut sitzt ein Viertelpixel ueber der zweiten Hautschicht. */
    private static final float KOPF_OBEN = 8.5f;

    public HutEbene(RenderLayerParent<AvatarRenderState, PlayerModel> parent) {
        super(parent);
    }

    @Override
    public void submit(PoseStack pose, SubmitNodeCollector collector, int licht, AvatarRenderState state, float yRot, float xRot) {
        try {
            if (state.isInvisible) return;
            Kopfschmuck.Design hut = Kopfschmuck.get(Cosmetics.fuerEntity(state.id).hut());
            if (hut == null) return;
            final float t = state.ageInTicks;
            Netz netz = new Netz();
            hut.bauer().baue(netz, t);
            pose.pushPose();
            getParentModel().head.translateAndRotate(pose);
            collector.submitCustomGeometry(pose, RenderTypes.entitySolid(WEISS), (p, vc) -> {
                for (Netz.Flaeche f : netz.flaechen) flaeche(p, vc, f, f.leuchten > 0.3f ? HELL : licht);
            });
            // Leuchten und Glanz: eigene Ebene darueber (leuchtet auch nachts)
            collector.submitCustomGeometry(pose, RenderTypes.eyes(WEISS), (p, vc) -> {
                for (Netz.Flaeche f : netz.flaechen) glanz(p, vc, f, t);
            });
            pose.popPose();
        } catch (Throwable t) {
            com.vortex.client.core.Errors.report("HutEbene", t);
        }
    }

    /** Netz-Koordinaten (Pixel, y nach oben ab Kopfoberkante) in den Modellraum (y nach unten, 1/16). */
    private static float mx(float x) { return x / 16f; }
    private static float my(float y) { return -(KOPF_OBEN + y) / 16f; }
    private static float mz(float z) { return z / 16f; }

    /** Die y-Spiegelung kehrt die Umlaufrichtung um -- deshalb Ecken rueckwaerts (sonst sieht man die Innenseiten). */
    private static final int[] REIHE = { 0, 3, 2, 1 };

    private static void flaeche(PoseStack.Pose p, VertexConsumer vc, Netz.Flaeche f, int licht) {
        for (int i : REIHE) {
            vc.addVertex(p, mx(f.p[i * 3]), my(f.p[i * 3 + 1]), mz(f.p[i * 3 + 2])).setColor(f.farbe[i] | 0xFF000000).setUv(0.5f, 0.5f)
                    .setOverlay(OverlayTexture.NO_OVERLAY).setLight(licht).setNormal(p, f.n[i * 3], -f.n[i * 3 + 1], f.n[i * 3 + 2]);
        }
    }

    /** Leuchtende Teile hell ueberlagern; Metall bekommt einen wandernden Glanzstreifen. */
    private static void glanz(PoseStack.Pose p, VertexConsumer vc, Netz.Flaeche f, float t) {
        if (f.leuchten <= 0.01f && !f.metall) return;
        float pos = ((t * 0.32f) % 30f) - 12f;
        int[] c = new int[4];
        boolean sichtbar = false;
        for (int i = 0; i < 4; i++) {
            float a = f.leuchten * 0.75f;
            int farbe = f.farbe[i] & 0xFFFFFF;
            if (f.metall) {
                float s = f.p[i * 3] * 0.8f + f.p[i * 3 + 1] * 1.1f - f.p[i * 3 + 2] * 0.3f - pos;
                float g = (float) Math.exp(-s * s / 5f) * 0.65f;
                if (g > a) farbe = Netz.mische(0xFF000000 | farbe, 0xFFFFFFFF, 0.6f) & 0xFFFFFF;
                a = Math.max(a, g);
            }
            int al = Math.max(0, Math.min(255, Math.round(a * 255f)));
            if (al > 3) sichtbar = true;
            c[i] = (al << 24) | farbe;
        }
        if (!sichtbar) return;
        for (int i : REIHE) {
            vc.addVertex(p, mx(f.p[i * 3]), my(f.p[i * 3 + 1]), mz(f.p[i * 3 + 2])).setColor(c[i]).setUv(0.5f, 0.5f)
                    .setOverlay(OverlayTexture.NO_OVERLAY).setLight(HELL).setNormal(p, 0, -1, 0);
        }
    }

    private static int dunkler(int argb, float f) {
        int a = argb >>> 24, r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;
        return (a << 24) | (Math.round(r * f) << 16) | (Math.round(g * f) << 8) | Math.round(b * f);
    }
}
