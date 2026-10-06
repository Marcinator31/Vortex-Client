package com.vortex.client.core;

import com.vortex.client.core.setting.BooleanSetting;
import com.vortex.client.core.setting.ModeSetting;
import com.vortex.client.core.setting.Setting;
import com.vortex.client.module.Module;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Allgemeine Einstellungen des Clients (nicht an ein Modul gebunden).
 *
 * Zu finden im Mods-Menue oben ("Settings") und im Startmenue unten.
 * Gespeichert in global.txt (gilt fuer alle Presets), Zeilen "__client__".
 */
public final class ClientSettings {

    public static final ClientSettings INSTANCE = new ClientSettings();

    /** Wo eine Meldung erscheint, wenn ein Modul an- oder ausgeht. */
    public final ModeSetting toggleMessage =
            new ModeSetting("Toggle Message", 1, "Off", "Above Hotbar", "Chat", "Popup");
    /** Nur bei der Modul-Taste melden -- oder auch beim Klick im Menue. */
    public final ModeSetting toggleMessageFor =
            new ModeSetting("Toggle Message For", 0, "Keybinds", "Keybinds And Menu");
    /** Kurzer Klick-Ton beim Umschalten per Taste. */
    public final BooleanSetting toggleSound = new BooleanSetting("Toggle Sound", false);
    /** Was Rechts-Shift oeffnet. */
    public final ModeSetting rightShiftOpens =
            new ModeSetting("Right Shift Opens", 0, "Start Screen", "Mods");
    /** Beschreibung eines Moduls anzeigen, wenn man im Menue darueberfaehrt. */
    public final BooleanSetting moduleTooltips = new BooleanSetting("Module Tooltips", true);
    /** Vor dem Neustart des Spiels nachfragen. */
    public final BooleanSetting confirmRestart = new BooleanSetting("Confirm Restart", true);
    /** Neues, glattes Aussehen fuer Haupt-, Einzelspieler-, Mehrspieler- und Pausenmenue. */
    public final BooleanSetting modernMenus = new BooleanSetting("Modern Menus", true);
    /** Hintergrund der Menues: Vortex-Panoramen (echte Szenen) oder das von Minecraft. */
    public final ModeSetting menuPanorama =
            new ModeSetting("Menu Panorama", 0, com.vortex.client.gui.Panoramen.optionen());
    /** Cheats und Bots verstecken und ausschalten, Chat leeren (siehe CleanModules). */
    public final BooleanSetting cleanModules = new BooleanSetting("Clean Modules", false);

    private ClientSettings() {}

    public List<Setting> all() {
        return List.of(cleanModules, toggleMessage, toggleMessageFor, toggleSound, rightShiftOpens, moduleTooltips, confirmRestart, modernMenus, menuPanorama);
    }

    /**
     * Ein Modul wurde umgeschaltet. {@code perTaste}: ueber seine Taste (sonst
     * im Menue). Zeigt die Meldung dort an, wo es eingestellt ist.
     */
    public static void umgeschaltet(Module m, boolean perTaste) {
        try {
            ClientSettings c = INSTANCE;
            if (!perTaste && c.toggleMessageFor.getIndex() == 0) return;
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;
            boolean an = m.isEnabled();
            if (perTaste && c.toggleSound.get()) {
                mc.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                        net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, an ? 1.2f : 0.9f));
            }
            switch (c.toggleMessage.getIndex()) {
                case 1 -> mc.player.sendOverlayMessage(Component.literal(
                        m.getName() + (an ? " §aenabled" : " §cdisabled")));
                case 2 -> mc.player.sendSystemMessage(Component.literal(
                        "§d[Vortex] §f" + m.getName() + (an ? " §aenabled" : " §cdisabled")));
                case 3 -> com.vortex.client.social.SocialToasts.push(m.getName(), an ? "Enabled" : "Disabled", false);
                default -> { }
            }
        } catch (Throwable pvpErr) {
            Errors.report("ClientSettings.toggle", pvpErr);
        }
    }
}
