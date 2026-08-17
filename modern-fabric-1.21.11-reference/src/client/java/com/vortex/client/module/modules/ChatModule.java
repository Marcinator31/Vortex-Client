package com.vortex.client.module.modules;

import com.vortex.client.core.setting.BooleanSetting;
import com.vortex.client.core.setting.ColorSetting;
import com.vortex.client.core.setting.ModeSetting;
import com.vortex.client.module.Module;

/**
 * Small improvements to the chat.
 *
 * Nothing here changes what is sent -- only how what arrives is presented.
 */
public class ChatModule extends Module {

    /** Put the time in front of every message. */
    public final BooleanSetting timestamps = new BooleanSetting("Timestamps", true);

    /** Format of the time. */
    public final ModeSetting format = new ModeSetting("Time Format", 0, "HH:mm", "HH:mm:ss");

    /** Colour of the timestamp, kept separate so it stays out of the way. */
    public final ColorSetting timeColor = new ColorSetting("Timestamp Colour", 0xFF7A7A86);

    /**
     * Keep far more messages than usual.
     *
     * Vanilla drops everything past a hundred lines, which on a busy server is
     * a couple of minutes. Worth having when you want to look up who said what.
     */
    public final BooleanSetting longHistory = new BooleanSetting("Longer History", true);

    /**
     * Key that copies the recent chat to the clipboard.
     *
     * A key rather than a click on a single line: working out which line sits
     * under the cursor needs parts of the chat that this version does not hand
     * out, and a copy that grabs the line above the one you clicked is worse
     * than no copy at all. This takes the last lines in one go, which is
     * usually what you wanted anyway.
     */
    public final com.vortex.client.core.setting.KeySetting copyKey =
            new com.vortex.client.core.setting.KeySetting(
                    "Copy Chat Key", org.lwjgl.glfw.GLFW.GLFW_KEY_UNKNOWN);

    /** How many lines the copy key takes. */
    public final com.vortex.client.core.setting.NumberSetting copyLines =
            new com.vortex.client.core.setting.NumberSetting("Lines to Copy", 50, 5, 500, 5);

    public ChatModule() {
        super("Chat", Category.MISC);
        addSetting(timestamps);
        addSetting(format);
        addSetting(timeColor);
        addSetting(longHistory);
        addSetting(copyKey);
        addSetting(copyLines);
    }
}
