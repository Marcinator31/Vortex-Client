package com.vortex.legacy.core;

import org.lwjgl.input.Keyboard;

/** Taste (LWJGL-2-Code, 0 = keine). */
public class KeySetting extends Setting {
    private final int def;
    private int key;

    public KeySetting(String name, int def) { super(name); this.def = def; this.key = def; }

    public int get() { return key; }
    public void set(int k) { key = k; }
    public String label() {
        if (key <= 0) return "None";
        String n = Keyboard.getKeyName(key);
        return n == null ? "#" + key : n;
    }
    @Override public String serialize() { return Integer.toString(key); }
    @Override public void deserialize(String s) { try { key = Integer.parseInt(s.trim()); } catch (NumberFormatException ignored) { } }
    @Override public void reset() { key = def; }
}
