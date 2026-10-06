package com.vortex.legacy.mixin;

import com.vortex.legacy.core.ModuleManager;
import com.vortex.legacy.module.hud.ScoreboardModule;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.util.Window;
import net.minecraft.scoreboard.ScoreboardObjective;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
public abstract class ScoreboardMixin {
    private static ScoreboardModule vx$m() {
        ScoreboardModule m = ModuleManager.INSTANCE.get(ScoreboardModule.class);
        return m != null && m.isEnabled() ? m : null;
    }

    @Inject(method = "renderScoreboardObjective", at = @At("HEAD"), cancellable = true)
    private void vortex$aus(ScoreboardObjective o, Window w, CallbackInfo ci) {
        ScoreboardModule m = vx$m();
        if (m != null && m.hide.get()) ci.cancel();
    }

    @Redirect(method = "renderScoreboardObjective", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/font/TextRenderer;draw(Ljava/lang/String;III)I", ordinal = 1))
    private int vortex$zahl(TextRenderer tr, String s, int x, int y, int c) {
        ScoreboardModule m = vx$m();
        return m != null && m.hideNumbers.get() ? 0 : tr.draw(s, x, y, c);
    }

    @Redirect(method = "renderScoreboardObjective", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/hud/InGameHud;fill(IIIII)V"))
    private void vortex$grund(int a, int b, int c, int d, int col) {
        ScoreboardModule m = vx$m();
        if (m == null || m.background.get()) net.minecraft.client.gui.DrawableHelper.fill(a, b, c, d, col);
    }
}
