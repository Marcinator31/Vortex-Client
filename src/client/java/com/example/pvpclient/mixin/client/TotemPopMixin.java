package com.example.pvpclient.mixin.client;

import com.example.pvpclient.hud.TotemPops;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityStatuses;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Erkennt, wenn ein Spieler ein Totem verbraucht.
 *
 * Der Server schickt dieses Ereignis ohnehin an alle Umstehenden, damit die
 * Totem-Animation abgespielt wird -- wir zaehlen es lediglich mit. Es wird nichts
 * gesendet und nichts abgefragt.
 *
 * Der Mixin sitzt bewusst auf Entity und nicht auf LivingEntity: handleStatus ist
 * dort in jedem Fall vorhanden, wodurch der Mixin sicher greift. Ob es sich um
 * einen Spieler handelt, wird danach geprueft.
 */
@Mixin(Entity.class)
public abstract class TotemPopMixin {

    @Inject(method = "method_5711", at = @At("HEAD"), require = 0)
    private void pvpclient$countTotemPop(byte status, CallbackInfo ci) {
        if (status != EntityStatuses.USE_TOTEM_OF_UNDYING) return;
        Object self = this;
        if (self instanceof PlayerEntity player) {
            try {
                // getName().getString() wird im Projekt bereits an anderer
                // Stelle genutzt und ist damit erprobt.
                TotemPops.add(player.getName().getString());
            } catch (Throwable pvpErr) {
                com.example.pvpclient.core.Errors.report("TotemPopMixin", pvpErr);
            }
        }
    }
}
