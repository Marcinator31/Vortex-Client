package com.vortex.client.mixin.client;

import com.vortex.client.core.PacketHooks;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import java.util.Iterator;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Der eine Eingriff in die Netzwerkschicht, den alle Paket-Module teilen.
 *
 * SIGNATUREN: Beide Zielmethoden sind mit vollstaendiger Beschreibung
 * angegeben, gegen Minecraft 26.2 geprueft (Meteor Client benutzt fuer 26.2
 * genau diese Stellen). Passt eine Beschreibung in einer anderen Fassung
 * nicht, findet Mixin schlicht kein Ziel -- mit require = 0 laeuft das Spiel
 * dann weiter, nur die Paket-Module bleiben wirkungslos.
 */
@Mixin(Connection.class)
public abstract class ConnectionHookMixin {

    @Inject(method = "channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/protocol/Packet;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/network/Connection;genericsFtw(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;)V",
                    shift = At.Shift.BEFORE),
            cancellable = true, require = 0)
    private void vortex$empfangen(ChannelHandlerContext ctx, Packet<?> packet, CallbackInfo ci) {
        try {
            // Buendel enthalten mehrere Pakete -- jedes einzeln pruefen, sonst
            // rutschen z. B. Bewegungspakete in einem Buendel durch.
            if (packet instanceof ClientboundBundlePacket buendel) {
                Iterator<?> it = buendel.subPackets().iterator();
                while (it.hasNext()) {
                    Object teil = it.next();
                    if (teil instanceof Packet<?> p && PacketHooks.empfangen(p)) {
                        try {
                            it.remove();
                        } catch (UnsupportedOperationException ignored) {
                            // Unveraenderliche Liste: dann bleibt es drin.
                        }
                    }
                }
                return;
            }
            if (PacketHooks.empfangen(packet)) ci.cancel();
        } catch (Throwable pvpErr) {
            com.vortex.client.core.Errors.report("ConnectionHookMixin.empfangen", pvpErr);
        }
    }

    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;)V",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void vortex$senden(Packet<?> packet, ChannelFutureListener listener, CallbackInfo ci) {
        try {
            if (PacketHooks.senden(packet)) ci.cancel();
        } catch (Throwable pvpErr) {
            com.vortex.client.core.Errors.report("ConnectionHookMixin.senden", pvpErr);
        }
    }
}
