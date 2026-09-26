package com.vortex.client.module.modules;

import com.vortex.client.core.setting.BooleanSetting;
import com.vortex.client.core.setting.KeySetting;
import com.vortex.client.core.setting.ModeSetting;
import com.vortex.client.core.setting.NumberSetting;
import com.vortex.client.module.Module;

/**
 * Freelook: die Kamera drehen, ohne die Blickrichtung zu aendern.
 *
 * Solange die Taste gehalten wird (oder bis zum naechsten Druck, im Modus
 * "Toggle"), dreht die Maus nur die Kamera. Du laeufst weiter geradeaus und
 * kannst dich dabei umsehen. Standardmaessig in der Ansicht von hinten, damit
 * du deine Figur siehst.
 *
 * HINWEIS: Manche Server (z. B. Hypixel) verbieten Freelook ausdruecklich,
 * obwohl der Server davon nichts mitbekommt. Dort besser aus lassen.
 */
public class FreelookModule extends Module {

    public final KeySetting key = new KeySetting("Look Key", org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_ALT);
    public final ModeSetting mode = new ModeSetting("Mode", 0, "Hold", "Toggle");
    public final BooleanSetting thirdPerson = new BooleanSetting("Third Person", true);
    public final NumberSetting sensitivity = new NumberSetting("Sensitivity", 1.0, 0.2, 3.0, 0.1);

    public FreelookModule() {
        super("Freelook", Category.MISC);
        addSetting(key);
        addSetting(mode);
        addSetting(thirdPerson);
        addSetting(sensitivity);
    }

    @Override
    protected void onDisable() {
        com.vortex.client.hud.Freelook.beenden();
    }
}
