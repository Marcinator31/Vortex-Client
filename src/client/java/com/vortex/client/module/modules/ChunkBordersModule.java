package com.vortex.client.module.modules;

import com.vortex.client.core.setting.BooleanSetting;
import com.vortex.client.core.setting.ColorSetting;
import com.vortex.client.core.setting.NumberSetting;
import com.vortex.client.module.Module;

/**
 * Chunk-Grenzen: zeichnet die Kanten des Chunks, in dem man steht -- und auf
 * Wunsch auch die der Nachbar-Chunks.
 *
 * Nuetzlich beim Bauen von Farmen (viele Mechaniken arbeiten pro Chunk), beim
 * Ausrichten von Bauwerken und beim Base-Hunting, um Fundstellen sauber einem
 * Chunk zuzuordnen.
 *
 * Rein clientseitig: es werden nur Linien gezeichnet, nichts an den Server
 * gesendet.
 */
public class ChunkBordersModule extends Module {

    public final ColorSetting color = new ColorSetting("Farbe", 0xFF55FFFF);
    public final NumberSetting lineWidth =
            new NumberSetting("Linienbreite", 1.5, 0.5, 5.0, 0.5);

    /** Auch die acht angrenzenden Chunks zeigen. */
    public final BooleanSetting neighbors = new BooleanSetting("Nachbar-Chunks", false);

    /** Wie weit die senkrechten Kanten ueber und unter dem Spieler reichen. */
    public final NumberSetting height = new NumberSetting("Hoehe", 32, 8, 128, 8);

    public ChunkBordersModule() {
        super("Chunk-Grenzen", Category.MISC);
        addSetting(color);
        addSetting(lineWidth);
        addSetting(neighbors);
        addSetting(height);
    }
}
