package com.vortex.client.mixin.client;

import com.vortex.client.gui.glatt.MenuStil;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Knopf-Hintergrund im neuen Menue-Stil (siehe MenuStil): statt des
 * Minecraft-Steinknopfs eine glatte, runde Glasflaeche. Gilt fuer alle
 * Knoepfe, die Minecrafts Standard-Hintergrund benutzen (auch Symbol-Knoepfe
 * wie Sprache/Barrierefreiheit -- deren Symbol wird danach normal darueber
 * gezeichnet).
 */
@Mixin(AbstractButton.class)
public abstract class MenuKnopfStilMixin {

    @Inject(method = "extractDefaultSprite", at = @At("HEAD"), cancellable = true, require = 0)
    private void vortex$grund(GuiGraphicsExtractor ctx, CallbackInfo ci) {
        try {
            if (!MenuStil.aktiv()) return;
            MenuStil.grund(ctx, (AbstractWidget) (Object) this);
            ci.cancel();
        } catch (Throwable t) {
            com.vortex.client.core.Errors.report("MenuKnopfStil", t);
        }
    }
}
