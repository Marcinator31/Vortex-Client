package com.vortex.legacy.mixin;

import com.mojang.blaze3d.platform.GlStateManager;
import com.vortex.legacy.core.ModuleManager;
import com.vortex.legacy.module.visual.ItemPhysics;
import net.minecraft.client.render.entity.ItemEntityRenderer;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemEntityRenderer.class)
public abstract class ItemPhysicsMixin {
    @Shadow private int method_10222(ItemStack stack) { return 1; }

    @Inject(method = "method_10221", at = @At("HEAD"), cancellable = true)
    private void vortex$flach(ItemEntity item, double x, double y, double z, float delta, BakedModel model, CallbackInfoReturnable<Integer> cir) {
        ItemPhysics p = ModuleManager.INSTANCE.get(ItemPhysics.class);
        if (p == null || !p.isEnabled()) return;
        ItemStack s = item.getItemStack();
        if (s == null || s.getItem() == null) { cir.setReturnValue(0); return; }
        boolean depth = model.hasDepth();
        int n = method_10222(s);
        float yaw = (item.getEntityId() * 47 % 360);
        GlStateManager.translate((float) x, (float) y + (depth ? 0.12F : 0.02F), (float) z);
        GlStateManager.rotate(yaw, 0, 1, 0);
        if (!item.onGround) GlStateManager.rotate((item.getAge() + delta) * 12F % 360, 1, 0, 0);
        if (!depth) {
            GlStateManager.rotate(90, 1, 0, 0);
            GlStateManager.translate(0, 0, -0.046875F * (n - 1) * 0.5F);
        }
        GlStateManager.color(1, 1, 1, 1);
        cir.setReturnValue(n);
    }
}
