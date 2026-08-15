package com.vortex.client.hud;

import com.vortex.client.module.ModuleManager;
import com.vortex.client.module.modules.ZoomModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.InputUtil;

/**
 * The state behind the zoom.
 *
 * Two numbers matter here: the level you asked for, and the level actually on
 * screen. The second follows the first a little at a time, which is what makes
 * the movement smooth instead of a jump.
 *
 * Deliberately not a per-tick thing. The tick happens twenty times a second,
 * and a movement stepped that coarsely looks choppy however carefully it is
 * eased. This is advanced once per frame, so it is as smooth as the frame rate
 * allows.
 */
public final class Zoom {

    /** What the wheel has been set to. */
    private static double target = 1.0;

    /** What the view is currently showing. Chases the target. */
    private static double current = 1.0;

    /** Was the key held on the previous frame? */
    private static boolean wasHeld = false;

    private Zoom() {}

    private static ZoomModule module() {
        return ModuleManager.INSTANCE.get(ZoomModule.class);
    }

    /** Is the zoom key held right now? */
    public static boolean isActive() {
        ZoomModule mod = module();
        if (mod == null || !mod.isEnabled()) return false;
        if (!mod.key.isBound()) return false;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null) return false;
        // A menu swallows input; zooming through one makes no sense.
        if (client.currentScreen != null) return false;

        try {
            return InputUtil.isKeyPressed(client.getWindow(), mod.key.getKeyCode());
        } catch (Throwable pvpErr) {
            return false;
        }
    }

    /**
     * Advances the movement. Called once per frame.
     *
     * The step is proportional to the distance left, so it starts quickly and
     * settles gently -- the same shape a camera makes when a person moves it,
     * and far easier on the eye than a straight line at constant speed.
     */
    public static void update() {
        ZoomModule mod = module();
        if (mod == null) return;

        boolean held = isActive();

        // On the first frame of holding, take up the starting zoom -- unless
        // the last one is meant to be kept.
        if (held && !wasHeld && !mod.remember.get()) {
            target = mod.level.get();
        }
        if (!held) {
            target = 1.0;
        }
        wasHeld = held;

        double smooth = mod.smoothness.get();
        current += (target - current) * smooth;

        // Snap the last sliver, so it does not creep towards the value forever.
        if (Math.abs(target - current) < 0.001) current = target;
    }

    /** Factor to divide the field of view by. 1 means no zoom. */
    public static double factor() {
        return Math.max(1.0, current);
    }

    /** Is anything visibly zoomed? */
    public static boolean isZoomed() {
        return current > 1.001;
    }

    /**
     * Handles a turn of the wheel while zooming.
     *
     * @return true if the wheel was used for the zoom, so the game should not
     *         also change the hotbar slot with it
     */
    public static boolean onScroll(double amount) {
        ZoomModule mod = module();
        if (mod == null || !isActive()) return false;

        target += amount * mod.step.get();

        // Bounds taken from the setting itself, so what the slider allows is
        // exactly what the wheel can reach.
        double min = 1.0;
        double max = mod.level.getMax();
        if (target < min) target = min;
        if (target > max) target = max;

        return mod.lockHotbar.get();
    }

    /** Mouse sensitivity while zoomed, as a multiplier. */
    public static double sensitivity() {
        ZoomModule mod = module();
        if (mod == null || !mod.slowMouse.get() || !isZoomed()) return 1.0;
        // Proportional to the zoom: at four times in, the mouse moves a quarter
        // as far, so aiming feels the same at every level.
        return 1.0 / factor();
    }
}
