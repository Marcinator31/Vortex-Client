package com.vortex.legacy.mixin;

import com.vortex.legacy.core.ModuleManager;
import com.vortex.legacy.module.visual.OldAnimations;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.item.HeldItemRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 1.7-Animationen: beim Benutzen (Block/Essen/Bogen) die Schlag-Animation mitnehmen. */
@Mixin(HeldItemRenderer.class)
public abstract class OldAnimationsMixin {
    private float vx$delta;

    @Inject(method = "renderArmHoldingItem", at = @At("HEAD"))
    private void vortex$delta(float tickDelta, CallbackInfo ci) { vx$delta = tickDelta; }

    private float vx$swing(boolean wanted, float v) {
        OldAnimations o = ModuleManager.INSTANCE.get(OldAnimations.class);
        if (o == null || !o.isEnabled() || !wanted) return v;
        MinecraftClient mc = MinecraftClient.getInstance();
        return mc.player == null ? v : mc.player.getHandSwingProgress(vx$delta);
    }
    private static OldAnimations vx$o() { return ModuleManager.INSTANCE.get(OldAnimations.class); }

    // Reihenfolge im Code: NONE(0), EAT/DRINK(1), BLOCK(2), BOW(3), normal(4)
    @ModifyArg(method = "renderArmHoldingItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/item/HeldItemRenderer;applyEquipAndSwingOffset(FF)V", ordinal = 1), index = 1)
    private float vortex$essen(float v) { OldAnimations o = vx$o(); return vx$swing(o != null && o.eat.get(), v); }

    @ModifyArg(method = "renderArmHoldingItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/item/HeldItemRenderer;applyEquipAndSwingOffset(FF)V", ordinal = 2), index = 1)
    private float vortex$block(float v) { OldAnimations o = vx$o(); return vx$swing(o != null && o.blockHit.get(), v); }

    @ModifyArg(method = "renderArmHoldingItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/item/HeldItemRenderer;applyEquipAndSwingOffset(FF)V", ordinal = 3), index = 1)
    private float vortex$bogen(float v) { OldAnimations o = vx$o(); return vx$swing(o != null && o.bow.get(), v); }
}
