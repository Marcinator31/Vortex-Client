package com.vortex.client.mixin.client;

import com.vortex.client.freecam.FreeCamera;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Verhindert, dass die Freecam-Kamera-Entity gerendert wird (kein Koerper, keine
 * Hitbox ueber dem Freecam-Kopf).
 *
 * WICHTIG -- richtige Klasse: In 1.21.11 entscheidet EntityRenderDispatcher
 * (intermediary class_898, frueher "EntityRenderDispatcher") per shouldRender,
 * ob eine Entity gezeichnet wird. (Ein frueherer Versuch zielte auf
 * EntityRenderer.shouldRender und griff nicht.) Fuer die FreeCamera geben wir
 * hier false zurueck -> sie wird komplett uebersprungen.
 *
 * shouldRender = method_3950 (Entity, Frustum, double, double, double) -> boolean.
 */
@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderShouldRenderMixin {

    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private void pvpclient$hideFreeCamera(Entity entity, Frustum frustum,
                                          double x, double y, double z,
                                          CallbackInfoReturnable<Boolean> cir) {
        // 1) Freecam-Kamera nie rendern.
        if (entity instanceof FreeCamera) {
            cir.setReturnValue(false);
            return;
        }
        // 2) Anti Render: ausgewaehlte Entity-Typen komplett ueberspringen.
        //
        // WICHTIG: Diese Methode laeuft PRO ENTITY PRO FRAME. Alles hier muss
        // billig sein. Frueher wurde je Aufruf die Modul-Liste durchsucht und
        // aus der Typ-ID ein neuer String gebaut -- bei vielen Entities waren
        // das Millionen Vergleiche und zehntausende neue Objekte pro Sekunde,
        // was regelmaessige Ruckler verursacht hat. Jetzt: Modul in konstanter
        // Zeit nachschlagen und das Ergebnis je Entity-Typ zwischenspeichern.
        try {
            if (entity == null) return;
            com.vortex.client.module.modules.AntiRenderModule ar =
                    com.vortex.client.module.ModuleManager.INSTANCE.get(
                            com.vortex.client.module.modules.AntiRenderModule.class);
            if (ar == null || !ar.isEnabled()) return;

            if (pvpclient$isTypeHidden(ar, entity.getType())) {
                cir.setReturnValue(false);
                return;
            }

            // 3) Distanz-Culling: alles jenseits der Grenze ueberspringen.
            //    x/y/z sind hier die Kamera-Position.
            double maxDist = ar.maxDistance.get();
            if (maxDist > 0) {
                boolean isPlayer =
                        entity instanceof net.minecraft.world.entity.player.Player;
                if (!(isPlayer && ar.keepPlayers.get())) {
                    double distSq = entity.distanceToSqr(x, y, z);
                    if (distSq > maxDist * maxDist) {
                        cir.setReturnValue(false);
                    }
                }
            }
        } catch (Throwable pvpErr) {
                com.vortex.client.core.Errors.report("EntityRenderShouldRenderMixin", pvpErr);
            }
    }

    // Zwischenspeicher: Entity-Typ -> ausgeblendet ja/nein. Wird verworfen,
    // sobald sich die Auswahl aendert (erkennbar am Versionszaehler).
    private static final java.util.Map<Object, Boolean> PVPCLIENT$HIDDEN_CACHE =
            new java.util.IdentityHashMap<>();
    private static int pvpclient$cacheVersion = -1;

    private static boolean pvpclient$isTypeHidden(
            com.vortex.client.module.modules.AntiRenderModule ar,
            net.minecraft.world.entity.EntityType<?> type) {
        if (type == null) return false;
        int v = ar.getVersion();
        if (v != pvpclient$cacheVersion) {
            PVPCLIENT$HIDDEN_CACHE.clear();
            pvpclient$cacheVersion = v;
        }
        Boolean cached = PVPCLIENT$HIDDEN_CACHE.get(type);
        if (cached != null) return cached;
        // Nur beim ersten Mal je Typ: ID aufloesen (teuer) und merken.
        boolean hidden = false;
        try {
            net.minecraft.resources.Identifier id =
                    net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(type);
            hidden = ar.isHidden(id);
        } catch (Throwable pvpErr) {
                com.vortex.client.core.Errors.report("EntityRenderShouldRenderMixin", pvpErr);
            }
        PVPCLIENT$HIDDEN_CACHE.put(type, hidden);
        return hidden;
    }
}
