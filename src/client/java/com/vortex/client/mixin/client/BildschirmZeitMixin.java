package com.vortex.client.mixin.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Misst, wie lange das Aufbauen eines Bildschirms (Menues) je Bild dauert --
 * fuer /lag und die Menue-Messung im Bot-Test ("Screen TitleScreen" usw.).
 * Zwei Zeitstempel je Bild, sonst nichts.
 */
@Mixin(Screen.class)
public abstract class BildschirmZeitMixin {
    @Unique private long vortex$start;

    @Inject(method = "extractRenderStateWithTooltipAndSubtitles", at = @At("HEAD"), require = 0)
    private void vortex$start(GuiGraphicsExtractor ctx, int mx, int my, float delta, CallbackInfo ci) {
        vortex$start = System.nanoTime();
    }

    @Inject(method = "extractRenderStateWithTooltipAndSubtitles", at = @At("RETURN"), require = 0)
    private void vortex$ende(GuiGraphicsExtractor ctx, int mx, int my, float delta, CallbackInfo ci) {
        if (vortex$start != 0) com.vortex.client.core.Profiler.record("Screen " + getClass().getSimpleName(), System.nanoTime() - vortex$start);
    }
}
