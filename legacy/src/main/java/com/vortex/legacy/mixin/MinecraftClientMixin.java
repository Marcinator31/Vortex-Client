package com.vortex.legacy.mixin;

import com.vortex.legacy.VortexLegacy;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.input.Keyboard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {
    @Inject(method = "tick", at = @At("TAIL"))
    private void vortex$tick(CallbackInfo ci) {
        VortexLegacy.tick((MinecraftClient) (Object) this);
    }

    /** Jede Taste (gedrueckt), bevor Minecraft sie verarbeitet. */
    @Inject(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/MinecraftClient;handleKeyInput()V"))
    private void vortex$taste(CallbackInfo ci) {
        try {
            if (Keyboard.getEventKeyState()) {
                int k = Keyboard.getEventKey() == 0 ? Keyboard.getEventCharacter() + 256 : Keyboard.getEventKey();
                VortexLegacy.onKey(k);
            }
        } catch (Throwable t) {
            com.vortex.legacy.core.Errors.report("Key", t);
        }
    }
}
