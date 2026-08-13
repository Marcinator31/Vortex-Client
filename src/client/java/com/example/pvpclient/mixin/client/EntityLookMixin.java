package com.example.pvpclient.mixin.client;

import com.example.pvpclient.freecam.Freecam;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Faengt die Maus-Drehung des Spielers ab, solange die Freecam aktiv ist.
 *
 * changeLookDirection(cursorDeltaX, cursorDeltaY) wird aufgerufen, wenn die Maus
 * bewegt wird, und dreht normalerweise den Spieler. Bei aktiver Freecam wollen
 * wir aber, dass die Maus die FREECAM dreht, nicht den Charakter. Deshalb:
 *   - leiten wir die Maus-Bewegung an die Freecam-Rotation weiter, und
 *   - canceln den Aufruf, damit sich der Spieler nicht mitdreht.
 *
 * Gilt nur fuer den eigenen Spieler -- andere Entities bleiben unberuehrt.
 */
@Mixin(Entity.class)
public abstract class EntityLookMixin {

    @Inject(method = "method_5872", at = @At("HEAD"), cancellable = true)
    private void pvpclient$freecamLook(double cursorDeltaX, double cursorDeltaY, CallbackInfo ci) {
        if (!Freecam.isActive()) return;
        Entity self = (Entity) (Object) this;
        if (self != MinecraftClient.getInstance().player) return;

        // Maus dreht die Freecam, nicht den Spieler.
        // Vanilla nutzt intern Faktor 0.15 auf die Cursor-Delta; die kommen hier
        // schon vorskaliert an, daher direkt weitergeben.
        Freecam.addRotation(cursorDeltaX, cursorDeltaY);
        ci.cancel();
    }
}
