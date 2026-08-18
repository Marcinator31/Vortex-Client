package net.fabricmc.fabric.api.client.keymapping.v1;

import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Forge-backed key mapping registrar retaining Vortex's existing key declarations. */
public final class KeyMappingHelper {
    private static final List<KeyMapping> PENDING = new CopyOnWriteArrayList<>();
    private static volatile boolean installed;
    private KeyMappingHelper() {}
    public static KeyMapping registerKeyMapping(KeyMapping mapping) { PENDING.add(mapping); return mapping; }
    public static void install(IEventBus modBus) {
        if (installed) return;
        installed = true;
        modBus.addListener((RegisterKeyMappingsEvent event) -> PENDING.forEach(event::register));
    }
}
