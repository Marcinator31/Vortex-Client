package com.example.pvpclient.core.setting;

import net.minecraft.client.util.InputUtil;

/**
 * Eine Tasten-Einstellung: speichert einen GLFW-Keycode. Im GUI klickt man
 * darauf, drueckt dann die gewuenschte Taste, und sie wird uebernommen.
 *
 * "listening" zeigt an, dass das GUI gerade auf einen Tastendruck wartet.
 */
public class KeySetting extends Setting {

    private int keyCode;
    private boolean listening = false;

    public KeySetting(String name, int defaultKey) {
        super(name);
        this.keyCode = defaultKey;
    }

    public int getKeyCode() {
        return keyCode;
    }

    public void setKeyCode(int keyCode) {
        this.keyCode = keyCode;
    }

    public boolean isListening() {
        return listening;
    }

    public void setListening(boolean listening) {
        this.listening = listening;
    }

    /** Lesbarer Name der Taste fuers GUI (z.B. "F", "LEFT SHIFT"). */
    public String getKeyName() {
        try {
            return InputUtil.Type.KEYSYM.createFromCode(keyCode)
                    .getLocalizedText().getString().toUpperCase();
        } catch (Throwable t) {
            return "?";
        }
    }

    @Override
    public String serialize() {
        return Integer.toString(keyCode);
    }

    @Override
    public void deserialize(String value) {
        try {
            keyCode = Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
        }
    }
}
