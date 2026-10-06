package com.vortex.legacy.module.hud;

import com.vortex.legacy.core.Module;

/** Tab-Liste: Ping als Zahl (farbig) statt Balken. */
public class PlayerListModule extends Module {
    public PlayerListModule() { super("Numeric Ping", Category.HUD, "Shows ping as a number in the tab list."); }
    @Override public boolean defaultEnabled() { return true; }
}
