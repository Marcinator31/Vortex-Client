package com.example.pvpclient.module.modules;

import com.example.pvpclient.core.setting.BooleanSetting;
import com.example.pvpclient.core.setting.ColorSetting;
import com.example.pvpclient.core.setting.NumberSetting;
import com.example.pvpclient.module.Module;

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
    public final BooleanSetting showPlayers = new BooleanSetting("Spieler anzeigen", true);
    public final ColorSetting playerColor   = new ColorSetting("Spieler-Farbe", 0xFFFF5555); // rot

    // --- Passive Mobs / Tiere ---
    public final BooleanSetting showAnimals = new BooleanSetting("Tiere anzeigen", true);
    public final ColorSetting animalColor   = new ColorSetting("Tier-Farbe", 0xFFFFFFFF);   // weiss

    // --- Feindliche Mobs ---
    public final BooleanSetting showHostiles = new BooleanSetting("Gegner anzeigen", true);
    public final ColorSetting hostileColor   = new ColorSetting("Gegner-Farbe", 0xFFFFFF55); // gelb

    // --- Sonstige Entities (Items, Pfeile, ...) ---
    public final BooleanSetting showMisc = new BooleanSetting("Sonstige anzeigen", false);
    public final ColorSetting miscColor  = new ColorSetting("Sonstige-Farbe", 0xFF55FFFF);   // cyan

    // Liniendicke der Box.
    public final NumberSetting lineWidth = new NumberSetting("Linienbreite", 2.0, 1.0, 5.0, 0.5);

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
