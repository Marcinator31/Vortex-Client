package com.vortex.legacy.mixin;

import com.mojang.blaze3d.platform.GlStateManager;
import com.vortex.legacy.core.ModuleManager;
import com.vortex.legacy.module.visual.SimpleToggles;
import net.minecraft.client.render.item.HeldItemRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HeldItemRenderer.class)
public abstract class HeldItemRendererMixin {
    private boolean vx$fire;

    @Inject(method = "renderFireOverlay", at = @At("HEAD"))
    private void vortex$feuerAn(float tickDelta, CallbackInfo ci) {
        SimpleToggles.LowFire m = ModuleManager.INSTANCE.get(SimpleToggles.LowFire.class);
        vx$fire = m != null && m.isEnabled();
        if (vx$fire) { GlStateManager.pushMatrix(); GlStateManager.translate(0, -m.height.getFloat(), 0); }
    }

    @Inject(method = "renderFireOverlay", at = @At("TAIL"))
    private void vortex$feuerAus(float tickDelta, CallbackInfo ci) {
        if (vx$fire) GlStateManager.popMatrix();
        vx$fire = false;
    }

    @Inject(method = "renderUnderwaterOverlay", at = @At("HEAD"), cancellable = true)
    private void vortex$wasser(float tickDelta, CallbackInfo ci) {
        SimpleToggles.ClearWater m = ModuleManager.INSTANCE.get(SimpleToggles.ClearWater.class);
        if (m != null && m.isEnabled()) ci.cancel();
    }
}
