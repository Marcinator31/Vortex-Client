package com.vortex.client.module.modules;

import com.vortex.client.core.setting.BooleanSetting;
import com.vortex.client.core.setting.NumberSetting;
import com.vortex.client.module.Module;

/**
 * Control over the name tags above players.
 *
 * The vanilla tag is hidden and drawn again by us. That sounds roundabout, and
 * it is the only way to get at all three of size, transparency and visibility
 * through walls: the original is drawn deep inside the render pipeline, where
 * those values are not handed out.
 *
 * Only players are affected. Mob names and armour stands keep their usual tags,
 * because shrinking a mob name has no use and the risk of breaking something
 * that works is not worth it.
 */
public class NametagModule extends Module {

    /** Size of the name, as a multiple of the usual. */
    public final NumberSetting scale = new NumberSetting("Size", 1.0, 0.5, 3.0, 0.1);

    /** How solid the name is. */
    public final NumberSetting opacity = new NumberSetting("Opacity", 1.0, 0.2, 1.0, 0.05);

    /** Show names through walls. */
    public final BooleanSetting throughWalls = new BooleanSetting("Through Walls", false);

    /** Keep the size the same at any distance instead of shrinking away. */
    public final BooleanSetting constantSize = new BooleanSetting("Same Size at Any Distance", false);

    /** Show how far away the player is, after the name. */
    public final BooleanSetting distance = new BooleanSetting("Show Distance", false);

    /** How far away a name is still drawn. */
    public final NumberSetting range = new NumberSetting("Range", 64, 8, 128, 4);

    public NametagModule() {
        super("Nametags", Category.MISC);
        addSetting(scale);
        addSetting(opacity);
        addSetting(throughWalls);
        addSetting(constantSize);
        addSetting(distance);
        addSetting(range);
    }
}
