package com.vortex.legacy.mixin;

import com.vortex.legacy.gui.VortexPack;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.ResourcePack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Eingebautes Resource Pack mit den Vortex-Dateien (siehe VortexPack). */
@Mixin(MinecraftClient.class)
public abstract class ResourcePackMixin {
    @Shadow @Final private List<ResourcePack> resourcePacks;

    @Inject(method = "reloadResources", at = @At("HEAD"))
    private void vortex$pack(CallbackInfo ci) {
        for (ResourcePack p : resourcePacks) if (p instanceof VortexPack) return;
        resourcePacks.add(new VortexPack());
    }
}
