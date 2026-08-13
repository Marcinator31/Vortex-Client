package com.example.pvpclient.module.modules;

import com.example.pvpclient.core.setting.BooleanSetting;
import com.example.pvpclient.core.setting.ColorSetting;
import com.example.pvpclient.core.setting.ModeSetting;
import com.example.pvpclient.core.setting.NumberSetting;
import com.example.pvpclient.module.Module;

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
        new ModeSetting("Anzeige", 0, "Herzen", "Zahl+Herz", "Zahl");
    public final NumberSetting scale = new NumberSetting("Skalierung", 1.0, 0.5, 3.0, 0.1);
    public final ColorSetting color = new ColorSetting("Farbe", 0xFFFF5555);

    // Fuer welche Entity-Arten anzeigen.
    public final BooleanSetting showPlayers  = new BooleanSetting("Spieler", true);
    public final BooleanSetting showMonsters = new BooleanSetting("Monster", true);
    public final BooleanSetting showAnimals  = new BooleanSetting("Tiere", false);

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
