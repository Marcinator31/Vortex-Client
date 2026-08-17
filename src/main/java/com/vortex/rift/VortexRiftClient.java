package com.vortex.rift;

import org.dimdev.rift.listener.MinecraftStartListener;

/**
 * Rift/RiftLoader 1.13.2 adapter entry point for the Vortex Client port.
 *
 * <p>Rift uses listener metadata rather than Fabric's entrypoint metadata.
 * This adapter is intentionally Java-8-compatible and establishes the
 * loader-specific lifecycle boundary for the gradual migration of the modern
 * Fabric client modules.</p>
 */
public final class VortexRiftClient implements MinecraftStartListener {
    @Override
    public void onMinecraftStart() {
        System.out.println("[Vortex Client] Rift/RiftLoader 1.13.2 port initialized.");
    }
}
