package com.vortex.client.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.vortex.client.cosmetics.SchildSkins;
import net.minecraft.client.model.object.equipment.ShieldModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.special.ShieldSpecialRenderer;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Unit;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Shield Skins und Shield Status (siehe SchildSkins, SchildStatus):
 * markierte Schilde mit der Vortex-Textur zeichnen, und die Status-Farbe
 * (gruen/gelb/rot) ueber Skin oder Vanilla-Schild legen.
 */
@Mixin(ShieldSpecialRenderer.class)
public abstract class SchildSkinMixin {
    @Shadow @Final private ShieldModel model;

    /** Status-Farbe fuer den Vanilla-Schild, der gerade gezeichnet wird (-1 = keine). Nur Render-Thread. */
    @Unique private int vortex$farbe = -1;

    @Inject(method = "extractArgument(Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/core/component/DataComponentMap;", at = @At("RETURN"), cancellable = true)
    private void vortex$markieren(ItemStack stack, CallbackInfoReturnable<DataComponentMap> cir) {
        try {
            DataComponentMap neu = SchildSkins.markieren(cir.getReturnValue(), stack);
            if (neu != cir.getReturnValue()) cir.setReturnValue(neu);
        } catch (Throwable t) {
            com.vortex.client.core.Errors.report("SchildSkin.markieren", t);
        }
    }

    //#if 26
    @Inject(method = "submit(Lnet/minecraft/core/component/DataComponentMap;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;IIZI)V", at = @At("HEAD"), cancellable = true)
    private void vortex$zeichnen(DataComponentMap daten, PoseStack pose, SubmitNodeCollector c, int licht, int overlay, boolean glanz, int umriss, CallbackInfo ci) {
    //#else
    //$ @Inject(method = "submit(Lnet/minecraft/core/component/DataComponentMap;Lnet/minecraft/world/item/ItemDisplayContext;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;IIZI)V", at = @At("HEAD"), cancellable = true)
    //$ private void vortex$zeichnen(DataComponentMap daten, net.minecraft.world.item.ItemDisplayContext kontext, PoseStack pose, SubmitNodeCollector c, int licht, int overlay, boolean glanz, int umriss, CallbackInfo ci) {
    //#endif
        vortex$farbe = -1;
        try {
            int farbe = SchildSkins.farbe(daten);
            Identifier tex = SchildSkins.textur(daten);
            if (tex == null) { vortex$farbe = farbe; return; }   // Vanilla zeichnet, Farbe kommt per ModifyArg dazu
            //#if 26
            c.submitModel(this.model, Unit.INSTANCE, pose, this.model.renderType(tex), licht, overlay, farbe, null, umriss, null);
            if (glanz) c.order(1).submitModel(this.model, Unit.INSTANCE, pose, RenderTypes.entityGlint(), licht, overlay, -1, null, 0, null);
            //#else
            //$ pose.pushPose();
            //$ pose.scale(1.0F, -1.0F, -1.0F);
            //$ c.submitModel(this.model, Unit.INSTANCE, pose, this.model.renderType(tex), licht, overlay, farbe, null, umriss, null);
            //$ if (glanz) c.order(1).submitModel(this.model, Unit.INSTANCE, pose, RenderTypes.entityGlint(), licht, overlay, -1, null, 0, null);
            //$ pose.popPose();
            //#endif
            ci.cancel();
        } catch (Throwable t) {
            com.vortex.client.core.Errors.report("SchildSkin.zeichnen", t);
        }
    }

    //#if 26
    @ModifyArg(method = "submit(Lnet/minecraft/core/component/DataComponentMap;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;IIZI)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lcom/mojang/blaze3d/vertex/PoseStack;IIILnet/minecraft/client/resources/model/sprite/SpriteId;Lnet/minecraft/client/resources/model/sprite/SpriteGetter;ILnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;)V"),
            index = 5, require = 0)
    //#else
    //$ @ModifyArg(method = "submit(Lnet/minecraft/core/component/DataComponentMap;Lnet/minecraft/world/item/ItemDisplayContext;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;IIZI)V",
    //$         at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitModelPart(Lnet/minecraft/client/model/geom/ModelPart;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;IILnet/minecraft/client/renderer/texture/TextureAtlasSprite;ZZILnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;I)V"),
    //$         index = 8, require = 0)
    //#endif
    private int vortex$statusFarbe(int farbe) {
        return vortex$farbe != -1 ? vortex$farbe : farbe;
    }
}
