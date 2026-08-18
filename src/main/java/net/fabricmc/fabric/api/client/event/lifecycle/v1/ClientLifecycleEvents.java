package net.fabricmc.fabric.api.client.event.lifecycle.v1;

import net.minecraft.client.Minecraft;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/** Forge-1.20.1 shutdown adapter used to persist Vortex configuration safely. */
public final class ClientLifecycleEvents {
    public static final ClientStopping CLIENT_STOPPING = new ClientStopping();
    private ClientLifecycleEvents() {}

    public static final class ClientStopping {
        private final List<Consumer<Minecraft>> callbacks = new CopyOnWriteArrayList<>();
        private ClientStopping() {
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                Minecraft client;
                try { client = Minecraft.getInstance(); } catch (Throwable ignored) { client = null; }
                for (Consumer<Minecraft> callback : callbacks) {
                    try { callback.accept(client); } catch (Throwable ignored) { }
                }
            }, "vortexclient-config-save"));
        }
        public void register(Consumer<Minecraft> callback) { callbacks.add(callback); }
    }
}
