package com.vortex.legacy.module.hud;

import com.vortex.legacy.core.BoolSetting;
import com.vortex.legacy.core.Combat;

public class CpsModule extends SimpleText {
    private final BoolSetting right = add(new BoolSetting("Show Right Click", true));
    public CpsModule() { super("CPS", "Clicks per second (left | right).", 4, 21); }
    @Override public boolean defaultEnabled() { return true; }
    @Override protected String text(boolean editor) {
        int l = Combat.cps(true), r = Combat.cps(false);
        return right.get() ? l + " | " + r + " CPS" : l + " CPS";
    }
}
