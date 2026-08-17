package com.vortex.client.module.modules;

import com.vortex.client.core.setting.BooleanSetting;
import com.vortex.client.core.setting.ColorSetting;
import com.vortex.client.core.setting.NumberSetting;
import com.vortex.client.hud.HudElement;
import com.vortex.client.module.Module;

/**
 * A readable replacement for the F3 screen.
 *
 * The vanilla one is a wall of text, most of it useful to whoever is debugging
 * the game rather than to whoever is playing it. This shows the handful of
 * things that actually come up -- where you are, what you are looking at, how
 * the game is running -- laid out so you can find them at a glance.
 *
 * Written from scratch rather than adapted from BetterF3. That mod is MIT
 * licensed and could have been copied, but only its compiled classes are here;
 * working from those would mean decompiling into something nobody could
 * maintain afterwards.
 */
public class DebugOverlayModule extends Module implements HudElement {

    // ---- which sections to show ----

    /** Frame rate, and how steady it is. */
    public final BooleanSetting showFps = new BooleanSetting("Performance", true);

    /** Position, direction and the chunk you are in. */
    public final BooleanSetting showPosition = new BooleanSetting("Position", true);

    /** Biome, light level and the time of day. */
    public final BooleanSetting showWorld = new BooleanSetting("World", true);

    /** The block or entity in front of you. */
    public final BooleanSetting showTarget = new BooleanSetting("Looking At", true);

    /** Memory use and how many entities are loaded. */
    public final BooleanSetting showSystem = new BooleanSetting("System", false);

    /** Server address, ping and player count. */
    public final BooleanSetting showServer = new BooleanSetting("Server", false);

    // ---- how it looks ----

    /**
     * Draw a dark panel behind the text.
     *
     * Without it the lighter lines vanish over snow and sand -- the same reason
     * the crosshair has an outline.
     */
    public final BooleanSetting background = new BooleanSetting("Background", true);

    /** Colour of the labels on the left. */
    public final ColorSetting labelColor = new ColorSetting("Label Colour", 0xFF6AA9FF);

    /** Colour of the values. */
    public final ColorSetting valueColor = new ColorSetting("Value Colour", 0xFFE6E6EC);

    /**
     * NO "replace F3" setting, on purpose.
     *
     * Taking over F3 means suppressing the vanilla screen, and that screen is
     * the one thing people fall back on when something is wrong -- including
     * when this client is what is wrong. Leaving it alone costs nothing: bind
     * this module to a key of your own and you have both.
     */

    public final NumberSetting x = new NumberSetting("X", 4, 0, 1920, 1);
    public final NumberSetting y = new NumberSetting("Y", 40, 0, 1080, 1);
    public final NumberSetting scale = new NumberSetting("Scale", 1.0, 0.5, 3.0, 0.1);

    public DebugOverlayModule() {
        super("Debug Overlay", Category.HUD);
        addSetting(showFps);
        addSetting(showPosition);
        addSetting(showWorld);
        addSetting(showTarget);
        addSetting(showSystem);
        addSetting(showServer);
        addSetting(background);
        addSetting(labelColor);
        addSetting(valueColor);
        addSetting(x);
        addSetting(y);
        addSetting(scale);
    }

    @Override public String hudName() { return "Debug Overlay"; }
    @Override public NumberSetting hudX() { return x; }
    @Override public NumberSetting hudY() { return y; }
    @Override public NumberSetting hudScale() { return scale; }
    @Override public ColorSetting hudColor() { return valueColor; }
    @Override public int hudWidth() { return 170; }
    @Override public int hudHeight() { return 80; }
}
