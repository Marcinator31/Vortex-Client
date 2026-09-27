package com.vortex.client.module.modules;

import com.vortex.client.core.setting.BooleanSetting;
import com.vortex.client.core.setting.ColorSetting;
import com.vortex.client.core.setting.ModeSetting;
import com.vortex.client.core.setting.NumberSetting;
import com.vortex.client.module.Module;

/**
 * Eigene Farbe fuer das Aufleuchten, wenn jemand Schaden nimmt.
 *
 * Minecraft faerbt getroffene Wesen kurz rot ein. Diese Farbe steckt in einer
 * kleinen Textur, die das Spiel fuer alle Wesen benutzt -- hier wird genau
 * diese Textur umgefaerbt. Die Deckkraft der Farbe bestimmt, wie stark das
 * Aufleuchten ist. Beim Ausschalten kommt das Vanilla-Rot zurueck.
 */
public class HitColorModule extends Module {

    public final ColorSetting color = new ColorSetting("Color", 0xB28B5CF6);

    /**
     * Auch Ruestung (und Elytra, Pferde-/Wolfsruestung) beim Treffer einfaerben.
     *
     * Vanilla laesst Ruestung beim Treffer bewusst unveraendert -- ihr Shader
     * hat das Treffer-Aufleuchten gar nicht eingebaut. Mit dieser Einstellung
     * wird die Ruestung waehrend des Aufleuchtens stattdessen mit der
     * Trefferfarbe getoent. AUS = wie Vanilla (nur der Koerper leuchtet).
     */
    public final BooleanSetting includeArmor = new BooleanSetting("Include Armor", false);

    public HitColorModule() {
        super("Hit Color", Category.PVP);
        addSetting(color);
        addSetting(includeArmor);
    }

    /** Toenung fuer Ruestung im Treffer-Moment (ARGB), oder -1 = keine. */
    public static int ruestungsToenung() {
        HitColorModule m = com.vortex.client.module.ModuleManager.INSTANCE.get(HitColorModule.class);
        if (m == null || !m.isEnabled() || !m.includeArmor.get()) return -1;
        int c = m.color.get();
        float staerke = ((c >>> 24) & 0xFF) / 255f;
        // Von Weiss (= keine Toenung) Richtung Trefferfarbe, so stark wie die
        // Deckkraft der Farbe -- dieselbe Bedeutung wie beim Koerper.
        return com.vortex.client.core.setting.ColorSetting.mix(0xFFFFFFFF, c | 0xFF000000, staerke);
    }
}
