package com.vortex.client.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.vortex.client.module.ModuleManager;
import com.vortex.client.module.modules.LowFireModule;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Low Fire -- senkt die Feuer-Flammen im First-Person-Overlay nach unten.
 *
 * In 26.1.2 wird das Feuer über renderFire mit einer MultiBufferSource
 * gezeichnet. Die Pose wird am Beginn verschoben und nach dem Rendern
 * zuverlässig wiederhergestellt.
 */
@Mixin(ScreenEffectRenderer.class)
public class LowFireMixin {

    @Inject(method = "renderFire", at = @At("HEAD"))
    private static void vortex$lowerFire(PoseStack matrices, MultiBufferSource buffers,
                                         TextureAtlasSprite sprite, CallbackInfo ci) {
        LowFireModule module = find();
        if (module != null && module.isEnabled()) {
            matrices.pushPose();
            matrices.translate(0.0f, -0.4f, 0.0f);
        }
    }

    @Inject(method = "renderFire", at = @At("RETURN"))
    private static void vortex$restoreFirePose(PoseStack matrices, MultiBufferSource buffers,
                                                TextureAtlasSprite sprite, CallbackInfo ci) {
        LowFireModule module = find();
        if (module != null && module.isEnabled()) {
            matrices.popPose();
        }
    }

    private static LowFireModule find() {
        return ModuleManager.INSTANCE.get(LowFireModule.class);
    }
}
