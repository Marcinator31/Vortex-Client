package com.example.pvpclient.module.modules;

import com.example.pvpclient.core.setting.BooleanSetting;
import com.example.pvpclient.core.setting.ColorSetting;
import com.example.pvpclient.core.setting.NumberSetting;
import com.example.pvpclient.hud.HudElement;
import com.example.pvpclient.module.Module;

/**
 * Entity-/ESP-Radar: ein runder Mini-Radar (wie ein Kompass-Sonar), der die
 * Entities im Umkreis anzeigt.
 *
 *  - Spieler ist in der Mitte als Pfeil (zeigt die Blickrichtung).
 *  - Entities erscheinen als Punkte rundherum, ihrer echten Lage entsprechend.
 *  - Reichweite standardmaessig 512 Bloecke (32 Chunks), einstellbar.
 *  - Bei SPIELERN zusaetzlich: Kopf, Name und Entfernung in Metern.
 *
 * Welche Entity-Typen gezeigt werden, ist per Schalter einstellbar
 * (Spieler / Feindliche / Tiere / Items). Die Punktfarbe pro Typ ist
 * ueber die Render-Logik fest, der Radar selbst hat Position/Groesse/Farbe
 * als HUD-Element (verschiebbar im HUD-Editor).
 *
 * Das Zeichnen passiert im RadarRenderer; dieses Modul haelt nur die
 * Einstellungen und gilt als HUD-Element (Position/Skalierung/Farbe).
 */
public class RadarModule extends Module implements HudElement {

    // HUD-Element-Position (linke obere Ecke des Radar-Quadrats).
    public final NumberSetting x = new NumberSetting("X", 10, 0, 1920, 1);
    public final NumberSetting y = new NumberSetting("Y", 80, 0, 1080, 1);
    public final ColorSetting color = new ColorSetting("Rahmenfarbe", 0xFF55FF7A);
    // Skalierung wirkt auf den Radar-Durchmesser.
    public final NumberSetting scale = new NumberSetting("Skalierung", 1.0, 0.5, 3.0, 0.1);

    // Radius der Erfassung in Bloecken (32 Chunks = 512).
    public final NumberSetting range = new NumberSetting("Reichweite (Bloecke)", 512, 32, 512, 16);

    // Welche Entity-Typen anzeigen.
    public final BooleanSetting showPlayers  = new BooleanSetting("Spieler", true);
    public final BooleanSetting showHostiles = new BooleanSetting("Feindliche", true);
    public final BooleanSetting showAnimals  = new BooleanSetting("Tiere", false);
    public final BooleanSetting showItems    = new BooleanSetting("Items", false);

    // Bei Spielern Kopf + Name + Entfernung anzeigen.
    public final BooleanSetting playerDetails = new BooleanSetting("Spieler-Details", true);

    public RadarModule() {
        super("Radar", Category.HUD);
        addSetting(x);
        addSetting(y);
        addSetting(color);
        addSetting(scale);
        addSetting(range);
        addSetting(showPlayers);
        addSetting(showHostiles);
        addSetting(showAnimals);
        addSetting(showItems);
        addSetting(playerDetails);
    }

    /** Basis-Durchmesser des Radars in Pixeln (vor Skalierung). */
    public int baseDiameter() { return 80; }

    @Override public String hudName() { return "Radar"; }
    @Override public NumberSetting hudX() { return x; }
    @Override public NumberSetting hudY() { return y; }
    @Override public NumberSetting hudScale() { return scale; }
    @Override public ColorSetting hudColor() { return color; }
    @Override public int hudWidth() { return (int) Math.round(baseDiameter() * scale.get()); }
    @Override public int hudHeight() { return (int) Math.round(baseDiameter() * scale.get()); }
}
