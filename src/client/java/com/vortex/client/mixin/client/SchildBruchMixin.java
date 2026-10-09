package com.vortex.client.mixin.client;

import com.vortex.client.cosmetics.SchildStatus;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Shield Status: "Schild bricht"-Geraeusch erkennen (der Server spielt es an der
 * Position des Spielers, dessen Schild eine Axt gebrochen hat).
 * TAIL: laeuft nur auf dem Render-Thread (auf dem Netzwerk-Thread bricht die
 * Methode vorher ab und wird neu eingeplant).
 */
@Mixin(ClientPacketListener.class)
public abstract class SchildBruchMixin {
    @Inject(method = "handleSoundEvent", at = @At("TAIL"), require = 0)
    private void vortex$schildBruch(ClientboundSoundPacket packet, CallbackInfo ci) {
        try {
            var key = packet.getSound().unwrapKey();
            String pfad = key.isPresent() ? key.get().identifier().getPath() : packet.getSound().value().location().getPath();
            if (!pfad.equals("item.shield.break")) return;
            SchildStatus.schildBricht(packet.getX(), packet.getY(), packet.getZ());
        } catch (Throwable t) {
            com.vortex.client.core.Errors.report("SchildBruch", t);
        }
    }
}
