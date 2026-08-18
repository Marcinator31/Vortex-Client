package com.vortex.client.mixin.client;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds the reconnect button to the disconnect screen.
 *
 * Only the button is created here. The countdown and the label run on the
 * client tick, because DisconnectedScreen has no tick method of its own -- an
 * injection there would silently do nothing, and a countdown that never counts
 * is worse than none at all.
 */
@Mixin(DisconnectedScreen.class)
public abstract class AutoReconnectMixin extends Screen {

    protected AutoReconnectMixin(Component title) {
        super(title);
    }

    @Inject(method = "method_25426", at = @At("TAIL"), require = 0)
    private void vortex$addReconnect(CallbackInfo ci) {
        try {
            com.vortex.client.hud.AutoReconnect.onDisconnected(
                    (DisconnectedScreen) (Object) this);

            Button button = Button.builder(
                    Component.literal(com.vortex.client.hud.AutoReconnect.buttonLabel()),
                    b -> com.vortex.client.hud.AutoReconnect.buttonPressed())
                    .bounds(this.width / 2 - 100, this.height - 30, 200, 20)
                    .build();
            this.addRenderableWidget(button);

            // Handed over so the countdown can keep the label current.
            com.vortex.client.hud.AutoReconnect.setButton(button);
        } catch (Throwable pvpErr) {
            com.vortex.client.core.Errors.report("AutoReconnectMixin", pvpErr);
        }
    }
}
