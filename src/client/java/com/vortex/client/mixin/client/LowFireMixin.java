package com.vortex.client.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.vortex.client.module.ModuleManager;
import com.vortex.client.module.modules.LowFireModule;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.mojang.blaze3d.vertex.PoseStack;

/**
 * Low Fire -- senkt die Feuer-Flammen im First-Person-Overlay nach unten.
 * Vorbild: BactroMod.
 *
 * ECHTE Signatur aus dem Crash-Log dieser exakten 1.21.11-Version:
 *   method_23070(class_4587, class_4597, class_1058)
 *   = (PoseStack, MultiBufferSource, TextureAtlasSprite)
 *
 * Das veroeffentlichte Yarn-Mapping war veraltet (MinecraftClient,
 * PoseStack) -- das Spiel selbst verlangt diese drei Typen. Mixin
 * meldet bei falscher Signatur exakt die erwartete, daher jetzt korrekt.
 */
@Mixin(ScreenEffectRenderer.class)
public class LowFireMixin {

    @Inject(
        method = "method_23070",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/class_4587;method_22903()V",
            shift = At.Shift.AFTER
        )
    )
    private static void pvpclient$lowFire(PoseStack matrices, MultiBufferSource consumers,
                                          TextureAtlasSprite sprite, CallbackInfo ci) {
        LowFireModule mod = find();
        if (mod != null && mod.isEnabled()) {
            matrices.translate(0.0f, -0.4f, 0.0f);
        }
    }

    private static LowFireModule find() {
        // Konstante Laufzeit statt die ganze Modul-Liste zu durchlaufen --
        // diese Methode wird in Render-Pfaden sehr haeufig aufgerufen.
        return ModuleManager.INSTANCE.get(LowFireModule.class);
    }
}
