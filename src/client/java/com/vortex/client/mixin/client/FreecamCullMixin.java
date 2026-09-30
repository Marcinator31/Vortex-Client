package com.vortex.client.mixin.client;

import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Freecam in Bloecken: wie im Zuschauermodus rendern.
 *
 * Steckt die Kamera in einem festen Block, schaltet Minecraft die
 * Verdeckungsberechnung nur fuer Zuschauer ab (cullTerrain(..., spectator)).
 * Fuer alle anderen startet die Berechnung im Block -- und man sah durch den
 * Boden hindurch nur Leere oder flackernde Chunks. Jetzt gilt die Freecam dort
 * als Zuschauer. Ausserhalb von Bloecken aendert sich nichts.
 *
 * Geaendert wird das Argument an der AUFRUFSTELLE (renderLevel in 1.21.11,
 * update in 26.1), nicht in cullTerrain selbst: Sodium ersetzt cullTerrain
 * komplett, ein Eingriff dort liess das Spiel mit Sodium beim Start abstuerzen.
 * So wirkt es mit und ohne Sodium (Sodium reicht "spectator" an seinen
 * eigenen Renderer weiter).
 */
@Mixin(LevelRenderer.class)
public abstract class FreecamCullMixin {

    @ModifyArg(method = {"renderLevel", "update"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;cullTerrain(Lnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/culling/Frustum;Z)V"),
            index = 2, require = 0)
    private boolean vortex$freecamWieZuschauer(boolean zuschauer) {
        return zuschauer || com.vortex.client.freecam.Freecam.isActive();
    }
}
