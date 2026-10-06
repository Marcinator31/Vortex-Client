package com.vortex.legacy.mixin;

import com.vortex.legacy.core.ModuleManager;
import com.vortex.legacy.module.misc.StreamerMode;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(TextRenderer.class)
public abstract class StreamerMixin {
    private static StreamerMode vx$m;

    @ModifyVariable(method = "draw(Ljava/lang/String;FFIZ)I", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private String vortex$name(String s) {
        if (s == null) return null;
        if (vx$m == null) vx$m = ModuleManager.INSTANCE.get(StreamerMode.class);
        if (vx$m == null || !vx$m.isEnabled()) return s;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.getSession() == null) return s;
        String n = mc.getSession().getUsername();
        return n != null && n.length() > 2 && s.contains(n) ? s.replace(n, "You") : s;
    }
}
