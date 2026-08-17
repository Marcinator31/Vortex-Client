package com.vortex.client.module.modules;

import com.vortex.client.core.setting.BooleanSetting;
import com.vortex.client.core.setting.ColorSetting;
import com.vortex.client.core.setting.NumberSetting;
import com.vortex.client.hud.HudElement;
import com.vortex.client.module.Module;

/**
 * Warns before a piece of armour breaks.
 *
 * The durability bar tells you the same thing, but only if you happen to look
 * at it -- and in a fight nobody does. This puts the warning where you cannot
 * miss it, at the moment it starts to matter.
 */
public class ArmorWarningModule extends Module implements HudElement {

    /** Warn below this percentage of durability. */
    public final NumberSetting threshold = new NumberSetting("Warn Below (%)", 20, 1, 90, 1);

    /** A second, more urgent step. */
    public final NumberSetting critical = new NumberSetting("Critical Below (%)", 8, 1, 50, 1);

    /** Also warn about the item in your hand. */
    public final BooleanSetting includeHand = new BooleanSetting("Include Held Item", true);

    /**
     * A short sound when a piece first drops below the threshold.
     *
     * Once per piece, not on a loop: a warning that keeps going is one you stop
     * hearing, and it would drown out the sounds that matter in a fight.
     */
    public final BooleanSetting sound = new BooleanSetting("Sound", true);

    /** Make the warning flash at the critical step. */
    public final BooleanSetting flash = new BooleanSetting("Flash When Critical", true);

    public final ColorSetting warnColor = new ColorSetting("Warning Colour", 0xFFFFAA00);
    public final ColorSetting critColor = new ColorSetting("Critical Colour", 0xFFFF5555);

    /** Position on screen. Defaults to the middle, above the hotbar. */
    public final NumberSetting x = new NumberSetting("X", 300, 0, 1920, 1);
    public final NumberSetting y = new NumberSetting("Y", 180, 0, 1080, 1);
    public final NumberSetting scale = new NumberSetting("Scale", 1.0, 0.5, 3.0, 0.1);

    public ArmorWarningModule() {
        super("Armor Warning", Category.HUD);
        addSetting(threshold);
        addSetting(critical);
        addSetting(includeHand);
        addSetting(sound);
        addSetting(flash);
        addSetting(warnColor);
        addSetting(critColor);
        addSetting(x);
        addSetting(y);
        addSetting(scale);
    }

    @Override public String hudName() { return "Armor Warning"; }
    @Override public NumberSetting hudX() { return x; }
    @Override public NumberSetting hudY() { return y; }
    @Override public NumberSetting hudScale() { return scale; }
    @Override public com.vortex.client.core.setting.ColorSetting hudColor() { return warnColor; }
    @Override public int hudWidth() { return 150; }
    @Override public int hudHeight() { return 24; }
}
