package com.vortex.client.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.vortex.client.module.ModuleManager;
import com.vortex.client.module.modules.HandItemScaleModule;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.mojang.blaze3d.vertex.PoseStack;

/**
 * Item-Groesse (Hand): macht das gehaltene Item in der ersten Person kleiner.
 *
 * Gebaut nach demselben Muster wie LowShieldMixin (das funktioniert):
 *   - @Mixin(ItemInHandRenderer)
 *   - @Inject in method_3233 (renderItem) @HEAD -- diese Methode rendert jedes
 *     gehaltene Item (Haupt- und Nebenhand).
 *   - intermediary-Name method_3233 statt voller Yarn-Signatur (robuster).
 *
 * Signatur (aus den Mappings):
 *   renderItem(LivingEntity, ItemStack, ItemDisplayContext, PoseStack,
 *              SubmitNodeCollector, int light)
 */
@Mixin(ItemInHandRenderer.class)
public class HeldItemRendererMixin {

    @Inject(method = "method_3233", at = @At("HEAD"))
    private void pvpclient$scaleHandItem(LivingEntity entity, ItemStack stack,
                                         ItemDisplayContext displayContext, PoseStack matrices,
                                         SubmitNodeCollector queue, int light,
                                         CallbackInfo ci) {
        HandItemScaleModule mod = find();
        if (mod != null && mod.isEnabled()) {
            float scale = (float) mod.size.get();
            if (scale != 1.0f) {
                matrices.scale(scale, scale, scale);
            }
        }
    }

    private static HandItemScaleModule find() {
        // Konstante Laufzeit statt die ganze Modul-Liste zu durchlaufen --
        // diese Methode wird in Render-Pfaden sehr haeufig aufgerufen.
        return ModuleManager.INSTANCE.get(HandItemScaleModule.class);
    }
}
