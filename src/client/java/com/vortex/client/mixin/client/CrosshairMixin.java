package com.vortex.client.mixin.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Draws our crosshair instead of the vanilla one.
 *
 * The original call is cancelled rather than drawn over: vanilla inverts the
 * colours underneath, and leaving that in place would show through anything
 * put on top of it.
 */
@Mixin(InGameHud.class)
public abstract class CrosshairMixin {

    @Inject(method = "method_1736", at = @At("HEAD"), cancellable = true, require = 0)
    private void vortex$customCrosshair(DrawContext ctx,
                                        net.minecraft.client.render.RenderTickCounter tickCounter,
                                        CallbackInfo ci) {
        try {
            var mod = com.vortex.client.module.ModuleManager.INSTANCE.get(
                    com.vortex.client.module.modules.CrosshairModule.class);
            if (mod == null || !mod.isEnabled()) return;

            var client = net.minecraft.client.MinecraftClient.getInstance();
            if (client == null) return;

            boolean first = client.options.getPerspective().isFirstPerson();
            if (!first && !mod.thirdPerson.get()) {
                ci.cancel();     // hide it entirely rather than draw the vanilla one
                return;
            }

            com.vortex.client.hud.CrosshairRenderer.draw(ctx, client, mod);
            ci.cancel();
        } catch (Throwable pvpErr) {
            com.vortex.client.core.Errors.report("CrosshairMixin", pvpErr);
        }
    }
}
