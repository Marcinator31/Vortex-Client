package com.vortex.client.module.modules;

import com.vortex.client.core.setting.BooleanSetting;
import com.vortex.client.core.setting.ColorSetting;
import com.vortex.client.core.setting.KeySetting;
import com.vortex.client.core.setting.ModeSetting;
import com.vortex.client.core.setting.NumberSetting;
import com.vortex.client.module.Module;

/**
 * Spotify (Kategorie Music): eigener Song fuer andere sichtbar, Songs anderer
 * Vortex-Spieler ueber ihrem Kopf, Mithoeren. Anmelden, Wiedergabe und
 * Uebersicht im Bildschirm "Music" (Startmenue-Kachel).
 * Die Logik steckt in {@link com.vortex.client.musik.MusikDienst}.
 */
public class SpotifyModule extends Module {

    // --- Teilen ---------------------------------------------------------
    public final BooleanSetting share = new BooleanSetting("Share My Song", true);
    public final ModeSetting shareWith = new ModeSetting("Share With", 0, "Everyone", "Friends");
    /** Ohne Anmeldung: Titel aus der Spotify-App lesen (Windows). */
    public final BooleanSetting desktopApp = new BooleanSetting("Read Spotify App", true);

    // --- Ueber dem Kopf ---------------------------------------------------
    public final BooleanSetting aboveHeads = new BooleanSetting("Songs Above Heads", true);
    public final BooleanSetting ownAboveHead = new BooleanSetting("My Song Above My Head", false);
    public final NumberSetting headDistance = new NumberSetting("Head Distance", 32, 4, 64, 1);
    public final ColorSetting headColor = new ColorSetting("Head Color", 0xFF1ED760);

    // --- Mithoeren --------------------------------------------------------
    /** Off: nie. Manual: per Knopf/Taste. Nearest Player: automatisch den naechsten Spieler mit Song. */
    public final ModeSetting listenAlong = new ModeSetting("Listen Along", 1, "Off", "Manual", "Nearest Player");
    public final NumberSetting listenRange = new NumberSetting("Listen Range", 12, 3, 48, 1);
    public final BooleanSetting resumeMine = new BooleanSetting("Resume My Music", true);
    /** Mithoeren beim Spieler, den man anschaut (bzw. wieder aufhoeren). */
    public final KeySetting listenKey = new KeySetting("Listen Along Key", org.lwjgl.glfw.GLFW.GLFW_KEY_UNKNOWN);

    public SpotifyModule() {
        super("Spotify", Category.MUSIC);
        enabledByDefault();
        addSetting(share);
        addSetting(shareWith);
        addSetting(desktopApp);
        addSetting(aboveHeads);
        addSetting(ownAboveHead);
        addSetting(headDistance);
        addSetting(headColor);
        addSetting(listenAlong);
        addSetting(listenRange);
        addSetting(resumeMine);
        addSetting(listenKey);
    }
}
