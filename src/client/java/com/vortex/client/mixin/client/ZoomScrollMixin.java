package com.vortex.client.mixin.client;

import net.minecraft.client.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lets the wheel set the zoom, and keeps it off the hotbar while it does.
 *
 * Cancelling the original call is the point: reaching for the wheel to adjust
 * your view and coming out of it holding a different item is exactly the kind
 * of surprise that costs a fight. While the zoom key is held the wheel belongs
 * to the zoom and to nothing else.
 */
@Mixin(Mouse.class)
public abstract class ZoomScrollMixin {

    @Inject(method = "method_1598", at = @At("HEAD"), cancellable = true, require = 0)
    private void vortex$zoomScroll(long window, double horizontal, double vertical,
                                   CallbackInfo ci) {
        try {
            if (com.vortex.client.hud.Zoom.onScroll(vertical)) {
                ci.cancel();
            }
        } catch (Throwable pvpErr) {
            com.vortex.client.core.Errors.report("ZoomScrollMixin", pvpErr);
        }
    }
}
