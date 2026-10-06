package com.vortex.legacy;

import net.fabricmc.api.ClientModInitializer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Vortex Client fuer Minecraft 1.8.9. */
public final class VortexLegacy implements ClientModInitializer {
    public static final Logger LOG = LogManager.getLogger("vortexclient");

    @Override
    public void onInitializeClient() {
        LOG.info("[Vortex] Vortex Client for 1.8.9 loaded.");
    }
}
