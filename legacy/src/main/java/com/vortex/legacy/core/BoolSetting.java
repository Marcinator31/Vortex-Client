package com.vortex.legacy.core;

public class BoolSetting extends Setting {
    private final boolean def;
    private boolean value;

    public BoolSetting(String name, boolean def) { super(name); this.def = def; this.value = def; }

    public boolean get() { return value; }
    public void set(boolean v) { value = v; }
    public void toggle() { value = !value; }
    @Override public String serialize() { return Boolean.toString(value); }
    @Override public void deserialize(String s) { value = Boolean.parseBoolean(s.trim()); }
    @Override public void reset() { value = def; }
}
