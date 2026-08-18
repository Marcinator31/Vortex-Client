package com.vortex.client.mixin.client;

import net.minecraft.client.Camera;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Reads the actual position of the render camera.
 *
 * WHY THIS IS NEEDED:
 * Everything drawn in the world -- hitboxes, ESP boxes, tracers, waypoints --
 * has to be positioned relative to the camera. Until now that was taken from
 * the player's eye position, which is only the same thing in first person.
 *
 * In third person (F5) the camera sits behind and above the player, so every
 * box and line was off by exactly that distance. Which is precisely the effect
 * described: things look right until the view changes, then they drift.
 *
 * The field is private, hence this read-only accessor.
 */
@Mixin(Camera.class)
public interface CameraPosAccessor {

    @Accessor("field_18712")
    Vec3 vortex$getPos();
}
