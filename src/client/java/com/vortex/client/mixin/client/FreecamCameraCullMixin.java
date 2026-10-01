package com.vortex.client.mixin.client;

import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
//#if 26.2
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//#endif

/**
 * Freecam-Sicht in Minecraft 26.2 (seit Client 4.14).
 *
 * In 26.2 entscheidet Minecraft NICHT mehr in LevelRenderer.cullTerrain, ob
 * verdeckte Chunk-Abschnitte weggelassen werden, sondern schon beim Vorbereiten
 * der Kamera (Camera.extractRenderState -> CameraRenderState.smartCull). Die
 * beiden alten Freecam-Mixins (FreecamCullMixin, FreecamSmartCullMixin) greifen
 * in 26.2 deshalb ins Leere -- unter der Erde fehlten in der Freecam ganze
 * Hoehlen und Gaenge. Ghost View (F5) sah mehr, weil es genau hier eingreift.
 *
 * Jetzt, solange die Freecam laeuft:
 *   - "No Culling" an (Standard): smartCull aus -- alles im Blickfeld wird
 *     gezeichnet (mehr als im Zuschauermodus)
 *   - "No Culling" aus: wie ein Zuschauer -- aus, sobald die Kamera in einem
 *     festen Block steckt
 * Sodium liest denselben Wert (cameraRenderState.smartCull) und folgt damit.
 *
 * In 26.1 und 1.21.11 bleibt diese Klasse leer (dort wirken die alten Mixins).
 */
@Mixin(Camera.class)
public abstract class FreecamCameraCullMixin {

    //#if 26.2
    @Shadow @Final private net.minecraft.core.BlockPos.MutableBlockPos blockPosition;

    @Inject(method = "extractRenderState", at = @At("TAIL"), require = 0)
    private void vortex$freecamSicht(CameraRenderState state, float partialTick, CallbackInfo ci) {
        try {
            if (!com.vortex.client.freecam.Freecam.isActive()) return;
            if (com.vortex.client.freecam.Freecam.ohneCulling()) {
                state.smartCull = false;
                return;
            }
            var mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.level != null && mc.level.getBlockState(blockPosition).isSolidRender()) state.smartCull = false;
        } catch (Throwable ignored) {
        }
    }
    //#endif
}
