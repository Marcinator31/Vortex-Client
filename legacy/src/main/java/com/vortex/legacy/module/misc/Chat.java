package com.vortex.legacy.module.misc;

import com.vortex.legacy.core.BoolSetting;
import com.vortex.legacy.core.Module;
import com.vortex.legacy.core.ModeSetting;
import java.text.SimpleDateFormat;
import java.util.Date;

/** Chat: Uhrzeit vor Nachrichten, laengerer Verlauf, Woerter filtern. */
public class Chat extends Module {
    public final BoolSetting timestamps = add(new BoolSetting("Timestamps", true));
    public final ModeSetting format = add(new ModeSetting("Time Format", 0, "24h", "12h"));
    public final BoolSetting longHistory = add(new BoolSetting("Longer History", true));
    public final BoolSetting filter = add(new BoolSetting("Hide Spam Lines", false));

    public Chat() { super("Chat", Category.MISC, "Timestamps, longer chat history and a spam filter."); }
    @Override public boolean defaultEnabled() { return true; }

    public String stamp() {
        return "§8[" + new SimpleDateFormat(format.is("12h") ? "h:mm" : "HH:mm").format(new Date()) + "] §r";
    }

    private static final String[] SPAM = { "▬▬▬▬▬▬▬▬▬▬▬▬▬▬", "--------------------", "==================" };
    public boolean hide(String plain) {
        if (!filter.get()) return false;
        String t = plain.trim();
        if (t.isEmpty()) return true;
        for (String s : SPAM) if (t.contains(s)) return true;
        return false;
    }
}
