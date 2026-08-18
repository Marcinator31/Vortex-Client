package com.vortex.client.mixin.client;

import com.vortex.client.freecam.Freecam;
import net.minecraft.client.player.Input;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Neutralisiert die Bewegungs-Eingabe des Spielers, solange die Freecam aktiv
 * ist. Dabei bleiben Geschwindigkeit und Physik unberührt.
 */
@Mixin(Input.class)
public abstract class KeyboardInputMixin {

    @Inject(method = "tick", at = @At("TAIL"))
    private void pvpclient$blockInputInFreecam(CallbackInfo ci) {
        if (!Freecam.isActive()) return;
        Input input = (Input) (Object) this;
        input.leftImpulse = 0.0F;
        input.forwardImpulse = 0.0F;
        input.up = false;
        input.down = false;
        input.left = false;
        input.right = false;
        input.jumping = false;
        input.shiftKeyDown = false;
    }
}
