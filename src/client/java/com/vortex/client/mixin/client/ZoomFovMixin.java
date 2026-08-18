package com.vortex.client.mixin.client;

import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Applies the zoom to the field of view.
 *
 * The game works out the angle of view every frame; this divides the result.
 * Nothing else is touched -- no camera position, no rendering distance -- so
 * the picture stays exactly what it was, only narrower.
 *
 * The smoothing sits in Zoom, not here: this method runs per frame and must
 * stay cheap, and the movement is easier to reason about in one place.
 */
@Mixin(GameRenderer.class)
public abstract class ZoomFovMixin {

    @Inject(method = "method_3196", at = @At("RETURN"), cancellable = true, require = 0)
    private void vortex$applyZoom(net.minecraft.client.Camera camera,
                                  float tickProgress, boolean changingFov,
                                  CallbackInfoReturnable<Float> cir) {
        try {
            // Advance the movement once per frame, right where the value is
            // needed -- that keeps it in step with what is on screen.
            com.vortex.client.hud.Zoom.update();

            double factor = com.vortex.client.hud.Zoom.factor();
            if (factor <= 1.001) return;

            float fov = cir.getReturnValueF();
            cir.setReturnValue((float) (fov / factor));
        } catch (Throwable pvpErr) {
            com.vortex.client.core.Errors.report("ZoomFovMixin", pvpErr);
        }
    }
}
