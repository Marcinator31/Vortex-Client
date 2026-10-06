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
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import org.joml.Quaternionf;

/**
 * Realistischeres Cape: statt einer starren Platte (Vanilla) besteht es aus
 * {@link #STREIFEN} Streifen, die sich nacheinander biegen -- wie Stoff.
 *
 *   - Oben haengt es wie das Vanilla-Cape (gleicher Winkel aus capeLean/
 *     capeFlap, gleicher Ansatz am Ruecken, gleiche Textur-Aufteilung).
 *   - Nach unten biegt es sich staerker vom Koerper weg.
 *   - Eine Welle laeuft von oben nach unten durch -- beim Laufen staerker,
 *     im Stehen nur ein leichtes Wehen.
 *
 * Gilt fuer jedes Cape (Mojang, Vortex, eigenes Bild) bei jedem Spieler. Das
 * Vanilla-Cape wird dann ueber Fabric (ALLOW_CAPE_RENDER) ausgeschaltet; mit
 * "Cape physics: Off" im Cosmetics-Menue bleibt alles wie bei Minecraft.
 */
public class CapeEbene extends RenderLayer<AvatarRenderState, PlayerModel> {
    static final int STREIFEN = 8;
    private static final float GRAD = (float) Math.PI / 180f;

    public CapeEbene(RenderLayerParent<AvatarRenderState, PlayerModel> parent) {
        super(parent);
    }

    /** Dieselben Bedingungen wie beim Vanilla-Cape: sichtbar, Cape vorhanden, keine Fluegel. */
    static boolean zeigt(AvatarRenderState s) {
        if (s.isInvisible || !s.showCape || s.skin == null || s.skin.cape() == null) return false;
        return s.chestEquipment == null || !s.chestEquipment.has(DataComponents.GLIDER);
    }

    private static boolean brustplatte(ItemStack brust) {
        if (brust == null || brust.isEmpty() || brust.has(DataComponents.GLIDER)) return false;
        var e = brust.get(DataComponents.EQUIPPABLE);
        return e != null && e.slot() == EquipmentSlot.CHEST && e.assetId().isPresent();
    }

    @Override
    public void submit(PoseStack pose, SubmitNodeCollector collector, int licht, AvatarRenderState s, float yRot, float xRot) {
        try {
            if (!Cosmetics.capePhysik() || !zeigt(s)) return;
            Identifier tex = s.skin.cape().texturePath();
            Identifier glow = AnimCapes.glowZu(tex);
            float zeit = s.ageInTicks;
            float tempo = Math.min(1f, s.walkAnimationSpeed);
            // Vanilla-Winkel oben (Grad): Grundneigung + Lean/Flap aus der Bewegung
            float oben = 6f + s.capeLean / 2f + s.capeFlap;
            float seite = s.capeLean2 / 2f;

            pose.pushPose();
            if (brustplatte(s.chestEquipment)) pose.translate(0f, -0.053125f, 0.06875f);
            getParentModel().body.translateAndRotate(pose);
            pose.translate(0f, 0f, 2f / 16f);                                  // Ansatz am Ruecken
            // Wie PlayerCapeModel.setupAnim: X(Winkel), Z(Lean2/2), dann Y(180 - Lean2/2).
            // Die Biegung haengt im Rahmen VOR der Y-Drehung (dort heisst "positiv"
            // wie bei Vanilla: vom Koerper weg); jeder Streifen dreht sich dann selbst um Y.
            pose.mulPose(new Quaternionf().rotationX(oben * GRAD).rotateZ(seite * GRAD));
            final float drehY = (float) Math.PI - seite * GRAD;

            float h = 16f / STREIFEN;
            float biegung = Math.min(40f, 7f + oben * 0.6f);                  // wie stark es sich nach unten wegbiegt
            float welle = 1.6f + tempo * 9f, schnell = 0.12f + tempo * 0.3f;
            float vorher = 0f;
            for (int k = 0; k < STREIFEN; k++) {
                float anteil = (k + 1f) / STREIFEN;
                float ziel = biegung * anteil * anteil + (float) Math.sin(zeit * schnell - k * 0.75f) * welle * anteil;
                pose.mulPose(new Quaternionf().rotationX((ziel - vorher) * GRAD));
                vorher = ziel;
                final int nr = k;
                pose.pushPose();
                pose.mulPose(new Quaternionf().rotationY(drehY));
                collector.submitCustomGeometry(pose, RenderTypes.entitySolid(tex), (p, vc) -> streifen(p, vc, nr, h, licht));
                // Animierte Capes: leuchtende Ebene darueber (Funken, Glanz, Sterne -- auch nachts hell)
                if (glow != null) collector.submitCustomGeometry(pose, RenderTypes.eyes(glow), (p, vc) -> streifen(p, vc, nr, h, 0xF000F0));
                pose.popPose();
                pose.translate(0f, h / 16f, 0f);
            }
            pose.popPose();
        } catch (Throwable t) {
            com.vortex.client.core.Errors.report("CapeEbene", t);
        }
    }

    /**
     * Ein Streifen (10 x h x 1 Pixel) mit dem passenden Ausschnitt der
     * Cape-Textur -- Aufteilung wie beim Vanilla-Cape (64 x 32):
     * vorne x 1-11, hinten x 12-22, Seiten x 0-1 und 11-12, jeweils ab y 1;
     * oben x 1-11, unten x 11-21 in der Zeile y 0-1.
     */
    private static void streifen(PoseStack.Pose p, VertexConsumer vc, int nr, float h, int licht) {
        float x0 = -5 / 16f, x1 = 5 / 16f, y0 = 0f, y1 = h / 16f, z0 = -1 / 16f, z1 = 0f;
        float v0 = 1 + nr * h, v1 = v0 + h;
        // vorne (-z) und hinten (+z)
        // (u laeuft hier von rechts nach links -- verglichen mit dem Vanilla-Cape im Test, sonst waere das Bild gespiegelt)
        quad(p, vc, licht, 0, 0, -1, x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0, 11, v0, 1, v1);
        quad(p, vc, licht, 0, 0, 1, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, 22, v0, 12, v1);
        // Seiten
        quad(p, vc, licht, -1, 0, 0, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, 0, v0, 1, v1);
        quad(p, vc, licht, 1, 0, 0, x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1, 11, v0, 12, v1);
        if (nr == 0) quad(p, vc, licht, 0, -1, 0, x1, y0, z1, x0, y0, z1, x0, y0, z0, x1, y0, z0, 1, 0, 11, 1);
        if (nr == STREIFEN - 1) quad(p, vc, licht, 0, 1, 0, x1, y1, z0, x0, y1, z0, x0, y1, z1, x1, y1, z1, 11, 0, 21, 1);
    }

    /** Vier Ecken a-b-c-d; Textur u0..u1 / v0..v1 (in 64x32-Pixeln) von a nach c. */
    private static void quad(PoseStack.Pose p, VertexConsumer vc, int licht, float nx, float ny, float nz,
                             float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz,
                             float u0, float v0, float u1, float v1) {
        float a = u0 / 64f, b = u1 / 64f, c = v0 / 32f, d = v1 / 32f;
        ecke(p, vc, ax, ay, az, a, c, licht, nx, ny, nz);
        ecke(p, vc, bx, by, bz, b, c, licht, nx, ny, nz);
        ecke(p, vc, cx, cy, cz, b, d, licht, nx, ny, nz);
        ecke(p, vc, dx, dy, dz, a, d, licht, nx, ny, nz);
    }

    private static void ecke(PoseStack.Pose p, VertexConsumer vc, float x, float y, float z, float u, float v, int licht, float nx, float ny, float nz) {
        vc.addVertex(p, x, y, z).setColor(0xFFFFFFFF).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(licht).setNormal(p, nx, ny, nz);
    }
}
