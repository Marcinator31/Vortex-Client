package com.vortex.client;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * Forge-1.20.1-Haupteinstieg des Vortex Client.
 *
 * Alle funktionsrelevanten Initialisierungen verbleiben im bisherigen
 * Client-Bootstrap. Forge ruft ihn ausschließlich über den Client-Lifecycle
 * auf, sodass eine dedizierte Serverumgebung keine Clientklassen lädt.
 */
@Mod(VortexClient.MOD_ID)
public final class VortexClient {
    public static final String MOD_ID = "vortexclient";

    public VortexClient() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper.install(modBus);
        modBus.addListener(this::onClientSetup);
    }

    private void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(VortexClientMod::initializeForgeClient);
    }
}
