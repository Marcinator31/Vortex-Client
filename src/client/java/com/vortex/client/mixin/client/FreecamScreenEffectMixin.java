package com.vortex.client.mixin.client;

import net.minecraft.client.renderer.ScreenEffectRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Freecam: Feuer-, Wasser- und "Kopf im Block"-Bild des Spielers weglassen.
 *
 * Brannte der Spieler, stand er im Wasser oder steckte sein Kopf in einem
 * Block, lag dieses Bild auch in der Freecam ueber dem halben Bildschirm --
 * obwohl die Kamera ganz woanders war.
 */
@Mixin(ScreenEffectRenderer.class)
public abstract class FreecamScreenEffectMixin {

    @Inject(method = "renderScreenEffect", at = @At("HEAD"), cancellable = true, require = 0)
    private void vortex$freecamOhneEffekte(CallbackInfo ci) {
        if (com.vortex.client.freecam.Freecam.ohneOverlays()) ci.cancel();
    }
}
