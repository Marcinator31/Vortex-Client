package com.vortex.client.mixin.client;

import com.vortex.client.gui.glatt.MenuStil;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.SpriteIconButton;
import net.minecraft.client.gui.components.WidgetSprites;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Symbol-Knoepfe (Sprache, Barrierefreiheit ...) im neuen Menue-Stil: glatte Vektor-Symbole statt Pixelbildchen. */
@Mixin(SpriteIconButton.class)
public abstract class MenuSymbolKnopfMixin {

    @Shadow @Final protected WidgetSprites sprite;
    @Shadow @Final protected int spriteWidth;
    @Shadow @Final protected int spriteHeight;

    @Inject(method = "extractSprite", at = @At("HEAD"), cancellable = true, require = 0)
    private void vortex$symbol(GuiGraphicsExtractor ctx, int x, int y, CallbackInfo ci) {
        try {
            if (!MenuStil.aktiv()) return;
            if (MenuStil.symbolKnopf(ctx, (AbstractWidget) (Object) this, sprite.enabled().getPath(), x, y, spriteWidth, spriteHeight)) ci.cancel();
        } catch (Throwable t) {
            com.vortex.client.core.Errors.report("MenuSymbolKnopf", t);
        }
    }
}
