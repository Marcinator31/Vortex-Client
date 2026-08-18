package com.vortex.client.mixin.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.vortex.client.module.ModuleManager;
import com.vortex.client.module.modules.ClearLavaModule;
import com.vortex.client.module.modules.ClearWaterModule;
import com.vortex.client.module.modules.NoFogModule;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.world.level.material.FogType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Classic 1.20.1 shader-fog override for No Fog, Clear Water and Clear Lava. */
@Mixin(FogRenderer.class)
public final class MixinFogRenderer {
    @Inject(method = "setupFog", at = @At("TAIL"), require = 0)
    private static void vortex$removeFog(Camera camera, FogRenderer.FogMode mode,
                                         float viewDistance, boolean thickFog,
                                         float partialTick, CallbackInfo ci) {
        boolean enabled;
        FogType fluid = camera.getFluidInCamera();
        if (fluid == FogType.LAVA) enabled = isEnabled(ClearLavaModule.class);
        else if (fluid == FogType.WATER) enabled = isEnabled(ClearWaterModule.class);
        else enabled = isEnabled(NoFogModule.class);
        if (!enabled) return;
        RenderSystem.setShaderFogStart(Float.MAX_VALUE);
        RenderSystem.setShaderFogEnd(Float.MAX_VALUE);
    }

    private static boolean isEnabled(Class<? extends com.vortex.client.module.Module> type) {
        try {
            com.vortex.client.module.Module module = ModuleManager.INSTANCE.get(type);
            return module != null && module.isEnabled();
        } catch (Throwable error) {
            com.vortex.client.core.Errors.report("MixinFogRenderer", error);
            return false;
        }
    }
}
