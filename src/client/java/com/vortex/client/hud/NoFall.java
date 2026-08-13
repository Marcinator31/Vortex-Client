package com.vortex.client.hud;

import com.vortex.client.module.Module;
import com.vortex.client.module.ModuleManager;
import com.vortex.client.module.modules.NoFallModule;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.network.ClientPlayerEntity;

/**
 * Logik fuer No Fall.
 *
 * Waehrend eines Falls wird dem Server ueber die Bewegungspakete Bodenkontakt
 * gemeldet und die gezaehlte Fallhoehe zurueckgesetzt. Dadurch kommt am Ende
 * kein Fallschaden an.
 *
 * Nicht eingegriffen wird beim Fliegen (Kreativ/Fly-Modul), beim Schweben mit
 * Elytra und im Spectator-Modus -- dort gibt es ohnehin keinen Fallschaden, und
 * ein Eingriff koennte die Bewegung stoeren.
 */
public final class NoFall {

    private NoFall() {}

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            long pvpT0 = System.nanoTime();
            try {
            NoFallModule mod = (NoFallModule) find(NoFallModule.class);
            if (mod == null || !mod.isEnabled()) return;
            if (client.player == null) return;

            ClientPlayerEntity self = client.player;

            // Situationen ohne Fallschaden auslassen.
            if (self.isSpectator()) return;
            if (self.getAbilities().flying) return;
            if (self.isGliding()) return;
            if (self.isTouchingWater() || self.isInLava()) return;

            // fallDistance ist in 1.21.11 ein double.
            double fallen = self.fallDistance;
            if (fallen > mod.minHeight.get()) {
                // Bodenkontakt melden + Fallhoehe zuruecksetzen. Beides geht in
                // das naechste Bewegungspaket ein -> der Server zaehlt neu.
                self.setOnGround(true);
                self.fallDistance = 0.0;
            }
                    } finally {
                com.vortex.client.core.Profiler.record("NoFall",
                        System.nanoTime() - pvpT0);
            }
        });
    }

    private static Module find(Class<? extends Module> type) {
        // Konstante Laufzeit statt die ganze Liste zu durchlaufen.
        return ModuleManager.INSTANCE.get(type);
    }
}
