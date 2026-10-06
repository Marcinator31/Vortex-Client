package com.vortex.legacy.module.visual;

import com.vortex.legacy.core.BoolSetting;
import com.vortex.legacy.core.Module;

/**
 * 1.7-Animationen: Schwert-Blockhit mit Schlag-Animation, Schlagen beim
 * Essen/Trinken/Bogenspannen sichtbar. Nur die Darstellung bei dir --
 * am Server aendert sich nichts.
 */
public class OldAnimations extends Module {
    public final BoolSetting blockHit = add(new BoolSetting("Block Hit", true));
    public final BoolSetting eat = add(new BoolSetting("Eat / Drink", true));
    public final BoolSetting bow = add(new BoolSetting("Bow", true));
    public final BoolSetting swingWhileUsing = add(new BoolSetting("Swing While Using", true));

    public OldAnimations() { super("1.7 Animations", Category.VISUAL, "Old blockhit and swing animations."); }
    @Override public boolean defaultEnabled() { return true; }

    /** Linksklick, waehrend ein Item benutzt wird: Arm sichtbar schwingen (ohne Paket). */
    public void onAttackClick() {
        if (!swingWhileUsing.get() || mc.player == null || !mc.player.isUsingItem()) return;
        if (!mc.player.handSwinging || mc.player.handSwingTicks >= 3 || mc.player.handSwingTicks < 0) {
            mc.player.handSwingTicks = -1;
            mc.player.handSwinging = true;
        }
    }
}
