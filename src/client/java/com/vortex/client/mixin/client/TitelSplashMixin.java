package com.vortex.client.mixin.client;

import com.vortex.client.gui.glatt.MenuStil;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.SplashRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hauptmenue: der schraege gelbe Spruch passt nicht zum neuen Logo -- im neuen Stil aus. */
@Mixin(SplashRenderer.class)
public abstract class TitelSplashMixin {

    @Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true, require = 0)
    private void vortex$spruch(GuiGraphicsExtractor ctx, int breite, Font font, float alpha, CallbackInfo ci) {
        if (MenuStil.titel()) ci.cancel();
    }
}
