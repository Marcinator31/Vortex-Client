package com.vortex.liteloader;

import java.io.File;

import com.mumfrey.liteloader.LiteMod;

/**
 * LiteLoader 1.12.2 adapter entry point for the Vortex Client port.
 *
 * <p>LiteLoader uses a litemod descriptor and the LiteMod lifecycle rather
 * than Fabric entrypoints. This class deliberately stays Java-8 compatible and
 * establishes the loader boundary for individual feature migrations.</p>
 */
public final class VortexLiteMod implements LiteMod {
    @Override
    public String getName() {
        return "Vortex Client";
    }

    @Override
    public String getVersion() {
        return "2.28.2-liteloader.1";
    }

    @Override
    public void init(File configPath) {
        System.out.println("[Vortex Client] LiteLoader 1.12.2 port initialized.");
    }

    @Override
    public void upgradeSettings(String version, File configPath, File oldConfigPath) {
        // No persisted LiteLoader settings exist in the initial adapter.
    }
}
