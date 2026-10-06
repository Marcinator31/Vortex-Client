package com.vortex.legacy.mixin;

import com.vortex.legacy.core.ModuleManager;
import com.vortex.legacy.module.visual.GlintColor;
import net.minecraft.client.render.item.ItemRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(ItemRenderer.class)
public abstract class GlintMixin {
    @ModifyConstant(method = "renderGlint", constant = @Constant(intValue = -8372020))
    private int vortex$farbe(int c) {
        GlintColor g = ModuleManager.INSTANCE.get(GlintColor.class);
        return g != null && g.isEnabled() ? (g.color.get() & 0xFFFFFF) | 0xFF000000 : c;
    }
}
