package com.vortex.client.mixin.client;

import com.vortex.client.module.ModuleManager;
import com.vortex.client.module.modules.NoPumpkinBlurModule;
import net.minecraft.client.gui.Hud;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

/**
 * No Pumpkin Blur: das Kuerbis-Overlay wird unsichtbar gezeichnet.
 *
 * DAS ALTE WAR TOT: Es zielte auf Gui.renderEffects mit einem Namen aus 1.21
 * (class_746.method_6118). Beides gibt es in 26.2 nicht -- das Modul tat nie
 * etwas. Jetzt: Hud.extractCameraOverlays -> extractTextureOverlay(..., alpha),
 * dieselbe Stelle wie Meteor fuer 26.2 (HudMixin). Die Deckkraft wird auf 0
 * gesetzt, aber NUR fuer die Kuerbis-Textur (am Namen erkannt) -- nicht fuer
 * Pulverschnee oder anderes, egal in welcher Reihenfolge Minecraft sie zeichnet.
 */
@Mixin(Hud.class)
public abstract class NoPumpkinBlurMixin {

    @ModifyArgs(method = "extractCameraOverlays",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/Hud;extractTextureOverlay(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/resources/Identifier;F)V"),
            require = 0)
    private void vortex$noPumpkinBlur(Args args) {
        try {
            NoPumpkinBlurModule mod = ModuleManager.INSTANCE.get(NoPumpkinBlurModule.class);
            if (mod == null || !mod.isEnabled()) return;
            Object tex = args.get(1);
            if (tex instanceof Identifier id && id.getPath().contains("pumpkin")) {
                args.set(2, 0f);
            }
        } catch (Throwable pvpErr) {
            com.vortex.client.core.Errors.report("NoPumpkinBlurMixin", pvpErr);
        }
    }
}
