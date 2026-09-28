package com.vortex.client.module.modules;

import com.vortex.client.module.Module;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;

/**
 * Toggle-Sprint: dauerhaftes Sprinten, ohne die Taste zu halten.
 *
 * KOMFORT-Feature (kein Kampfvorteil -- du koenntest die Taste auch
 * selbst gedrueckt halten).
 *
 * Umsetzung: die Sprint-TASTE wird gehalten, nicht setSprinting(true)
 * erzwungen. Frueher setzte das Modul den Sprint jeden Tick direkt -- auch im
 * Stehen, mit leerem Hungerbalken, bei Blindheit und direkt nach einem
 * Treffer (wo Minecraft den Sprint absichtlich beendet). Das sah fuer
 * Anti-Cheats verdaechtig aus und machte W-Tap/Sprint-Reset unmoeglich.
 * Mit gehaltener Taste entscheidet Minecraft selbst -- genau wie bei Strg.
 */
public class ToggleSprintModule extends Module {

    private boolean gehalten = false;

    public ToggleSprintModule() {
        super("Toggle Sprint", Category.PVP);
        ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
    }

    private void onTick(Minecraft client) {
        if (client.options == null) return;
        if (!isEnabled() || client.player == null) {
            if (gehalten) {
                gehalten = false;
                // Nur loslassen, wenn der Spieler die Taste nicht selbst drueckt.
                client.options.keySprint.setDown(tasteWirklichGedrueckt(client));
            }
            return;
        }
        client.options.keySprint.setDown(true);
        gehalten = true;
    }

    private static boolean tasteWirklichGedrueckt(Minecraft client) {
        try {
            var key = com.mojang.blaze3d.platform.InputConstants.getKey(client.options.keySprint.saveString());
            return key.getType() == com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM
                    && com.mojang.blaze3d.platform.InputConstants.isKeyDown(client.getWindow(), key.getValue());
        } catch (Throwable t) {
            return false;
        }
    }
}
