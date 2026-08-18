package com.vortex.client.mixin.client;

import com.vortex.client.module.ModuleManager;
import com.vortex.client.module.modules.FullbrightModule;
import net.minecraft.client.OptionInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Umgeht die Gamma-Begrenzung, damit Fullbright wirklich hell wird.
 *
 * Problem: client.options.getGamma().setValue(10.0) wird normalerweise
 * auf das Maximum 1.0 begrenzt -- deshalb war Fullbright kaum sichtbar.
 *
 * Loesung: Wir haengen uns in getValue() der Gamma-Option ein und geben
 * einen hohen Wert zurueck, wenn Fullbright an ist. So sieht der
 * Lightmap-Renderer einen Gamma-Wert von z.B. 15, ohne dass wir den
 * gespeicherten Wert veraendern.
 *
 * HINWEIS: Dieser Mixin zielt auf OptionInstance.getValue(). Da viele
 * Optionen diese Methode nutzen, pruefen wir, ob es WIRKLICH die
 * Gamma-Option ist -- ueber den Vergleich mit client.options.getGamma().
 */
@Mixin(OptionInstance.class)
public class GammaMixin {

    @Inject(method = "get", at = @At("HEAD"), cancellable = true)
    private void pvpclient$boostGamma(CallbackInfoReturnable<Object> cir) {
        // Hell machen, wenn Fullbright an ist ODER die Freecam aktiv ist.
        // Letzteres, damit man beim Umschauen unter der Erde etwas sieht.
        FullbrightModule mod = find();
        boolean fullbright = mod != null && mod.isEnabled();
        boolean freecam = com.vortex.client.freecam.Freecam.isActive();
        if (!fullbright && !freecam) return;

        net.minecraft.client.Minecraft client =
            net.minecraft.client.Minecraft.getInstance();
        if (client.options == null) return;

        // Nur eingreifen, wenn DIESE Option die Gamma-Option ist.
        if ((Object) this == client.options.gamma()) {
            cir.setReturnValue(15.0);
        }
    }

    private static FullbrightModule find() {
        // Konstante Laufzeit statt die ganze Modul-Liste zu durchlaufen --
        // diese Methode wird in Render-Pfaden sehr haeufig aufgerufen.
        return ModuleManager.INSTANCE.get(FullbrightModule.class);
    }
}
