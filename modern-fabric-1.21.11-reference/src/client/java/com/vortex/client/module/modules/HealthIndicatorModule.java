package com.vortex.client.module.modules;

import com.vortex.client.core.setting.BooleanSetting;
import com.vortex.client.core.setting.ColorSetting;
import com.vortex.client.core.setting.ModeSetting;
import com.vortex.client.core.setting.NumberSetting;
import com.vortex.client.module.Module;

/**
 * Health Indicator: zeigt die Lebenspunkte ueber Entities an (schwebt ueber dem
 * Kopf, wie ein Nametag).
 *
 * Drei Anzeige-Modi (umschaltbar):
 *   "Herzen" -> Herz-Symbole (ein ❤ je 2 HP, wie die Lebensleiste)
 *   "Zahl+Herz" -> z.B. "20 ❤"
 *   "Zahl" -> nur die Zahl, z.B. "20"
 *
 * Einstellbar: Skalierung und Farbe des Textes, sowie fuer welche Entity-Arten
 * angezeigt wird (Spieler / Monster / Tiere).
 *
 * Gezeichnet wird im LivingEntityRendererMixin (Billboard ueber dem Kopf,
 * Ansatz von der HealthIndicators-Mod uebernommen).
 */
public class HealthIndicatorModule extends Module {

    public final ModeSetting mode =
        new ModeSetting("Display", 0, "Herzen", "Zahl+Herz", "Zahl");
    public final NumberSetting scale = new NumberSetting("Scale", 1.0, 0.5, 3.0, 0.1);
    public final ColorSetting color = new ColorSetting("Color", 0xFFFF5555);

    // Fuer welche Entity-Arten anzeigen.
    public final BooleanSetting showPlayers  = new BooleanSetting("Players", true);
    public final BooleanSetting showMonsters = new BooleanSetting("Monsters", true);
    public final BooleanSetting showAnimals  = new BooleanSetting("Animals", false);

    public HealthIndicatorModule() {
        super("Health Indicator", Category.PVP);
        addSetting(mode);
        addSetting(scale);
        addSetting(color);
        addSetting(showPlayers);
        addSetting(showMonsters);
        addSetting(showAnimals);
    }
}
