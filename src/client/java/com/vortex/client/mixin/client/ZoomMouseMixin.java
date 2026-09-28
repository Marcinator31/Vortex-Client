package com.vortex.client.mixin.client;

import net.minecraft.client.MouseHandler;
import net.minecraft.client.OptionInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * "Slow Mouse While Zoomed": die Einstellung gab es, sie wurde aber nirgends
 * angewendet. Jetzt wird beim Drehen die Maus-Empfindlichkeit passend zur
 * Zoomstufe verringert -- bei 4-fachem Zoom bewegt sich der Blick ein Viertel
 * so weit, das Zielen fuehlt sich auf jeder Stufe gleich an.
 *
 * Minecraft rechnet: f = (s * 0.6 + 0.2)^3. Damit f um den Faktor k kleiner
 * wird, wird s so umgerechnet, dass (s' * 0.6 + 0.2) = cbrt(k) * (s * 0.6 + 0.2).
 */
@Mixin(MouseHandler.class)
public abstract class ZoomMouseMixin {

    @Redirect(method = "turnPlayer",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/OptionInstance;get()Ljava/lang/Object;", ordinal = 0),
            require = 0)
    private Object vortex$zoomSensitivity(OptionInstance<?> option) {
        Object wert = option.get();
        try {
            double k = com.vortex.client.hud.Zoom.sensitivity();
            if (k >= 0.999 || !(wert instanceof Double s)) return wert;
            double basis = s * 0.6 + 0.2;
            return (Math.cbrt(k) * basis - 0.2) / 0.6;
        } catch (Throwable t) {
            return wert;
        }
    }
}
