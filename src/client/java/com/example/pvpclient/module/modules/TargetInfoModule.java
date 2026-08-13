package com.example.pvpclient.module.modules;

import com.example.pvpclient.core.setting.BooleanSetting;
import com.example.pvpclient.core.setting.ColorSetting;
import com.example.pvpclient.core.setting.NumberSetting;
import com.example.pvpclient.module.Module;

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
    public final BooleanSetting armor = new BooleanSetting("Ruestung zeigen", true);

    /** Haltbarkeit der Ruestungsteile in Prozent dazuschreiben. */
    public final BooleanSetting durability = new BooleanSetting("Haltbarkeit", true);

    /** Reichweiten-Anzeige: faerbt den Namen je nach Erreichbarkeit. */
    public final BooleanSetting range = new BooleanSetting("Reichweite", true);
    public final ColorSetting inRangeColor = new ColorSetting("In Reichweite", 0xFF55FF7A);
    public final ColorSetting outRangeColor = new ColorSetting("Ausser Reichweite", 0xFFFF5555);

    /** Bis zu welcher Entfernung die Anzeige ueberhaupt erscheint. */
    public final NumberSetting maxDistance = new NumberSetting("Max. Distanz", 24, 4, 64, 4);

    public TargetInfoModule() {
        super("Ziel-Info", Category.PVP);
        addSetting(armor);
        addSetting(durability);
        addSetting(range);
        addSetting(inRangeColor);
        addSetting(outRangeColor);
        addSetting(maxDistance);
    }
}
