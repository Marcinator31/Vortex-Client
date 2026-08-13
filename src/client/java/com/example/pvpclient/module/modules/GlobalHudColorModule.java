package com.example.pvpclient.module.modules;

import com.example.pvpclient.core.setting.BooleanSetting;
import com.example.pvpclient.core.setting.ColorSetting;
import com.example.pvpclient.hud.HudElement;
import com.example.pvpclient.module.Module;
import com.example.pvpclient.module.ModuleManager;

/**
 * "HUD-Farbe" -- wendet EINE Farbe auf alle HUD-Elemente gleichzeitig an
 * (FPS, CPS, Koordinaten, Trank-Effekte, Ruestungs-HUD).
 *
 * Bedienung im ClickGUI:
 *   - "Farbe" einstellen -> wird beim Aendern SOFORT live auf alle uebertragen.
 *   - "Jetzt anwenden" -> uebertraegt die Farbe noch einmal manuell.
 *   - "Zuruecksetzen (weiss)" -> setzt alle HUD-Texte wieder auf Weiss.
 *
 * So muss man nicht jedes HUD-Element einzeln einfaerben. Dieses Modul ist
 * ein Werkzeug, kein an/aus-Feature -- der "Aktiviert"-Schalter spielt hier
 * keine Rolle, es zaehlen nur die Knoepfe.
 */
public class GlobalHudColorModule extends Module {

    private static final int WHITE = 0xFFFFFFFF;

    public final ColorSetting color = new ColorSetting("Farbe", WHITE);
    public final BooleanSetting apply = new BooleanSetting("Jetzt anwenden", false);
    public final BooleanSetting reset = new BooleanSetting("Zuruecksetzen (weiss)", false);

    public GlobalHudColorModule() {
        super("HUD-Farbe", Category.HUD);
        addSetting(color);
        addSetting(apply);
        addSetting(reset);
    }

    /** Uebertraegt die eingestellte Farbe auf alle HUD-Elemente. */
    public void applyToAll() {
        applyColor(color.get());
        // Knopf zuruecksetzen (er ist nur ein Ausloeser, kein Dauerzustand).
        apply.set(false);
    }

    /** Setzt alle HUD-Texte wieder auf Weiss zurueck. */
    public void resetToWhite() {
        applyColor(WHITE);
        // Auch die eingestellte Farbe selbst auf Weiss zuruecksetzen, damit
        // die Anzeige konsistent ist.
        color.set(WHITE);
        reset.set(false);
    }

    /** Setzt die gegebene Farbe auf alle HUD-Elemente (inkl. ArmorHud). */
    private void applyColor(int c) {
        for (Module m : ModuleManager.INSTANCE.getModules()) {
            // Alle HUD-Elemente mit Farb-Setting einfaerben.
            if (m instanceof HudElement he) {
                he.hudColor().set(c);
            }
            // ArmorHud implementiert HudElement nicht (kein x/y), hat aber
            // eine textColor -- separat mit abdecken.
            if (m instanceof ArmorHudModule armor) {
                armor.textColor.set(c);
            }
        }
    }
}
