package com.example.pvpclient.mixin.client;

import com.example.pvpclient.hud.CpsCounter;
import net.minecraft.client.Mouse;
import net.minecraft.client.input.MouseInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Faengt Mausklicks ab und meldet sie an den CpsCounter.
 *
 * WICHTIG -- Signatur fuer 1.21.11 (verifiziert gegen Yarn-Javadocs):
 *   onMouseButton(long window, MouseInput input, int action)
 *
 * In aelteren Versionen waren das vier ints (long, int, int, int).
 * Jetzt ist Button + Modifier im MouseInput-Record zusammengefasst:
 *   - input.button() -> welche Taste (0 = links, 1 = rechts)
 *   - action         -> 1 = gedrueckt, 0 = losgelassen
 *
 * Die Mixin-Signatur MUSS exakt zur Zielmethode passen, sonst stuerzt
 * Minecraft beim Start ab (genau der vorherige Crash).
 */
@Mixin(Mouse.class)
public class MouseMixin {

    private static final int LEFT_BUTTON = 0;
    private static final int RIGHT_BUTTON = 1;
    private static final int ACTION_PRESS = 1; // GLFW: 1 = gedrueckt

    @Inject(method = "onMouseButton", at = @At("HEAD"))
    private void pvpclient$onMouseButton(long window, MouseInput input, int action, CallbackInfo ci) {
        if (action != ACTION_PRESS) {
            return; // nur das Druecken zaehlen, nicht das Loslassen
        }
        int button = input.button();
        if (button == LEFT_BUTTON) {
            CpsCounter.LEFT.onClick();
        } else if (button == RIGHT_BUTTON) {
            CpsCounter.RIGHT.onClick();
        }
    }
}
