package com.vortex.client.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.vortex.client.module.ModuleManager;
import com.vortex.client.module.modules.HandItemScaleModule;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hand-item scaling for the classic Minecraft-1.20.1 item-in-hand renderer. */
@Mixin(ItemInHandRenderer.class)
public final class HeldItemRendererMixin {
    @Inject(method = "renderItem", at = @At("HEAD"), require = 0)
    private void vortex$scaleHandItem(LivingEntity entity, ItemStack stack,
                                      ItemDisplayContext displayContext, boolean leftHand,
                                      PoseStack poseStack, MultiBufferSource buffers, int light,
                                      CallbackInfo ci) {
        HandItemScaleModule module = ModuleManager.INSTANCE.get(HandItemScaleModule.class);
        if (module != null && module.isEnabled()) {
            float scale = (float) module.size.get();
            if (scale != 1.0F) poseStack.scale(scale, scale, scale);
        }
    }
}
