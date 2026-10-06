package com.vortex.legacy.core;

/** Farbe als ARGB. */
public class ColorSetting extends Setting {
    private final int def;
    private int value;

    public ColorSetting(String name, int def) { super(name); this.def = def; this.value = def; }

    public int get() { return value; }
    public void set(int argb) { value = argb; }
    @Override public String serialize() { return String.format("%08X", value); }
    @Override public void deserialize(String s) { try { value = (int) Long.parseLong(s.trim(), 16); } catch (NumberFormatException ignored) { } }
    @Override public void reset() { value = def; }
}
