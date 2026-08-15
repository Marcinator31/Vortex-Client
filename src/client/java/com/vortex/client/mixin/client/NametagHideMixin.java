package com.vortex.client.mixin.client;

import net.minecraft.client.render.entity.EntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hides the vanilla name tag while our own is drawn.
 *
 * Without this both would be visible, one on top of the other -- which looks
 * exactly like a rendering fault. Only players are hidden; everything else
 * keeps its usual tag.
 */
@Mixin(EntityRenderer.class)
public abstract class NametagHideMixin {

    @Inject(method = "method_3926", at = @At("HEAD"), cancellable = true, require = 0)
    private void vortex$hideVanillaNametag(Object state, Object matrices,
                                           Object a, Object b, CallbackInfo ci) {
        try {
            var mod = com.vortex.client.module.ModuleManager.INSTANCE.get(
                    com.vortex.client.module.modules.NametagModule.class);
            if (mod == null || !mod.isEnabled()) return;

            // Only for players -- our own tag covers those, and only those.
            if (state instanceof net.minecraft.client.render.entity.state.PlayerEntityRenderState) {
                ci.cancel();
            }
        } catch (Throwable pvpErr) {
            com.vortex.client.core.Errors.report("NametagHideMixin", pvpErr);
        }
    }
}
