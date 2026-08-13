package com.vortex.client.module.modules;

import com.vortex.client.core.setting.BooleanSetting;
import com.vortex.client.core.setting.KeySetting;
import com.vortex.client.core.setting.NumberSetting;
import com.vortex.client.module.Module;
import org.lwjgl.glfw.GLFW;

/**
 * Freecam: loest die Kamera vom Spieler. Mit der eingestellten Taste schaltet
 * man die freie Kamera an/aus und fliegt dann mit WASD + Leertaste/Shift herum.
 * Der Spieler bleibt dabei stehen.
 *
 * Die eigentliche Logik steckt in der Freecam-Klasse + CameraMixin. Dieses
 * Modul haelt nur die Tasten-Einstellung (in der GUI aenderbar).
 */
public class FreecamModule extends Module {

    public final KeySetting key = new KeySetting("Key", GLFW.GLFW_KEY_F4);

    // Fluggeschwindigkeit (Bloecke pro Sekunde) und Multiplikator, solange die
    // Sprint-Taste gehalten wird.
    public final NumberSetting speed =
            new NumberSetting("Speed", 10.0, 1.0, 50.0, 1.0);
    public final NumberSetting sprintMult =
            new NumberSetting("Sprint Multiplier", 3.0, 1.0, 10.0, 0.5);

    /**
     * Render-Anker: spawnt eine unsichtbare Kamera-Entity und macht sie zur
     * aktiven Kamera. Das verbessert das Chunk-Rendering unter der Erde, hat aber
     * einen Haken: der echte Spieler gilt dann nicht mehr als "Kamera", woran
     * Minecraft u.a. das Senden der Bewegungspakete koppelt -- dadurch kann der
     * Spieler nach dem Beenden haengen bleiben.
     *
     * Standard AUS: sicheres Verhalten. Fuer bessere Sicht unter der Erde sorgt
     * ohnehin die automatische Helligkeit in der Freecam.
     */
    public final BooleanSetting renderAnchor =
            new BooleanSetting("Render Anchor", false);

    public FreecamModule() {
        super("Freecam", Category.MISC);
        addSetting(key);
        addSetting(speed);
        addSetting(sprintMult);
        addSetting(renderAnchor);
    }
}
