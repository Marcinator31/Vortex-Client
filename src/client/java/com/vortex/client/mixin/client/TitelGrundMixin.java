package com.vortex.client.mixin.client;

import com.vortex.client.gui.glatt.MenuStil;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hauptmenue: ruhiger Verlauf ueber dem Panorama, unter den Knoepfen (siehe MenuStil). */
@Mixin(TitleScreen.class)
public abstract class TitelGrundMixin extends Screen {

    protected TitelGrundMixin(Component title) {
        super(title);
    }

    @Inject(method = "extractRenderState",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/Screen;extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V"),
            require = 0)
    private void vortex$grund(GuiGraphicsExtractor ctx, int mx, int my, float delta, CallbackInfo ci) {
        try {
            if (MenuStil.titel()) MenuStil.titelGrund(ctx, this.width, this.height);
        } catch (Throwable t) {
            com.vortex.client.core.Errors.report("TitelGrund", t);
        }
    }
}
