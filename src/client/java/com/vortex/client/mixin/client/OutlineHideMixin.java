package com.vortex.client.mixin.client;

import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Blendet den schwarzen Vanilla-Rahmen aus, solange Block Outline an ist --
 * sonst saehe man zwei Rahmen uebereinander.
 */
@Mixin(LevelRenderer.class)
public abstract class OutlineHideMixin {

    @Inject(method = "submitBlockOutline", at = @At("HEAD"), cancellable = true, require = 0)
    private void vortex$rahmenAus(CallbackInfo ci) {
        try {
            if (com.vortex.client.hud.BlockOutline.aktiv()) ci.cancel();
        } catch (Throwable pvpErr) {
            com.vortex.client.core.Errors.report("OutlineHideMixin", pvpErr);
        }
    }
}
