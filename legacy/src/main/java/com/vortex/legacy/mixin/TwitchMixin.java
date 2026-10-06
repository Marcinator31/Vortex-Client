package com.vortex.legacy.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TwitchErrorScreen;
import net.minecraft.client.util.NullTwitchStream;
import net.minecraft.client.util.TwitchStreamProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Optionen > "Broadcast Settings" liess 1.8.9 abstuerzen (NullPointerException
 * in TwitchErrorScreen.openNew): Die Twitch-Bibliothek laedt heute nicht mehr,
 * und ihr Fehler hat oft keine Meldung -- Minecraft ruft trotzdem
 * getMessage().contains(...) auf. Dann zeigen wir direkt "nicht verfuegbar".
 */
@Mixin(TwitchErrorScreen.class)
public abstract class TwitchMixin {
    @Inject(method = "openNew", at = @At("HEAD"), cancellable = true)
    private static void vortex$ohneAbsturz(Screen parent, CallbackInfo ci) {
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            TwitchStreamProvider p = mc.getTwitchStreamProvider();
            if (p == null || (p instanceof NullTwitchStream
                    && (((NullTwitchStream) p).getThrowable() == null || ((NullTwitchStream) p).getThrowable().getMessage() == null))) {
                mc.setScreen(new TwitchErrorScreen(parent, TwitchErrorScreen.ErrorCause.LIBRARY_FAILURE));
                ci.cancel();
            }
        } catch (Throwable t) {
            com.vortex.legacy.core.Errors.report("TwitchMixin", t);
        }
    }
}
