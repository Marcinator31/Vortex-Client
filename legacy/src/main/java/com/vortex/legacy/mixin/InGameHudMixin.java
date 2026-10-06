package com.vortex.legacy.mixin;

import com.vortex.legacy.core.ModuleManager;
import com.vortex.legacy.module.pvp.Crosshair;
import com.vortex.legacy.module.visual.SimpleToggles;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.util.Window;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(InGameHud.class)
public abstract class InGameHudMixin {
    /** Eigenes Fadenkreuz statt Vanilla (gezeichnet in VortexLegacy.hud). */
    @Inject(method = "showCrosshair", at = @At("HEAD"), cancellable = true)
    private void vortex$fadenkreuz(CallbackInfoReturnable<Boolean> cir) {
        Crosshair c = ModuleManager.INSTANCE.get(Crosshair.class);
        if (c != null && c.isEnabled()) cir.setReturnValue(false);
    }

    @Inject(method = "renderPumpkinBlur", at = @At("HEAD"), cancellable = true)
    private void vortex$kuerbis(Window w, CallbackInfo ci) {
        SimpleToggles.NoPumpkinBlur m = ModuleManager.INSTANCE.get(SimpleToggles.NoPumpkinBlur.class);
        if (m != null && m.isEnabled()) ci.cancel();
    }
}
