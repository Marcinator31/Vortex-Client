package com.vortex.client.module.modules;

import com.vortex.client.core.setting.BooleanSetting;
import com.vortex.client.core.setting.ColorSetting;
import com.vortex.client.core.setting.NumberSetting;
import com.vortex.client.module.Module;

/**
 * Hitbox-Anzeige.
 *
 * Zeigt die ECHTEN Vanilla-Hitboxen von Entities an (wie F3+B), aber
 * eingefaerbt nach Typ und einzeln schaltbar. Reine Visualisierung --
 * die Hitboxen werden NICHT veraendert, nur sichtbar gemacht.
 *
 * Pro Entity-Kategorie gibt es:
 *   - einen Schalter (zeigt diese Kategorie ueberhaupt an?)
 *   - eine Farbe
 *
 * So bekommst du genau das gewuenschte Verhalten:
 *   Spieler rot, Tiere weiss, Gegner gelb -- oder Kategorie komplett aus.
 */
public class HitboxModule extends Module {

    // --- Spieler ---
    public final BooleanSetting showPlayers = new BooleanSetting("Show Players", true);
    public final ColorSetting playerColor   = new ColorSetting("Player Color", 0xFFFF5555); // rot

    // --- Passive Mobs / Tiere ---
    public final BooleanSetting showAnimals = new BooleanSetting("Show Animals", true);
    public final ColorSetting animalColor   = new ColorSetting("Animal Color", 0xFFFFFFFF);   // weiss

    // --- Feindliche Mobs ---
    public final BooleanSetting showHostiles = new BooleanSetting("Show Enemies", true);
    public final ColorSetting hostileColor   = new ColorSetting("Enemy Color", 0xFFFFFF55); // gelb

    // --- Sonstige Entities (Items, Pfeile, ...) ---
    public final BooleanSetting showMisc = new BooleanSetting("Show Others", false);
    public final ColorSetting miscColor  = new ColorSetting("Other Color", 0xFF55FFFF);   // cyan

    // Liniendicke der Box.
    public final NumberSetting lineWidth = new NumberSetting("Line Width", 2.0, 1.0, 5.0, 0.5);

    public HitboxModule() {
        super("Hitboxes", Category.PVP);
        addSetting(showPlayers);
        addSetting(playerColor);
        addSetting(showAnimals);
        addSetting(animalColor);
        addSetting(showHostiles);
        addSetting(hostileColor);
        addSetting(showMisc);
        addSetting(miscColor);
        addSetting(lineWidth);
    }
}
