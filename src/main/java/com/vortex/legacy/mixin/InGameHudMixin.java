package com.vortex.legacy.mixin;

import com.vortex.legacy.VortexLegacyClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.InGameHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Renders the first migrated Vortex HUD elements on the 1.13.2 in-game HUD. */
@Mixin(InGameHud.class)
public abstract class InGameHudMixin {
    @Inject(method = "render", at = @At("TAIL"))
    private void vortex$renderHud(float tickDelta, CallbackInfo callbackInfo) {
        VortexLegacyClient.ClientState state = VortexLegacyClient.state();
        if (!state.isHudEnabled()) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.textRenderer == null) {
            return;
        }

        int y = 4;
        client.textRenderer.drawWithShadow("Vortex Client | Legacy Fabric 1.13.2", 4.0F, (float) y, 0x55DDFF);
        y += 11;
        client.textRenderer.drawWithShadow("FPS: " + MinecraftClient.getCurrentFps(), 4.0F, (float) y, 0xFFFFFF);
        y += 10;

        if (client.player != null) {
            String coordinates = String.format("XYZ: %.1f / %.1f / %.1f", client.player.x, client.player.y, client.player.z);
            client.textRenderer.drawWithShadow(coordinates, 4.0F, (float) y, 0xFFFFFF);
            y += 10;
        }

        String status = "F6 HUD | F7 Fullbright: " + state.isFullbrightEnabled()
                + " | F8 ToggleSprint: " + state.isToggleSprintEnabled();
        client.textRenderer.drawWithShadow(status, 4.0F, (float) y, 0xAAAAAA);
    }
}
