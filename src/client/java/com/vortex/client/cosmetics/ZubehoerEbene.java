package com.vortex.client.cosmetics;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;

/**
 * Zeichnet die Pixel-Cosmetics (seit 4.29) am Spieler:
 *   Bandana und Face am Kopf, Back am Oberkoerper, Aura um den ganzen Koerper.
 *
 * Gezeichnet wird in Pixeln mit y nach oben (siehe {@link Zubehoer}); die
 * Teile sind zwischengespeicherte Netze, bewegt wird nur ueber den PoseStack.
 */
public class ZubehoerEbene extends RenderLayer<AvatarRenderState, PlayerModel> {
    private static final Identifier WEISS = Identifier.fromNamespaceAndPath("vortexclient", "textures/cosmetics/white.png");
    private static final int HELL = 0xF000F0;
    /** y-Spiegelung kehrt die Umlaufrichtung um -- Ecken rueckwaerts. */
    private static final int[] REIHE = { 0, 3, 2, 1 };

    public ZubehoerEbene(RenderLayerParent<AvatarRenderState, PlayerModel> parent) {
        super(parent);
    }

    @Override
    public void submit(PoseStack pose, SubmitNodeCollector collector, int licht, AvatarRenderState state, float yRot, float xRot) {
        if (state.isInvisible) return;
        Cosmetics.Auswahl a = Cosmetics.fuerEntity(state.id);
        float t = state.ageInTicks;
        try {
            Zubehoer.Design bandana = Zubehoer.get(Zubehoer.Kategorie.BANDANA, a.bandana());
            Zubehoer.Design face = Zubehoer.get(Zubehoer.Kategorie.FACE, a.gesicht());
            if (bandana != null || face != null) {
                pose.pushPose();
                getParentModel().head.translateAndRotate(pose);
                pose.scale(1 / 16f, -1 / 16f, 1 / 16f);
                if (bandana != null) bandana(pose, collector, licht, bandana, t);
                if (face != null) face(pose, collector, licht, face, t);
                pose.popPose();
            }
            Zubehoer.Design back = Zubehoer.get(Zubehoer.Kategorie.BACK, a.ruecken());
            if (back != null) {
                pose.pushPose();
                getParentModel().body.translateAndRotate(pose);
                pose.scale(1 / 16f, -1 / 16f, 1 / 16f);
                float wippen = (float) Math.sin(state.walkAnimationPos * 0.6662f * 2) * Math.min(1f, state.walkAnimationSpeed) * 0.35f;
                for (Zubehoer.Teil teil : back.teile()) teil(pose, collector, licht, teil, t, wippen);
                pose.popPose();
            }
            Zubehoer.Design aura = Zubehoer.get(Zubehoer.Kategorie.AURA, a.partikel());
            if (aura != null && aura.aura() != null) {
                pose.pushPose();
                pose.scale(1 / 16f, -1 / 16f, 1 / 16f);
                aura(pose, collector, aura.aura(), t, state.id, a.dichte());
                pose.popPose();
            }
        } catch (Throwable e) {
            com.vortex.client.core.Errors.report("ZubehoerEbene", e);
        }
    }

    // ------------------------------------------------------------------
    // Kopf
    // ------------------------------------------------------------------

    private void bandana(PoseStack pose, SubmitNodeCollector c, int licht, Zubehoer.Design d, float t) {
        Zubehoer.Ring r = d.ring();
        if (r == null) return;
        zeichne(pose, c, Zubehoer.ringNetz(r), licht, t);
        knoten(pose, c, licht, r, t);
    }

    /** Knoten hinten und zwei Baender, die leicht wehen. */
    private void knoten(PoseStack pose, SubmitNodeCollector c, int licht, Zubehoer.Ring r, float t) {
        if (r.baender().length == 0) return;
        float a = Zubehoer.BAND_AUSSEN + Zubehoer.BAND_DICKE;
        float mitte = r.y0() + r.farben().length * r.px() / 2f;
        Netz knoten = KNOTEN.computeIfAbsent(r, k -> {
            Netz n = new Netz();
            n.quader(-0.9f, -0.8f, 0, 0.9f, 0.8f, 0.9f, r.knoten(), 0x3F);
            return n;
        });
        Netz band = BAND.computeIfAbsent(r, k -> {
            Netz n = new Netz();
            for (int i = 0; i < 4; i++) {
                int f = r.baender()[i % r.baender().length];
                n.quader(-0.5f, -(i + 1) * 1.0f, 0, 0.5f, -i * 1.0f, 0.4f, f, 0x3F);
            }
            return n;
        });
        pose.pushPose();
        pose.translate(0, mitte, a);
        zeichne(pose, c, knoten, licht, t);
        for (int s = -1; s <= 1; s += 2) {
            pose.pushPose();
            pose.translate(s * 0.45f, -0.3f, 0.5f);
            float wehen = (float) Math.sin(t * 0.12f + s) * 9f + 14f;
            pose.mulPose(Axis.XP.rotationDegrees(wehen));
            pose.mulPose(Axis.ZP.rotationDegrees(s * 16f));
            zeichne(pose, c, band, licht, t);
            pose.popPose();
        }
        pose.popPose();
    }

    private static final java.util.Map<Object, Netz> KNOTEN = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Map<Object, Netz> BAND = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Map<Object, Netz> BUEGEL = new java.util.concurrent.ConcurrentHashMap<>();

    private void face(PoseStack pose, SubmitNodeCollector c, int licht, Zubehoer.Design d, float t) {
        if (d.ring() != null) zeichne(pose, c, Zubehoer.ringNetz(d.ring()), licht, t);
        for (Zubehoer.Teil teil : d.teile()) teil(pose, c, licht, teil, t, 0);
        // Brillenbuegel an beiden Seiten nach hinten
        if (d.buegel() != 0 && !d.teile().isEmpty()) {
            Zubehoer.Teil vorne = d.teile().get(0);
            float y = vorne.at()[1] + vorne.farben().length * vorne.px() / 2f - vorne.px() * 0.5f;
            Netz b = BUEGEL.computeIfAbsent(d, k -> {
                Netz n = new Netz();
                float a = 4.55f;
                n.quader(a, -0.25f, -4.8f, a + 0.45f, 0.25f, 1.5f, d.buegel(), 0x3F);
                n.quader(-a - 0.45f, -0.25f, -4.8f, -a, 0.25f, 1.5f, d.buegel(), 0x3F);
                return n;
            });
            pose.pushPose();
            pose.translate(0, y, 0);
            zeichne(pose, c, b, licht, t);
            pose.popPose();
        }
    }

    /** Ein Pixelteil an seine Stelle setzen (at, Drehung, Groesse) und zeichnen. */
    private void teil(PoseStack pose, SubmitNodeCollector c, int licht, Zubehoer.Teil teil, float t, float wippen) {
        pose.pushPose();
        float[] at = teil.at(), rot = teil.rot();
        float dy = teil.anim().equals("bob") ? wippen : 0;
        pose.translate(at[0], at[1] + dy, at[2]);
        if (rot[1] != 0) pose.mulPose(Axis.YP.rotationDegrees(rot[1]));
        if (rot[0] != 0) pose.mulPose(Axis.XP.rotationDegrees(rot[0]));
        if (rot[2] != 0) pose.mulPose(Axis.ZP.rotationDegrees(rot[2]));
        float s = teil.px();
        if (teil.anim().equals("flicker")) {
            float f = 0.75f + 0.25f * (float) Math.abs(Math.sin(t * 1.7f) * Math.cos(t * 0.9f));
            pose.translate(0, (1 - f) * teil.farben().length * s / 2f, 0);
            pose.scale(s, s * f, s);
        } else {
            pose.scale(s, s, s);
        }
        zeichne(pose, c, Zubehoer.netz(teil), licht, t);
        pose.popPose();
    }

    // ------------------------------------------------------------------
    // Aura: Sprites um den ganzen Koerper (Kopf bei y 0..8, Fuesse bei -24)
    // ------------------------------------------------------------------

    private void aura(PoseStack pose, SubmitNodeCollector c, Zubehoer.Aura au, float t, int seed, int dichte) {
        if (au.sprites().isEmpty()) return;
        int n = Math.max(1, Math.round(au.anzahl() * (dichte == 1 ? 0.6f : dichte == 3 ? 1.5f : 1f)));
        if (au.bewegung().equals("twin")) n = 2;
        int licht = HELL;
        for (int i = 0; i < n; i++) {
            float r1 = zufall(seed * 31 + i * 7), r2 = zufall(seed * 17 + i * 13 + 3), r3 = zufall(seed * 5 + i * 29 + 11);
            Zubehoer.Teil sp = au.sprites().get(i % au.sprites().size());
            float x, y, z, groesse = 1f, drehY = 0, drehZ = 0, drehX = 0, fluegel = 1f;
            switch (au.bewegung()) {
                case "fall", "rise" -> {
                    float dauer = 90 + r1 * 40;
                    float ph = ((t / dauer) + i / (float) n + r2) % 1f;
                    float w = r3 * 6.283f + t * 0.012f;
                    float rad = 9 + r1 * 5;
                    y = au.bewegung().equals("fall") ? 12 - ph * 38 : -25 + ph * 38;
                    if (au.bewegung().equals("rise")) w += ph * 2.5f;
                    x = (float) Math.cos(w) * rad + (float) Math.sin(t * 0.05f + i) * 1.5f;
                    z = (float) Math.sin(w) * rad;
                    groesse = rampe(ph);
                    drehY = -w * 57.3f + 90;
                    drehZ = t * 3f * au.drehen() + i * 47;
                    drehX = (float) Math.sin(t * 0.07f + i) * 35f * au.drehen();
                }
                case "flutter" -> {
                    float w = t * 0.025f + i * 6.283f / n;
                    float rad = 11 + (float) Math.sin(t * 0.03f + i) * 2;
                    x = (float) Math.cos(w) * rad;
                    z = (float) Math.sin(w) * rad;
                    y = -6 + (float) Math.sin(t * 0.06f + i * 1.9f) * 7 + r1 * 6 - 3;
                    drehY = -w * 57.3f;
                    fluegel = 0.25f + 0.75f * Math.abs((float) Math.sin(t * 0.55f + i));
                }
                case "twin" -> {
                    float w = (i == 0 ? 1 : -1) * t * 0.07f + i * 3.1416f;
                    float rad = 12;
                    x = (float) Math.cos(w) * rad;
                    z = (float) Math.sin(w) * rad;
                    y = -6 + (float) Math.sin(t * 0.05f + i * 3.1416f) * 5;
                    drehY = -w * 57.3f - 90;
                }
                case "rings" -> {
                    float ph = ((t / 70f) + i / (float) n) % 1f;
                    x = 0; z = 0;
                    y = -25 + ph * 34;
                    drehX = 90;
                    groesse = rampe(ph) * (0.8f + 0.25f * (float) Math.sin(ph * 3.1416f));
                }
                case "twinkle", "flicker" -> {
                    float dauer = au.bewegung().equals("flicker") ? 22 + r1 * 10 : 50 + r1 * 30;
                    float zeit = t / dauer + r2;
                    int runde = (int) Math.floor(zeit);
                    float ph = zeit - runde;
                    float q1 = zufall(seed * 3 + i * 41 + runde * 101), q2 = zufall(seed * 7 + i * 43 + runde * 103), q3 = zufall(seed * 11 + i * 47 + runde * 107);
                    float w = q1 * 6.283f, rad = 8 + q2 * 6;
                    x = (float) Math.cos(w) * rad;
                    z = (float) Math.sin(w) * rad;
                    y = -22 + q3 * 30;
                    drehY = -w * 57.3f + 90;
                    if (au.bewegung().equals("flicker")) {
                        groesse = ph < 0.35f ? 1f : 0f;
                        drehZ = (q1 - 0.5f) * 40;
                    } else {
                        groesse = (float) Math.sin(ph * 3.1416f);
                        drehZ = t * 2f;
                    }
                }
                default -> {   // orbit
                    float w = t * 0.035f + i * 6.283f / n;
                    float rad = 10.5f + r1 * 2;
                    x = (float) Math.cos(w) * rad;
                    z = (float) Math.sin(w) * rad;
                    y = -10 + (float) Math.sin(t * 0.045f + i * 2.1f) * 8 + (r2 - 0.5f) * 6;
                    drehY = -w * 57.3f + 90;
                    drehZ = (float) Math.sin(t * 0.08f + i) * 15 * au.drehen();
                }
            }
            if (groesse <= 0.02f) continue;
            float s = au.px() * au.groesse() * groesse;
            pose.pushPose();
            pose.translate(x, y, z);
            pose.mulPose(Axis.YP.rotationDegrees(drehY));
            if (drehX != 0) pose.mulPose(Axis.XP.rotationDegrees(drehX));
            if (drehZ != 0) pose.mulPose(Axis.ZP.rotationDegrees(drehZ));
            pose.scale(s * fluegel, s, s);
            zeichne(pose, c, Zubehoer.netz(sp), licht, t);
            pose.popPose();
        }
    }

    /** Ein- und Ausblenden am Anfang und Ende der Bahn (ueber die Groesse). */
    private static float rampe(float ph) {
        float a = Math.min(1f, ph / 0.12f), b = Math.min(1f, (1 - ph) / 0.12f);
        return Math.min(a, b);
    }

    private static float zufall(int s) {
        int x = s * 0x45d9f3b;
        x = ((x >>> 16) ^ x) * 0x45d9f3b;
        x = (x >>> 16) ^ x;
        return (x & 0xFFFF) / 65535f;
    }

    // ------------------------------------------------------------------
    // Zeichnen
    // ------------------------------------------------------------------

    private static void zeichne(PoseStack pose, SubmitNodeCollector c, Netz netz, int licht, float t) {
        boolean leuchtet = false;
        for (Netz.Flaeche f : netz.flaechen) if (f.leuchten > 0.3f) { leuchtet = true; break; }
        c.submitCustomGeometry(pose, RenderTypes.entitySolid(WEISS), (p, vc) -> {
            for (Netz.Flaeche f : netz.flaechen) flaeche(p, vc, f, f.leuchten > 0.3f ? HELL : licht, f.farbe);
        });
        if (leuchtet) {
            c.submitCustomGeometry(pose, RenderTypes.eyes(WEISS), (p, vc) -> {
                for (Netz.Flaeche f : netz.flaechen) {
                    if (f.leuchten <= 0.3f) continue;
                    int[] k = new int[4];
                    for (int i = 0; i < 4; i++) k[i] = (Math.round(f.leuchten * 0.7f * 255) << 24) | (f.farbe[i] & 0xFFFFFF);
                    flaeche(p, vc, f, HELL, k);
                }
            });
        }
    }

    private static void flaeche(PoseStack.Pose p, VertexConsumer vc, Netz.Flaeche f, int licht, int[] farbe) {
        for (int i : REIHE) {
            vc.addVertex(p, f.p[i * 3], f.p[i * 3 + 1], f.p[i * 3 + 2]).setColor(farbe[i] | (farbe == f.farbe ? 0xFF000000 : 0)).setUv(0.5f, 0.5f)
                    .setOverlay(OverlayTexture.NO_OVERLAY).setLight(licht).setNormal(p, f.n[i * 3], f.n[i * 3 + 1], f.n[i * 3 + 2]);
        }
    }
}
