package com.vortex.client.module.modules;

import com.mojang.blaze3d.platform.InputConstants;
import com.vortex.client.module.Module;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.platform.InputConstants;

/**
 * Keeps you sneaking without holding the key.
 *
 * Done by holding the sneak binding down rather than by setting the state on
 * the player: sneaking is not just a flag, it changes eye height, step height
 * and whether you walk off an edge. Going through the binding means the game
 * handles all of that itself, exactly as it would with a held key.
 */
public class ToggleSneakModule extends Module {

    public ToggleSneakModule() {
        super("Toggle Sneak", Category.PVP);
        ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
    }

    private void onTick(Minecraft client) {
        try {
            if (client.player == null) return;

            // While a screen is open the key must be released, otherwise you
            // stay stuck in a crouch inside your inventory.
            boolean want = isEnabled() && client.gui.screen() == null;

            var binding = client.options.keyShift;
            if (binding == null) return;

            net.minecraft.client.KeyMapping.set(
                    InputConstants.getKey(binding.saveString()),
                    want || binding.isDown());
        } catch (Throwable pvpErr) {
            com.vortex.client.core.Errors.report("ToggleSneak", pvpErr);
        }
    }

    @Override
    protected void onDisable() {
        // Let go at once, so you are not left crouching after switching off.
        try {
            var client = Minecraft.getInstance();
            var binding = client.options.keyShift;
            if (binding != null) {
                net.minecraft.client.KeyMapping.set(
                        InputConstants.getKey(binding.saveString()),
                        false);
            }
        } catch (Throwable pvpErr) {
            com.vortex.client.core.Errors.report("ToggleSneak.disable", pvpErr);
        }
    }
}
