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

    //#if 26.2
    @Inject(method = "setupFog", at = @At("RETURN"))
    private void pvpclient$removeFog(Camera camera, int renderDistance,
                                     DeltaTracker tickCounter, float skyDarkness,
                                     ClientLevel world,
                                     CallbackInfoReturnable<FogData> cir) {
        com.vortex.client.hud.FogCheck.setupLief();
        klaeren(cir.getReturnValue(), camera.getFluidInCamera());
    }

    /**
     * ZWEITER EINGRIFF (4.6.2): direkt bevor der Nebel in den Grafikspeicher
     * geschrieben wird.
     *
     * Der erste Eingriff (setupFog) greift nur, wenn Minecraft die Nebelwerte
     * selbst ausrechnet. Ersetzt eine andere Mod diese Rechnung, kam er nie
     * zum Zug -- genau so sah "geht bei mir, bei Freunden nicht" aus. Hier
     * landen die Werte in jedem Fall, egal wer sie berechnet hat.
     */
    @Inject(method = "updateBuffer(Lnet/minecraft/client/renderer/fog/FogData;)V",
            at = @At("HEAD"), require = 0)
    private void vortex$vorDemSchreiben(FogData data,
                                        org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        com.vortex.client.hud.FogCheck.pufferLief();
        try {
            var mc = net.minecraft.client.Minecraft.getInstance();
            klaeren(data, mc.gameRenderer.mainCamera().getFluidInCamera());
        } catch (Throwable ignored) {
        }
    }

    //#else
    //$ /**
    //$  * 1.21.11: setupFog rechnet die Werte aus und schreibt sie ueber
    //$  * updateBuffer(..., envStart, envEnd, renderStart, renderEnd, sky, cloud)
    //$  * in den Grafikspeicher -- dort die Nebel-Abstaende austauschen.
    //$  */
    //$ @org.spongepowered.asm.mixin.injection.ModifyArgs(method = "setupFog",
    //$         at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/fog/FogRenderer;updateBuffer(Ljava/nio/ByteBuffer;ILorg/joml/Vector4f;FFFFFF)V"),
    //$         require = 0)
    //$ private void vortex$nebel(org.spongepowered.asm.mixin.injection.invoke.arg.Args args) {
    //$     com.vortex.client.hud.FogCheck.setupLief();
    //$     com.vortex.client.hud.FogCheck.pufferLief();
    //$     try {
    //$         var mc = net.minecraft.client.Minecraft.getInstance();
    //$         FogData d = new FogData();
    //$         d.environmentalStart = args.get(3);
    //$         d.environmentalEnd = args.get(4);
    //$         d.renderDistanceStart = args.get(5);
    //$         d.renderDistanceEnd = args.get(6);
    //$         klaeren(d, mc.gameRenderer.getMainCamera().getFluidInCamera());
    //$         args.set(3, d.environmentalStart);
    //$         args.set(4, d.environmentalEnd);
    //$         args.set(5, d.renderDistanceStart);
    //$         args.set(6, d.renderDistanceEnd);
    //$     } catch (Throwable pvpErr) {
    //$         com.vortex.client.core.Errors.report("MixinFogRenderer", pvpErr);
    //$     }
    //$ }
    //#endif

    private static void klaeren(FogData data, FogType fogType) {
        if (data == null || fogType == null) return;
        boolean remove;
        // Freecam: Nebel (auch Blindheit/Dunkelheit des Spielers, Nether-Dunst,
        // Wasser) nimmt der Kamera sonst die halbe Sicht.
        if (com.vortex.client.freecam.Freecam.ohneNebel()) {
            remove = true;
        } else if (fogType == FogType.LAVA) {
            remove = isEnabled(ClearLavaModule.class);
        } else if (fogType == FogType.WATER) {
            remove = isEnabled(ClearWaterModule.class);
        } else {
            remove = isEnabled(NoFogModule.class);
        }
        if (!remove) return;

        try {
            // Endliche Werte, Start ungleich Ende: unendlich in einer
            // Grafikkarten-Variable ist unsauber, und gleiche Werte ergeben
            // in der Nebelrechnung eine Spanne von null.
            float start = 1.0e7f;
            float ende = 2.0e7f;
            data.environmentalStart = start;
            data.environmentalEnd = ende;
            data.renderDistanceStart = start;
            data.renderDistanceEnd = ende;
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
