package com.vortex.client.module.modules;

import com.vortex.client.core.setting.ModeSetting;
import com.vortex.client.core.setting.NumberSetting;
import com.vortex.client.module.Module;

/**
 * Gedroppte Gegenstaende groesser (oder kleiner) zeichnen.
 *
 * Im Kampf liegen nach einem Tod Totems, Goldaepfel und Perlen zwischen
 * Bloecken und Gras -- groesser sieht man sie sofort. Nur die Anzeige wird
 * veraendert; die Trefferflaeche zum Aufsammeln bleibt, wie sie ist.
 *
 *   Scale   1.0 = normal, 2.0 = doppelt so gross
 *   Items   All = alle, Valuables Only = nur Wertvolles (Totems, Goldaepfel,
 *           Perlen, Traenke, Diamant/Netherite, Elytra, Shulker, Kristalle,
 *           XP-Flaschen)
 *
 * Laeuft zusammen mit Item Physics: dann liegen die Gegenstaende flach UND
 * gross.
 */
public class ItemSizeModule extends Module {

    public final NumberSetting scale = new NumberSetting("Scale", 1.6, 0.5, 4.0, 0.1);
    public final ModeSetting items = new ModeSetting("Items", 0, "All", "Valuables Only");

    public ItemSizeModule() {
        super("Item Size", Category.PVP);
        addSetting(scale);
        addSetting(items);
    }
}
