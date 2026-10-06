package com.vortex.legacy.mixin;

import com.vortex.legacy.core.Combat;
import net.minecraft.client.option.KeyBinding;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Mausklicks im Spiel kommen als KeyBinding.onKeyPressed(-100 / -99) an -> CPS. */
@Mixin(KeyBinding.class)
public abstract class KeyBindingMixin {
    @Inject(method = "onKeyPressed", at = @At("HEAD"))
    private static void vortex$klick(int code, CallbackInfo ci) {
        if (code == -100) {
            Combat.click(true);
            com.vortex.legacy.module.visual.OldAnimations o = com.vortex.legacy.core.ModuleManager.INSTANCE.get(com.vortex.legacy.module.visual.OldAnimations.class);
            if (o != null && o.isEnabled()) o.onAttackClick();
        }
        else if (code == -99) Combat.click(false);
    }
}
