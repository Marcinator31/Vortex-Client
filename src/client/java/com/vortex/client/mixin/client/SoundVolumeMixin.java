package com.vortex.client.mixin.client;

import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Sound Control: jede Lautstaerke, die das Spiel fuer ein Geraeusch
 * ausrechnet, mit dem eingestellten Faktor multiplizieren. Gilt beim Start
 * UND bei jeder Nachfuehrung (z.B. Regen, Minecart), weil beides ueber diese
 * Methode laeuft. Bei 0 startet das Spiel das Geraeusch gar nicht erst.
 */
@Mixin(SoundEngine.class)
public abstract class SoundVolumeMixin {

    @Inject(method = "calculateVolume(Lnet/minecraft/client/resources/sounds/SoundInstance;)F",
            at = @At("RETURN"), cancellable = true, require = 0)
    private void vortex$soundVolume(SoundInstance sound, CallbackInfoReturnable<Float> cir) {
        try {
            float f = com.vortex.client.hud.SoundControl.faktor(sound);
            if (f != 1f) cir.setReturnValue(Math.max(0f, Math.min(1f, cir.getReturnValue() * f)));
        } catch (Throwable ignored) {
        }
    }
}
