package com.vortex.client.module;

import net.minecraft.client.gui.screens.Screen;

/**
 * Fuer Module mit eigenem Einstellungsbildschirm.
 *
 * Auch hier stand vorher ein instanceof je Modul im ClickGui -- ein Modul aus
 * einem Addon konnte deshalb gar keinen eigenen Bildschirm anbieten.
 */
public interface HasOwnScreen {
    String screenButtonLabel();
    Screen createScreen(Screen parent);
}
