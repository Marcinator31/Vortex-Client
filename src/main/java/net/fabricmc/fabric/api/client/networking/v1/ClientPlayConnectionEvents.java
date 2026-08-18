package net.fabricmc.fabric.api.client.networking.v1;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.common.MinecraftForge;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Forge-backed connection lifecycle adapter for Vortex's server-specific state. */
public final class ClientPlayConnectionEvents {
    public static final Event JOIN = new Event(true);
    public static final Event DISCONNECT = new Event(false);
    private ClientPlayConnectionEvents() {}

    @FunctionalInterface
    public interface Callback {
        void call(ClientPacketListener handler, Object sender, Minecraft client);
    }

    public static final class Event {
        private final List<Callback> callbacks = new CopyOnWriteArrayList<>();
        private Event(boolean join) {
            if (join) {
                MinecraftForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingIn event) -> fire());
            } else {
                MinecraftForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> fire());
            }
        }
        public void register(Callback callback) { callbacks.add(callback); }
        private void fire() {
            Minecraft client = Minecraft.getInstance();
            ClientPacketListener handler = client.getConnection();
            for (Callback callback : callbacks) callback.call(handler, null, client);
        }
    }
}
