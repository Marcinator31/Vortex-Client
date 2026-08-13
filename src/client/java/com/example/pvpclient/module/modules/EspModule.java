package com.example.pvpclient.module.modules;

import com.example.pvpclient.core.setting.ColorSetting;
import com.example.pvpclient.module.Module;
import net.minecraft.util.Identifier;

import java.util.HashSet;
import java.util.Set;

/**
 * ESP: laesst ausgewaehlte Mobs mit einer leuchtenden Outline durch Waende
 * erscheinen. Welche Mobs glühen, waehlt man im ESP-Menue (Spawn-Egg-Grid);
 * die Farbe ist einstellbar.
 *
 * Die Auswahl wird als Menge von Entity-Type-IDs gehalten und ueber das
 * id-Setting (kommasepariert) persistiert.
 */
public class EspModule extends Module {

    public final ColorSetting color = new ColorSetting("Glow-Farbe", 0xFFFF0000);

    // Aktive Mob-Typen (z.B. "minecraft:zombie"). Wird im ESP-Menue umgeschaltet.
    private final Set<String> enabledMobs = new HashSet<>();

    public EspModule() {
        super("ESP", Category.PVP);
        addSetting(color);
    }

    /** Ist dieser Entity-Typ (per Identifier) fuer ESP aktiviert? */
    public boolean isMobEnabled(Identifier id) {
        return id != null && enabledMobs.contains(id.toString());
    }

    public boolean isMobEnabled(String id) {
        return enabledMobs.contains(id);
    }

    public void toggleMob(String id) {
        if (!enabledMobs.add(id)) enabledMobs.remove(id);
    }

    public void setMob(String id, boolean on) {
        if (on) enabledMobs.add(id); else enabledMobs.remove(id);
    }

    public Set<String> getEnabledMobs() {
        return enabledMobs;
    }

    public int getGlowColor() {
        return color.get();
    }

    /** Fuer die Persistenz: aktive Mobs als kommaseparierte Liste. */
    public String serializeMobs() {
        return String.join(",", enabledMobs);
    }

    public void deserializeMobs(String data) {
        enabledMobs.clear();
        if (data == null || data.isEmpty()) return;
        for (String s : data.split(",")) {
            String t = s.trim();
            if (!t.isEmpty()) enabledMobs.add(t);
        }
    }
}
