package com.vortex.legacy.core;

public class NumberSetting extends Setting {
    private final double def, min, max, step;
    private double value;

    public NumberSetting(String name, double def, double min, double max, double step) {
        super(name);
        this.def = def; this.min = min; this.max = max; this.step = step;
        this.value = def;
    }

    public double get() { return value; }
    public int getInt() { return (int) Math.round(value); }
    public float getFloat() { return (float) value; }
    public double getMin() { return min; }
    public double getMax() { return max; }
    public double getStep() { return step; }
    public void set(double v) {
        double c = Math.max(min, Math.min(max, v));
        if (step > 0) c = Math.round((c - min) / step) * step + min;
        value = Math.max(min, Math.min(max, c));
    }
    /** Ohne Schritt-Raster (fuer HUD-Positionen beim Ziehen). */
    public void setRaw(double v) { value = v; }
    @Override public String serialize() { return Double.toString(value); }
    @Override public void deserialize(String s) { try { value = Double.parseDouble(s.trim()); } catch (NumberFormatException ignored) { } }
    @Override public void reset() { value = def; }
}
