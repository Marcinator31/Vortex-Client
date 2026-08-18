package com.vortex.client.mixin.client;

import com.vortex.client.freecam.Freecam;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Sorgt dafuer, dass die Hotbar (und anderes HUD) waehrend der Freecam den
 * ECHTEN Spieler zeigt statt der leeren Kamera-Entity.
 *
 * Problem: Gui.getCameraPlayer() liefert die aktuelle cameraEntity, wenn
 * sie ein Player ist. Unsere FreeCamera IST ein Player, hat aber ein
 * leeres Inventar -> die Hotbar erscheint leer. (Erst beim Oeffnen des Inventars
 * wird neu geladen, daher fiel es genau dort auf.)
 *
 * Loesung: Bei aktiver Freecam geben wir hier den echten client.player zurueck,
 * dessen Inventar korrekt gefuellt ist.
 *
 * getCameraPlayer = method_1737 (gibt Player zurueck).
 */
@Mixin(Gui.class)
public abstract class HudCameraPlayerMixin {

    @Inject(method = "getCameraPlayer", at = @At("HEAD"), cancellable = true)
    private void pvpclient$realPlayerForHud(CallbackInfoReturnable<Player> cir) {
        if (Freecam.isActive()) {
            Player real = Minecraft.getInstance().player;
            if (real != null) {
                cir.setReturnValue(real);
            }
        }
    }
}
