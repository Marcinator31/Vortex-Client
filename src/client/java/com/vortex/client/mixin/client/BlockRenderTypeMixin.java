package com.vortex.client.mixin.client;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.BlockRenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Makes selected blocks invisible.
 *
 * Every block tells the game how it should be drawn, and INVISIBLE is a value
 * the game already uses for things like barriers. Reporting that value for a
 * chosen block means it is left out when the chunk mesh is built — so it costs
 * nothing per frame, unlike skipping it while drawing.
 *
 * Only the appearance changes. The block still blocks movement, still takes the
 * same time to break, and the server never learns about any of it.
 *
 * NOTE ON WHAT THIS CANNOT DO: blocks drawn by a block entity — chests, signs,
 * beds, shulker boxes — do not go through this path. Their model is drawn
 * separately, so hiding them needs a different hook. Everything built from the
 * normal block model, which is the vast majority, works here.
 */
@Mixin(AbstractBlock.AbstractBlockState.class)
public abstract class BlockRenderTypeMixin {

    @Inject(method = "method_26217", at = @At("HEAD"), cancellable = true, require = 0)
    private void vortex$hideSelectedBlocks(CallbackInfoReturnable<BlockRenderType> cir) {
        try {
            com.vortex.client.module.modules.NoRenderBlocksModule mod =
                    com.vortex.client.module.ModuleManager.INSTANCE.get(
                            com.vortex.client.module.modules.NoRenderBlocksModule.class);
            if (mod == null || !mod.isEnabled()) return;
            if (mod.getHiddenBlocks().isEmpty()) return;

            AbstractBlock.AbstractBlockState self = (AbstractBlock.AbstractBlockState) (Object) this;
            var id = net.minecraft.registry.Registries.BLOCK.getId(self.getBlock());
            if (id == null) return;

            if (mod.isHidden(id.toString())) {
                cir.setReturnValue(BlockRenderType.INVISIBLE);
            }
        } catch (Throwable pvpErr) {
            com.vortex.client.core.Errors.report("BlockRenderTypeMixin", pvpErr);
        }
    }
}
