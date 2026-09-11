package com.vortex.client.mixin.client;

import com.vortex.client.waypoint.ServerFingerprint;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Records a fingerprint of the server we just joined.
 *
 * WHY: On a proxy network the address is shared, so it cannot tell backend
 * servers apart. The world seed usually can — but not always. Lobby worlds are
 * often flat worlds generated with the same fixed seed, and then two different
 * servers look identical again.
 *
 * The join packet carries a few things that differ almost every time: which
 * dimensions the server offers (a lobby typically has only the overworld, a
 * survival server all three), the player limit, and whether it is hardcore.
 * None of these identify a server on their own, but together with the seed
 * they make a collision very unlikely.
 *
 * Read only. The packet arrives anyway; nothing is requested or sent.
 */
@Mixin(ClientPacketListener.class)
public abstract class GameJoinMixin {

    @Inject(method = "handleLogin", at = @At("TAIL"), require = 0)
    private void vortex$recordServer(ClientboundLoginPacket packet, CallbackInfo ci) {
        try {
            int dims = (packet.levels() == null) ? 0 : packet.levels().size();
            ServerFingerprint.record(dims, packet.maxPlayers(), packet.hardcore());
        } catch (Throwable pvpErr) {
            com.vortex.client.core.Errors.report("GameJoinMixin", pvpErr);
        }
    }
}
