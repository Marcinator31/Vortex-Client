package com.vortex.babric;

import net.fabricmc.api.ModInitializer;

/**
 * Babric adapter entry point for the Vortex Client Beta 1.7.3 port.
 *
 * <p>Babric keeps the Fabric-style ModInitializer contract, but the Minecraft
 * Beta renderer, world model, and input stack are distinct from modern Fabric.
 * This Java-8 adapter establishes the target-specific lifecycle boundary for
 * migration of modules from the preserved modern reference source.</p>
 */
public final class VortexBabricClient implements ModInitializer {
    public static final String MOD_ID = "vortexclient";

    @Override
    public void onInitialize() {
        System.out.println("[Vortex Client] Babric Beta 1.7.3 port initialized.");
    }
}
