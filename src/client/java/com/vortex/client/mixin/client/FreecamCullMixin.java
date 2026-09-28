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

    /**
     * MEHR SICHT: Verdeckungsberechnung ("Smart Cull") in der Freecam ganz aus.
     *
     * Minecraft blendet Chunk-Abschnitte aus, die es fuer verdeckt HAELT --
     * und ab etwa 60 Bloecken zusaetzlich alles, was nur "ueber Umwege"
     * erreichbar waere. Beides ist fuer eine Kamera am Boden gebaut. Von oben,
     * aus einer Hoehle heraus oder beim schnellen Fliegen fehlen dadurch ganze
     * Stuecke der Welt, die eigentlich sichtbar waeren. Ohne diese Rechnung
     * wird alles im Blickfeld gezeichnet (was hinter einer Wand liegt, bleibt
     * trotzdem verdeckt -- das erledigt die Grafikkarte).
     */
    @org.spongepowered.asm.mixin.injection.Redirect(method = "cullTerrain",
            at = @At(value = "FIELD", target = "Lnet/minecraft/client/Minecraft;smartCull:Z", opcode = 180),
            require = 0)
    private boolean vortex$freecamOhneCulling(net.minecraft.client.Minecraft mc) {
        if (com.vortex.client.freecam.Freecam.ohneCulling()) return false;
        return mc.smartCull;
    }
}
