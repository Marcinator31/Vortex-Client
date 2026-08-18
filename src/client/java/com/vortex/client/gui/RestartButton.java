package com.vortex.client.gui;

import com.vortex.client.util.GameRestarter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;

/** Adds the Vortex skin and restart controls through Forge's screen-init event. */
public final class RestartButton {
    private static boolean registered;
    private RestartButton() {}

    public static void register() {
        if (registered) return;
        registered = true;
        MinecraftForge.EVENT_BUS.addListener((ScreenEvent.Init.Post event) -> {
            var screen = event.getScreen();
            try {
                if (screen instanceof TitleScreen) {
                    Button skins = Button.builder(Component.literal("Skins"), button ->
                            Minecraft.getInstance().setScreen(new SkinScreen(screen)))
                            .bounds(6, 6, 70, 20).build();
                    event.addListener(skins);
                } else if (screen instanceof PauseScreen) {
                    Button restart = Button.builder(Component.literal("Restart game"), button -> {
                        try { GameRestarter.restart(); }
                        catch (Throwable error) { button.setMessage(Component.literal("Restart failed")); }
                    }).bounds(6, 6, 140, 20).build();
                    event.addListener(restart);
                }
            } catch (Throwable error) {
                com.vortex.client.core.Errors.report("RestartButton", error);
            }
        });
    }
}
