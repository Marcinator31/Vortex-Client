package com.vortex.client.gui;

import com.vortex.client.util.GameRestarter;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/**
 * Fuegt dem Pause-Menue (ESC) einen "Spiel neu starten"-Knopf hinzu.
 *
 * Der Knopf macht dasselbe wie der Befehl /relaunch: er startet einen neuen
 * Spiel-Prozess und faehrt den aktuellen sauber herunter.
 */
public final class RestartButton {

    private RestartButton() {}

    public static void register() {
        // Hauptmenue: Knopf zur Skin-Garderobe.
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (!(screen instanceof net.minecraft.client.gui.screen.TitleScreen)) return;
            try {
                ButtonWidget skins = ButtonWidget.builder(
                        Text.literal("Skins"),
                        b -> net.minecraft.client.MinecraftClient.getInstance()
                                .setScreen(new SkinScreen(screen))
                ).dimensions(6, 6, 70, 20).build();
                Screens.getButtons(screen).add(skins);
            } catch (Throwable pvpErr) {
                com.vortex.client.core.Errors.report("SkinButton", pvpErr);
            }
        });

        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (!(screen instanceof GameMenuScreen)) return;
            try {
                // Oben links platzieren, damit nichts vom Vanilla-Menue verdeckt wird.
                ButtonWidget btn = ButtonWidget.builder(
                        Text.literal("Spiel neu starten"),
                        b -> {
                            try {
                                GameRestarter.restart();
                            } catch (Throwable t) {
                                b.setMessage(Text.literal("Neustart fehlgeschlagen"));
                            }
                        }
                ).dimensions(6, 6, 140, 20).build();
                Screens.getButtons(screen).add(btn);
            } catch (Throwable pvpErr) {
                com.vortex.client.core.Errors.report("RestartButton", pvpErr);
            }
        });
    }
}
