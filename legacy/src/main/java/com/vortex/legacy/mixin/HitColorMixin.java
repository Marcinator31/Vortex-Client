package com.vortex.legacy.mixin;

import com.vortex.legacy.core.ModuleManager;
import com.vortex.legacy.module.pvp.HitColor;
import java.nio.FloatBuffer;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Hit Color: die vier Werte (1, 0, 0, 0.3) der Treffer-Farbe ersetzen. */
@Mixin(LivingEntityRenderer.class)
public abstract class HitColorMixin {
    private static HitColor hc() {
        HitColor h = ModuleManager.INSTANCE.get(HitColor.class);
        return h != null && h.isEnabled() ? h : null;
    }
    @Redirect(method = "method_10252", at = @At(value = "INVOKE", target = "Ljava/nio/FloatBuffer;put(F)Ljava/nio/FloatBuffer;", ordinal = 0), require = 0)
    private FloatBuffer vortex$r(FloatBuffer b, float v) { HitColor h = hc(); return b.put(h != null ? h.r() : v); }
    @Redirect(method = "method_10252", at = @At(value = "INVOKE", target = "Ljava/nio/FloatBuffer;put(F)Ljava/nio/FloatBuffer;", ordinal = 1), require = 0)
    private FloatBuffer vortex$g(FloatBuffer b, float v) { HitColor h = hc(); return b.put(h != null ? h.g() : v); }
    @Redirect(method = "method_10252", at = @At(value = "INVOKE", target = "Ljava/nio/FloatBuffer;put(F)Ljava/nio/FloatBuffer;", ordinal = 2), require = 0)
    private FloatBuffer vortex$b(FloatBuffer b, float v) { HitColor h = hc(); return b.put(h != null ? h.b() : v); }
    @Redirect(method = "method_10252", at = @At(value = "INVOKE", target = "Ljava/nio/FloatBuffer;put(F)Ljava/nio/FloatBuffer;", ordinal = 3), require = 0)
    private FloatBuffer vortex$a(FloatBuffer b, float v) { HitColor h = hc(); return b.put(h != null ? h.a() : v); }
}
