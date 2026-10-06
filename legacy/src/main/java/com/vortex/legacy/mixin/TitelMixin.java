package com.vortex.legacy.mixin;

import com.vortex.legacy.gui.MenuStil;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hauptmenue mit Vortex-Logo (Modern Menus). */
@Mixin(TitleScreen.class)
public abstract class TitelMixin extends Screen {
    @Redirect(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screen/TitleScreen;drawTexture(IIIIII)V"))
    private void vortex$keinLogo(TitleScreen s, int x, int y, int u, int v, int w, int h) {
        if (!MenuStil.aktiv()) s.drawTexture(x, y, u, v, w, h);
    }

    @Redirect(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screen/TitleScreen;drawCenteredString(Lnet/minecraft/client/font/TextRenderer;Ljava/lang/String;III)V"))
    private void vortex$keinSpruch(TitleScreen s, TextRenderer tr, String text, int x, int y, int c) {
        if (!MenuStil.aktiv()) s.drawCenteredString(tr, text, x, y, c);
    }

    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/GlStateManager;pushMatrix()V", ordinal = 0))
    private void vortex$logo(int mx, int my, float delta, CallbackInfo ci) {
        if (!MenuStil.aktiv()) return;
        try { MenuStil.titel(width, height); } catch (Throwable t) { com.vortex.legacy.core.Errors.report("Titel", t); }
    }
}
