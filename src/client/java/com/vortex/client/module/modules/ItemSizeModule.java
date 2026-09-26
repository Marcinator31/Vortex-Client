package com.vortex.client.module.modules;

import com.vortex.client.core.setting.ModeSetting;
import com.vortex.client.core.setting.NumberSetting;
import com.vortex.client.module.Module;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Gedroppte Gegenstaende groesser (oder kleiner) zeichnen.
 *
 * Im Kampf liegen nach einem Tod Totems, Goldaepfel und Perlen zwischen
 * Bloecken und Gras -- groesser sieht man sie sofort. Nur die Anzeige wird
 * veraendert; die Trefferflaeche zum Aufsammeln bleibt, wie sie ist.
 *
 *   Scale   1.0 = normal, 2.0 = doppelt so gross
 *   Items   All            = alle
 *           Valuables Only = Totems, Goldaepfel, Perlen, Traenke,
 *                            Diamant/Netherite, Elytra, Shulker, Kristalle ...
 *           Selected Items = nur die, die du in der Auswahl ("Select items")
 *                            angehakt hast
 *
 * Laeuft zusammen mit Item Physics: dann liegen die Gegenstaende flach UND
 * gross.
 */
public class ItemSizeModule extends Module
        implements com.vortex.client.module.ExtraData, com.vortex.client.module.HasOwnScreen {

    public final NumberSetting scale = new NumberSetting("Scale", 1.6, 0.5, 4.0, 0.1);
    public final ModeSetting items = new ModeSetting("Items", 0, "All", "Valuables Only", "Selected Items");

    /** Angehakte Gegenstaende (z. B. "minecraft:totem_of_undying"). */
    private final Set<String> ausgewaehlt = new LinkedHashSet<>();

    public ItemSizeModule() {
        super("Item Size", Category.PVP);
        addSetting(scale);
        addSetting(items);
    }

    public Set<String> ausgewaehlt() {
        return ausgewaehlt;
    }

    // --- ExtraData ------------------------------------------------------------
    @Override public String extraKey() { return "__items__"; }
    @Override public String serializeExtra() { return String.join(",", ausgewaehlt); }
    @Override public void deserializeExtra(String value) {
        ausgewaehlt.clear();
        if (value == null) return;
        for (String s : value.split(",")) if (!s.isBlank()) ausgewaehlt.add(s.trim());
    }
    @Override public void clearExtra() { ausgewaehlt.clear(); }

    // --- Auswahlfenster ----------------------------------------------------------
    @Override public String screenButtonLabel() { return "Select items"; }

    @Override
    public net.minecraft.client.gui.screens.Screen createScreen(net.minecraft.client.gui.screens.Screen parent) {
        // Wer Gegenstaende auswaehlt, will auch nur die -- Modus gleich umstellen.
        if (items.getIndex() != 2) items.set("Selected Items");
        return new com.vortex.client.gui.ItemSizeScreen(parent, this);
    }
}
