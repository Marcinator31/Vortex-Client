package com.vortex.legacy.module.visual;

import com.vortex.legacy.core.BoolSetting;
import com.vortex.legacy.core.KeySetting;
import com.vortex.legacy.core.Module;
import com.vortex.legacy.core.NumberSetting;
import org.lwjgl.input.Keyboard;

/** Zoom auf Taste (Standard C), weich, Mausrad aendert die Staerke. */
public class Zoom extends Module {
    public final KeySetting zoomKey = add(new KeySetting("Zoom Key", Keyboard.KEY_C));
    public final NumberSetting factor = add(new NumberSetting("Zoom", 4, 1.5, 30, 0.5));
    public final BoolSetting smooth = add(new BoolSetting("Smooth Animation", true));
    public final BoolSetting cinematic = add(new BoolSetting("Cinematic Camera", true));
    public final BoolSetting scroll = add(new BoolSetting("Scroll To Zoom", true));
    private float current = 1f;
    private long last;
    private boolean smoothBefore, held;
    public float scrollExtra = 1f;

    public Zoom() { super("Zoom", Category.VISUAL, "Zoom in while holding a key."); }
    @Override public boolean defaultEnabled() { return true; }

    public boolean held() {
        return isEnabled() && mc.currentScreen == null && zoomKey.get() > 0 && Keyboard.isCreated() && Keyboard.isKeyDown(zoomKey.get());
    }

    @Override
    public void onTick() {
        boolean h = held();
        if (h != held) {
            if (cinematic.get()) {
                if (h) { smoothBefore = mc.options.smoothCameraEnabled; mc.options.smoothCameraEnabled = true; }
                else mc.options.smoothCameraEnabled = smoothBefore;
            }
            if (!h) scrollExtra = 1f;
            held = h;
        }
    }

    /** Teiler fuer das Sichtfeld (1 = kein Zoom). */
    public float divisor() {
        long now = System.currentTimeMillis();
        float dt = last == 0 ? 0 : Math.min(0.1f, (now - last) / 1000f);
        last = now;
        float target = held() ? (float) factor.get() * scrollExtra : 1f;
        if (!smooth.get()) current = target;
        else current += (target - current) * (1f - (float) Math.exp(-dt * 14f));
        return Math.max(1f, current);
    }

    public void onScroll(int d) {
        if (!scroll.get() || !held()) return;
        scrollExtra = Math.max(0.3f, Math.min(4f, scrollExtra * (d > 0 ? 1.15f : 1 / 1.15f)));
    }

    @Override protected void onDisable() { if (held) mc.options.smoothCameraEnabled = smoothBefore; held = false; }
}
