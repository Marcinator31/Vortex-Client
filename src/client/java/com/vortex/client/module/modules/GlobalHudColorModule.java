package com.vortex.client.module.modules;

import com.vortex.client.core.setting.BooleanSetting;
import com.vortex.client.core.setting.ColorSetting;
import com.vortex.client.hud.HudElement;
import com.vortex.client.module.Module;
import com.vortex.client.module.ModuleManager;

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

    public final ColorSetting color = new ColorSetting("Color", WHITE);
    public final BooleanSetting apply = new BooleanSetting("Apply Now", false);
    public final BooleanSetting reset = new BooleanSetting("Reset (White)", false);

    public GlobalHudColorModule() {
        super("HUD Color", Category.HUD);
        addSetting(color);
        addSetting(apply);
        addSetting(reset);
    }

    /** Uebertraegt die eingestellte Farbe auf alle HUD-Elemente. */
    public void applyToAll() {
        applyColor(color);
        // Knopf zuruecksetzen (er ist nur ein Ausloeser, kein Dauerzustand).
        apply.set(false);
    }

    /** Setzt alle HUD-Texte wieder auf Weiss zurueck. */
    public void resetToWhite() {
        // Auch die eingestellte Farbe selbst auf Weiss zuruecksetzen, damit
        // die Anzeige konsistent ist -- und zwar einfarbig.
        color.set(WHITE);
        color.setType(ColorSetting.SOLID);
        applyColor(color);
        reset.set(false);
    }

    /**
     * Uebertraegt die Farbe auf alle HUD-Elemente (inkl. ArmorHud) -- mit
     * Verlauf, Art und Tempo. Ein Verlauf auf "HUD Color" macht also alle
     * HUD-Texte zum Verlauf.
     */
    private void applyColor(ColorSetting c) {
        for (Module m : ModuleManager.INSTANCE.getModules()) {
            // Alle HUD-Elemente mit Farb-Setting einfaerben.
            if (m instanceof HudElement he && he.hudColor() != null) {
                he.hudColor().copyFrom(c);
            }
            // ArmorHud implementiert HudElement nicht (kein x/y), hat aber
            // eine textColor -- separat mit abdecken.
            if (m instanceof ArmorHudModule armor) {
                armor.textColor.copyFrom(c);
            }
        }
    }
}
