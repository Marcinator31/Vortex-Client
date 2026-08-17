package com.vortex.client.module.modules;

import com.vortex.client.core.setting.BooleanSetting;
import com.vortex.client.core.setting.KeySetting;
import com.vortex.client.core.setting.NumberSetting;
import com.vortex.client.module.Module;
import org.lwjgl.glfw.GLFW;

/**
 * Hold a key, zoom with the wheel.
 *
 * Works the way a scope does: hold the key and the view pulls in, turn the
 * wheel to choose how far, let go and it eases back. The hotbar stays put
 * while zooming, because reaching for the wheel to aim and ending up with a
 * different item in hand is the last thing anyone wants mid-fight.
 *
 * The movement is smoothed rather than snapped. A hard jump between fields of
 * view is disorienting and makes it hard to keep track of what you were
 * looking at; easing into it keeps the picture readable the whole way.
 */
public class ZoomModule extends Module {

    /** Key that activates the zoom while held. */
    public final KeySetting key = new KeySetting("Zoom Key", GLFW.GLFW_KEY_LEFT_ALT);

    /**
     * How far the view pulls in at the start, as a divisor of the field of
     * view. 4 means a quarter of the normal angle.
     */
    public final NumberSetting level = new NumberSetting("Zoom", 4.0, 1.5, 20.0, 0.5);

    /** How much one notch of the wheel changes the zoom. */
    public final NumberSetting step = new NumberSetting("Wheel Step", 1.0, 0.1, 5.0, 0.1);

    /**
     * How quickly the view follows, per second.
     *
     * Lower is smoother and slower; higher gets closer to a snap. Now measured
     * against time rather than frames, so the movement feels the same at 60 as
     * at 240 frames a second -- before, a higher frame rate made it faster.
     *
     * The default is gentler than it used to be. Around 5 the movement is
     * clearly a movement; above 15 it starts to read as a jump.
     */
    public final NumberSetting smoothness = new NumberSetting("Zoom Speed", 5.0, 1.0, 25.0, 0.5);

    /** Slow the mouse down while zoomed, in proportion to the zoom. */
    public final BooleanSetting slowMouse = new BooleanSetting("Slow Mouse While Zoomed", true);

    /** Keep the wheel from switching hotbar slots while zooming. */
    public final BooleanSetting lockHotbar = new BooleanSetting("Lock Hotbar While Zoomed", true);

    /** Remember the chosen zoom for next time instead of resetting. */
    public final BooleanSetting remember = new BooleanSetting("Remember Zoom", false);

    public ZoomModule() {
        super("Zoom", Category.MISC);
        addSetting(key);
        addSetting(level);
        addSetting(step);
        addSetting(smoothness);
        addSetting(slowMouse);
        addSetting(lockHotbar);
        addSetting(remember);
    }
}
