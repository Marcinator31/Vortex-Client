package com.vortex.client.module.modules;

import com.vortex.client.core.setting.BooleanSetting;
import com.vortex.client.core.setting.NumberSetting;
import com.vortex.client.module.Module;

/**
 * Better Tooltips: mehr Infos, weniger Platz.
 *
 *  - Verzauberungen kompakt in wenigen Zeilen ("Prot IV, Unb III, Mend")
 *  - Nahrung: Hunger- und Saettigungspunkte
 *  - Haltbarkeit als Zahl (auch ohne F3+H)
 *  - Karten-Vorschau beim Draufzeigen im Inventar
 */
public class BetterTooltipsModule extends Module {

    public final BooleanSetting compactEnchants = new BooleanSetting("Compact Enchantments", true);
    public final BooleanSetting shortNames = new BooleanSetting("Short Enchantment Names", true);
    public final BooleanSetting food = new BooleanSetting("Food Values", true);
    public final BooleanSetting durability = new BooleanSetting("Durability", true);
    public final BooleanSetting mapPreview = new BooleanSetting("Map Preview", true);
    public final NumberSetting mapSize = new NumberSetting("Map Preview Size", 96, 48, 160, 8);

    public BetterTooltipsModule() {
        super("Better Tooltips", Category.MISC);
        addSetting(compactEnchants);
        addSetting(shortNames);
        addSetting(food);
        addSetting(durability);
        addSetting(mapPreview);
        addSetting(mapSize);
    }
}
