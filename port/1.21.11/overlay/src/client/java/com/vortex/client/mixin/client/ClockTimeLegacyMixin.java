package com.vortex.client.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * NUR 1.21.11 (Overlay aus port/1.21.11): Time Changer.
 *
 * In 26.x liest der Himmel die Uhr ueber ClientClockManager (ClockTimeMixin).
 * In 1.21.11 gibt es das noch nicht -- dort steht die Tageszeit in
 * ClientLevel.ClientLevelData.getDayTime(). Nur lokal: der Wert wird beim
 * Lesen ersetzt, am Spielstand aendert sich nichts.
 */
@Mixin(targets = "net.minecraft.client.multiplayer.ClientLevel$ClientLevelData")
public abstract class ClockTimeLegacyMixin {

    @Inject(method = "getDayTime()J", at = @At("RETURN"), cancellable = true, require = 0)
    private void vortex$uhrzeit(CallbackInfoReturnable<Long> cir) {
        try {
            long wunsch = com.vortex.client.hud.TimeWeather.uhrzeit();
            if (wunsch < 0) return;
            long original = cir.getReturnValue();
            cir.setReturnValue(Math.floorDiv(original, 24000L) * 24000L + wunsch);
        } catch (Throwable pvpErr) {
            com.vortex.client.core.Errors.report("ClockTimeLegacyMixin", pvpErr);
        }
    }
}
