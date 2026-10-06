package com.vortex.legacy.mixin;

import com.mojang.blaze3d.platform.GlStateManager;
import com.vortex.legacy.core.ModuleManager;
import com.vortex.legacy.module.hud.PlayerListModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.PlayerListHud;
import net.minecraft.client.network.PlayerListEntry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerListHud.class)
public abstract class PlayerListMixin {
    @Inject(method = "renderLatencyIcon", at = @At("HEAD"), cancellable = true)
    private void vortex$ping(int width, int x, int y, PlayerListEntry e, CallbackInfo ci) {
        PlayerListModule m = ModuleManager.INSTANCE.get(PlayerListModule.class);
        if (m == null || !m.isEnabled()) return;
        ci.cancel();
        int p = e.getLatency();
        String s = p < 0 ? "?" : String.valueOf(p);
        int c = p < 0 ? 0xAAAAAA : p < 80 ? 0x55FF55 : p < 150 ? 0xA0FF55 : p < 300 ? 0xFFFF55 : p < 600 ? 0xFFAA00 : 0xFF5555;
        MinecraftClient mc = MinecraftClient.getInstance();
        GlStateManager.pushMatrix();
        GlStateManager.translate(x + width - 1, y + 1.5f, 0);
        GlStateManager.scale(0.75f, 0.75f, 1);
        mc.textRenderer.draw(s, -mc.textRenderer.getStringWidth(s), 0, c, true);
        GlStateManager.popMatrix();
    }
}
