package com.vortex.client.mixin.client;

import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Waehrend options.txt geschrieben wird, liefert GammaMixin den echten
 * Helligkeitswert. Vorher landete bei aktivem Fullbright "gamma:15.0" in der
 * Datei -- das ist ungueltig, und beim naechsten Start war die eigene
 * Helligkeit auf den Standard zurueckgesetzt.
 */
@Mixin(Options.class)
public abstract class OptionsSaveMixin {

    @Inject(method = "save", at = @At("HEAD"), require = 0)
    private void vortex$saveStart(CallbackInfo ci) {
        com.vortex.client.module.modules.FullbrightModule.optionsSpeichern = true;
    }

    @Inject(method = "save", at = @At("RETURN"), require = 0)
    private void vortex$saveEnd(CallbackInfo ci) {
        com.vortex.client.module.modules.FullbrightModule.optionsSpeichern = false;
    }
}
