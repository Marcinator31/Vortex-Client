package com.vortex.client.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.registries.BuiltInRegistries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * No Render Blocks fuer Kisten, Schilder, Betten und Co.
 *
 * Diese Bloecke zeichnet ein eigener Renderer, nicht das Blockmodell --
 * deshalb reichte es nicht, sie beim Blockmodell auszublenden.
 *
 * DAS ALTE WAR TOT: Es hing an "method_33892", einem Namen aus 1.21, den es in
 * 26.2 nicht gibt -- Kisten blieben immer sichtbar. Jetzt: der zentrale
 * Verteiler BlockEntityRenderDispatcher.submit, dieselbe Stelle und Signatur
 * wie Meteor fuer 26.2 (NoRender, Xray).
 */
@Mixin(BlockEntityRenderDispatcher.class)
public abstract class BlockEntityHideMixin {

    @Inject(method = "submit", at = @At("HEAD"), cancellable = true, require = 0)
    private <S extends BlockEntityRenderState> void vortex$hideBlockEntity(S state, PoseStack poseStack,
            SubmitNodeCollector collector, CameraRenderState camera, CallbackInfo ci) {
        try {
            var mod = com.vortex.client.module.ModuleManager.INSTANCE.get(
                    com.vortex.client.module.modules.NoRenderBlocksModule.class);
            if (mod == null || !mod.isEnabled()) return;
            if (mod.getHiddenBlocks().isEmpty()) return;
            var bs = ((BlockEntityStateAccessor) state).vortex$getBlockState();
            if (bs == null) return;
            var id = BuiltInRegistries.BLOCK.getKey(bs.getBlock());
            if (id != null && mod.isHidden(id.toString())) ci.cancel();
        } catch (Throwable pvpErr) {
            com.vortex.client.core.Errors.report("BlockEntityHideMixin", pvpErr);
        }
    }
}
