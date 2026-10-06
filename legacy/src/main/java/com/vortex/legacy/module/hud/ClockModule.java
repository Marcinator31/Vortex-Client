package com.vortex.legacy.module.hud;

import com.vortex.legacy.core.ModeSetting;
import java.text.SimpleDateFormat;
import java.util.Date;

public class ClockModule extends SimpleText {
    private final ModeSetting format = add(new ModeSetting("Format", 0, "24h", "12h"));
    public ClockModule() { super("Clock", "The real time.", 4, 94); }
    @Override protected String text(boolean editor) {
        return new SimpleDateFormat(format.is("12h") ? "h:mm a" : "HH:mm").format(new Date());
    }
}
