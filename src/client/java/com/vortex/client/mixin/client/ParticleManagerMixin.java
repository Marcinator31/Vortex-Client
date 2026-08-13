package com.vortex.client.mixin.client;

import com.vortex.client.module.ModuleManager;
import com.vortex.client.module.modules.NoParticlesModule;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Faengt addParticle ab und bricht bestimmte Partikel ab, wenn das
 * NoParticlesModule sie unterdruecken soll.
 *
 * Signatur verifiziert (Yarn): addParticle(ParticleEffect, double x,y,z,
 * double vx,vy,vz) -> Particle (nullbar).
 *
 * Wir injizieren am HEAD und canceln per Rueckgabe null, wenn der
 * Partikeltyp unterdrueckt werden soll.
 */
@Mixin(ParticleManager.class)
public class ParticleManagerMixin {

    @Inject(
        method = "addParticle(Lnet/minecraft/particle/ParticleEffect;DDDDDD)Lnet/minecraft/client/particle/Particle;",
        at = @At("HEAD"),
        cancellable = true
    )
    private void pvpclient$filterParticle(ParticleEffect parameters, double x, double y, double z,
                                          double vx, double vy, double vz,
                                          CallbackInfoReturnable<Object> cir) {
        NoParticlesModule mod = find();
        if (mod == null || !mod.isEnabled()) return;

        var type = parameters.getType();

        // Totem-Partikel.
        if (mod.noTotem.get() && type == ParticleTypes.TOTEM_OF_UNDYING) {
            cir.setReturnValue(null);
            return;
        }

        // Explosions-Partikel (Crystals/Anchors/TNT erzeugen diese).
        if (mod.noExplosion.get()
            && (type == ParticleTypes.EXPLOSION
                || type == ParticleTypes.EXPLOSION_EMITTER)) {
            cir.setReturnValue(null);
        }
    }

    private static NoParticlesModule find() {
        // Konstante Laufzeit statt die ganze Modul-Liste zu durchlaufen --
        // diese Methode wird in Render-Pfaden sehr haeufig aufgerufen.
        return ModuleManager.INSTANCE.get(NoParticlesModule.class);
    }
}
