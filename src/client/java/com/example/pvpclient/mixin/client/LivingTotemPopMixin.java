package com.example.pvpclient.mixin.client;

import com.example.pvpclient.hud.TotemPops;
import net.minecraft.entity.EntityStatuses;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Zweiter Einstiegspunkt fuer die Totem-Erkennung.
 *
 * WARUM ZWEI STELLEN:
 * Das Ereignis "Totem verbraucht" wird von LivingEntity selbst behandelt -- die
 * Methode reicht diesen Fall NICHT an die Basisklasse Entity weiter. Ein Mixin,
 * der nur auf Entity sitzt, wird bei Totems deshalb nie ausgeloest. Genau daran
 * lag es, dass der Zaehler leer blieb.
 *
 * Der Mixin auf Entity bleibt trotzdem bestehen: er greift fuer alle Faelle, in
 * denen keine Ueberschreibung dazwischenliegt. Doppelt gezaehlt wird nichts --
 * TotemPops ignoriert Wiederholungen, die unmittelbar aufeinander folgen.
 *
 * require = 0 bedeutet: sollte die Methode hier wider Erwarten nicht existieren,
 * wird der Mixin still uebersprungen statt das Spiel beim Start abstuerzen zu
 * lassen.
 */
@Mixin(LivingEntity.class)
public abstract class LivingTotemPopMixin {

    @Inject(method = "method_5711", at = @At("HEAD"), require = 0)
    private void pvpclient$countTotemPop(byte status, CallbackInfo ci) {
        if (status != EntityStatuses.USE_TOTEM_OF_UNDYING) return;
        Object self = this;
        if (self instanceof PlayerEntity player) {
            try {
                TotemPops.add(player.getName().getString());
            } catch (Throwable pvpErr) {
                com.example.pvpclient.core.Errors.report("LivingTotemPopMixin", pvpErr);
            }
        }
    }
}
