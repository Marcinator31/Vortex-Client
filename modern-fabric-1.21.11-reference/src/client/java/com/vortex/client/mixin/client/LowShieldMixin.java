package com.vortex.client.mixin.client;

import com.vortex.client.module.ModuleManager;
import com.vortex.client.module.modules.LowShieldModule;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Low Shield -- verschiebt das gehaltene Schild nach unten. Vorbild: BactroMod.
 *
 * Signatur jetzt mit den ECHTEN 1.21.11-Typen (aus HeldItemRenderer-Javadoc):
 *   renderItem(LivingEntity, ItemStack, ItemDisplayContext, MatrixStack,
 *              OrderedRenderCommandQueue, int light)
 * intermediary method_3233.
 *
 * Der vorige Crash kam daher, dass ich Object statt der echten Typen
 * (class_811 = ItemDisplayContext, class_11659 = OrderedRenderCommandQueue)
 * verwendet hatte -- Mixin braucht exakte Typen.
 */
@Mixin(HeldItemRenderer.class)
public class LowShieldMixin {

    @Inject(method = "method_3233", at = @At("HEAD"))
    private void pvpclient$lowShield(LivingEntity entity, ItemStack stack,
                                     ItemDisplayContext displayContext, MatrixStack matrices,
                                     OrderedRenderCommandQueue queue, int light, CallbackInfo ci) {
        LowShieldModule mod = find();
        if (mod != null && mod.isEnabled() && stack.isOf(Items.SHIELD)) {
            matrices.translate(0.0, -0.25, 0.0);
        }
    }

    private static LowShieldModule find() {
        // Konstante Laufzeit statt die ganze Modul-Liste zu durchlaufen --
        // diese Methode wird in Render-Pfaden sehr haeufig aufgerufen.
        return ModuleManager.INSTANCE.get(LowShieldModule.class);
    }
}
