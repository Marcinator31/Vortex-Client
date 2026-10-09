package com.vortex.client.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.vortex.client.cosmetics.SchildSkins;
import net.minecraft.client.model.object.equipment.ShieldModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.special.ShieldSpecialRenderer;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Unit;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Shield Skins: markierte Schilde mit der Vortex-Textur zeichnen (siehe SchildSkins). */
@Mixin(ShieldSpecialRenderer.class)
public abstract class SchildSkinMixin {
    @Shadow @Final private ShieldModel model;

    @Inject(method = "extractArgument(Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/core/component/DataComponentMap;", at = @At("RETURN"), cancellable = true)
    private void vortex$markieren(ItemStack stack, CallbackInfoReturnable<DataComponentMap> cir) {
        try {
            DataComponentMap neu = SchildSkins.markieren(cir.getReturnValue());
            if (neu != cir.getReturnValue()) cir.setReturnValue(neu);
        } catch (Throwable t) {
            com.vortex.client.core.Errors.report("SchildSkin.markieren", t);
        }
    }

    @Inject(method = "submit(Lnet/minecraft/core/component/DataComponentMap;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;IIZI)V", at = @At("HEAD"), cancellable = true)
    private void vortex$zeichnen(DataComponentMap daten, PoseStack pose, SubmitNodeCollector c, int licht, int overlay, boolean glanz, int umriss, CallbackInfo ci) {
        try {
            Identifier tex = SchildSkins.textur(daten);
            if (tex == null) return;
            c.submitModel(this.model, Unit.INSTANCE, pose, tex, licht, overlay, umriss, null);
            ci.cancel();
        } catch (Throwable t) {
            com.vortex.client.core.Errors.report("SchildSkin.zeichnen", t);
        }
    }
}
