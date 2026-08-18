package net.fabricmc.loader.api;

import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLPaths;
import java.nio.file.Path;

/** Forge-backed compatibility facade for Vortex's local configuration paths. */
public final class FabricLoader {
    private static final FabricLoader INSTANCE = new FabricLoader();
    private FabricLoader() {}
    public static FabricLoader getInstance() { return INSTANCE; }
    public Path getConfigDir() { return FMLPaths.CONFIGDIR.get(); }
    public Path getGameDir() { return FMLPaths.GAMEDIR.get(); }
    public boolean isModLoaded(String modId) { return ModList.get().isLoaded(modId); }
}
