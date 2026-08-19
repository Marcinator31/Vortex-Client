package com.vortex.client.mixin.client;

import com.vortex.client.freecam.Freecam;
import net.minecraft.client.player.ClientInput;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Neutralisiert die Bewegungs-Eingabe des Spielers, solange die Freecam aktiv
 * ist. Dabei bleiben Geschwindigkeit und Physik unberührt.
 */
@Mixin(ClientInput.class)
public abstract class KeyboardInputMixin {

    @Inject(method = "tick", at = @At("TAIL"))
    private void pvpclient$blockInputInFreecam(CallbackInfo ci) {
        if (!Freecam.isActive()) return;
        InputAccessor acc = (InputAccessor) this;
        acc.pvpclient$setMovementVector(Vec2.ZERO);
        acc.pvpclient$setPlayerInput(Input.EMPTY);
    }
}
