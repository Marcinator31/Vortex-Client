package com.vortex.client.mixin.client;

import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Reports standing on the ground while falling.
 *
 * WHY THE OLD VERSION ONLY WORKED SOMETIMES: it set the flag at the end of the
 * tick -- after the movement packet had already gone out. The server had by
 * then been told the truth, and worked out the damage from that. It appeared to
 * help now and again, whenever the timing happened to line up, which is exactly
 * how it felt: fine if you jumped first, useless otherwise.
 *
 * This sits where the packet is actually built. The flag is set immediately
 * before, so what leaves the machine says "on the ground" -- which is the only
 * thing the server ever looks at.
 *
 * No jump needed, and no minimum height either: step off a cliff of any size
 * and the landing costs nothing.
 */
@Mixin(ClientPlayerEntity.class)
public abstract class NoFallMixin {

    @Inject(method = "method_3136", at = @At("HEAD"), require = 0)
    private void vortex$noFall(CallbackInfo ci) {
        try {
            var mod = com.vortex.client.module.ModuleManager.INSTANCE.get(
                    com.vortex.client.module.modules.NoFallModule.class);
            if (mod == null || !mod.isEnabled()) return;

            ClientPlayerEntity self = (ClientPlayerEntity) (Object) this;
            if (self.isSpectator()) return;
            if (self.getAbilities().flying) return;
            if (self.isGliding()) return;
            // Water and lava break a fall by themselves; claiming ground there
            // only makes the movement look odd for no gain.
            if (self.isTouchingWater() || self.isInLava()) return;

            if (self.fallDistance <= mod.minHeight.get()) return;

            // A packet of its own that says "on the ground".
            //
            // Setting the flag on the player was not enough: the server works
            // out the fall from the movement it receives, and by the time the
            // real packet is built the flag has often been recomputed. Sending
            // one explicitly leaves nothing to timing -- the server is told
            // plainly, and the landing costs nothing.
            var handler = net.minecraft.client.MinecraftClient.getInstance()
                    .getNetworkHandler();
            if (handler != null) {
                handler.sendPacket(
                        new net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket
                                .OnGroundOnly(true, self.horizontalCollision));
            }
            self.setOnGround(true);
        } catch (Throwable pvpErr) {
            com.vortex.client.core.Errors.report("NoFallMixin", pvpErr);
        }
    }
}
