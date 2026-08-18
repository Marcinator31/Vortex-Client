package com.vortex.client.mixin.client;

import com.vortex.client.hud.TotemPops;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityEvent;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Counts totem pops, hooked at the packet handler.
 *
 * WHY HERE AND NOT ON THE ENTITY:
 * The two earlier attempts hooked handleStatus — first on Entity, then on
 * LivingEntity as well. Neither fired. Checking the mappings shows why: only
 * Entity declares that method at all, and the totem case never reaches it,
 * because whatever handles it in between does not pass it on.
 *
 * The packet handler sits before all of that. Every entity status the server
 * sends arrives here first, so the count cannot be missed. The packet already
 * carries both pieces we need: which entity, and which event.
 *
 * Nothing is requested and nothing is sent — this event reaches every client
 * nearby anyway, so the totem animation can play.
 */
@Mixin(ClientPacketListener.class)
public abstract class EntityStatusMixin {

    @Inject(method = "handleEntityEvent", at = @At("TAIL"), require = 0)
    private void vortex$countTotemPop(ClientboundEntityEventPacket packet, CallbackInfo ci) {
        try {
            if (packet.getEventId() != (byte) 35) return;

            net.minecraft.client.Minecraft client =
                    net.minecraft.client.Minecraft.getInstance();
            if (client == null || client.level == null) return;

            Entity entity = packet.getEntity(client.level);
            if (entity instanceof Player player) {
                TotemPops.add(player.getName().getString());
            }
        } catch (Throwable pvpErr) {
            com.vortex.client.core.Errors.report("EntityStatusMixin", pvpErr);
        }
    }
}
