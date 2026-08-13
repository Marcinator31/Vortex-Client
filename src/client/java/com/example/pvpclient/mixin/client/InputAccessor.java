package com.example.pvpclient.mixin.client;

import net.minecraft.client.input.Input;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.math.Vec2f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Zugriff auf die (protected/package) Bewegungsfelder der Input-Basisklasse.
 * KeyboardInput erbt diese Felder, daher koennen wir ein KeyboardInput-Objekt
 * zu diesem Accessor casten und die Werte neutralisieren (fuer die Freecam).
 *
 * Der Accessor liegt direkt auf Input (class_744), wo die Felder definiert sind
 * -- so loest Mixin die Feldnamen korrekt auf (das war beim @Shadow auf der
 * Subklasse das Problem).
 */
@Mixin(Input.class)
public interface InputAccessor {

    @Accessor("field_55868")
    void pvpclient$setMovementVector(Vec2f vec);

    @Accessor("field_54155")
    void pvpclient$setPlayerInput(PlayerInput input);
}
