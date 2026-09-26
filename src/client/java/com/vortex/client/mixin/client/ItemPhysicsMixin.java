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

/**
 * Item Physics (liegende Gegenstaende, siehe hud/ItemPhysics) und Item Size
 * (Groesse, siehe hud/ItemSize) -- beide an derselben Stelle.
 *
 * Item Size skaliert die Zeichenmatrix um den Standpunkt des Gegenstands.
 * Push und Pop liegen beide hier: zeichnet Item Physics selbst und bricht ab,
 * wird sofort zurueckgesetzt; sonst am Ende der Methode (RETURN). So bleibt
 * die Matrix immer im Gleichgewicht.
 */
@Mixin(ItemEntityRenderer.class)
public abstract class ItemPhysicsMixin {

    @org.spongepowered.asm.mixin.Unique
    private boolean vortex$skaliert = false;

    @Inject(method = "submit(Lnet/minecraft/client/renderer/entity/state/ItemEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void vortex$liegen(ItemEntityRenderState state, PoseStack ps, SubmitNodeCollector col,
                              CameraRenderState cam, CallbackInfo ci) {
        vortex$skaliert = false;
        try {
            float f = com.vortex.client.hud.ItemSize.faktor(state);
            if (f != 1f) {
                ps.pushPose();
                ps.scale(f, f, f);
                vortex$skaliert = true;
            }
            if (com.vortex.client.hud.ItemPhysics.zeichne(state, ps, col)) {
                if (vortex$skaliert) {
                    ps.popPose();
                    vortex$skaliert = false;
                }
                ci.cancel();
            }
        } catch (Throwable pvpErr) {
            com.vortex.client.core.Errors.report("ItemPhysicsMixin", pvpErr);
        }
    }

    @Inject(method = "submit(Lnet/minecraft/client/renderer/entity/state/ItemEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            at = @At("RETURN"), require = 0)
    private void vortex$ende(ItemEntityRenderState state, PoseStack ps, SubmitNodeCollector col,
                            CameraRenderState cam, CallbackInfo ci) {
        if (vortex$skaliert) {
            ps.popPose();
            vortex$skaliert = false;
        }
    }
}
