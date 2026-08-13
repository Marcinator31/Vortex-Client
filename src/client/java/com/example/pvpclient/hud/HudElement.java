package com.example.pvpclient.hud;

import com.example.pvpclient.core.setting.ColorSetting;
import com.example.pvpclient.core.setting.NumberSetting;

/**
 * Gemeinsame Schnittstelle fuer alle HUD-Module, die eine frei verschiebbare
 * Position (x/y) und eine Skalierung haben. Der HUD-Editor (Drag-and-Drop)
 * nutzt das, um die Elemente generisch anzufassen, ohne jeden Modultyp
 * einzeln zu kennen.
 *
 * Die Module geben einfach ihre vorhandenen x/y/scale/color-Settings zurueck.
 */
public interface HudElement {
    /** Anzeigename im Editor (z.B. "FPS", "CPS"). */
    String hudName();

    /** Das X-Positions-Setting (Pixel). */
    NumberSetting hudX();

    /** Das Y-Positions-Setting (Pixel). */
    NumberSetting hudY();

    /** Das Skalierungs-Setting. */
    NumberSetting hudScale();

    /** Die Textfarbe dieses HUD-Elements (fuer "Farbe auf alle anwenden"). */
    ColorSetting hudColor();

    /** Ungefaehre Breite des Elements in Pixeln (fuer den Editor-Kasten). */
    int hudWidth();

    /** Ungefaehre Hoehe des Elements in Pixeln (fuer den Editor-Kasten). */
    int hudHeight();
}
