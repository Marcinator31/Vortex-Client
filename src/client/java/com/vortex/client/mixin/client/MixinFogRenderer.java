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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.material.FogType;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

/**
 * Entfernt verschiedene Nebel-Arten (No Fog / Klarsicht Wasser / Klarsicht Lava).
 *
 * Ansatz uebernommen von BactroMod (das fuer 1.21.11 funktioniert): Wir haengen
 * uns in FogRenderer.applyFog ein, und zwar GENAU an die Stelle, nachdem das
 * Feld renderDistanceEnd der FogData gesetzt wurde (@At FIELD, shift=AFTER).
 * Per Local-Capture holen wir die lokale FogData und den FogType
 * heraus und schieben die Nebel-Distanzen auf Float.MAX_VALUE -> kein Nebel.
 *
 * Welcher Nebel gerade gilt, bestimmen wir ueber den FogType
 * (LAVA / WATER) bzw. behandeln alles andere als den normalen (atmosphaerischen)
 * Nebel.
 *
 * Die FogData-Felder werden ueber den FogDataAccessor gesetzt.
 */
@Mixin(FogRenderer.class)
public class MixinFogRenderer {

    @Inject(
        method = "setupFog(Lnet/minecraft/client/Camera;ILnet/minecraft/client/DeltaTracker;FLnet/minecraft/client/multiplayer/ClientLevel;)Lorg/joml/Vector4f;",
        at = @At(
            value = "FIELD",
            target = "Lnet/minecraft/client/renderer/fog/FogData;renderDistanceEnd:F",
            shift = At.Shift.AFTER
        ),
        locals = LocalCapture.CAPTURE_FAILSOFT
    )
    private void pvpclient$removeFog(Camera camera, int renderDistance,
                                     DeltaTracker tickCounter, float skyDarkness,
                                     ClientLevel world,
                                     CallbackInfoReturnable<Vector4f> cir,
                                     float f, Vector4f color, float f2,
                                     FogType submersion, Entity entity,
                                     FogData data) {
        if (data == null) return;

        boolean remove;
        if (submersion == FogType.LAVA) {
            remove = isEnabled(ClearLavaModule.class);
        } else if (submersion == FogType.WATER) {
            remove = isEnabled(ClearWaterModule.class);
        } else {
            remove = isEnabled(NoFogModule.class);
        }
        if (!remove) return;

        try {
            FogDataAccessor acc = (FogDataAccessor) (Object) data;
            float far = Float.MAX_VALUE;
            acc.pvpclient$setEnvironmentalStart(far);
            acc.pvpclient$setEnvironmentalEnd(far);
            acc.pvpclient$setRenderDistanceStart(far);
            acc.pvpclient$setRenderDistanceEnd(far);
        } catch (Throwable pvpErr) {
                com.vortex.client.core.Errors.report("MixinFogRenderer", pvpErr);
            }
    }

    private static boolean isEnabled(Class<? extends com.vortex.client.module.Module> type) {
        try {
            // Konstante Laufzeit statt Liste durchlaufen -- laeuft pro Frame.
            com.vortex.client.module.Module m = ModuleManager.INSTANCE.get(type);
            if (m != null) return m.isEnabled();
        } catch (Throwable pvpErr) {
                com.vortex.client.core.Errors.report("MixinFogRenderer", pvpErr);
            }
        return false;
    }
}
