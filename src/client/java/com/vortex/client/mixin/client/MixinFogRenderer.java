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

    private static boolean pvpclient$gemeldet = false;

    @Inject(method = "setupFog", at = @At("RETURN"))
    private void pvpclient$removeFog(Camera camera, int renderDistance,
                                     DeltaTracker tickCounter, float skyDarkness,
                                     ClientLevel world,
                                     CallbackInfoReturnable<FogData> cir) {
        FogData data = cir.getReturnValue();
        if (data == null) {
            // Einmalige Meldung: liefert setupFog ueberhaupt ein Objekt?
            // Ohne das laesst sich nicht unterscheiden, ob der Hook nicht
            // greift oder ob die Werte nichts bewirken.
            if (!pvpclient$gemeldet) {
                pvpclient$gemeldet = true;
                com.vortex.client.core.Errors.note("MixinFogRenderer",
                        "setupFog lieferte null -- Nebel kann nicht geaendert werden");
            }
            return;
        }

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

        // Einmal pro Sitzung melden, dass der Hook wirklich greift. Damit
        // ist im Fehlerfall sofort klar, ob es am Mixin oder an den Werten
        // liegt -- statt beides gleichzeitig zu vermuten.
        if (!pvpclient$gemeldet) {
            pvpclient$gemeldet = true;
            com.vortex.client.core.Errors.note("MixinFogRenderer",
                    "Nebel wird entfernt (Typ " + fogType + ", vorher "
                            + data.environmentalStart + " bis " + data.environmentalEnd + ")");
        }

        try {
            // Start und Ende MUESSEN sich unterscheiden.
            //
            // Vorher stand hier zweimal Float.MAX_VALUE. Die Nebelstaerke
            // ergibt sich aus (Entfernung - Start) / (Ende - Start) -- bei
            // gleichen Werten wird durch null geteilt, und das Ergebnis ist
            // keine Zahl. Je nach Grafikkarte bleibt der Nebel dann stehen
            // oder das Bild wird milchig. Genau das war der Fehler.
            //
            // Ausserdem endliche Werte statt MAX_VALUE: damit bleibt Platz
            // fuer die Rechnung, ohne ins Unendliche zu laufen.
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
