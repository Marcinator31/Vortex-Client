package com.vortex.client.mixin.client;

import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Zwei Stellen im Spielmodus des Clients.
 *
 *  - attack: der Moment eines Schlags, fuer Reichweite und Combo.
 *  - handleContainerInput: jede Inventar-Aktion, fuer die Slot-Sperre.
 *
 * Beide Signaturen gegen 26.2 geprueft und vollstaendig angegeben; passt eine
 * nicht, bleibt nur das jeweilige Modul wirkungslos (require = 0).
 */
@Mixin(MultiPlayerGameMode.class)
public abstract class GameModeHookMixin {

    @Inject(method = "attack(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/entity/Entity;)V",
            at = @At("HEAD"), require = 0)
    private void vortex$schlag(Player player, Entity target, CallbackInfo ci) {
        try {
            com.vortex.client.hud.CombatTracker.schlag(target);
        } catch (Throwable pvpErr) {
            com.vortex.client.core.Errors.report("GameModeHookMixin.attack", pvpErr);
        }
    }

    @Inject(method = "handleContainerInput(IIILnet/minecraft/world/inventory/ContainerInput;Lnet/minecraft/world/entity/player/Player;)V",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void vortex$slotSperre(int containerId, int slotId, int button, ContainerInput input,
                                  Player player, CallbackInfo ci) {
        try {
            if (com.vortex.client.hud.SlotLock.blockiert(slotId, button, input, player)) {
                ci.cancel();
            }
        } catch (Throwable pvpErr) {
            com.vortex.client.core.Errors.report("GameModeHookMixin.container", pvpErr);
        }
    }
}
