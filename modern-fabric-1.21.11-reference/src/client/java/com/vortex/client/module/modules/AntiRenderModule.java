package com.vortex.client.module.modules;

import com.vortex.client.core.setting.BooleanSetting;
import com.vortex.client.core.setting.NumberSetting;
import com.vortex.client.module.Module;
import net.minecraft.util.Identifier;

import java.util.HashSet;
import java.util.Set;

/**
 * Anti Render: blendet ausgewaehlte Entity-Typen komplett aus dem Rendering aus
 * -- inklusive nicht-lebendiger Entities wie Ruestungsstaender, Loren
 * (Minecarts), Boote, Item-Rahmen usw.
 *
 * Sinn: Das Zeichnen vieler Entities ist teuer. Wenn z.B. tausende gestackte
 * Loren die FPS zerstoeren (bekannte "Entity-Lag-Maschine" auf Servern), kann man
 * hier einfach "minecraft:minecart" ausblenden -- die Loren werden dann gar nicht
 * mehr gerendert und die FPS kommen zurueck. Rein clientseitig: die Entities
 * existieren weiter, sie werden nur nicht gezeichnet.
 *
 * Welche Typen ausgeblendet werden, waehlt man im Anti-Render-Menue (Grid aller
 * Entity-Typen). Die Auswahl wird als Menge von Entity-Type-IDs gehalten und
 * kommasepariert persistiert.
 */
public class AntiRenderModule extends Module {

    /**
     * Distanz-Culling: Entities, die weiter als dieser Wert (in Bloecken) von der
     * Kamera entfernt sind, werden gar nicht erst gerendert. 0 = aus.
     *
     * Das ist unabhaengig von der Typ-Auswahl unten und wirkt auf alles -- gut
     * gegen viele weit entfernte Entities, die man ohnehin kaum sieht.
     */
    public final NumberSetting maxDistance =
            new NumberSetting("Max Distance", 0, 0, 256, 8);

    /** Spieler nie per Distanz ausblenden (wichtig fuer PvP). */
    public final BooleanSetting keepPlayers =
            new BooleanSetting("Always Show Players", true);

    // Ausgeblendete Entity-Typen (z.B. "minecraft:minecart").
    private final Set<String> hiddenTypes = new HashSet<>();

    /**
     * Wird bei jeder Aenderung der Auswahl hochgezaehlt. Render-Code kann damit
     * ein eigenes, schnelles Zwischenergebnis halten und merkt trotzdem sofort,
     * wenn sich die Auswahl geaendert hat.
     */
    private int version = 0;

    public int getVersion() { return version; }

    public AntiRenderModule() {
        super("Anti Render", Category.PERFORMANCE);
        addSetting(maxDistance);
        addSetting(keepPlayers);
    }

    /** Soll dieser Entity-Typ (per Identifier) ausgeblendet werden? */
    public boolean isHidden(Identifier id) {
        return id != null && hiddenTypes.contains(id.toString());
    }

    public boolean isHidden(String id) {
        return hiddenTypes.contains(id);
    }

    public void toggle(String id) {
        if (!hiddenTypes.add(id)) hiddenTypes.remove(id);
        version++;
    }

    public void set(String id, boolean on) {
        if (on) hiddenTypes.add(id); else hiddenTypes.remove(id);
        version++;
    }

    public Set<String> getHiddenTypes() {
        return hiddenTypes;
    }

    public String serialize() {
        return String.join(",", hiddenTypes);
    }

    public void deserialize(String data) {
        version++;
        hiddenTypes.clear();
        if (data == null || data.isEmpty()) return;
        for (String s : data.split(",")) {
            String t = s.trim();
            if (!t.isEmpty()) hiddenTypes.add(t);
        }
    }
}
