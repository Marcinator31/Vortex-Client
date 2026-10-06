package com.vortex.legacy.mixin;

import com.mojang.blaze3d.platform.GlStateManager;
import com.vortex.legacy.core.ModuleManager;
import com.vortex.legacy.module.visual.Freelook;
import com.vortex.legacy.module.visual.SimpleToggles;
import com.vortex.legacy.module.visual.Zoom;
import net.minecraft.block.material.Material;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.ClientPlayerEntity;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Shadow private MinecraftClient client;
    @Shadow private float viewDistance;
    @Shadow private boolean renderingPanorama;

    /** Zoom */
    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void vortex$zoom(float tickDelta, boolean changingFov, CallbackInfoReturnable<Float> cir) {
        if (renderingPanorama) return;
        Zoom z = ModuleManager.INSTANCE.get(Zoom.class);
        if (z == null || !z.isEnabled()) return;
        float d = z.divisor();
        if (d > 1.001f) cir.setReturnValue(cir.getReturnValue() / d);
    }

    /** No Fog / Clear Water / Clear Lava */
    @Inject(method = "renderFog", at = @At("TAIL"))
    private void vortex$nebel(int i, float tickDelta, CallbackInfo ci) {
        try {
            Entity e = client.getCameraEntity();
            if (e == null || client.world == null) return;
            Material m = Camera.getSubmergedBlock(client.world, e, tickDelta).getMaterial();
            if (m == Material.WATER) {
                if (an(SimpleToggles.ClearWater.class)) GlStateManager.fogDensity(0.004F);
            } else if (m == Material.LAVA) {
                if (an(SimpleToggles.ClearLava.class)) GlStateManager.fogDensity(0.08F);
            } else if (an(SimpleToggles.NoFog.class)) {
                GlStateManager.fogStart(viewDistance * 4F);
                GlStateManager.fogEnd(viewDistance * 5F);
            }
        } catch (Throwable t) {
            com.vortex.legacy.core.Errors.report("Fog", t);
        }
    }

    /** No Hurt Cam: Wackeln beim Treffer schwaecher/aus */
    @Inject(method = "bobViewWhenHurt", at = @At("HEAD"), cancellable = true)
    private void vortex$hurtCam(float tickDelta, CallbackInfo ci) {
        SimpleToggles.NoHurtCam m = ModuleManager.INSTANCE.get(SimpleToggles.NoHurtCam.class);
        if (m != null && m.isEnabled() && m.strength.get() <= 0 && client.player != null && client.player.getHealth() > 0) ci.cancel();
    }

    /** Minimal Bobbing: Kamera wackelt nicht (Hand bleibt lebendig) */
    @Inject(method = "bobView", at = @At("HEAD"), cancellable = true)
    private void vortex$bob(float tickDelta, CallbackInfo ci) {
        if (an(SimpleToggles.NoBob.class)) ci.cancel();
    }

    /** Freelook: Mausbewegung dreht die Kamera, nicht den Spieler */
    @Redirect(method = "render", at = @At(value = "INVOKE", target = "Lnet.minecraft.entity.player.ClientPlayerEntity;increaseTransforms(FF)V"))
    private void vortex$drehen(ClientPlayerEntity p, float dx, float dy) {
        Freelook f = ModuleManager.INSTANCE.get(Freelook.class);
        if (f != null && f.isEnabled() && f.active) f.turn(dx, dy);
        else p.increaseTransforms(dx, dy);
    }

    private float vx$yaw, vx$pitch, vx$pyaw, vx$ppitch;
    private boolean vx$swap;

    @Inject(method = "transformCamera", at = @At("HEAD"))
    private void vortex$kameraAn(float tickDelta, CallbackInfo ci) {
        Freelook f = ModuleManager.INSTANCE.get(Freelook.class);
        Entity e = client.getCameraEntity();
        vx$swap = f != null && f.isEnabled() && f.active && e != null;
        if (!vx$swap) return;
        vx$yaw = e.yaw; vx$pitch = e.pitch; vx$pyaw = e.prevYaw; vx$ppitch = e.prevPitch;
        e.yaw = e.prevYaw = f.camYaw;
        e.pitch = e.prevPitch = f.camPitch;
    }

    @Inject(method = "transformCamera", at = @At("TAIL"))
    private void vortex$kameraAus(float tickDelta, CallbackInfo ci) {
        if (!vx$swap) return;
        Entity e = client.getCameraEntity();
        if (e != null) { e.yaw = vx$yaw; e.pitch = vx$pitch; e.prevYaw = vx$pyaw; e.prevPitch = vx$ppitch; }
        vx$swap = false;
    }

    private static boolean an(Class<? extends com.vortex.legacy.core.Module> c) {
        com.vortex.legacy.core.Module m = ModuleManager.INSTANCE.get(c);
        return m != null && m.isEnabled();
    }
}
