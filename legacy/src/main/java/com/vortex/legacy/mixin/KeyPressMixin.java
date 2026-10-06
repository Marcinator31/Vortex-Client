package com.vortex.legacy.mixin;

import com.vortex.legacy.core.ModuleManager;
import com.vortex.legacy.module.pvp.ToggleSneak;
import com.vortex.legacy.module.pvp.ToggleSprint;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Toggle Sprint/Sneak: die Taste gilt als gedrueckt, solange umgeschaltet. */
@Mixin(KeyBinding.class)
public abstract class KeyPressMixin {
    @Inject(method = "isPressed", at = @At("HEAD"), cancellable = true)
    private void vortex$gedrueckt(CallbackInfoReturnable<Boolean> cir) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.options == null || mc.currentScreen != null) return;
        Object self = this;
        if (self == mc.options.sprintKey) {
            ToggleSprint t = ModuleManager.INSTANCE.get(ToggleSprint.class);
            if (t != null && t.isEnabled() && t.active()) cir.setReturnValue(true);
        } else if (self == mc.options.sneakKey) {
            ToggleSneak t = ModuleManager.INSTANCE.get(ToggleSneak.class);
            if (t != null && t.isEnabled() && t.toggled) cir.setReturnValue(true);
        }
    }
}
