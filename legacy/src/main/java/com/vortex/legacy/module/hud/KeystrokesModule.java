package com.vortex.legacy.module.hud;

import com.vortex.legacy.core.BoolSetting;
import com.vortex.legacy.core.Combat;
import com.vortex.legacy.gui.Render2D;
import com.vortex.legacy.hud.HudModule;
import net.minecraft.client.option.KeyBinding;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/** WASD, Maustasten (mit CPS) und Leertaste; gedrueckte Tasten leuchten weich auf. */
public class KeystrokesModule extends HudModule {
    private final BoolSetting mouse = add(new BoolSetting("Mouse Buttons", true));
    private final BoolSetting space = add(new BoolSetting("Space Bar", true));
    private final BoolSetting cps = add(new BoolSetting("CPS On Buttons", true));
    private final float[] glow = new float[7];
    private long last;

    public KeystrokesModule() { super("Keystrokes", "Shows WASD, mouse buttons and space.", 4, 200); }
    @Override public boolean defaultEnabled() { return true; }

    private static final float K = 22, G = 2;
    @Override public float width() { return K * 3 + G * 2; }
    @Override public float height() { return K * 2 + G + (mouse.get() ? K + G : 0) + (space.get() ? 12 + G : 0); }

    private static boolean down(KeyBinding kb) {
        int c = kb.getCode();
        if (c < 0) return Mouse.isCreated() && Mouse.isButtonDown(c + 100);
        return c > 0 && Keyboard.isCreated() && Keyboard.isKeyDown(c);
    }

    @Override
    public void render(boolean editor) {
        long now = System.currentTimeMillis();
        float dt = last == 0 ? 0 : Math.min(0.1f, (now - last) / 1000f);
        last = now;
        KeyBinding[] keys = { mc.options.forwardKey, mc.options.leftKey, mc.options.backKey, mc.options.rightKey, mc.options.attackKey, mc.options.useKey, mc.options.jumpKey };
        for (int i = 0; i < 7; i++) {
            boolean d = down(keys[i]);
            glow[i] += ((d ? 1f : 0f) - glow[i]) * (1f - (float) Math.exp(-dt * (d ? 30f : 10f)));
        }
        key(K + G, 0, K, K, label(keys[0]), 0);
        key(0, K + G, K, K, label(keys[1]), 1);
        key(K + G, K + G, K, K, label(keys[2]), 2);
        key((K + G) * 2, K + G, K, K, label(keys[3]), 3);
        float y = (K + G) * 2;
        if (mouse.get()) {
            float mw = (K * 3 + G) / 2f;
            key(0, y, mw, K, cps.get() ? Combat.cps(true) + " CPS" : "LMB", 4);
            key(mw + G, y, mw, K, cps.get() ? Combat.cps(false) + " CPS" : "RMB", 5);
            y += K + G;
        }
        if (space.get()) key(0, y, K * 3 + G * 2, 12, "———", 6);
    }

    private static String label(KeyBinding kb) {
        String n = kb.getCode() > 0 ? Keyboard.getKeyName(kb.getCode()) : "?";
        return n == null ? "?" : (n.length() > 3 ? n.substring(0, 3) : n);
    }

    private void key(float x, float y, float w, float h, String s, int i) {
        float g = glow[i];
        int bg = Render2D.mix(0x90000000, Render2D.alpha(accent(), 0.85f), g);
        if (background.get() || g > 0.01f) Render2D.round(x, y, w, h, 3, bg);
        int tc = Render2D.mix(textColor.get(), 0xFFFFFFFF, g);
        Render2D.textCentered(s, x + w / 2f, y + (h - Render2D.height()) / 2f + 1, tc, textShadow.get());
    }
}
