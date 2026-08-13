package com.vortex.client.module.modules;

import com.vortex.client.core.setting.BooleanSetting;
import com.vortex.client.core.setting.ColorSetting;
import com.vortex.client.core.setting.NumberSetting;
import com.vortex.client.module.Module;

/**
 * Ziel-Info: zeigt ueber dem Kopf anderer Spieler, was sie tragen -- und ob sie
 * in Angriffsreichweite sind.
 *
 * Die Reichweiten-Anzeige nimmt den Wert, den das Spiel selbst fuer Angriffe
 * verwendet. Man sieht also verlaesslich, ob ein Schlag ueberhaupt treffen kann,
 * statt es zu schaetzen.
 *
 * Rein anzeigend: alle Angaben stammen aus Daten, die der Client ohnehin hat
 * (Ausruestung wird an alle Umstehenden geschickt, damit sie dargestellt werden
 * kann). Es wird nichts erfragt und nichts gesendet.
 */
public class TargetInfoModule extends Module {

    /** Ausruestung der Gegner ueber ihrem Kopf anzeigen. */
    public final BooleanSetting armor = new BooleanSetting("Show Armor", true);

    /** Haltbarkeit der Ruestungsteile in Prozent dazuschreiben. */
    public final BooleanSetting durability = new BooleanSetting("Durability", true);

    /** Reichweiten-Anzeige: faerbt den Namen je nach Erreichbarkeit. */
    public final BooleanSetting range = new BooleanSetting("Range", true);
    public final ColorSetting inRangeColor = new ColorSetting("In Range", 0xFF55FF7A);
    public final ColorSetting outRangeColor = new ColorSetting("Out of Range", 0xFFFF5555);

    /** Bis zu welcher Entfernung die Anzeige ueberhaupt erscheint. */
    public final NumberSetting maxDistance = new NumberSetting("Max Distance", 24, 4, 64, 4);

    public TargetInfoModule() {
        super("Target Info", Category.PVP);
        addSetting(armor);
        addSetting(durability);
        addSetting(range);
        addSetting(inRangeColor);
        addSetting(outRangeColor);
        addSetting(maxDistance);
    }
}
