package com.vortex.client.mixin.client;

import com.vortex.client.gui.glatt.MenuStil;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.LogoRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hauptmenue: Vortex-Logo mit Schriftzug statt des Minecraft-Logos (siehe MenuStil). */
@Mixin(LogoRenderer.class)
public abstract class TitelLogoMixin {

    @Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IFI)V",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void vortex$logo(GuiGraphicsExtractor ctx, int breite, float alpha, int hoehe, CallbackInfo ci) {
        try {
            if (!MenuStil.titel()) return;
            MenuStil.titelLogo(ctx, breite, hoehe + 4, alpha);
            ci.cancel();
        } catch (Throwable t) {
            com.vortex.client.core.Errors.report("TitelLogo", t);
        }
    }
}
