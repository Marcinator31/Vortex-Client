package com.vortex.client.mixin.client;

import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemCooldowns;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Cooldown HUD (4.9.0): Beginn und Dauer jeder Abklingzeit mitschreiben.
 *
 * ItemCooldowns verraet nur "wie viel Prozent noch" -- fuer "noch 0,6 s"
 * braucht man die Dauer. Die steht in addCooldown (vom Server per
 * ClientboundCooldownPacket, beim Werfen einer Perle auch schon vom Client).
 */
@Mixin(ItemCooldowns.class)
public abstract class ItemCooldownsHookMixin {

    @Inject(method = "addCooldown(Lnet/minecraft/resources/Identifier;I)V", at = @At("HEAD"), require = 0)
    private void vortex$start(Identifier group, int ticks, CallbackInfo ci) {
        try { com.vortex.client.hud.CooldownHud.started((ItemCooldowns) (Object) this, group, ticks); }
        catch (Throwable e) { com.vortex.client.core.Errors.report("CooldownHud", e); }
    }

    @Inject(method = "removeCooldown(Lnet/minecraft/resources/Identifier;)V", at = @At("HEAD"), require = 0)
    private void vortex$stop(Identifier group, CallbackInfo ci) {
        try { com.vortex.client.hud.CooldownHud.removed((ItemCooldowns) (Object) this, group); }
        catch (Throwable e) { com.vortex.client.core.Errors.report("CooldownHud", e); }
    }
}
