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
            Huete.Hut hut = Huete.get(Cosmetics.fuerEntity(state.id).hut());
            if (hut == null) return;
            final float t = state.ageInTicks;
            pose.pushPose();
            getParentModel().head.translateAndRotate(pose);
            // Kleine Bewegungen: der Heiligenschein schwebt und dreht sich, Bommel wippen
            if ("halo".equals(hut.id())) {
                pose.translate(0f, -0.35f / 16f * (float) Math.sin(t * 0.09f), 0f);
                pose.mulPose(new org.joml.Quaternionf().rotationY(t * 0.025f));
            }
            collector.submitCustomGeometry(pose, RenderTypes.entitySolid(WEISS), (p, vc) -> {
                for (Huete.Quader q : hut.teile()) quader(p, vc, q, q.leuchtet() ? HELL : licht);
            });
            // Glanz: ein heller Streifen wandert ueber den Hut, Edelsteine und Leuchtendes pulsieren
            collector.submitCustomGeometry(pose, RenderTypes.eyes(WEISS), (p, vc) -> {
                for (Huete.Quader q : hut.teile()) glanz(p, vc, q, t);
            });
            pose.popPose();
        } catch (Throwable t) {
            com.vortex.client.core.Errors.report("HutEbene", t);
        }
    }

    /**
     * Ein Quader aus sechs Flaechen. Im Modellraum zeigt y nach UNTEN und ein
     * Pixel ist 1/16 -- deshalb das Minus und das Teilen.
     */
    private static void quader(PoseStack.Pose p, VertexConsumer vc, Huete.Quader q, int licht) {
        float x0 = q.x0() / 16f, x1 = q.x1() / 16f;
        float y0 = -(KOPF_OBEN + q.y1()) / 16f, y1 = -(KOPF_OBEN + q.y0()) / 16f;   // oben = kleiner
        float z0 = q.z0() / 16f, z1 = q.z1() / 16f;
        int c = q.farbe();
        // Seiten etwas dunkler als oben -- wie bei Minecraft-Bloecken, sonst wirkt es flach
        // Leuchtende Teile (Heiligenschein, Edelsteine) ueberall gleich hell
        int seite = q.leuchtet() ? c : dunkler(c, 0.82f), unten = q.leuchtet() ? c : dunkler(c, 0.6f);
        flaeche(p, vc, licht, c, 0, -1, 0, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1);       // oben (y0 ist oben)
        flaeche(p, vc, licht, unten, 0, 1, 0, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0);    // unten
        flaeche(p, vc, licht, seite, 0, 0, -1, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0);   // vorne
        flaeche(p, vc, licht, seite, 0, 0, 1, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1);    // hinten
        flaeche(p, vc, licht, seite, -1, 0, 0, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0);   // links
        flaeche(p, vc, licht, seite, 1, 0, 0, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1);    // rechts
    }

    /**
     * Vier Ecken einer Flaeche. Die Reihenfolge wird an der Normale geprueft
     * und notfalls umgedreht -- so zeigt jede Flaeche sicher nach aussen und
     * wird nicht weggeschnitten.
     */
    private static void flaeche(PoseStack.Pose p, VertexConsumer vc, int licht, int farbe, float nx, float ny, float nz,
                                float ax, float ay, float az, float bx, float by, float bz,
                                float cx, float cy, float cz, float dx, float dy, float dz) {
        float ux = bx - ax, uy = by - ay, uz = bz - az, wx = cx - ax, wy = cy - ay, wz = cz - az;
        float kx = uy * wz - uz * wy, ky = uz * wx - ux * wz, kz = ux * wy - uy * wx;
        boolean richtig = kx * nx + ky * ny + kz * nz >= 0;
        float[][] e = richtig
                ? new float[][] { {ax, ay, az}, {bx, by, bz}, {cx, cy, cz}, {dx, dy, dz} }
                : new float[][] { {ax, ay, az}, {dx, dy, dz}, {cx, cy, cz}, {bx, by, bz} };
        // Leuchtend: Minecraft schattiert Flaechen nach ihrer Normale (seitlich ~70 %
        // hell) -- mit einer Normale nach oben bleibt jede Seite voll hell.
        boolean hell = licht == HELL;
        for (float[] v : e) {
            vc.addVertex(p, v[0], v[1], v[2]).setColor(farbe).setUv(0.5f, 0.5f)
                    .setOverlay(OverlayTexture.NO_OVERLAY).setLight(licht)
                    .setNormal(p, hell ? 0 : nx, hell ? -1 : ny, hell ? 0 : nz);
        }
    }

    /** Glanz-Ebene eines Quaders: Deckkraft je Ecke aus dem wandernden Lichtstreifen. */
    private static void glanz(PoseStack.Pose p, VertexConsumer vc, Huete.Quader q, float t) {
        float x0 = q.x0() / 16f, x1 = q.x1() / 16f;
        float y0 = -(KOPF_OBEN + q.y1()) / 16f, y1 = -(KOPF_OBEN + q.y0()) / 16f;
        float z0 = q.z0() / 16f, z1 = q.z1() / 16f;
        float pos = ((t * 0.32f) % 30f) - 12f;
        float puls = q.leuchtet() ? 0.3f + 0.25f * (float) Math.sin(t * 0.18f + q.x0()) : 0f;
        int grund = q.leuchtet() ? (q.farbe() & 0xFFFFFF) : 0xFFFFFF;
        float[][] ecken = {
                {x0, y0, z0}, {x1, y0, z0}, {x1, y0, z1}, {x0, y0, z1},
                {x0, y1, z0}, {x1, y1, z0}, {x1, y1, z1}, {x0, y1, z1}};
        int[] farbe = new int[8];
        for (int i = 0; i < 8; i++) {
            float px = ecken[i][0] * 16f, py = -ecken[i][1] * 16f, pz = ecken[i][2] * 16f;
            float s = px * 0.8f + py * 1.1f - pz * 0.3f - pos;
            float a = (float) Math.exp(-s * s / 5f) * 0.6f + puls;
            int al = Math.max(0, Math.min(255, Math.round(a * 255f)));
            int c = q.leuchtet() && a > puls + 0.2f ? 0xFFFFFF : grund;
            farbe[i] = (al << 24) | c;
        }
        // Flaechen: oben, unten, vorne, hinten, links, rechts (Ecken-Indizes)
        int[][] f = { {0, 1, 2, 3}, {4, 7, 6, 5}, {0, 4, 5, 1}, {3, 2, 6, 7}, {0, 3, 7, 4}, {1, 5, 6, 2} };
        for (int[] fl : f) {
            boolean sichtbar = false;
            for (int i : fl) if ((farbe[i] >>> 24) > 3) sichtbar = true;
            if (!sichtbar) continue;
            for (int k = 3; k >= 0; k--) {          // beide Richtungen: ohne Backface-Probleme
                float[] v = ecken[fl[k]];
                vc.addVertex(p, v[0], v[1], v[2]).setColor(farbe[fl[k]]).setUv(0.5f, 0.5f)
                        .setOverlay(OverlayTexture.NO_OVERLAY).setLight(HELL).setNormal(p, 0, -1, 0);
            }
            for (int k = 0; k < 4; k++) {
                float[] v = ecken[fl[k]];
                vc.addVertex(p, v[0], v[1], v[2]).setColor(farbe[fl[k]]).setUv(0.5f, 0.5f)
                        .setOverlay(OverlayTexture.NO_OVERLAY).setLight(HELL).setNormal(p, 0, -1, 0);
            }
        }
    }

    private static int dunkler(int argb, float f) {
        int a = argb >>> 24, r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;
        return (a << 24) | (Math.round(r * f) << 16) | (Math.round(g * f) << 8) | Math.round(b * f);
    }
}
