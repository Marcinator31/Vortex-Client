package com.vortex.client.core;

import com.vortex.client.module.ModuleManager;
import com.vortex.client.module.modules.FriendsModule;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/**
 * Die Freundesliste. Namen ohne Gross-/Kleinschreibung, damit "Steve" und
 * "steve" derselbe Freund sind.
 *
 * Oeffentlich und statisch, weil das Addon sie ebenfalls fragt: Aimbot, Auto
 * Hit und Auto Anchor ueberspringen Freunde ueber schuetzt().
 */
public final class Friends {

    private Friends() {}

    private static final Set<String> NAMEN = ConcurrentHashMap.newKeySet();
    /** Anzeige-Schreibweise, so wie sie hinzugefuegt wurde. */
    private static final java.util.Map<String, String> ANZEIGE = new ConcurrentHashMap<>();

    public static boolean istFreundName(String name) {
        return name != null && NAMEN.contains(name.toLowerCase(Locale.ROOT));
    }

    public static boolean istFreund(Entity e) {
        return e instanceof Player p && istFreundName(p.getName().getString());
    }

    /** Freund UND die Wirkung ist eingeschaltet -- fuer Farben. */
    public static boolean markiert(Entity e) {
        FriendsModule m = ModuleManager.INSTANCE.get(FriendsModule.class);
        return m != null && m.isEnabled() && istFreund(e);
    }

    /** Sollen Kampf-Module dieses Ziel auslassen? */
    public static boolean schuetzt(Entity e) {
        FriendsModule m = ModuleManager.INSTANCE.get(FriendsModule.class);
        return m != null && m.isEnabled() && m.protect.get() && istFreund(e);
    }

    public static int farbe() {
        FriendsModule m = ModuleManager.INSTANCE.get(FriendsModule.class);
        return m == null ? 0xFF55FFFF : m.color.get();
    }

    public static boolean hinzu(String name) {
        if (name == null || name.isBlank()) return false;
        String k = name.toLowerCase(Locale.ROOT);
        ANZEIGE.put(k, name);
        return NAMEN.add(k);
    }

    public static boolean weg(String name) {
        if (name == null) return false;
        String k = name.toLowerCase(Locale.ROOT);
        ANZEIGE.remove(k);
        return NAMEN.remove(k);
    }

    /** Hinzufuegen oder entfernen. true = ist jetzt Freund. */
    public static boolean umschalten(String name) {
        if (istFreundName(name)) {
            weg(name);
            return false;
        }
        hinzu(name);
        return true;
    }

    public static List<String> alle() {
        List<String> l = new ArrayList<>();
        for (String k : NAMEN) l.add(ANZEIGE.getOrDefault(k, k));
        l.sort(String.CASE_INSENSITIVE_ORDER);
        return l;
    }

    public static void leeren() {
        NAMEN.clear();
        ANZEIGE.clear();
    }
}
