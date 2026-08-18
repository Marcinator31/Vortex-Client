package com.vortex.client.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hides only vanilla player name tags while the Vortex player tag is active. */
@Mixin(EntityRenderer.class)
public abstract class NametagHideMixin {
    @Inject(method = "renderNameTag", at = @At("HEAD"), cancellable = true, require = 0)
    private void vortex$hideVanillaNametag(Entity entity, Component text, PoseStack poseStack,
                                           MultiBufferSource buffers, int packedLight,
                                           CallbackInfo ci) {
        try {
            var module = com.vortex.client.module.ModuleManager.INSTANCE.get(
                    com.vortex.client.module.modules.NametagModule.class);
            if (module != null && module.isEnabled() && entity instanceof Player) ci.cancel();
        } catch (Throwable error) {
            com.vortex.client.core.Errors.report("NametagHideMixin", error);
        }
    }
}
