package com.vortex.client.module.modules;

import com.vortex.client.core.setting.BooleanSetting;
import com.vortex.client.core.setting.ColorSetting;
import com.vortex.client.core.setting.ModeSetting;
import com.vortex.client.core.setting.NumberSetting;
import com.vortex.client.module.Module;

/**
 * Replaces the vanilla crosshair.
 *
 * The default one is a fixed size and inverts whatever is behind it, which
 * makes it disappear against some backgrounds at exactly the wrong moment.
 * This one has a colour of its own and stays where you put it.
 */
public class CrosshairModule extends Module {

    /** Shape of the crosshair. */
    public final ModeSetting shape = new ModeSetting("Shape", 0, "Cross", "Dot", "Circle", "T-shape");

    /** Length of an arm, or the radius for a dot or circle. */
    public final NumberSetting size = new NumberSetting("Size", 5, 1, 20, 1);

    /** How thick the lines are. */
    public final NumberSetting thickness = new NumberSetting("Thickness", 1, 1, 5, 1);

    /**
     * Space left open in the middle.
     *
     * A gap keeps the centre clear, so what you are aiming at stays visible
     * instead of sitting behind the crosshair.
     */
    public final NumberSetting gap = new NumberSetting("Centre Gap", 2, 0, 12, 1);

    public final ColorSetting color = new ColorSetting("Colour", 0xFFFFFFFF);

    /** Dark outline, so it stays readable on a light background. */
    public final BooleanSetting outline = new BooleanSetting("Outline", true);

    /**
     * Keep the attack charge bar underneath.
     *
     * Vanilla draws it inside the crosshair routine, so replacing the crosshair
     * took the bar with it -- and in a fight that bar is the more useful of the
     * two. It is drawn here again, in the same place and size.
     */
    public final BooleanSetting attackIndicator = new BooleanSetting("Attack Indicator", true);

    /** Also show it in third person. */
    public final BooleanSetting thirdPerson = new BooleanSetting("Show in Third Person", false);

    public CrosshairModule() {
        super("Crosshair", Category.MISC);
        addSetting(shape);
        addSetting(size);
        addSetting(thickness);
        addSetting(gap);
        addSetting(color);
        addSetting(outline);
        addSetting(attackIndicator);
        addSetting(thirdPerson);
    }
}
