package com.vortex.client.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.vortex.client.module.ModuleManager;
import com.vortex.client.module.modules.LowFireModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Senkt die Feuer-Flammen im First-Person-Overlay nach unten. In Forge 1.20.1
 * erzeugt ScreenEffectRenderer das Feuer in der privaten renderFire-Methode;
 * nach dem jeweiligen PoseStack.pushPose wird die zusätzliche Verschiebung
 * angewendet, bevor die Flammenquad in diesen PoseStack geschrieben werden.
 */
@Mixin(ScreenEffectRenderer.class)
public class LowFireMixin {

    @Inject(
        method = "renderFire",
        at = @At(
            value = "INVOKE",
            target = "Lcom/mojang/blaze3d/vertex/PoseStack;pushPose()V",
            shift = At.Shift.AFTER
        )
    )
    private static void vortex$lowFire(Minecraft client, PoseStack matrices, CallbackInfo ci) {
        LowFireModule mod = ModuleManager.INSTANCE.get(LowFireModule.class);
        if (mod != null && mod.isEnabled()) {
            matrices.translate(0.0f, -0.4f, 0.0f);
        }
    }
}
