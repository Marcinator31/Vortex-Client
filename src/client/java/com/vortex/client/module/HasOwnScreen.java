package com.vortex.client.module;

import net.minecraft.client.gui.screen.Screen;

/**
 * Fuer Module mit einem eigenen Einstellungsbildschirm.
 *
 * Auch hier stand vorher ein instanceof je Modul im ClickGui. Ein Modul aus
 * einem Addon konnte deshalb gar keinen eigenen Bildschirm anbieten -- der
 * Client haette es kennen muessen.
 */
public interface HasOwnScreen {

    /** Beschriftung des Knopfes, z. B. "Mobs" oder "Bloecke". */
    String screenButtonLabel();

    /** Erzeugt den Bildschirm. */
    Screen createScreen(Screen parent);
}
