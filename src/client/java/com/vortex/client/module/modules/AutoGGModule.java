package com.vortex.client.module.modules;

import com.vortex.client.core.setting.BooleanSetting;
import com.vortex.client.core.setting.NumberSetting;
import com.vortex.client.module.Module;

/**
 * Auto GG: schreibt nach einem Kill oder am Rundenende eine Nachricht in den
 * Chat -- je Server eine eigene.
 *
 *   /autogg set <text>      Nachricht fuer diesen Server ({name} = Gegner)
 *   /autogg default <text>  fuer alle Server ohne eigene
 *   /autogg list | remove
 *
 * Ein Kill zaehlt, wenn ein Spieler stirbt, den du in den letzten Sekunden
 * getroffen hast. Rundenende = typische Siegesmeldungen im Chat.
 */
public class AutoGGModule extends Module {

    public final BooleanSetting onKill = new BooleanSetting("On Kill", true);
    public final BooleanSetting onGameEnd = new BooleanSetting("On Game End", true);
    public final BooleanSetting onlyPlayers = new BooleanSetting("Only Players", true);
    public final NumberSetting delay = new NumberSetting("Delay (ms)", 400, 0, 3000, 50);
    public final NumberSetting cooldown = new NumberSetting("Cooldown (s)", 5, 1, 60, 1);

    public AutoGGModule() {
        super("Auto GG", Category.MISC);
        addSetting(onKill);
        addSetting(onGameEnd);
        addSetting(onlyPlayers);
        addSetting(delay);
        addSetting(cooldown);
    }
}
