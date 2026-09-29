package com.vortex.client.mixin.client;

import net.minecraft.client.Camera;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Wendet Zoom unmittelbar auf die Welt-Projektionsmatrix an, die Minecraft 26.x
 * nach der Kameraextraktion rendert. Anders als getFov() und calculateFov()
 * ist dieser Zustand der tatsächliche Input des LevelRenderer-Renderpasses.
 */
//#if 26.2
@Mixin(Camera.class)
//#else
//$ @Mixin(net.minecraft.client.renderer.GameRenderer.class)
//#endif
public abstract class ZoomFovMixin {

    //#if 26.2
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void vortex$applyZoomToProjection(CameraRenderState state, float partialTick,
                                               CallbackInfo ci) {
        try {
            com.vortex.client.hud.Zoom.update();
            float factor = (float) com.vortex.client.hud.Zoom.factor();
            if (factor <= 1.001F) return;

            // Bei einer Perspektivmatrix verengt ein groesserer X/Y-Skalierungs-
            // faktor den sichtbaren Winkel. Das entspricht einer Division des
            // FOV und wirkt direkt auf die vom LevelRenderer verwendete Welt.
            state.projectionMatrix.scale(factor, factor, 1.0F);
            // Die 3D-Handprojektion folgt derselben Zoomstufe; das normale 2D-HUD
            // bleibt unverändert, weil es eine separate GUI-Projektion verwendet.
            state.hudFov /= factor;
        } catch (Throwable error) {
            com.vortex.client.core.Errors.report("ZoomFovMixin", error);
        }
    //#else
    //$ // 1.21.11: das Sichtfeld kommt aus GameRenderer.getFov -- dort teilen.
    //$ @Inject(method = "getFov", at = @At("RETURN"), cancellable = true, require = 0)
    //$ private void vortex$applyZoomToFov(net.minecraft.client.Camera camera, float partialTick, boolean useFovSetting,
    //$                                    org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Float> cir) {
    //$     try {
    //$         com.vortex.client.hud.Zoom.update();
    //$         float factor = (float) com.vortex.client.hud.Zoom.factor();
    //$         if (factor > 1.001F) cir.setReturnValue(cir.getReturnValue() / factor);
    //$     } catch (Throwable error) {
    //$         com.vortex.client.core.Errors.report("ZoomFovMixin", error);
    //$     }
    //#endif
    }
}
