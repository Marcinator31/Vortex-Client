package com.vortex.client.mixin.client;

import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Verbindet den Renderzustand mit seinem Wesen. Minecraft trennt beides seit
 * 1.21.2; Item Physics braucht aber den Gegenstand selbst (Art, Wasser,
 * Position). Nur Gegenstaende werden gemerkt, und nur bei aktivem Modul.
 */
@Mixin(EntityRenderDispatcher.class)
public abstract class EntityDispatchMixin {

    @Inject(method = "extractEntity(Lnet/minecraft/world/entity/Entity;F)Lnet/minecraft/client/renderer/entity/state/EntityRenderState;",
            at = @At("RETURN"), require = 0)
    private void vortex$zuordnen(Entity entity, float partialTicks,
                                CallbackInfoReturnable<EntityRenderState> cir) {
        try {
            com.vortex.client.hud.ItemPhysics.merke(entity, cir.getReturnValue());
        } catch (Throwable pvpErr) {
            com.vortex.client.core.Errors.report("EntityDispatchMixin", pvpErr);
        }
    }
}
