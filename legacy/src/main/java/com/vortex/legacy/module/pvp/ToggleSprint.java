package com.vortex.legacy.module.pvp;

import com.vortex.legacy.core.BoolSetting;
import com.vortex.legacy.core.Module;
import com.vortex.legacy.core.ModeSetting;

/**
 * Sprinten ohne die Taste zu halten. "Toggle": Sprint-Taste schaltet um;
 * "Always": immer, sobald du vorwaerts laeufst. Wirkt ueber die Sprint-Taste
 * selbst (KeyBindingPressMixin) -- Minecraft entscheidet weiter, ob Sprinten geht.
 */
public class ToggleSprint extends Module {
    public final ModeSetting mode = add(new ModeSetting("Mode", 1, "Toggle", "Always"));
    public final BoolSetting showStatus = add(new BoolSetting("Show Status", true));
    public boolean toggled = true;
    private boolean wasDown;

    public ToggleSprint() { super("Toggle Sprint", Category.PVP, "Sprint without holding the key."); }
    @Override public boolean defaultEnabled() { return true; }

    @Override
    public void onTick() {
        boolean down = org.lwjgl.input.Keyboard.isCreated() && mc.options.sprintKey.getCode() > 0
                && org.lwjgl.input.Keyboard.isKeyDown(mc.options.sprintKey.getCode());
        if (mode.is("Toggle") && down && !wasDown && mc.currentScreen == null) toggled = !toggled;
        wasDown = down;
    }

    /** Soll die Sprint-Taste als gedrueckt gelten? */
    public boolean active() {
        if (mc.player == null) return false;
        return mode.is("Always") || toggled;
    }

    public String status() {
        if (mc.player == null) return null;
        if (mc.player.isSprinting()) return mode.is("Always") ? "[Sprinting (Always)]" : "[Sprinting (Toggled)]";
        return null;
    }
}
