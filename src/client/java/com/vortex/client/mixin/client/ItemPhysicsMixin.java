package com.vortex.client.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Item Physics: zeichnet liegende Gegenstaende selbst (siehe hud/ItemPhysics). */
@Mixin(ItemEntityRenderer.class)
public abstract class ItemPhysicsMixin {

    @Inject(method = "submit(Lnet/minecraft/client/renderer/entity/state/ItemEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void vortex$liegen(ItemEntityRenderState state, PoseStack ps, SubmitNodeCollector col,
                              CameraRenderState cam, CallbackInfo ci) {
        try {
            if (com.vortex.client.hud.ItemPhysics.zeichne(state, ps, col)) ci.cancel();
        } catch (Throwable pvpErr) {
            com.vortex.client.core.Errors.report("ItemPhysicsMixin", pvpErr);
        }
    }
}
