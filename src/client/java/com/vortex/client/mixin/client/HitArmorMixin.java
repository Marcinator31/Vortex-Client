package com.vortex.client.mixin.client;

import com.vortex.client.module.modules.HitColorModule;
import net.minecraft.client.renderer.entity.layers.EquipmentLayerRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

/**
 * Hit Color "Include Armor": Ruestung beim Treffer mit einfaerben.
 *
 * Warum nicht einfach das Treffer-Overlay auf die Ruestung legen? Weil die
 * Ruestungs-Shader es nicht koennen: im Minecraft-Code (26.1.2, RenderPipelines)
 * sind ARMOR_CUTOUT_NO_CULL, ARMOR_DECAL_CUTOUT_NO_CULL und ARMOR_TRANSLUCENT
 * mit "NO_OVERLAY" gebaut, und EquipmentLayerRenderer uebergibt ausserdem fest
 * OverlayTexture.NO_OVERLAY.
 *
 * Stattdessen wird die Toenungsfarbe veraendert, die jeder Ruestungsteil
 * ohnehin bekommt (weiss = unveraendert, sonst z. B. die Farbe gefaerbter
 * Lederruestung). Waehrend das Wesen rot aufleuchtet (hasRedOverlay im
 * Zeichenzustand), wird sie mit der Trefferfarbe multipliziert.
 *
 * Eingriff ueber ModifyArgs am submitModel-Aufruf: der Zustand ist dort
 * selbst ein Argument (Nr. 1), die Farbe Nr. 6. Keine Handler-Parameter, die
 * zur Methode passen muessten -- passt der Aufruf in einer Version nicht,
 * findet Mixin ihn schlicht nicht (require = 0), und es bleibt Vanilla.
 */
@Mixin(EquipmentLayerRenderer.class)
public abstract class HitArmorMixin {

    @ModifyArgs(method = "renderLayers(Lnet/minecraft/client/resources/model/EquipmentClientInfo$LayerType;Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lnet/minecraft/world/item/ItemStack;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/resources/Identifier;II)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/OrderedSubmitNodeCollector;submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;IIILnet/minecraft/client/renderer/texture/TextureAtlasSprite;ILnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;)V"),
            require = 0)
    private void vortex$trefferRuestung(Args args) {
        try {
            Object zustand = args.get(1);
            if (!(zustand instanceof LivingEntityRenderState ls) || !ls.hasRedOverlay) return;
            int toenung = HitColorModule.ruestungsToenung();
            if (toenung == -1) return;
            int alt = args.get(6);
            args.set(6, multipliziere(alt, toenung));
        } catch (Throwable ignored) {
        }
    }

    private static int multipliziere(int a, int b) {
        int aa = (a >>> 24) & 0xFF, ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        return (aa << 24) | ((ar * br / 255) << 16) | ((ag * bg / 255) << 8) | (ab * bb / 255);
    }
}
