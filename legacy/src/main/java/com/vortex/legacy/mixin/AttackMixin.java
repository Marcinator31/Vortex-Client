package com.vortex.legacy.mixin;

import com.vortex.legacy.core.Combat;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayerInteractionManager.class)
public abstract class AttackMixin {
    @Inject(method = "attackEntity", at = @At("HEAD"))
    private void vortex$angriff(PlayerEntity player, Entity target, CallbackInfo ci) {
        try { Combat.attack(target); com.vortex.legacy.module.pvp.HitEffects.onAttack(target); }
        catch (Throwable t) { com.vortex.legacy.core.Errors.report("Attack", t); }
    }
}
