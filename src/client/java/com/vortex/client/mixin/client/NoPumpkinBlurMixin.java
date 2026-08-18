package com.vortex.client.mixin.client;

import com.vortex.client.module.ModuleManager;
import com.vortex.client.module.modules.NoPumpkinBlurModule;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Unterdrückt ausschließlich das Kürbis-Overlay, wenn das NoPumpkinBlur-Modul
 * aktiv ist. Forge 1.20.1 prüft den Helm direkt im Gui-Renderpfad; ein Hook
 * auf das konkrete Texture-Overlay ist deshalb robuster als ein Redirect auf
 * einen inzwischen nicht mehr verwendeten Inventarzugriff.
 */
@Mixin(Gui.class)
public class NoPumpkinBlurMixin {

    private static final String PUMPKIN_OVERLAY = "textures/misc/pumpkinblur.png";

    @Inject(method = "renderTextureOverlay", at = @At("HEAD"), cancellable = true)
    private void vortex$noPumpkinBlur(GuiGraphics graphics, ResourceLocation texture,
                                      float opacity, CallbackInfo ci) {
        if (!PUMPKIN_OVERLAY.equals(texture.getPath())) return;

        NoPumpkinBlurModule mod = ModuleManager.INSTANCE.get(NoPumpkinBlurModule.class);
        if (mod != null && mod.isEnabled()) {
            ci.cancel();
        }
    }
}
