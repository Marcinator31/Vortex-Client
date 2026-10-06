package com.vortex.legacy.mixin;

import com.vortex.legacy.core.ModuleManager;
import com.vortex.legacy.module.pvp.HealthIndicator;
import com.vortex.legacy.module.visual.Nametags;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntityRenderer.class)
public abstract class NametagMixin {
    @Inject(method = "hasLabel", at = @At("RETURN"), cancellable = true)
    private void vortex$eigenes(LivingEntity e, CallbackInfoReturnable<Boolean> cir) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (e != mc.player || cir.getReturnValueZ() || mc.options.perspective == 0 || !MinecraftClient.isHudEnabled()) return;
        Nametags n = ModuleManager.INSTANCE.get(Nametags.class);
        if (n != null && n.isEnabled() && !e.isInvisible()) cir.setReturnValue(true);
    }

    @ModifyVariable(method = "method_10256", at = @At("STORE"), ordinal = 0)
    private String vortex$leben(String name, LivingEntity e, double x, double y, double z) {
        HealthIndicator h = ModuleManager.INSTANCE.get(HealthIndicator.class);
        if (h == null || !h.isEnabled()) return name;
        try { return h.label(e, name); } catch (Throwable t) { return name; }
    }
}
