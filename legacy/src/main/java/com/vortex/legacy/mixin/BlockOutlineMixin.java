package com.vortex.legacy.mixin;

import com.vortex.legacy.core.ModuleManager;
import com.vortex.legacy.module.visual.BlockOutline;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldRenderer.class)
public abstract class BlockOutlineMixin {
    @Inject(method = "drawBlockOutline", at = @At("HEAD"), cancellable = true)
    private void vortex$rahmen(PlayerEntity player, BlockHitResult hit, int i, float tickDelta, CallbackInfo ci) {
        BlockOutline o = ModuleManager.INSTANCE.get(BlockOutline.class);
        if (o == null || !o.isEnabled()) return;
        ci.cancel();
        if (i != 0 || hit.type != BlockHitResult.Type.BLOCK) return;
        try { o.draw(hit.getBlockPos(), tickDelta); } catch (Throwable t) { com.vortex.legacy.core.Errors.report("BlockOutline", t); }
    }
}
