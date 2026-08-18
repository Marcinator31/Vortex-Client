package com.vortex.client.mixin.client;

import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Wendet den Zoom auf das von der 26.2-Kamera ermittelte Sichtfeld an.
 */
@Mixin(Camera.class)
public abstract class ZoomFovMixin {

    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void vortex$applyZoom(CallbackInfoReturnable<Float> cir) {
        try {
            com.vortex.client.hud.Zoom.update();
            double factor = com.vortex.client.hud.Zoom.factor();
            if (factor <= 1.001) return;

            cir.setReturnValue((float) (cir.getReturnValueF() / factor));
        } catch (Throwable pvpErr) {
            com.vortex.client.core.Errors.report("ZoomFovMixin", pvpErr);
        }
    }
}
