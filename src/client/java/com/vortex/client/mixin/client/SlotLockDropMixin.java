package com.vortex.client.mixin.client;

import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Slot-Sperre: Q in der Hotbar auf einem gesperrten Platz tut nichts. */
@Mixin(LocalPlayer.class)
public abstract class SlotLockDropMixin {

    @Inject(method = "drop(Z)Z", at = @At("HEAD"), cancellable = true, require = 0)
    private void vortex$nichtWegwerfen(boolean alles, CallbackInfoReturnable<Boolean> cir) {
        try {
            if (com.vortex.client.hud.SlotLock.wegwerfenGesperrt((LocalPlayer) (Object) this)) {
                cir.setReturnValue(false);
            }
        } catch (Throwable pvpErr) {
            com.vortex.client.core.Errors.report("SlotLockDropMixin", pvpErr);
        }
    }
}
