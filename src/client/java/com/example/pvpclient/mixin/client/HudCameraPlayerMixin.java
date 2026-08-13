package com.example.pvpclient.mixin.client;

import com.example.pvpclient.freecam.Freecam;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Sorgt dafuer, dass die Hotbar (und anderes HUD) waehrend der Freecam den
 * ECHTEN Spieler zeigt statt der leeren Kamera-Entity.
 *
 * Problem: InGameHud.getCameraPlayer() liefert die aktuelle cameraEntity, wenn
 * sie ein PlayerEntity ist. Unsere FreeCamera IST ein PlayerEntity, hat aber ein
 * leeres Inventar -> die Hotbar erscheint leer. (Erst beim Oeffnen des Inventars
 * wird neu geladen, daher fiel es genau dort auf.)
 *
 * Loesung: Bei aktiver Freecam geben wir hier den echten client.player zurueck,
 * dessen Inventar korrekt gefuellt ist.
 *
 * getCameraPlayer = method_1737 (gibt PlayerEntity zurueck).
 */
@Mixin(InGameHud.class)
public abstract class HudCameraPlayerMixin {

    @Inject(method = "method_1737", at = @At("HEAD"), cancellable = true)
    private void pvpclient$realPlayerForHud(CallbackInfoReturnable<PlayerEntity> cir) {
        if (Freecam.isActive()) {
            PlayerEntity real = MinecraftClient.getInstance().player;
            if (real != null) {
                cir.setReturnValue(real);
            }
        }
    }
}
