package com.vortex.client.mixin.client;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Blendet das normale Namensschild von Spielern aus, solange unser eigenes
 * (Modul Nametags) gezeichnet wird -- sonst stehen beide uebereinander.
 *
 * DAS ALTE WAR TOT: Es zielte auf EntityRenderer.renderNameTag, das es in
 * 26.2 nicht mehr gibt. Jetzt: getNameTag(entity) liefert fuer Spieler
 * "kein Name" -- dieselbe Stelle und Signatur wie Meteor fuer 26.2
 * (EntityRendererMixin). Unser eigenes Schild haengt nicht davon ab.
 */
@Mixin(EntityRenderer.class)
public abstract class NametagHideMixin {

    @Inject(method = "getNameTag", at = @At("HEAD"), cancellable = true, require = 0)
    private void vortex$hideVanillaNametag(Entity entity, CallbackInfoReturnable<Component> cir) {
        try {
            var mod = com.vortex.client.module.ModuleManager.INSTANCE.get(
                    com.vortex.client.module.modules.NametagModule.class);
            if (mod == null || !mod.isEnabled()) return;
            if (entity instanceof Player) cir.setReturnValue(null);
        } catch (Throwable pvpErr) {
            com.vortex.client.core.Errors.report("NametagHideMixin", pvpErr);
        }
    }
}
