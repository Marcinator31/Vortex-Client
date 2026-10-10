package com.vortex.client.core.setting;

/**
 * Ein Texteingabefeld fuer benutzerdefinierte Werte (z.B. Filter-Keywords).
 */
public class StringSetting extends Setting {

    private String value;

    public StringSetting(String name, String defaultValue) {
        super(name);
        this.value = defaultValue;
    }

    public String get() {
        return value;
    }

    public void set(String value) {
        this.value = value;
    }

    @Override
    public String serialize() {
        return value;
    }

    @Override
    public void deserialize(String value) {
        this.value = value;
    }
}
