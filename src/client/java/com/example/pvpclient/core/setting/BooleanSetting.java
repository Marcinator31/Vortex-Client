package com.example.pvpclient.core.setting;

/** Ein einfacher An/Aus-Schalter (z.B. "Schatten anzeigen"). */
public class BooleanSetting extends Setting {

    private boolean value;

    public BooleanSetting(String name, boolean defaultValue) {
        super(name);
        this.value = defaultValue;
    }

    public boolean get() {
        return value;
    }

    public void set(boolean value) {
        this.value = value;
    }

    public void toggle() {
        this.value = !this.value;
    }

    @Override
    public String serialize() {
        return Boolean.toString(value);
    }

    @Override
    public void deserialize(String value) {
        this.value = Boolean.parseBoolean(value);
    }
}
