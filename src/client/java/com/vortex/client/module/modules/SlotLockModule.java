package com.vortex.client.module.modules;

import com.vortex.client.core.setting.BooleanSetting;
import com.vortex.client.core.setting.KeySetting;
import com.vortex.client.module.Module;

/**
 * Slot-Sperre: gesperrte Hotbar-Plaetze lassen sich nicht wegwerfen.
 *
 * Gesperrt wird mit der Sperrtaste (sperrt oder entsperrt den gerade
 * gewaehlten Platz) oder direkt hier mit den Schaltern. Ein kleines Schloss
 * ueber der Hotbar zeigt, welche Plaetze gesperrt sind.
 *
 * Verhindert wird das Wegwerfen mit Q -- in der Hotbar und im Inventar.
 * Verschieben, Tauschen und Benutzen bleiben erlaubt; auch Vortex-Module wie
 * Auto Totem arbeiten ungestoert weiter.
 */
public class SlotLockModule extends Module {

    public final KeySetting lockKey = new KeySetting("Lock Key", org.lwjgl.glfw.GLFW.GLFW_KEY_UNKNOWN);
    public final BooleanSetting showIcons = new BooleanSetting("Show Lock Icons", true);
    public final BooleanSetting[] slots = new BooleanSetting[9];

    public SlotLockModule() {
        super("Slot Lock", Category.MISC);
        addSetting(lockKey);
        addSetting(showIcons);
        for (int i = 0; i < 9; i++) {
            slots[i] = new BooleanSetting("Slot " + (i + 1), false);
            addSetting(slots[i]);
        }
    }

    public boolean gesperrt(int hotbarPlatz) {
        return isEnabled() && hotbarPlatz >= 0 && hotbarPlatz < 9 && slots[hotbarPlatz].get();
    }
}
