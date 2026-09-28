package com.vortex.client.module.modules;

import com.vortex.client.core.setting.BooleanSetting;
import com.vortex.client.core.setting.ModeSetting;
import com.vortex.client.core.setting.NumberSetting;
import com.vortex.client.module.Module;

/**
 * Damage Numbers: schwebende Schadenszahlen ueber getroffenen Wesen.
 *
 * Berechnet aus der Lebensaenderung, die der Server schickt (Leben + Absorption).
 * Ehrlich: Manche Server verstecken das echte Leben anderer Spieler -- dann
 * stimmen die Zahlen dort nicht (oder es erscheinen keine).
 */
public class DamageNumbersModule extends Module {

    public final ModeSetting unit = new ModeSetting("Unit", 0, "Health", "Hearts");
    public final BooleanSetting onlyPlayers = new BooleanSetting("Only Players", false);
    public final BooleanSetting onlyMyHits = new BooleanSetting("Only My Hits", false);
    public final BooleanSetting showHealing = new BooleanSetting("Show Healing", false);
    public final BooleanSetting showSelf = new BooleanSetting("Show Own Damage", false);
    public final NumberSetting range = new NumberSetting("Range", 24, 4, 64, 1);
    public final NumberSetting size = new NumberSetting("Size", 1.0, 0.5, 2.5, 0.1);
    public final NumberSetting duration = new NumberSetting("Duration", 1.2, 0.4, 3.0, 0.1);

    public DamageNumbersModule() {
        super("Damage Numbers", Category.PVP);
        addSetting(unit); addSetting(onlyPlayers); addSetting(onlyMyHits); addSetting(showHealing);
        addSetting(showSelf); addSetting(range); addSetting(size); addSetting(duration);
    }
}
