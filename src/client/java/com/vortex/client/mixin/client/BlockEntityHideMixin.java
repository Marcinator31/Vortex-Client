package com.vortex.client.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hides separately rendered block entities selected by NoRenderBlocks. */
@Mixin(BlockEntityRenderDispatcher.class)
public final class BlockEntityHideMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true, require = 0)
    private <E extends BlockEntity> void vortex$hideBlockEntity(E blockEntity, float partialTick,
                                                                PoseStack poseStack,
                                                                MultiBufferSource buffers,
                                                                CallbackInfo ci) {
        try {
            var module = com.vortex.client.module.ModuleManager.INSTANCE.get(
                    com.vortex.client.module.modules.NoRenderBlocksModule.class);
            if (module == null || !module.isEnabled() || module.getHiddenBlocks().isEmpty()
                    || blockEntity == null) return;
            var id = BuiltInRegistries.BLOCK.getKey(blockEntity.getBlockState().getBlock());
            if (id != null && module.isHidden(id.toString())) ci.cancel();
        } catch (Throwable error) {
            com.vortex.client.core.Errors.report("BlockEntityHideMixin", error);
        }
    }
}
