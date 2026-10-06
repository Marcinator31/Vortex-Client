package com.vortex.legacy.module.pvp;

import com.vortex.legacy.core.Module;

/** Schleich-Taste schaltet Schleichen an/aus statt gehalten zu werden. */
public class ToggleSneak extends Module {
    public boolean toggled;
    private boolean wasDown;

    public ToggleSneak() { super("Toggle Sneak", Category.PVP, "Sneak key toggles sneaking."); }

    @Override
    public void onTick() {
        boolean down = org.lwjgl.input.Keyboard.isCreated() && mc.options.sneakKey.getCode() > 0
                && org.lwjgl.input.Keyboard.isKeyDown(mc.options.sneakKey.getCode());
        if (down && !wasDown && mc.currentScreen == null) toggled = !toggled;
        wasDown = down;
    }
    @Override protected void onDisable() { toggled = false; }
}
