package com.vortex.client.mixin.client;

import com.vortex.client.cosmetics.SchildSkins;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Shield Skins: beim Aufbereiten eines Items merken, wem es gehoert (siehe SchildSkins). */
@Mixin(ItemModelResolver.class)
public abstract class SchildBesitzerMixin {
    @Inject(method = "updateForTopItem", at = @At("HEAD"))
    private void vortex$besitzer(ItemStackRenderState state, ItemStack stack, ItemDisplayContext ctx, Level level, ItemOwner owner, int seed, CallbackInfo ci) {
        SchildSkins.besitzer(owner);
    }

    @Inject(method = "updateForTopItem", at = @At("RETURN"))
    private void vortex$fertig(ItemStackRenderState state, ItemStack stack, ItemDisplayContext ctx, Level level, ItemOwner owner, int seed, CallbackInfo ci) {
        SchildSkins.besitzer(null);
    }
}
