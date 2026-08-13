package com.example.pvpclient.core.setting;

/**
 * Ein Zahlenwert mit Grenzen -- wird im GUI als Schieberegler dargestellt.
 * Z.B. "Skalierung 0.5 - 2.0" oder "X-Position 0 - 1920".
 */
public class NumberSetting extends Setting {

    private double value;
    private final double min;
    private final double max;
    private final double step;

    public NumberSetting(String name, double defaultValue, double min, double max, double step) {
        super(name);
        this.value = defaultValue;
        this.min = min;
        this.max = max;
        this.step = step;
    }

    public double get() {
        return value;
    }

    public float getFloat() {
        return (float) value;
    }

    public int getInt() {
        return (int) Math.round(value);
    }

    public void set(double value) {
        // Auf erlaubten Bereich begrenzen.
        this.value = Math.max(min, Math.min(max, value));
    }

    public double getMin() { return min; }
    public double getMax() { return max; }
    public double getStep() { return step; }

    @Override
    public String serialize() {
        return Double.toString(value);
    }

    @Override
    public void deserialize(String value) {
        try {
            set(Double.parseDouble(value));
        } catch (NumberFormatException ignored) {
            // Bei kaputtem Wert einfach Default behalten.
        }
    }
}
