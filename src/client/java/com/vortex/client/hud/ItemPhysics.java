package com.vortex.client.hud;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.vortex.client.module.ModuleManager;
import com.vortex.client.module.modules.ItemPhysicsModule;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import org.joml.Vector3fc;

/**
 * Liegende Gegenstaende (Modul Item Physics).
 *
 * Minecraft laesst fallengelassene Gegenstaende schweben und kreisen. Hier
 * uebernehmen wir das Zeichnen: kein Auf und Ab, kein Kreisen. Flache
 * Gegenstaende werden um 90 Grad gekippt und liegen, Bloecke stehen.
 *
 * FLACH ODER NICHT wird am Modell selbst gemessen: ist es in einer Richtung
 * hoechstens ein Pixel duenn, ist es flach. Das Ergebnis wird je Gegenstand
 * gemerkt -- gemessen wird also einmal, nicht in jedem Bild.
 *
 * ROLLEN: Die Ausrichtung haengt von der Position ab. Rutscht ein Gegenstand,
 * dreht er sich mit; liegt er still, bleibt er so liegen.
 *
 * Klappt irgendetwas nicht (fremde Mod, unbekanntes Modell), wird der
 * Gegenstand ganz normal von Minecraft gezeichnet.
 */
public final class ItemPhysics {

    private ItemPhysics() {}

    private static final float PIXEL = 1f / 16f;

    /** Renderzustand -> Gegenstand. Beides lebt nur ein Bild lang. */
    private static final Map<EntityRenderState, ItemEntity> ZUORDNUNG = new WeakHashMap<>();
    private static final Map<Item, Boolean> FLACH = new HashMap<>();

    public static boolean aktiv() {
        ItemPhysicsModule m = ModuleManager.INSTANCE.get(ItemPhysicsModule.class);
        return m != null && m.isEnabled();
    }

    /** Vom Mixin: welcher Gegenstand zu welchem Renderzustand gehoert. */
    public static void merke(Entity e, EntityRenderState state) {
        if (e instanceof ItemEntity ie && state != null && aktiv()) ZUORDNUNG.put(state, ie);
    }

    /**
     * Zeichnet den Gegenstand selbst.
     *
     * @return true, wenn gezeichnet wurde -- dann laesst Minecraft es aus
     */
    public static boolean zeichne(ItemEntityRenderState state, PoseStack ps, SubmitNodeCollector col) {
        ItemPhysicsModule m = ModuleManager.INSTANCE.get(ItemPhysicsModule.class);
        if (m == null || !m.isEnabled()) return false;
        if (state.item.isEmpty()) return false;
        ItemEntity e = ZUORDNUNG.get(state);
        if (e == null) return false;

        boolean flach = istFlach(e, state.item);
        java.util.Random zufall = new java.util.Random(e.getId() * 89748956L + 7L);

        float drehung = m.randomRotation.get() ? (zufall.nextFloat() * 360f) : 0f;
        if (m.rolling.get()) {
            // Mitrollen: die zurueckgelegte Strecke dreht den Gegenstand.
            drehung += (float) ((e.getX() + e.getZ()) * 90.0);
        }

        ps.pushPose();
        try {
            if (e.isInWater()) ps.translate(0f, 0.3f, 0f);
            if (flach) {
                ps.translate(0f, 0.02f, 0f);
                ps.mulPose(Axis.XP.rotationDegrees(90f));
                ps.mulPose(Axis.ZP.rotationDegrees(drehung));
            } else {
                // Das Bodenmodell von Bloecken schwebt einen Pixel -- abziehen.
                ps.translate(0f, -PIXEL, 0f);
                ps.mulPose(Axis.YP.rotationDegrees(drehung));
            }

            int anzahl = Math.max(1, state.count);
            for (int i = 0; i < anzahl; i++) {
                ps.pushPose();
                if (i > 0) {
                    float dx = (zufall.nextFloat() * 2f - 1f) * 0.15f;
                    float dz = (zufall.nextFloat() * 2f - 1f) * 0.15f;
                    // Gestapelt: jede Kopie liegt etwas hoeher auf der vorigen
                    if (flach) ps.translate(dx, dz, -i * PIXEL * 0.6f);
                    else ps.translate(dx, i * PIXEL, dz);
                }
                state.item.submit(ps, col, state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
                ps.popPose();
            }
        } finally {
            ps.popPose();
        }
        return true;
    }

    private static boolean istFlach(ItemEntity e, ItemStackRenderState item) {
        Item art = e.getItem().getItem();
        Boolean gemerkt = FLACH.get(art);
        if (gemerkt != null) return gemerkt;
        boolean flach;
        try {
            flach = misst(item);
        } catch (Throwable t) {
            // Messen nicht moeglich: Bloecke stehen, alles andere liegt.
            flach = !(art instanceof BlockItem);
        }
        FLACH.put(art, flach);
        return flach;
    }

    /** Ist das Modell in einer Richtung hoechstens einen Pixel duenn? */
    private static boolean misst(ItemStackRenderState item) {
        var zugriff = (com.vortex.client.mixin.client.ItemStackRenderStateAccessor) item;
        int n = zugriff.vortex$getActiveLayerCount();
        ItemStackRenderState.LayerRenderState[] ebenen = zugriff.vortex$getLayers();
        float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE;
        float minY = Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        float minZ = Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
        boolean etwas = false;
        for (int i = 0; i < n && i < ebenen.length; i++) {
            List<BakedQuad> quads = ebenen[i].prepareQuadList();
            for (BakedQuad q : quads) {
                for (int k = 0; k < 4; k++) {
                    Vector3fc v = q.position(k);
                    minX = Math.min(minX, v.x()); maxX = Math.max(maxX, v.x());
                    minY = Math.min(minY, v.y()); maxY = Math.max(maxY, v.y());
                    minZ = Math.min(minZ, v.z()); maxZ = Math.max(maxZ, v.z());
                    etwas = true;
                }
            }
        }
        if (!etwas) throw new IllegalStateException("leeres Modell");
        return (maxX - minX) > PIXEL && (maxY - minY) > PIXEL && (maxZ - minZ) <= PIXEL + 0.001f;
    }
}
