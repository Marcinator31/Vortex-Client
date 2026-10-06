package com.vortex.legacy.mixin;

import com.vortex.legacy.core.Errors;
import com.vortex.legacy.gui.Render3D;
import com.vortex.legacy.module.WorldRenderers;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Eigene Dinge in die Welt zeichnen: nach der Welt, vor der Hand. */
@Mixin(GameRenderer.class)
public abstract class WorldRenderMixin {
    @Inject(method = "renderWorld(IFJ)V", at = @At(value = "INVOKE_STRING",
            target = "Lnet/minecraft/util/profiler/Profiler;swap(Ljava/lang/String;)V", args = "ldc=hand"))
    private void vortex$welt(int anaglyph, float tickDelta, long limit, CallbackInfo ci) {
        try {
            Render3D.setCamera(tickDelta);
            WorldRenderers.render(tickDelta);
        } catch (Throwable t) {
            Errors.report("WorldRender", t);
        }
    }
}
