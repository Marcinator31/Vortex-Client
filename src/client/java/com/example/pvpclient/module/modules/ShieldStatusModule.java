package com.example.pvpclient.module.modules;

import com.example.pvpclient.core.setting.BooleanSetting;
import com.example.pvpclient.module.Module;

/**
 * ShieldStatus-Steuerung -- schaltet ShieldStatus' Schild-Faerbung an/aus.
 *
 * ShieldStatus ist als Mod fest in den Client eingebettet (Jar-in-Jar,
 * zusammen mit seiner Pflicht-Lib WalksyLib) und faerbt Schilde je nach
 * Zustand ein: aktiv, in Benutzung, oder GEBROCHEN (z.B. durch eine Axt).
 * Funktioniert auch fuer gegnerische Schilde.
 *
 * Verifiziert aus ShieldStatus-Bytecode (walksy/shieldstatus/config/Config):
 *   STATISCHE public boolean-Felder (vom ShieldModelRendererMixin gelesen):
 *     modEnabled        -> Haupt-An/Aus            (Default: true)
 *     grayscaleTexture  -> gebrochenen Schild grau (Default: false)
 *     selfStateOnly     -> nur eigener Schild      (Default: false = auch Gegner)
 *     colorInterpolation-> weicher Farbverlauf     (Default: false)
 *
 * Da ShieldStatus per Default bereits AN ist (modEnabled=true) und auch
 * Gegner-Schilde zeigt, ist dieses Modul ebenfalls standardmaessig an --
 * so stimmt der angezeigte Zustand mit der Realitaet ueberein.
 *
 * Felder werden per Reflection gesetzt (statisch -> obj = null), damit
 * unser Code nicht hart gegen ShieldStatus' Klassen kompiliert (die erst
 * zur Laufzeit da sind). Alles in try-catch -> kein Crash falls die Mod fehlt.
 */
public class ShieldStatusModule extends Module {

    // Default-Werte bewusst passend zu ShieldStatus' eigenen Defaults gewaehlt,
    // damit beim Ein-/Ausschalten nichts ueberraschend umspringt.
    public final BooleanSetting grayscaleBroken =
        new BooleanSetting("Gebrochen ausgrauen", false);
    public final BooleanSetting opponentShields =
        new BooleanSetting("Gegner-Schilde", true);
    public final BooleanSetting smoothColor =
        new BooleanSetting("Weicher Farbverlauf", false);

    public ShieldStatusModule() {
        super("Shield Status", Category.PVP);
        // Settings im GUI sichtbar machen.
        addSetting(grayscaleBroken);
        addSetting(opponentShields);
        addSetting(smoothColor);
        // ShieldStatus ist von Haus aus aktiv -> Modul ebenfalls standardmaessig an.
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

    /** Legt ShieldStatus' statische Config-Schalter per Reflection um. */
    private void apply(boolean on) {
        try {
            Class<?> cfgClass = Class.forName("walksy.shieldstatus.config.Config");

            // Haupt-Schalter folgt dem Modul-Zustand.
            setStaticBoolean(cfgClass, "modEnabled", on);

            // Optionen folgen Modul-Zustand UND der jeweiligen Einstellung.
            setStaticBoolean(cfgClass, "grayscaleTexture", on && grayscaleBroken.get());
            // selfStateOnly ist INVERS zu "Gegner-Schilde": wenn wir Gegner
            // sehen wollen, muss selfStateOnly = false sein.
            setStaticBoolean(cfgClass, "selfStateOnly", on && !opponentShields.get());
            setStaticBoolean(cfgClass, "colorInterpolation", on && smoothColor.get());
        } catch (Throwable ignored) {
            // ShieldStatus nicht geladen -> nichts tun (kein Crash).
        }
    }

    /** Setzt ein statisches public boolean-Feld (obj = null). */
    private static void setStaticBoolean(Class<?> cls, String name, boolean value) {
        try {
            var f = cls.getDeclaredField(name);
            f.setAccessible(true);
            f.setBoolean(null, value);
        } catch (Throwable ignored) {
            // Feld evtl. anders benannt -> ueberspringen.
        }
    }
}
