package com.example.pvpclient.module.modules;

import com.example.pvpclient.module.Module;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;

/**
 * Toggle-Sprint: dauerhaftes Sprinten, ohne die Taste zu halten.
 *
 * KOMFORT-Feature (kein Kampfvorteil -- du koenntest die Taste auch
 * selbst gedrueckt halten).
 *
 * Umsetzung ueber den Client-Tick (kein Mixin noetig): Solange das Modul
 * an ist, setzen wir jeden Tick setSprinting(true). Minecraft ignoriert
 * Sprint automatisch, wenn der Spieler steht oder rueckwaerts geht, also
 * muessen wir die Bewegungsrichtung hier nicht selbst pruefen -- das
 * macht den Code robust gegen Versions-Aenderungen der Input-API.
 *
 * 'setSprinting' ist gegen die 1.21.11-Javadocs verifiziert.
 */
public class ToggleSprintModule extends Module {

    public ToggleSprintModule() {
        super("Toggle Sprint", Category.PVP);
        ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
    }

    private void onTick(MinecraftClient client) {
        if (!isEnabled()) return;
        ClientPlayerEntity player = client.player;
        if (player == null) return;

        // Einfach und robust: immer auf sprintend setzen, solange das Modul
        // an ist. Minecraft zeigt/nutzt Sprint ohnehin nur bei
        // Vorwaertsbewegung, daher keine eigene Richtungspruefung noetig.
        player.setSprinting(true);
    }
}
