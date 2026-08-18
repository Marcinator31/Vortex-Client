package com.vortex.client.mixin.client;

import net.minecraft.client.player.ClientInput;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Zugriff auf die Bewegungsfelder der ClientInput-Basisklasse. Die konkrete
 * Tastatureingabe liegt als Player-Input-Record im ClientInput-Objekt vor.
 */
@Mixin(ClientInput.class)
public interface InputAccessor {

    @Accessor("moveVector")
    void pvpclient$setMovementVector(Vec2 vec);

    @Accessor("keyPresses")
    void pvpclient$setPlayerInput(Input input);
}
