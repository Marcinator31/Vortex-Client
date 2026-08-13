package com.vortex.client.module.modules;

import com.vortex.client.core.setting.BooleanSetting;
import com.vortex.client.core.setting.ColorSetting;
import com.vortex.client.core.setting.NumberSetting;
import com.vortex.client.module.Module;

/**
 * Projektil-Flugbahn: zeigt vorher an, wo eine Enderperle (oder ein Schneeball,
 * Ei, Wurftrank, Erfahrungsflasche) landen wird.
 *
 * Die Bahn wird mit denselben Regeln berechnet, die Minecraft auch beim echten
 * Wurf anwendet: Startgeschwindigkeit aus der Blickrichtung, pro Tick etwas
 * Luftwiderstand und Schwerkraft. Gerechnet wird ausschliesslich im Client mit
 * Daten, die ohnehin vorliegen -- es wird nichts an den Server gesendet, und am
 * Spielverhalten aendert sich nichts. Man sieht nur vorher, was ohnehin passieren
 * wuerde.
 */
public class ProjectilePathModule extends Module {

    public final ColorSetting color = new ColorSetting("Farbe", 0xFF55FFFF);
    public final NumberSetting lineWidth =
            new NumberSetting("Linienbreite", 2.0, 0.5, 5.0, 0.5);

    /** Kaestchen am Einschlagpunkt einzeichnen. */
    public final BooleanSetting marker = new BooleanSetting("Landepunkt", true);

    /** Auch anzeigen, wenn das Wurfobjekt in der Nebenhand liegt. */
    public final BooleanSetting offHand = new BooleanSetting("Nebenhand", true);

    /** Wie viele Schritte hoechstens vorausberechnet werden (1 Schritt = 1 Tick). */
    public final NumberSetting maxSteps = new NumberSetting("Vorausschau", 120, 20, 300, 10);

    /**
     * Auch die Bahn von Pfeilen zeigen (Bogen und Armbrust).
     *
     * Beim Bogen haengt Geschwindigkeit und damit Reichweite davon ab, wie weit
     * gespannt wurde -- die Bahn passt sich waehrend des Spannens laufend an.
     */
    public final BooleanSetting bow = new BooleanSetting("Bogen/Armbrust", true);

    public ProjectilePathModule() {
        super("Projektil-Flugbahn", Category.PVP);
        addSetting(color);
        addSetting(lineWidth);
        addSetting(marker);
        addSetting(offHand);
        addSetting(maxSteps);
        addSetting(bow);
    }
}
