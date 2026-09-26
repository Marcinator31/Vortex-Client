package com.vortex.client.module.modules;

import com.vortex.client.core.setting.BooleanSetting;
import com.vortex.client.core.setting.ColorSetting;
import com.vortex.client.core.setting.ModeSetting;
import com.vortex.client.core.setting.NumberSetting;
import com.vortex.client.module.Module;

/**
 * Scoreboard rechts verschieben, verkleinern, die roten Zahlen ausblenden.
 *
 * Die Zahlen sind auf den meisten Servern nur Platzhalter fuer die
 * Reihenfolge und tragen keine Information. Ausgeblendet wird das Scoreboard
 * schmaler. Verschoben wird relativ zur Vanilla-Position (rechts, mittig).
 */
public class ScoreboardModule extends Module {

    public final NumberSetting offsetX = new NumberSetting("Offset X", 0, -600, 100, 1);
    public final NumberSetting offsetY = new NumberSetting("Offset Y", 0, -300, 300, 1);
    public final NumberSetting scale = new NumberSetting("Scale", 1.0, 0.4, 2.0, 0.05);
    public final BooleanSetting hideNumbers = new BooleanSetting("Hide Numbers", true);
    public final BooleanSetting hide = new BooleanSetting("Hide Completely", false);

    public ScoreboardModule() {
        super("Scoreboard", Category.HUD);
        addSetting(offsetX);
        addSetting(offsetY);
        addSetting(scale);
        addSetting(hideNumbers);
        addSetting(hide);
    }
}
