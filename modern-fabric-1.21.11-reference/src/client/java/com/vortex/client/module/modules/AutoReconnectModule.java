package com.vortex.client.module.modules;

import com.vortex.client.core.setting.BooleanSetting;
import com.vortex.client.core.setting.NumberSetting;
import com.vortex.client.module.Module;

/**
 * Reconnects on its own after a disconnect.
 *
 * A dropped connection on a busy server usually means the queue again, and if
 * it happens while you are away it means finding your things gone. This waits
 * a moment and dials back in.
 *
 * The countdown is shown on the disconnect screen and can be stopped there, so
 * you are never dragged back into a server you meant to leave.
 */
public class AutoReconnectModule extends Module {

    /** Seconds to wait before the first attempt. */
    public final NumberSetting delay = new NumberSetting("Delay (s)", 5, 1, 120, 1);

    /**
     * How many attempts before giving up. 0 means keep trying.
     *
     * Worth thinking about: a server that is down stays down, and hammering it
     * helps nobody. The waiting time grows with each attempt for that reason.
     */
    public final NumberSetting attempts = new NumberSetting("Attempts", 5, 0, 50, 1);

    /**
     * Wait longer after each failed attempt.
     *
     * Five seconds, then ten, then twenty. A server restarting is back in a
     * minute or two, and this finds that moment without knocking every five
     * seconds in the meantime.
     */
    public final BooleanSetting backoff = new BooleanSetting("Increase Delay Each Try", true);

    /** Longest wait when the delay grows, in seconds. */
    public final NumberSetting maxDelay = new NumberSetting("Max Delay (s)", 60, 5, 600, 5);

    /**
     * Skip it when the server kicked you on purpose.
     *
     * A ban or a kick is a decision, not a fault -- walking straight back in
     * is pointless and looks worse than staying out.
     */
    public final BooleanSetting skipKicks = new BooleanSetting("Not After a Kick", true);

    /** Try again after a timeout or a lost connection. */
    public final BooleanSetting onTimeout = new BooleanSetting("After Connection Loss", true);

    /** Play a sound once the connection is back. */
    public final BooleanSetting sound = new BooleanSetting("Sound When Back", true);

    public AutoReconnectModule() {
        super("Auto Reconnect", Category.MISC);
        addSetting(delay);
        addSetting(attempts);
        addSetting(backoff);
        addSetting(maxDelay);
        addSetting(skipKicks);
        addSetting(onTimeout);
        addSetting(sound);
    }
}
