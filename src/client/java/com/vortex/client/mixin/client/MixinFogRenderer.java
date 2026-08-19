package com.vortex.client.mixin.client;

import com.vortex.client.module.ModuleManager;
import com.vortex.client.module.modules.ClearLavaModule;
import com.vortex.client.module.modules.ClearWaterModule;
import com.vortex.client.module.modules.NoFogModule;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.world.level.material.FogType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Entfernt atmosphaerischen Nebel sowie Nebel in Wasser und Lava.
 *
 * Minecraft 26.2 erzeugt die Nebelparameter in {@code FogRenderer.setupFog}
 * als {@link FogData}. Der Hook erweitert nach der Berechnung die Distanzen
 * des Rueckgabeobjekts, damit die regulaere Fog-Buffer-Aktualisierung die
 * gewuenschten Werte uebernimmt.
 */
@Mixin(FogRenderer.class)
public class MixinFogRenderer {

    @Inject(method = "setupFog", at = @At("RETURN"))
    private void pvpclient$removeFog(Camera camera, int renderDistance,
                                     DeltaTracker tickCounter, float skyDarkness,
                                     ClientLevel world,
                                     CallbackInfoReturnable<FogData> cir) {
        FogData data = cir.getReturnValue();
        if (data == null) return;

        FogType fogType = camera.getFluidInCamera();
        boolean remove;
        if (fogType == FogType.LAVA) {
            remove = isEnabled(ClearLavaModule.class);
        } else if (fogType == FogType.WATER) {
            remove = isEnabled(ClearWaterModule.class);
        } else {
            remove = isEnabled(NoFogModule.class);
        }
        if (!remove) return;

        try {
            float far = Float.MAX_VALUE;
            data.environmentalStart = far;
            data.environmentalEnd = far;
            data.renderDistanceStart = far;
            data.renderDistanceEnd = far;
        } catch (Throwable pvpErr) {
            com.vortex.client.core.Errors.report("MixinFogRenderer", pvpErr);
        }
    }

    private static boolean isEnabled(Class<? extends com.vortex.client.module.Module> type) {
        try {
            com.vortex.client.module.Module module = ModuleManager.INSTANCE.get(type);
            return module != null && module.isEnabled();
        } catch (Throwable pvpErr) {
            com.vortex.client.core.Errors.report("MixinFogRenderer", pvpErr);
            return false;
        }
    }
}
