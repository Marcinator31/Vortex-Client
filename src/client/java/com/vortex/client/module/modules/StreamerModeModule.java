package com.vortex.client.module.modules;

import com.vortex.client.core.setting.BooleanSetting;
import com.vortex.client.core.setting.ModeSetting;
import com.vortex.client.module.Module;

/**
 * Streamer-Modus: versteckt, was in einem Video nicht zu sehen sein soll.
 *
 *  - Dein Name wird ueberall ersetzt, wo Minecraft Text zeichnet: Chat,
 *    Tabliste, Scoreboard, Namensschilder, Titel.
 *  - Die Adresse des Servers ebenso (typisch unten im Scoreboard).
 *  - Die Koordinaten verschwinden aus den Vortex-Anzeigen (Coordinates,
 *    Debug Overlay). Den Vanilla-F3-Bildschirm einfach nicht oeffnen.
 *
 * Nur die Anzeige aendert sich. Was du tippst und was der Server bekommt,
 * bleibt unveraendert.
 */
public class StreamerModeModule extends Module {

    public final BooleanSetting hideName = new BooleanSetting("Hide Name", true);
    public final ModeSetting alias = new ModeSetting("Show Name As", 0, "You", "Player", "Streamer", "*****");
    public final BooleanSetting hideServer = new BooleanSetting("Hide Server Address", true);
    public final BooleanSetting hideCoords = new BooleanSetting("Hide Coordinates", true);

    public StreamerModeModule() {
        super("Streamer Mode", Category.MISC);
        addSetting(hideName);
        addSetting(alias);
        addSetting(hideServer);
        addSetting(hideCoords);
    }

    /** Koordinaten in Vortex-Anzeigen verstecken? */
    public static boolean koordinatenVerstecken() {
        StreamerModeModule m = com.vortex.client.module.ModuleManager.INSTANCE.get(StreamerModeModule.class);
        return m != null && m.isEnabled() && m.hideCoords.get();
    }
}
