package com.vortex.client.mixin.client;

import com.vortex.client.freecam.Freecam;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.client.player.Input;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Neutralisiert die Tasteneingabe waehrend der Freecam -- an der Klasse, die
 * tatsaechlich benutzt wird.
 *
 * WARUM ZUSAETZLICH ZUM MIXIN AUF DER BASISKLASSE:
 * Die Eingabe-Berechnung steckt in KeyboardInput, das die Methode der
 * Basisklasse Input ueberschreibt. Ein Mixin, der nur auf Input sitzt, wird
 * dadurch nie ausgeloest. In den Mappings faellt das nicht auf, weil dort
 * Ueberschreibungen mit gleichem Namen nicht noch einmal aufgefuehrt werden --
 * genau dieselbe Falle wie beim Totem-Zaehler.
 *
 * require = 0: Sollte die Methode hier wider Erwarten nicht ueberschrieben sein,
 * wird der Mixin still uebersprungen statt den Start abzubrechen. Der Mixin auf
 * der Basisklasse greift dann weiterhin.
 */
@Mixin(KeyboardInput.class)
public abstract class KeyboardInputOverrideMixin {

    @Inject(method = "tick", at = @At("TAIL"), require = 0)
    private void pvpclient$blockInputDuringFreecam(CallbackInfo ci) {
        if (!Freecam.isActive()) return;
        try {
            Input input = (Input) (Object) this;
            input.leftImpulse = 0.0F;
            input.forwardImpulse = 0.0F;
            input.up = false;
            input.down = false;
            input.left = false;
            input.right = false;
            input.jumping = false;
            input.shiftKeyDown = false;
        } catch (Throwable pvpErr) {
            com.vortex.client.core.Errors.report("KeyboardInputOverride", pvpErr);
        }
    }
}
