package com.vortex.client.mixin.client;

import com.vortex.client.hud.CpsCounter;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Captures classic 1.20.1 mouse presses for CPS and macro recording. */
@Mixin(MouseHandler.class)
public final class MouseMixin {
    @Inject(method = "onPress", at = @At("HEAD"), require = 0)
    private void vortex$onMousePress(long window, int button, int action, int modifiers,
                                     CallbackInfo ci) {
        if (action != 1) return;
        if (button == 0) {
            CpsCounter.LEFT.onClick();
            com.vortex.client.macro.MacroManager.record(
                    com.vortex.client.macro.Macro.Action.LEFT_CLICK, 0, 0);
        } else if (button == 1) {
            CpsCounter.RIGHT.onClick();
            com.vortex.client.macro.MacroManager.record(
                    com.vortex.client.macro.Macro.Action.RIGHT_CLICK, 0, 0);
        }
    }
}
