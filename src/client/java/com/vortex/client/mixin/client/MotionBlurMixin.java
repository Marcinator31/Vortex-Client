package com.vortex.client.mixin.client;

import com.mojang.blaze3d.resource.CrossFrameResourcePool;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelTargetBundle;
import net.minecraft.client.renderer.PostChain;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Motion Blur (4.9.0): nach dem Zeichnen der Welt und vor dem HUD unseren
 * Post-Effekt anwenden -- genau die Stelle, an der Vanilla seine eigenen
 * Effekte (Creeper-/Spinnen-Sicht) anwendet (GameRenderer.render nach
 * LevelRenderer.doEntityOutline). HUD und Menues bleiben dadurch scharf.
 *
 * Der Effekt (assets/vortexclient/post_effect/motion_blur_N.json) mischt das
 * neue Bild mit dem vorigen, das in einem "persistent" Ziel liegt.
 */
@Mixin(GameRenderer.class)
public abstract class MotionBlurMixin {

    @Shadow @Final private CrossFrameResourcePool resourcePool;

    @Inject(method = "render(Lnet/minecraft/client/DeltaTracker;Z)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;doEntityOutline()V", shift = At.Shift.AFTER),
            require = 0)
    private void vortex$motionBlur(DeltaTracker delta, boolean advance, CallbackInfo ci) {
        try {
            var id = com.vortex.client.module.modules.MotionBlurModule.activeEffect();
            if (id == null) return;
            Minecraft mc = Minecraft.getInstance();
            PostChain chain = mc.getShaderManager().getPostChain(id, LevelTargetBundle.MAIN_TARGETS);
            if (chain != null) chain.process(mc.getMainRenderTarget(), resourcePool);
        } catch (Throwable e) {
            com.vortex.client.core.Errors.report("MotionBlur", e);
        }
    }
}
