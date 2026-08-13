package com.example.pvpclient.module.modules;

import com.example.pvpclient.core.setting.BooleanSetting;
import com.example.pvpclient.module.Module;

/**
 * AppleSkin-Steuerung -- schaltet AppleSkins echte HUD-Overlays an/aus.
 *
 * AppleSkin ist als Mod fest in den Client eingebettet (Jar-in-Jar) und
 * zeichnet die Saturation direkt auf die Hunger-Keulen, optional die
 * Food-/Health-Vorschau. Dieses Modul legt nur AppleSkins eigene
 * Config-Schalter um -- ueber ModConfig.getInstance().
 *
 * Verifiziert aus AppleSkin-Bytecode (squeek/appleskin/ModConfig):
 *   Felder showSaturationHudOverlay, showFoodValuesHudOverlay,
 *   showFoodHealthHudOverlay; getInstance(); save().
 *
 * Die Felder werden per Reflection gesetzt, damit unser Code nicht hart
 * gegen AppleSkins Klassen kompiliert (die erst zur Laufzeit da sind).
 */
public class SaturationModule extends Module {

    public final BooleanSetting foodPreview =
        new BooleanSetting("Food-Vorschau", true);
    public final BooleanSetting healthPreview =
        new BooleanSetting("Health-Vorschau", true);

    public SaturationModule() {
        super("AppleSkin", Category.HUD);
        // Settings im GUI sichtbar machen.
        addSetting(foodPreview);
        addSetting(healthPreview);
        // AppleSkin zeigt sein Saturation-Overlay von Haus aus an
        // (showSaturationHudOverlay=true). Damit Modul-Anzeige und Realitaet
        // uebereinstimmen, ist auch dieses Modul standardmaessig an.
        enabledByDefault();
    }

    @Override
    protected void onEnable() {
        apply(true);
    }

    @Override
    protected void onDisable() {
        apply(false);
    }

    /** Legt AppleSkins Config-Schalter per Reflection um. */
    private void apply(boolean on) {
        try {
            Class<?> cfgClass = Class.forName("squeek.appleskin.ModConfig");

            // Instanz holen: erst getInstance(), sonst das statische INSTANCE-Feld.
            Object cfg;
            try {
                cfg = cfgClass.getMethod("getInstance").invoke(null);
            } catch (Throwable t) {
                var inst = cfgClass.getDeclaredField("INSTANCE");
                inst.setAccessible(true);
                cfg = inst.get(null);
            }
            if (cfg == null) return;

            // Saturation-Overlay folgt dem Modul-Zustand.
            setField(cfgClass, cfg, "showSaturationHudOverlay", on);
            // Vorschauen folgen Modul-Zustand UND der jeweiligen Einstellung.
            setField(cfgClass, cfg, "showFoodValuesHudOverlay", on && foodPreview.get());
            setField(cfgClass, cfg, "showFoodHealthHudOverlay", on && healthPreview.get());

            // Speichern (optional -- Felder wirken meist sofort).
            try {
                cfgClass.getMethod("save").invoke(cfg);
            } catch (Throwable ignored) {}
        } catch (Throwable ignored) {
            // AppleSkin nicht geladen -> nichts tun (kein Crash).
        }
    }

    private static void setField(Class<?> cls, Object obj, String name, boolean value) {
        try {
            var f = cls.getDeclaredField(name);
            f.setAccessible(true);
            f.setBoolean(obj, value);
        } catch (Throwable ignored) {
            // Feld evtl. anders benannt -> ueberspringen.
        }
    }
}
