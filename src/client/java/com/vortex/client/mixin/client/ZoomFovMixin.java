package com.vortex.client.mixin.client;

import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Verändert den FOV-Wert, aus dem Minecraft 26.x die sichtbare
 * Welt-Projektionsmatrix erzeugt. Die öffentliche getFov()-Methode liefert
 * lediglich den später gespeicherten Wert und beeinflusst die Perspektive
 * selbst nicht mehr.
 */
@Mixin(Camera.class)
public abstract class ZoomFovMixin {

    @Inject(method = "calculateFov", at = @At("RETURN"), cancellable = true)
    private void vortex$applyZoom(float partialTick, CallbackInfoReturnable<Float> cir) {
        try {
            com.vortex.client.hud.Zoom.update();
            double factor = com.vortex.client.hud.Zoom.factor();
            if (factor <= 1.001) return;

            cir.setReturnValue((float) (cir.getReturnValueF() / factor));
        } catch (Throwable error) {
            com.vortex.client.core.Errors.report("ZoomFovMixin", error);
        }
    }
}
