package com.vortex.client.mixin.client;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hides chests, signs, beds and the like.
 *
 * These are the gap No Render Blocks could not close. They are blocks, but they
 * are drawn by a renderer of their own rather than from the block model, so
 * reporting them as invisible had no effect on them -- a hidden chest stayed
 * exactly where it was.
 *
 * The hook is the distance check: a plain yes-or-no question asked once per
 * block entity. Answering no simply leaves it out, which is precisely what
 * happens for anything too far away -- a path the game takes constantly and
 * handles without complaint.
 */
@Mixin(BlockEntityRenderer.class)
/*
 * An interface, because the target is one -- a mixin has to match. Private
 * methods inside an interface are allowed from Java 9 onwards, and this runs
 * on 21.
 */
public interface BlockEntityHideMixin {

    @Inject(method = "method_33892", at = @At("HEAD"), cancellable = true, require = 0)
    private void vortex$hideBlockEntity(BlockEntity blockEntity, Vec3d cameraPos,
                                        CallbackInfoReturnable<Boolean> cir) {
        try {
            var mod = com.vortex.client.module.ModuleManager.INSTANCE.get(
                    com.vortex.client.module.modules.NoRenderBlocksModule.class);
            if (mod == null || !mod.isEnabled()) return;
            if (mod.getHiddenBlocks().isEmpty()) return;
            if (blockEntity == null) return;

            var id = Registries.BLOCK.getId(blockEntity.getCachedState().getBlock());
            if (id != null && mod.isHidden(id.toString())) {
                cir.setReturnValue(false);
            }
        } catch (Throwable pvpErr) {
            com.vortex.client.core.Errors.report("BlockEntityHideMixin", pvpErr);
        }
    }
}
