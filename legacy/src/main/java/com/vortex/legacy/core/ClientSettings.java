package com.vortex.legacy.core;

import java.util.Arrays;
import java.util.List;

/** Allgemeine Einstellungen (nicht an ein Modul gebunden). */
public final class ClientSettings {
    public static final ClientSettings INSTANCE = new ClientSettings();
    private ClientSettings() {}

    public final ModeSetting toggleMessage = new ModeSetting("Toggle Message", 1, "Off", "Above Hotbar", "Chat");
    public final ColorSetting accent = new ColorSetting("Accent Color", 0xFF8B5CF6);
    public final BoolSetting modernMenus = new BoolSetting("Modern Menus", true);
    public final ModeSetting menuPanorama = new ModeSetting("Menu Panorama", 0, com.vortex.legacy.gui.Panoramen.optionen());
    public final KeySetting menuKey = new KeySetting("Menu Key", org.lwjgl.input.Keyboard.KEY_RSHIFT);

    public List<Setting> all() { return Arrays.<Setting>asList(toggleMessage, accent, modernMenus, menuPanorama, menuKey); }

    void load(String name, String value) {
        for (Setting s : all()) if (s.getName().equals(name)) s.deserialize(value);
    }
}
