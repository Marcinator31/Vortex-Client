package com.vortex.client.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.vortex.client.gui.glatt.MenuStil;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Eingabefelder (Suche, Weltname, Serveradresse) im neuen Menue-Stil: runde dunkle Flaeche statt schwarzem Kasten. */
@Mixin(EditBox.class)
public abstract class MenuEingabeStilMixin {

    @WrapOperation(method = "extractWidgetRenderState",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V"),
            require = 0)
    private void vortex$feld(GuiGraphicsExtractor ctx, RenderPipeline p, Identifier id, int x, int y, int w, int h, Operation<Void> original) {
        try {
            if (MenuStil.aktiv()) {
                EditBox self = (EditBox) (Object) this;
                MenuStil.eingabe(ctx, self, x, y, w, h);
                return;
            }
        } catch (Throwable t) {
            com.vortex.client.core.Errors.report("MenuEingabeStil", t);
        }
        original.call(ctx, p, id, x, y, w, h);
    }
}
