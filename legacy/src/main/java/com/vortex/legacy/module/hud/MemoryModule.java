package com.vortex.legacy.module.hud;

public class MemoryModule extends SimpleText {
    public MemoryModule() { super("Memory", "Java memory in use.", 4, 211); }
    @Override protected String text(boolean editor) {
        Runtime r = Runtime.getRuntime();
        long used = (r.totalMemory() - r.freeMemory()) >> 20, max = r.maxMemory() >> 20;
        return "Mem: " + (used * 100 / Math.max(1, max)) + "% " + used + "/" + max + "MB";
    }
}
