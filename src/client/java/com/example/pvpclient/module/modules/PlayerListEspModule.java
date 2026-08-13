package com.example.pvpclient.module.modules;

import com.example.pvpclient.core.setting.ColorSetting;
import com.example.pvpclient.module.Module;

/**
 * Player List ESP: zeigt oben am Bildschirmrand eine Liste aller in Reichweite
 * geladenen Spieler mit Name und Distanz. Nuetzlich, um Gegner fruehzeitig zu
 * bemerken (PvP-Server), ohne den Tab oder die Minimap zu brauchen.
 */
public class PlayerListEspModule extends Module {

    public final ColorSetting color = new ColorSetting("Textfarbe", 0xFFFFFFFF);

    public PlayerListEspModule() {
        super("Player List", Category.HUD);
        addSetting(color);
    }

    public int getColor() { return color.get(); }
}
