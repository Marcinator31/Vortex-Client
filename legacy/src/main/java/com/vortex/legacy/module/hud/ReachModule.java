package com.vortex.legacy.module.hud;

import com.vortex.legacy.core.Combat;

public class ReachModule extends SimpleText {
    public ReachModule() { super("Reach Display", "Distance of your last hit.", 4, 194); }
    @Override protected String text(boolean editor) {
        if (!Combat.reachFresh(3000)) return editor ? "2.87 blocks" : "Hasn't attacked";
        return String.format("%.2f blocks", Combat.lastReach);
    }
}
