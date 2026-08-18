package com.vortex.client.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.vortex.client.module.ModuleManager;
import com.vortex.client.module.modules.LowShieldModule;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Low Shield for the classic 1.20.1 first-person item renderer. */
@Mixin(ItemInHandRenderer.class)
public final class LowShieldMixin {
    @Inject(method = "renderItem", at = @At("HEAD"), require = 0)
    private void vortex$lowShield(LivingEntity entity, ItemStack stack,
                                  ItemDisplayContext displayContext, boolean leftHand,
                                  PoseStack poseStack, MultiBufferSource buffers, int light,
                                  CallbackInfo ci) {
        LowShieldModule module = ModuleManager.INSTANCE.get(LowShieldModule.class);
        if (module != null && module.isEnabled() && stack.is(Items.SHIELD)) {
            poseStack.translate(0.0D, -0.25D, 0.0D);
        }
    }
}
