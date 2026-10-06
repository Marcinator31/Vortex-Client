package com.vortex.legacy.mixin;

import com.mojang.blaze3d.platform.GlStateManager;
import com.vortex.legacy.core.ModuleManager;
import com.vortex.legacy.module.visual.HandItemSize;
import net.minecraft.client.render.item.HeldItemRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HeldItemRenderer.class)
public abstract class HandItemMixin {
    @Inject(method = "renderArmHoldingItem", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/GlStateManager;pushMatrix()V", ordinal = 0, shift = At.Shift.AFTER))
    private void vortex$groesse(float tickDelta, CallbackInfo ci) {
        HandItemSize h = ModuleManager.INSTANCE.get(HandItemSize.class);
        if (h == null || !h.isEnabled()) return;
        GlStateManager.translate(h.x.getFloat(), h.y.getFloat(), 0);
        float s = h.scale.getFloat();
        GlStateManager.scale(s, s, s);
    }
}
