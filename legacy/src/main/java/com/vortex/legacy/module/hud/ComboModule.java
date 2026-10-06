package com.vortex.legacy.module.hud;

import com.vortex.legacy.core.Combat;

public class ComboModule extends SimpleText {
    public ComboModule() { super("Combo Counter", "Hits in a row without being hit back.", 4, 177); }
    @Override protected String text(boolean editor) {
        int c = Combat.combo;
        if (c == 0 && !editor) return "No Combo";
        return (c == 0 ? 3 : c) + " Combo";
    }
}
