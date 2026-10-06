package com.vortex.legacy.module.hud;

import com.vortex.legacy.core.BoolSetting;
import com.vortex.legacy.core.Module;

/** Seitliche Anzeige (Scoreboard): rote Zahlen und Hintergrund ausblenden. */
public class ScoreboardModule extends Module {
    public final BoolSetting hideNumbers = add(new BoolSetting("Hide Red Numbers", true));
    public final BoolSetting background = add(new BoolSetting("Background", true));
    public final BoolSetting hide = add(new BoolSetting("Hide Scoreboard", false));
    public ScoreboardModule() { super("Scoreboard", Category.HUD, "Hide the red numbers or the whole sidebar."); }
}
