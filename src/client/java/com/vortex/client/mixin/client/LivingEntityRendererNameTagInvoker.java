package com.vortex.client.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Opens the protected 1.20.1 vanilla name-tag rendering path for Vortex overlays. */
@Mixin(EntityRenderer.class)
public interface LivingEntityRendererNameTagInvoker {
    @Invoker("renderNameTag")
    void vortex$renderNameTag(Entity entity, Component text, PoseStack poseStack,
                              MultiBufferSource buffers, int packedLight);
}
