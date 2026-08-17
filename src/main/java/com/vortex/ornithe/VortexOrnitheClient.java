package com.vortex.ornithe;

import net.ornithemc.osl.entrypoints.api.ModInitializer;

/**
 * Ornithe/OSL adapter entry point for the Vortex Client 1.14.4 port.
 *
 * <p>Ornithe uses OSL entrypoints rather than the modern Fabric client
 * initializer. This Java-8 adapter is the loader boundary for the gradual
 * migration of Vortex modules from the preserved Fabric 1.21.11 source.</p>
 */
public final class VortexOrnitheClient implements ModInitializer {
    @Override
    public void init() {
        System.out.println("[Vortex Client] Ornithe 1.14.4 port initialized.");
    }
}
