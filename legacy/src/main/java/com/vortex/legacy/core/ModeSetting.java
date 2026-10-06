package com.vortex.legacy.core;

import java.util.Arrays;
import java.util.List;

public class ModeSetting extends Setting {
    private final List<String> options;
    private final int def;
    private int index;

    public ModeSetting(String name, int def, String... options) {
        super(name);
        this.options = Arrays.asList(options);
        this.def = def;
        this.index = def;
    }

    public String get() { return options.get(index); }
    public int getIndex() { return index; }
    public boolean is(String o) { return get().equalsIgnoreCase(o); }
    public List<String> getOptions() { return options; }
    public void cycle(int d) { index = Math.floorMod(index + d, options.size()); }
    public void set(String o) { int i = options.indexOf(o); if (i >= 0) index = i; }
    @Override public String serialize() { return get(); }
    @Override public void deserialize(String s) { set(s.trim()); }
    @Override public void reset() { index = def; }
}
