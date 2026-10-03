package com.vortex.client.core;

import com.vortex.client.module.Module;
import com.vortex.client.module.ModuleManager;
import net.minecraft.client.Minecraft;

import java.util.Set;

/**
 * "Clean Modules" (Client Settings): der Client sieht aus wie ein normaler
 * PvP-Client -- z. B. wenn ein Server-Admin einen Screenshot vom Mod-Menue
 * sehen will.
 *
 * Solange es an ist:
 *   - alle Cheats und Bots sind AUS und bleiben aus (jeden Tick geprueft,
 *     auch gegen Tasten, Presets oder Addon-Code, die sie einschalten wollen),
 *   - die Kategorien "Cheats" und "Bots" fehlen im Mod-Menue, die Bots-Kachel
 *     im Startmenue, ihre Tasten in "Keybinds", ihre HUD-Elemente im HUD-Editor
 *     und ihre Zaehler ("x of y active"),
 *   - ihre Tasten tun nichts.
 * Beim Einschalten wird ausserdem der Chat geleert (Meldungen der Cheats).
 *
 * Schaltet man es wieder aus, ist alles wieder sichtbar. Die Cheats bleiben
 * aber aus -- man schaltet sie bewusst selbst wieder ein.
 */
public final class CleanModules {
    private CleanModules() {}

    /** Module anderer Kategorien, die trotzdem zu den Cheats/Bots gehoeren (Plus Addon). */
    private static final Set<String> AUCH = Set.of("Bot Status");

    private static Boolean zuletzt;

    public static boolean aktiv() {
        return ClientSettings.INSTANCE.cleanModules.get();
    }

    /** Gehoert das Modul zu den Cheats/Bots (unabhaengig davon, ob Clean Modules an ist)? */
    public static boolean istCheat(Module m) {
        return m.getCategory() == Module.Category.CHEATS || m.getCategory() == Module.Category.BOTS
                || AUCH.contains(m.getName());
    }

    /** Jetzt verstecken? */
    public static boolean versteckt(Module m) {
        return aktiv() && istCheat(m);
    }

    /** Ganze Kategorie jetzt verstecken? */
    public static boolean versteckt(Module.Category c) {
        return aktiv() && (c == Module.Category.CHEATS || c == Module.Category.BOTS);
    }

    /** Jeden Client-Tick: Cheats aus halten, beim Einschalten den Chat leeren. */
    public static void tick(Minecraft mc) {
        boolean an = aktiv();
        if (an) {
            boolean geaendert = false;
            for (Module m : ModuleManager.INSTANCE.getModules()) {
                if (istCheat(m) && m.isEnabled()) {
                    try { m.setEnabled(false); } catch (Throwable t) { Errors.report("CleanModules", t); }
                    geaendert = true;
                }
            }
            if (geaendert) ConfigManager.save();
            if (Boolean.FALSE.equals(zuletzt)) chatLeeren(mc);
        }
        zuletzt = an;
    }

    /** Sichtbaren Chat und den Verlauf gesendeter Nachrichten leeren. */
    private static void chatLeeren(Minecraft mc) {
        try {
            //#if 26.2
            mc.gui.hud.getChat().clearMessages(true);
            //#else
            //$ mc.gui.getChat().clearMessages(true);
            //#endif
        } catch (Throwable t) {
            Errors.report("CleanModules.chat", t);
        }
    }
}
