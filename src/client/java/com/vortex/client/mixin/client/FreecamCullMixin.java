package com.vortex.client.mixin.client;

import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Freecam in Bloecken: wie im Zuschauermodus rendern.
 *
 * Steckt die Kamera in einem festen Block, schaltet Minecraft die
 * Verdeckungsberechnung nur fuer Zuschauer ab (cullTerrain(..., spectator)).
 * Fuer alle anderen startet die Berechnung im Block -- und man sah durch den
 * Boden hindurch nur Leere oder flackernde Chunks. Jetzt gilt die Freecam dort
 * als Zuschauer. Ausserhalb von Bloecken aendert sich nichts.
 */
@Mixin(LevelRenderer.class)
public abstract class FreecamCullMixin {

    @ModifyVariable(method = "cullTerrain", at = @At("HEAD"), argsOnly = true, require = 0)
    private boolean vortex$freecamWieZuschauer(boolean zuschauer) {
        return zuschauer || com.vortex.client.freecam.Freecam.isActive();
    }
}
