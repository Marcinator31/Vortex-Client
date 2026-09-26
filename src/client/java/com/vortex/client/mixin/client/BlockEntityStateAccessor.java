package com.vortex.client.mixin.client;

import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Welcher Block gehoert zu diesem Renderzustand? Wie in Meteor fuer 26.2. */
@Mixin(BlockEntityRenderState.class)
public interface BlockEntityStateAccessor {
    @Accessor("blockState")
    BlockState vortex$getBlockState();
}
