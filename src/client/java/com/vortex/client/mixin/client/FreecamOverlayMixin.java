package com.vortex.client.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Freecam: die Bildschirm-Overlays des SPIELERS weglassen.
 *
 * Kuerbiskopf, Pulverschnee, Vignette, Portal-Schwindel, Fernrohr: alles
 * gehoert zum Koerper, nicht zur Kamera -- verdeckte aber in der Freecam
 * grosse Teile des Bildes. In 26.2 liegt die Methode in Hud, davor in Gui;
 * beide Ziele, require = 0. Der Handler nimmt nur CallbackInfo, damit ein
 * geaenderter Parametersatz nicht zum Absturz fuehrt.
 */
@Mixin({net.minecraft.client.gui.Gui.class, net.minecraft.client.gui.Hud.class})
public abstract class FreecamOverlayMixin {

    @Inject(method = "extractCameraOverlays", at = @At("HEAD"), cancellable = true, require = 0)
    private void vortex$freecamOhneOverlays(CallbackInfo ci) {
        if (com.vortex.client.freecam.Freecam.ohneOverlays()) ci.cancel();
    }
}
