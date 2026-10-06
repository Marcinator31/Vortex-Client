package com.vortex.legacy.mixin;

import com.vortex.legacy.core.ModuleManager;
import com.vortex.legacy.module.visual.NoParticles;
import net.minecraft.block.BlockState;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ParticleManager.class)
public abstract class ParticleMixin {
    @Inject(method = "addBlockBreakParticles", at = @At("HEAD"), cancellable = true)
    private void vortex$abbau(BlockPos pos, BlockState state, CallbackInfo ci) {
        NoParticles n = ModuleManager.INSTANCE.get(NoParticles.class);
        if (n != null && n.isEnabled() && n.blockBreak.get()) ci.cancel();
    }

    @Inject(method = "addBlockBreakingParticles", at = @At("HEAD"), cancellable = true)
    private void vortex$schlagen(BlockPos pos, Direction dir, CallbackInfo ci) {
        NoParticles n = ModuleManager.INSTANCE.get(NoParticles.class);
        if (n != null && n.isEnabled() && n.blockHit.get()) ci.cancel();
    }
}
