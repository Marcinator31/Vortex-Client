package com.vortex.client.gui;

import com.vortex.client.util.GameRestarter;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.network.chat.Component;

/**
 * Fuegt dem Pause-Menue (ESC) einen "Restart game"-Knopf hinzu.
 *
 * Der Knopf macht dasselbe wie der Befehl /relaunch: er startet einen neuen
 * Spiel-Prozess und faehrt den aktuellen sauber herunter.
 */
public final class RestartButton {

    private RestartButton() {}

    public static void register() {
        // Die Skin-Garderobe steht seit der Vortex-Leiste (gui/menu/MenuLeiste) dort als "Wardrobe".
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (!(screen instanceof PauseScreen)) return;
            try {
                // Oben links platzieren, damit nichts vom Vanilla-Menue verdeckt wird.
                Button btn = Button.builder(
                        Component.literal("Restart game"),
                        b -> {
                            try {
                                GameRestarter.restart();
                            } catch (Throwable t) {
                                b.setMessage(Component.literal("Restart failed"));
                            }
                        }
                ).bounds(6, 6, 140, 20).build();
                Screens.getWidgets(screen).add(btn);
            } catch (Throwable pvpErr) {
                com.vortex.client.core.Errors.report("RestartButton", pvpErr);
            }
        });
    }
}
