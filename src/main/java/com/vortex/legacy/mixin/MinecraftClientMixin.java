package com.vortex.legacy.mixin;

import com.vortex.legacy.VortexLegacyClient;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Calls the Java-8 port core once at the end of every client tick. */
@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {
    @Inject(method = "tick", at = @At("TAIL"))
    private void vortex$afterClientTick(CallbackInfo callbackInfo) {
        VortexLegacyClient.state().onClientTick((MinecraftClient) (Object) this);
    }
}
