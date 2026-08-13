package com.example.pvpclient.gui;

import com.example.pvpclient.core.setting.ColorSetting;

/**
 * Zentrale Farbgebung des Client-GUI.
 *
 * Statt ueberall Farben hartzukodieren, kommen alle GUI-Farben von hier.
 * Das ist die Grundlage fuers "alles farblich anpassen": aendere den
 * Wert hier (oder spaeter ueber einen Farbwaehler im Menue), und das
 * ganze GUI zieht mit.
 *
 * Es sind ColorSettings -- also direkt speicherbar und im GUI editierbar.
 */
public final class Theme {

    public static final Theme INSTANCE = new Theme();

    public final ColorSetting accent      = new ColorSetting("Akzent",        0xFF4C8BF5); // blau
    public final ColorSetting background   = new ColorSetting("Hintergrund",   0xCC1A1A1E); // dunkel, halbtransparent
    public final ColorSetting panel        = new ColorSetting("Panel",         0xFF232329);
    public final ColorSetting text         = new ColorSetting("Text",          0xFFFFFFFF);
    public final ColorSetting textDim       = new ColorSetting("Text gedimmt",  0xFFB0B0B8);
    public final ColorSetting enabledColor = new ColorSetting("An-Farbe",      0xFF55FF7A); // gruen
    public final ColorSetting disabledColor = new ColorSetting("Aus-Farbe",     0xFF55585F); // grau

    /** Alle Farben in fester Reihenfolge -- fuers Menue und zum Speichern. */
    public java.util.List<ColorSetting> all() {
        return java.util.List.of(accent, background, panel, text, textDim,
                enabledColor, disabledColor);
    }

    /** Setzt alles auf die Werksfarben zurueck. */
    public void resetDefaults() {
        opacity.set(0.95);
        accent.set(0xFF4C8BF5);
        background.set(0xCC1A1A1E);
        panel.set(0xFF232329);
        text.set(0xFFFFFFFF);
        textDim.set(0xFFB0B0B8);
        enabledColor.set(0xFF55FF7A);
        disabledColor.set(0xFF55585F);
    }

    /**
     * Durchsichtigkeit der Fenster (1.0 = deckend, 0.3 = stark durchsichtig).
     * Damit sieht man beim Einstellen, was im Spiel dahinter passiert.
     */
    public final com.example.pvpclient.core.setting.NumberSetting opacity =
            new com.example.pvpclient.core.setting.NumberSetting(
                    "Deckkraft", 0.95, 0.30, 1.0, 0.05);

    private Theme() {}
}
