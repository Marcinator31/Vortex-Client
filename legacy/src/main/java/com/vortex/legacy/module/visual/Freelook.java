package com.vortex.legacy.module.visual;

import com.vortex.legacy.core.BoolSetting;
import com.vortex.legacy.core.KeySetting;
import com.vortex.legacy.core.Module;
import org.lwjgl.input.Keyboard;

/** Taste halten: Kamera frei um dich drehen, du laeufst weiter geradeaus. */
public class Freelook extends Module {
    public final KeySetting lookKey = add(new KeySetting("Freelook Key", Keyboard.KEY_LMENU));
    public final BoolSetting invertPitch = add(new BoolSetting("Invert Pitch", false));
    public boolean active;
    public float camYaw, camPitch;
    private int perspectiveBefore;

    public Freelook() { super("Freelook", Category.VISUAL, "Look around without turning your player."); }

    @Override
    public void onTick() {
        boolean h = mc.currentScreen == null && lookKey.get() > 0 && Keyboard.isCreated() && Keyboard.isKeyDown(lookKey.get()) && mc.player != null;
        if (h && !active) {
            camYaw = mc.player.yaw;
            camPitch = mc.player.pitch;
            perspectiveBefore = mc.options.perspective;
            mc.options.perspective = 1;
            active = true;
        } else if (!h && active) {
            mc.options.perspective = perspectiveBefore;
            active = false;
        }
    }

    /** Mausbewegung waehrend Freelook (statt den Spieler zu drehen). */
    public void turn(float dx, float dy) {
        camYaw += dx * 0.15f;
        camPitch += (invertPitch.get() ? 1 : -1) * dy * 0.15f;  // wie Entity.increaseTransforms: pitch -= dy*0.15
        camPitch = Math.max(-90, Math.min(90, camPitch));
    }

    @Override protected void onDisable() { if (active) { mc.options.perspective = perspectiveBefore; active = false; } }
}
