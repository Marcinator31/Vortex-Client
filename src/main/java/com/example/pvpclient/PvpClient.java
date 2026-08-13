package com.example.pvpclient;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.ResourcePackActivationType;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.util.Identifier;

/**
 * Common-Einstiegspunkt. Registriert hier die vier gebuendelten
 * Resource Packs (Small Totem, No Pumpkin Blur, Low Fire, Low Shield),
 * damit sie direkt aus dem Client kommen.
 *
 * registerBuiltinResourcePack-Signatur gegen die Fabric-Javadocs
 * verifiziert: (Identifier, ModContainer, ResourcePackActivationType).
 * Die Pack-Ordner liegen unter resources/resourcepacks/<id-pfad>/.
 *
 * ActivationType.NORMAL = im Resource-Pack-Menue an/aus schaltbar
 * (Packs koennen laut Fabric nicht von Haus aus aktiv sein).
 */
public class PvpClient implements ModInitializer {

    public static final String MOD_ID = "pvpclient";

    @Override
    public void onInitialize() {
        System.out.println("[" + MOD_ID + "] geladen.");
        registerBundledPacks();
    }

    private void registerBundledPacks() {
        ModContainer self = FabricLoader.getInstance()
            .getModContainer(MOD_ID)
            .orElseThrow();

        registerPack(self, "smalltotem");
    }

    private void registerPack(ModContainer container, String path) {
        ResourceManagerHelper.registerBuiltinResourcePack(
            Identifier.of(MOD_ID, path),
            container,
            ResourcePackActivationType.NORMAL
        );
    }
}
