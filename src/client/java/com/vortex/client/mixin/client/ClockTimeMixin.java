package com.vortex.client.mixin.client;

import net.minecraft.core.Holder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Time Changer: die Uhrzeit, die der Himmel sieht.
 *
 * SEIT 26.1 GIBT ES "WORLD CLOCKS". Sonne, Mond, Himmelsfarbe und Sterne
 * richten sich nicht mehr nach der Weltzeit (getGameTime -- das ist jetzt nur
 * noch das Alter der Welt), sondern nach der Uhr der Dimension. Der Client
 * fragt sie bei ClientClockManager.getTotalTicks(uhr) ab. Genau dort wird
 * hier die gewuenschte Tageszeit eingesetzt -- der Tag (die Mondphase) bleibt
 * erhalten, nur die Uhrzeit innerhalb des Tages aendert sich.
 *
 * Vorher wurde die Weltzeit verstellt. Das hatte seit 26.1 keinerlei Wirkung
 * mehr auf den Himmel -- deshalb "ging der Time Changer gar nicht".
 *
 * Stelle nach der gemeinfreien Mod "ClientTime" (CC0) fuer 26.1. Ziel als
 * Text mit @Pseudo und vollstaendiger Beschreibung: fehlt die Klasse oder
 * weicht die Methode ab, findet Mixin nichts (require = 0) -- dann bleibt nur
 * die Uhrzeit wirkungslos, das Spiel startet normal.
 */
@org.spongepowered.asm.mixin.Pseudo
@Mixin(targets = "net.minecraft.client.ClientClockManager")
public abstract class ClockTimeMixin {

    @Inject(method = "getTotalTicks(Lnet/minecraft/core/Holder;)J",
            at = @At("RETURN"), cancellable = true, require = 0)
    private void vortex$uhrzeit(Holder<?> uhr, CallbackInfoReturnable<Long> cir) {
        try {
            long wunsch = com.vortex.client.hud.TimeWeather.uhrzeit();
            if (wunsch < 0) return;
            long original = cir.getReturnValue();
            // Tag behalten, Uhrzeit ersetzen
            long tag = Math.floorDiv(original, 24000L);
            cir.setReturnValue(tag * 24000L + wunsch);
        } catch (Throwable pvpErr) {
            com.vortex.client.core.Errors.report("ClockTimeMixin", pvpErr);
        }
    }
}
