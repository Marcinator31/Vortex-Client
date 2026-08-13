package com.example.pvpclient.mixin.client;

import com.example.pvpclient.freecam.Freecam;
import net.minecraft.client.input.Input;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.math.Vec2f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Neutralisiert die Bewegungs-EINGABE des Spielers, solange die Freecam aktiv
 * ist -- damit WASD/Sprung/Sneak nur die Freecam steuern und nicht gleichzeitig
 * den echten Spieler.
 *
 * WICHTIG (1.21.11): Die tick()-Methode (method_3129) und die Eingabe-Felder
 * (movementVector, playerInput) liegen auf der Basisklasse Input (class_744) --
 * KeyboardInput hat keine eigene tick() mehr. Der Mixin MUSS daher auf
 * Input.class zielen, sonst greift der @Inject nicht zuverlaessig (ein frueherer
 * Versuch auf KeyboardInput.class traf die geerbte Methode nicht sicher).
 *
 * Es wird ausschliesslich die EINGABE genullt (movementVector + playerInput) --
 * NICHT die Velocity/Physik. Schwerkraft, Sprung-Bahn und Wasserstroemung wirken
 * dadurch ganz normal weiter: springt man und oeffnet im Sprung die Freecam,
 * fliegt der Sprung normal zu Ende und der Spieler landet. Die Freecam nimmt dem
 * Spieler also nur die Steuerung, nicht die Physik.
 */
@Mixin(Input.class)
public abstract class KeyboardInputMixin {

    @Inject(method = "method_3129", at = @At("TAIL"))
    private void pvpclient$blockInputInFreecam(CallbackInfo ci) {
        if (!Freecam.isActive()) return;
        InputAccessor acc = (InputAccessor) this;
        // Nur die Eingabe neutralisieren -- die Velocity bleibt unberuehrt.
        acc.pvpclient$setMovementVector(Vec2f.ZERO);
        acc.pvpclient$setPlayerInput(PlayerInput.DEFAULT);
    }
}
