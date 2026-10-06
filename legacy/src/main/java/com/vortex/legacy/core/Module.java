package com.vortex.legacy.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.MinecraftClient;

/** Ein Modul: an/aus, Taste, Einstellungen. */
public abstract class Module {
    public enum Category {
        HUD("HUD"), PVP("PvP"), VISUAL("Visual"), MISC("Misc"), PERFORMANCE("Performance");
        public final String label;
        Category(String l) { label = l; }
    }

    protected static final MinecraftClient mc = MinecraftClient.getInstance();

    private final String name;
    private final String description;
    private final Category category;
    private final List<Setting> settings = new ArrayList<Setting>();
    private boolean enabled;
    public final KeySetting key = new KeySetting("Key", 0);

    protected Module(String name, Category category, String description) {
        this.name = name;
        this.category = category;
        this.description = description;
    }

    protected <T extends Setting> T add(T s) { settings.add(s); return s; }

    public String getName() { return name; }
    public String getDescription() { return description; }
    public Category getCategory() { return category; }
    public List<Setting> getSettings() { return Collections.unmodifiableList(settings); }
    public boolean isEnabled() { return enabled; }

    public void setEnabled(boolean on) {
        if (on == enabled) return;
        enabled = on;
        try {
            if (on) onEnable(); else onDisable();
        } catch (Throwable t) {
            Errors.report(name + (on ? ".enable" : ".disable"), t);
        }
    }
    public void toggle() { setEnabled(!enabled); }

    protected void onEnable() {}
    protected void onDisable() {}
    /** Jeder Client-Tick, nur wenn an. */
    public void onTick() {}

    /** Standardmaessig an? (beim ersten Start) */
    public boolean defaultEnabled() { return false; }
}
