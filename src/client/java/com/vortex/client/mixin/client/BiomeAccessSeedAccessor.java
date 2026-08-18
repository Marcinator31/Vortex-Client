package com.vortex.client.mixin.client;

import net.minecraft.world.level.biome.BiomeManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Reads the world seed the server sent us.
 *
 * WHY THIS IS NEEDED:
 * On a proxy network every backend server is reached through the same address,
 * so the address cannot tell them apart. Worlds called "Spawn" on two different
 * servers looked identical to the waypoint system, and markers leaked between
 * them.
 *
 * The seed does distinguish them. Every world has its own, the server sends it
 * on join, and it stays the same across reconnects — which is exactly what a
 * marker needs to stay attached to the right place.
 *
 * The field is private, hence this accessor. Nothing is modified; it is read
 * only, and the value is one the client already holds.
 */
@Mixin(BiomeManager.class)
public interface BiomeAccessSeedAccessor {

    @Accessor("biomeZoomSeed")
    long vortex$getSeed();
}
