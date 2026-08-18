package net.fabricmc.fabric.api.client.event.lifecycle.v1;

import net.minecraft.client.Minecraft;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/** Forge-backed END client tick adapter used by Vortex modules and macros. */
public final class ClientTickEvents {
    public static final EndTick END_CLIENT_TICK = new EndTick();
    private ClientTickEvents() {}
    public static final class EndTick {
        private final List<Consumer<Minecraft>> callbacks = new CopyOnWriteArrayList<>();
        private EndTick() {
            MinecraftForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent event) -> {
                if (event.phase == TickEvent.Phase.END) {
                    Minecraft client = Minecraft.getInstance();
                    for (Consumer<Minecraft> callback : callbacks) callback.accept(client);
                }
            });
        }
        public void register(Consumer<Minecraft> callback) { callbacks.add(callback); }
    }
}
