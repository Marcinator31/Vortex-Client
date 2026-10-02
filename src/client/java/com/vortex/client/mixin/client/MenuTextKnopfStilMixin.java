package com.vortex.client.mixin.client;

import com.vortex.client.gui.glatt.MenuStil;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Normale Text-Knoepfe (Singleplayer, Multiplayer, Options ...) im neuen
 * Menue-Stil: glatte Flaeche und Beschriftung in der Schrift "Inter" statt
 * der Pixelschrift.
 */
@Mixin(Button.Plain.class)
public abstract class MenuTextKnopfStilMixin {

    @Inject(method = "extractContents", at = @At("HEAD"), cancellable = true, require = 0)
    private void vortex$knopf(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        try {
            if (!MenuStil.aktiv()) return;
            MenuStil.knopf(ctx, (AbstractWidget) (Object) this);
            ci.cancel();
        } catch (Throwable t) {
            com.vortex.client.core.Errors.report("MenuTextKnopfStil", t);
        }
    }
}
