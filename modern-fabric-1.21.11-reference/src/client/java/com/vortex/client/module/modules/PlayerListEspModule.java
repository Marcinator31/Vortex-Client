package com.vortex.client.module.modules;

import com.vortex.client.core.setting.ColorSetting;
import com.vortex.client.module.Module;

/**
 * Player List ESP: zeigt oben am Bildschirmrand eine Liste aller in Reichweite
 * geladenen Spieler mit Name und Distanz. Nuetzlich, um Gegner fruehzeitig zu
 * bemerken (PvP-Server), ohne den Tab oder die Minimap zu brauchen.
 */
public class PlayerListEspModule extends Module {

    public final ColorSetting color = new ColorSetting("Text Color", 0xFFFFFFFF);

    public PlayerListEspModule() {
        super("Player List", Category.HUD);
        addSetting(color);
    }

    public int getColor() { return color.get(); }
}
