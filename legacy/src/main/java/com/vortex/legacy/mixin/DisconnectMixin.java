package com.vortex.legacy.mixin;

import com.vortex.legacy.core.ModuleManager;
import com.vortex.legacy.module.misc.AutoReconnect;
import net.minecraft.client.gui.screen.ConnectScreen;
import net.minecraft.client.gui.screen.DisconnectedScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Auto Reconnect: zweiter Knopf mit Countdown. */
@Mixin(DisconnectedScreen.class)
public abstract class DisconnectMixin extends Screen {
    private long vx$until;
    private ButtonWidget vx$button;

    @Inject(method = "init", at = @At("TAIL"))
    private void vortex$knopf(CallbackInfo ci) {
        AutoReconnect ar = ModuleManager.INSTANCE.get(AutoReconnect.class);
        if (ar == null || !ar.isEnabled() || AutoReconnect.last == null || buttons.isEmpty()) return;
        ButtonWidget first = (ButtonWidget) buttons.get(0);
        vx$button = new ButtonWidget(4242, first.x, first.y + 24, "Reconnect");
        buttons.add(vx$button);
        vx$until = System.currentTimeMillis() + ar.seconds.getInt() * 1000L;
    }

    @Inject(method = "buttonClicked", at = @At("HEAD"), cancellable = true)
    private void vortex$klick(ButtonWidget b, CallbackInfo ci) {
        if (b.id == 4242) { ci.cancel(); vx$verbinden(); }
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void vortex$countdown(int mx, int my, float d, CallbackInfo ci) {
        if (vx$button == null || vx$until == 0) return;
        long left = vx$until - System.currentTimeMillis();
        vx$button.message = "Reconnect in " + Math.max(0, (left + 999) / 1000) + "s";
        if (left <= 0) { vx$until = 0; vx$verbinden(); }
    }

    private void vx$verbinden() {
        if (AutoReconnect.last == null) return;
        client.setScreen(new ConnectScreen(new MultiplayerScreen(new TitleScreen()), client, AutoReconnect.last));
    }
}
