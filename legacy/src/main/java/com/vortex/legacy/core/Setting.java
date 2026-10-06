package com.vortex.legacy.core;

/** Eine Einstellung eines Moduls (Name + Wert, als Text speicherbar). */
public abstract class Setting {
    private final String name;
    private String description = "";
    /** Nur anzeigen, wenn diese Bedingung erfuellt ist (z. B. abhaengig von einem Modus). */
    private java.util.function.BooleanSupplier visible = () -> true;

    protected Setting(String name) { this.name = name; }

    public String getName() { return name; }
    public String getDescription() { return description; }
    @SuppressWarnings("unchecked")
    public <T extends Setting> T describe(String d) { this.description = d; return (T) this; }
    @SuppressWarnings("unchecked")
    public <T extends Setting> T visibleIf(java.util.function.BooleanSupplier b) { this.visible = b; return (T) this; }
    public boolean isVisible() { return visible.getAsBoolean(); }

    public abstract String serialize();
    public abstract void deserialize(String s);
    public abstract void reset();
}
