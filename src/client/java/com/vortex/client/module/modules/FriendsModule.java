package com.vortex.client.module.modules;

import com.vortex.client.core.setting.BooleanSetting;
import com.vortex.client.core.setting.ColorSetting;
import com.vortex.client.module.Module;

/**
 * Freundesliste.
 *
 * Freunde bekommen eine eigene Farbe (Hitboxen, Namensschilder, ESP), und die
 * Kampf-Module im Addon (Aimbot, Auto Hit, Auto Anchor) lassen sie in Ruhe.
 *
 * HINZUFUEGEN: Mittelklick auf einen Spieler -- oder /friend add Name.
 * ENTFERNEN: noch einmal Mittelklick -- oder /friend remove Name.
 * /friend list zeigt alle.
 *
 * Die Liste selbst ist immer aktiv; das Modul schaltet nur die Wirkung. So
 * geht beim Ausschalten keine Liste verloren.
 */
public class FriendsModule extends Module implements com.vortex.client.module.ExtraData {

    public final ColorSetting color = new ColorSetting("Friend Color", 0xFF55FFFF);
    public final BooleanSetting middleClick = new BooleanSetting("Middle Click To Add", true);
    public final BooleanSetting protect = new BooleanSetting("Protect From Combat Modules", true);

    public FriendsModule() {
        super("Friends", Category.MISC);
        enabledByDefault();
        addSetting(color);
        addSetting(middleClick);
        addSetting(protect);
    }

    @Override public String extraKey() { return "__friends__"; }

    @Override public String serializeExtra() {
        return String.join(",", com.vortex.client.core.Friends.alle());
    }

    @Override public void deserializeExtra(String value) {
        com.vortex.client.core.Friends.leeren();
        if (value == null) return;
        for (String n : value.split(",")) {
            if (!n.isBlank()) com.vortex.client.core.Friends.hinzu(n.trim());
        }
    }

    @Override public void clearExtra() {
        com.vortex.client.core.Friends.leeren();
    }
}
