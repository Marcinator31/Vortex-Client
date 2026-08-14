package com.vortex.client.core.setting;

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
        // Unbound is a normal state, not an error -- say so plainly instead of
        // showing a question mark that leaves the reader guessing.
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_UNKNOWN) return "Not bound";
        try {
            return InputUtil.Type.KEYSYM.createFromCode(keyCode)
                    .getLocalizedText().getString().toUpperCase();
        } catch (Throwable t) {
            return "Not bound";
        }
    }

    /** Is a usable key assigned? */
    public boolean isBound() {
        return keyCode != org.lwjgl.glfw.GLFW.GLFW_KEY_UNKNOWN;
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
