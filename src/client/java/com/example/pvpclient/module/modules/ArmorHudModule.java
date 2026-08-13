package com.example.pvpclient.module.modules;

import com.example.pvpclient.core.setting.ColorSetting;
import com.example.pvpclient.core.setting.ModeSetting;
import com.example.pvpclient.core.setting.NumberSetting;
import com.example.pvpclient.module.Module;

/**
 * ArmorHUD als Modul.
 *
 * Kann jetzt:
 *  - die ganze HUD verschieben (baseX / baseY)
 *  - skalieren (scale)
 *  - die Haltbarkeits-Anzeige waehlen: Prozent / Schlaege / Balken
 *  - die Textfarbe der Prozentanzeige waehlen (textColor)
 *  - JEDES Teil einzeln verschieben (Offsets je Helm/Brust/Hose/Schuhe/Waffe)
 *
 * Die Einzel-Offsets werden ZUSAETZLICH zur Basisposition angewendet.
 */
public class ArmorHudModule extends Module {

    // Haltbarkeits-Anzeige: drei Modi.
    //   "Prozent" -> z.B. "85%"
    //   "Schlaege" -> verbleibende Haltbarkeitspunkte als Zahl
    //   "Balken"  -> der Vanilla-Haltbarkeitsbalken (gruen -> rot)
    public final ModeSetting durabilityMode =
        new ModeSetting("Haltbarkeit", 0, "Prozent", "Schlaege", "Balken");
    public final ColorSetting textColor = new ColorSetting("Textfarbe", 0xFFFFFFFF);
    public final NumberSetting scale = new NumberSetting("Skalierung", 1.0, 0.5, 3.0, 0.1);

    public final NumberSetting baseX = new NumberSetting("X", 0, -1920, 1920, 1);
    public final NumberSetting baseY = new NumberSetting("Y", 0, -1080, 1080, 1);

    public final NumberSetting helmetOffsetX = new NumberSetting("Helm X", 0, -400, 400, 1);
    public final NumberSetting helmetOffsetY = new NumberSetting("Helm Y", 0, -400, 400, 1);
    public final NumberSetting chestOffsetX  = new NumberSetting("Brust X", 0, -400, 400, 1);
    public final NumberSetting chestOffsetY  = new NumberSetting("Brust Y", 0, -400, 400, 1);
    public final NumberSetting legsOffsetX   = new NumberSetting("Hose X", 0, -400, 400, 1);
    public final NumberSetting legsOffsetY   = new NumberSetting("Hose Y", 0, -400, 400, 1);
    public final NumberSetting bootsOffsetX  = new NumberSetting("Schuhe X", 0, -400, 400, 1);
    public final NumberSetting bootsOffsetY  = new NumberSetting("Schuhe Y", 0, -400, 400, 1);
    public final NumberSetting handOffsetX   = new NumberSetting("Waffe X", 0, -400, 400, 1);
    public final NumberSetting handOffsetY   = new NumberSetting("Waffe Y", 0, -400, 400, 1);

    public ArmorHudModule() {
        super("ArmorHUD", Category.HUD);
        enabledByDefault();
        addSetting(durabilityMode);
        addSetting(textColor);
        addSetting(scale);
        addSetting(baseX);
        addSetting(baseY);
        addSetting(helmetOffsetX);
        addSetting(helmetOffsetY);
        addSetting(chestOffsetX);
        addSetting(chestOffsetY);
        addSetting(legsOffsetX);
        addSetting(legsOffsetY);
        addSetting(bootsOffsetX);
        addSetting(bootsOffsetY);
        addSetting(handOffsetX);
        addSetting(handOffsetY);
    }

    /**
     * Ein einzelnes ArmorHUD-Teil fuer den HUD-Editor: Name, aktuelle
     * Bildschirmposition (linke obere Ecke) und die zugehoerigen Offset-
     * Settings, die beim Ziehen veraendert werden.
     */
    public static final class ArmorPart {
        public final String name;
        public final int x;
        public final int y;
        public final int size;
        public final NumberSetting offsetX;
        public final NumberSetting offsetY;

        ArmorPart(String name, int x, int y, int size,
                  NumberSetting offsetX, NumberSetting offsetY) {
            this.name = name;
            this.x = x;
            this.y = y;
            this.size = size;
            this.offsetX = offsetX;
            this.offsetY = offsetY;
        }
    }

    /** Konstanten, identisch zur Zeichen-Logik in ArmorHud. */
    private static final int SLOT = 16;
    private static final int PADDING = 2;

    /**
     * Berechnet die aktuelle Bildschirmposition ALLER 5 Teile -- exakt so wie
     * ArmorHud sie zeichnet. Der HUD-Editor nutzt das, um verschiebbare Kaesten
     * an die richtige Stelle zu legen. screenW/screenH = Bildschirmgroesse.
     *
     * Reihenfolge: Helm, Brust, Hose, Schuhe, Waffe.
     */
    public java.util.List<ArmorPart> computeParts(int screenW, int screenH) {
        double sc = scale.get();
        int step = (int) Math.round((SLOT + PADDING) * sc);
        int totalHeight = 5 * step;
        int bX = screenW - SLOT - 6 + baseX.getInt();
        int startY = (screenH - totalHeight) / 2 + baseY.getInt();

        NumberSetting[][] offs = new NumberSetting[][] {
            { helmetOffsetX, helmetOffsetY },
            { chestOffsetX,  chestOffsetY  },
            { legsOffsetX,   legsOffsetY   },
            { bootsOffsetX,  bootsOffsetY  },
            { handOffsetX,   handOffsetY   }
        };
        String[] names = { "Helm", "Brust", "Hose", "Schuhe", "Waffe" };

        java.util.List<ArmorPart> parts = new java.util.ArrayList<>();
        for (int i = 0; i < 5; i++) {
            int x = bX + offs[i][0].getInt();
            int y = startY + i * step + offs[i][1].getInt();
            parts.add(new ArmorPart(names[i], x, y, SLOT, offs[i][0], offs[i][1]));
        }
        return parts;
    }
}
