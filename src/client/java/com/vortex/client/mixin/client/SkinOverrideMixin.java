package com.vortex.client.mixin.client;

import com.vortex.client.skin.ActiveSkin;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Client-only wardrobe override for the classical 1.20.1 player skin path.
 * It affects only the local player's rendered skin and model name; remote
 * player skins, cape and elytra data remain supplied by Minecraft unchanged.
 */
@Mixin(AbstractClientPlayer.class)
public abstract class SkinOverrideMixin {
    @Inject(method = "getSkinTextureLocation", at = @At("HEAD"), cancellable = true, require = 0)
    private void vortex$overrideOwnSkin(CallbackInfoReturnable<ResourceLocation> cir) {
        try {
            if ((Object) this != Minecraft.getInstance().player) return;
            ResourceLocation texture = ActiveSkin.textureId();
            if (texture != null) cir.setReturnValue(texture);
        } catch (Throwable error) {
            com.vortex.client.core.Errors.report("SkinOverride.texture", error);
        }
    }

    @Inject(method = "getModelName", at = @At("HEAD"), cancellable = true, require = 0)
    private void vortex$overrideOwnModel(CallbackInfoReturnable<String> cir) {
        try {
            if ((Object) this != Minecraft.getInstance().player || ActiveSkin.get() == null) return;
            cir.setReturnValue(ActiveSkin.isSlim() ? "slim" : "default");
        } catch (Throwable error) {
            com.vortex.client.core.Errors.report("SkinOverride.model", error);
        }
    }
}
