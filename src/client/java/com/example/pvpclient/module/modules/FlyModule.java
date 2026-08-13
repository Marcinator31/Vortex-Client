package com.example.pvpclient.module.modules;

import com.example.pvpclient.core.setting.BooleanSetting;
import com.example.pvpclient.core.setting.NumberSetting;
import com.example.pvpclient.module.Module;

/**
 * Fly: laesst einen wie im Kreativmodus fliegen, mit einstellbarer
 * Geschwindigkeit.
 *
 * Technisch wird dem Spieler clientseitig die Flug-Faehigkeit gesetzt
 * (allowFlying/flying) und die Fluggeschwindigkeit angepasst -- also genau die
 * Werte, die der Kreativmodus auch benutzt. Steuerung daher wie im Kreativ:
 * doppelt Springen zum Umschalten, Leertaste hoch, Sneak runter.
 *
 * ACHTUNG: Auf Servern ist das serverseitig NICHT erlaubt. Der Server erwartet
 * Bewegungen, die zur Schwerkraft passen -- Fliegen faellt jedem Anticheat sofort
 * auf und fuehrt in der Regel direkt zum Bann.
 */
public class FlyModule extends Module {

    // Multiplikator auf die normale Kreativ-Fluggeschwindigkeit (0.05).
    public final NumberSetting speed =
            new NumberSetting("Geschwindigkeit", 1.0, 0.1, 10.0, 0.1);

    // Beim Ausschalten sanft absetzen statt sofort zu fallen.
    public final BooleanSetting keepAllowFlying =
            new BooleanSetting("Schweben nach Aus", false);

    public FlyModule() {
        super("Fly", Category.PVP);
        addSetting(speed);
        addSetting(keepAllowFlying);
    }

    public float flySpeed() {
        return (float) (0.05 * speed.get());
    }
}
