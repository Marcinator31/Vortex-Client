package com.vortex.client.hud;

import com.vortex.client.core.Errors;
import com.vortex.client.module.ModuleManager;
import com.vortex.client.module.modules.AutoReconnectModule;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.DisconnectedScreen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;

/**
 * Counts down on the disconnect screen and dials back in.
 *
 * The countdown runs on the client tick rather than inside the screen, because
 * DisconnectedScreen has no tick method of its own -- hooking one there would
 * quietly do nothing.
 *
 * Which server to return to is remembered while you are still connected: once
 * the disconnect screen is up, the client no longer knows where you were.
 */
public final class AutoReconnect {

    /** The last server we were actually connected to. */
    private static ServerInfo lastServer = null;

    /** Ticks left before the next attempt, or -1 when nothing is pending. */
    private static int ticksLeft = -1;

    /** How many attempts have been made since the last successful connect. */
    private static int tries = 0;

    /** Set while the countdown is stopped by hand. */
    private static boolean cancelled = false;

    /** The button on the disconnect screen, so its label can be kept current. */
    private static net.minecraft.client.gui.widget.ButtonWidget button = null;

    public static void setButton(net.minecraft.client.gui.widget.ButtonWidget b) {
        button = b;
    }

    private AutoReconnect() {}

    private static AutoReconnectModule module() {
        return ModuleManager.INSTANCE.get(AutoReconnectModule.class);
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            try {
                remember(client);
                tick(client);
            } catch (Throwable pvpErr) {
                Errors.report("AutoReconnect", pvpErr);
            }
        });
    }

    /**
     * Notes the current server while still connected.
     *
     * Has to happen here: by the time the disconnect screen appears the entry
     * is gone, and there would be nothing left to reconnect to.
     */
    private static void remember(MinecraftClient client) {
        if (client.world == null) return;
        var entry = client.getCurrentServerEntry();
        if (entry != null) {
            lastServer = entry;
            tries = 0;          // a working connection clears the count
        }
    }

    /** Called when the disconnect screen opens. */
    public static void onDisconnected(DisconnectedScreen screen) {
        cancelled = false;
        ticksLeft = -1;

        AutoReconnectModule mod = module();
        if (mod == null || !mod.isEnabled()) return;
        if (lastServer == null) return;

        if (mod.skipKicks.get() && looksLikeAKick(screen)) {
            return;
        }
        if (mod.attempts.getInt() > 0 && tries >= mod.attempts.getInt()) {
            return;
        }

        ticksLeft = waitTicks(mod);
    }

    /**
     * Guesses whether the server threw us out on purpose.
     *
     * A guess, and it says so: the reason is free text written by the server,
     * in whatever language it likes. Common words are checked, nothing more.
     * Getting it wrong costs a pointless reconnect attempt, which is why the
     * check is allowed to be this rough.
     */
    private static boolean looksLikeAKick(DisconnectedScreen screen) {
        try {
            var info = ((com.vortex.client.mixin.client.DisconnectInfoAccessor) screen)
                    .vortex$getInfo();
            if (info == null) return false;
            String reason = info.reason().getString().toLowerCase(java.util.Locale.ROOT);
            String[] words = { "ban", "kick", "gebannt", "gekickt", "gesperrt",
                               "suspend", "blacklist", "cheat" };
            for (String w : words) {
                if (reason.contains(w)) return true;
            }
            return false;
        } catch (Throwable pvpErr) {
            return false;
        }
    }

    /** How long to wait before the next attempt, in ticks. */
    private static int waitTicks(AutoReconnectModule mod) {
        int seconds = mod.delay.getInt();
        if (mod.backoff.get() && tries > 0) {
            // Doubles each time: 5, 10, 20, 40 ... A server coming back up is
            // found within a minute or two, without knocking constantly while
            // it is still down.
            long grown = (long) seconds << Math.min(tries, 6);
            seconds = (int) Math.min(grown, mod.maxDelay.getInt());
        }
        return Math.max(1, seconds) * 20;
    }

    /** Advances the countdown; connects when it runs out. */
    public static void tick(MinecraftClient client) {
        if (ticksLeft < 0 || cancelled) return;
        if (!(client.currentScreen instanceof DisconnectedScreen)) {
            // Screen gone -- someone moved on, so the countdown is void.
            ticksLeft = -1;
            return;
        }

        ticksLeft--;

        // Keep the countdown on the button visible.
        if (button != null) {
            button.setMessage(net.minecraft.text.Text.literal(buttonLabel()));
        }

        if (ticksLeft > 0) return;

        ticksLeft = -1;
        connectNow(client);
    }

    /** Starts the connection attempt. */
    public static void connectNow(MinecraftClient client) {
        if (lastServer == null) return;
        try {
            tries++;
            var parent = new MultiplayerScreen(new TitleScreen());
            ConnectScreen.connect(parent, client,
                    ServerAddress.parse(lastServer.address),
                    lastServer, false, null);
        } catch (Throwable pvpErr) {
            Errors.report("AutoReconnect.connect", pvpErr);
        }
    }

    /** Text for the button on the disconnect screen. */
    public static String buttonLabel() {
        if (lastServer == null) return "Reconnect";
        if (cancelled || ticksLeft < 0) return "Reconnect now";
        int seconds = (ticksLeft + 19) / 20;
        return "Reconnecting in " + seconds + "s  (click to cancel)";
    }

    /** Button pressed: cancel a running countdown, otherwise connect at once. */
    public static void buttonPressed() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (ticksLeft > 0 && !cancelled) {
            cancelled = true;
            ticksLeft = -1;
            return;
        }
        connectNow(client);
    }

    /** Is a countdown running? */
    public static boolean isPending() {
        return ticksLeft > 0 && !cancelled;
    }
}
