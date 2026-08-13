package com.example.pvpclient.hud;

import com.example.pvpclient.module.Module;
import com.example.pvpclient.module.ModuleManager;
import com.example.pvpclient.module.modules.FlyModule;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerAbilities;

/**
 * Logik fuer das Fly-Modul.
 *
 * Es werden nur die Flug-Faehigkeiten des Spielers gesetzt -- die eigentliche
 * Flugbewegung uebernimmt Minecraft danach selbst, exakt wie im Kreativmodus.
 * Beim Ausschalten werden die Werte wieder zurueckgesetzt.
 *
 * Im Kreativ-/Spectator-Modus wird nichts angefasst: dort darf man ohnehin
 * fliegen, und ein Zuruecksetzen wuerde das kaputt machen.
 */
public final class Fly {

    // Merker, damit beim Ausschalten genau einmal zurueckgesetzt wird.
    private static boolean wasActive = false;

    private Fly() {}

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            long pvpT0 = System.nanoTime();
            try {
            if (client.player == null) {
                wasActive = false;
                return;
            }

            ClientPlayerEntity self = client.player;
            PlayerAbilities abilities = self.getAbilities();

            // Kreativ/Spectator: Finger weg, da fliegt man sowieso.
            if (abilities.creativeMode || self.isSpectator()) {
                wasActive = false;
                return;
            }

            FlyModule mod = (FlyModule) find(FlyModule.class);
            boolean active = mod != null && mod.isEnabled();

            if (active) {
                abilities.allowFlying = true;
                abilities.flying = true;
                abilities.setFlySpeed(mod.flySpeed());
                wasActive = true;
            } else if (wasActive) {
                // Genau einmal beim Ausschalten aufraeumen.
                boolean keep = mod != null && mod.keepAllowFlying.get();
                abilities.flying = false;
                abilities.allowFlying = keep;
                abilities.setFlySpeed(0.05f);
                wasActive = false;
            }
                    } finally {
                com.example.pvpclient.core.Profiler.record("Fly",
                        System.nanoTime() - pvpT0);
            }
        });
    }

    private static Module find(Class<? extends Module> type) {
        // Konstante Laufzeit statt die ganze Liste zu durchlaufen.
        return ModuleManager.INSTANCE.get(type);
    }
}
