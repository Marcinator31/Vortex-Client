package com.vortex.legacy.mixin;

import com.vortex.legacy.module.hud.TpsModule;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.WorldTimeUpdateS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public abstract class NetworkMixin {
    @Inject(method = "onWorldTimeUpdate", at = @At("TAIL"))
    private void vortex$zeit(WorldTimeUpdateS2CPacket p, CallbackInfo ci) {
        TpsModule.onWorldTime(p.getTime());
    }
}
