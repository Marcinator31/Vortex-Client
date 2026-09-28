package com.vortex.client.module.modules;

import com.vortex.client.core.setting.BooleanSetting;
import com.vortex.client.core.setting.ColorSetting;
import com.vortex.client.core.setting.NumberSetting;
import com.vortex.client.module.Module;

/**
 * Pearl Tracker: zeigt, wo die Enderperlen ANDERER Spieler landen -- Linie,
 * Markierung am Ziel und oben ein Hinweis mit Name und Entfernung.
 *
 * Gerechnet wie der Server (ThrowableProjectile.tick in 26.x): pro Tick
 * Schwerkraft 0,03, dann Luftwiderstand 0,99 (Wasser 0,8), dann bewegen.
 *
 * Achtung: Auf manchen Servern verbieten die Regeln solche Hilfen. Es sendet
 * nichts an den Server -- ein Anticheat kann es nicht sehen, eine Regel aber
 * trotzdem verbieten.
 */
public class PearlTrackerModule extends Module {

    public final ColorSetting color = new ColorSetting("Color", 0xFFB14DFF);
    public final NumberSetting lineWidth = new NumberSetting("Line Width", 2.0, 0.5, 5.0, 0.5);
    public final BooleanSetting showOwn = new BooleanSetting("Show Own Pearls", false);
    public final BooleanSetting landingText = new BooleanSetting("Landing Info", true);
    public final BooleanSetting warnNear = new BooleanSetting("Warn If Near Me", true);

    public PearlTrackerModule() {
        super("Pearl Tracker", Category.PVP);
        addSetting(color); addSetting(lineWidth); addSetting(showOwn); addSetting(landingText); addSetting(warnNear);
    }
}
