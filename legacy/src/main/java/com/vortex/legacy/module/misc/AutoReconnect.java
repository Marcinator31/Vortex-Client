package com.vortex.legacy.module.misc;

import com.vortex.legacy.core.Module;
import com.vortex.legacy.core.NumberSetting;
import net.minecraft.client.network.ServerInfo;

/** Nach einem Rauswurf: Knopf "Reconnect" mit Countdown, verbindet von selbst neu. */
public class AutoReconnect extends Module {
    public final NumberSetting seconds = add(new NumberSetting("Delay (s)", 5, 1, 60, 1));
    public static ServerInfo last;

    public AutoReconnect() { super("Auto Reconnect", Category.MISC, "Reconnects after you get kicked."); }

    @Override
    public void onTick() {
        if (mc.getCurrentServerEntry() != null) last = mc.getCurrentServerEntry();
    }
}
