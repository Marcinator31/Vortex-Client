package com.example.pvpclient.core.setting;

/**
 * Eine Farbe (ARGB als int). Das ist die Grundlage fuers
 * "alles farblich anpassen". Im GUI haengt ein Farbwaehler dran.
 *
 * Format: 0xAARRGGBB
 *   AA = Deckkraft (alpha), RR = rot, GG = gruen, BB = blau.
 */
public class ColorSetting extends Setting {

    private int argb;

    public ColorSetting(String name, int defaultArgb) {
        super(name);
        this.argb = defaultArgb;
    }

    public int get() {
        return argb;
    }

    public void set(int argb) {
        this.argb = argb;
    }

    // Bequeme Einzelzugriffe fuer einen Farbwaehler.
    public int alpha() { return (argb >> 24) & 0xFF; }
    public int red()   { return (argb >> 16) & 0xFF; }
    public int green() { return (argb >> 8)  & 0xFF; }
    public int blue()  { return argb & 0xFF; }

    public void setComponents(int a, int r, int g, int b) {
        this.argb = ((a & 0xFF) << 24) | ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF);
    }

    @Override
    public String serialize() {
        return Integer.toHexString(argb);
    }

    @Override
    public void deserialize(String value) {
        try {
            this.argb = (int) Long.parseLong(value, 16);
        } catch (NumberFormatException ignored) {
        }
    }
}
