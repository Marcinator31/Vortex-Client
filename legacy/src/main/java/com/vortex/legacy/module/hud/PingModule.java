package com.vortex.legacy.module.hud;

import net.minecraft.client.network.PlayerListEntry;

public class PingModule extends SimpleText {
    public PingModule() { super("Ping", "Your latency to the server.", 4, 76); }
    @Override protected String text(boolean editor) {
        if (mc.player == null || mc.getNetworkHandler() == null) return editor ? "42 ms" : null;
        if (mc.isInSingleplayer()) return editor ? "0 ms" : "0 ms";
        PlayerListEntry e = mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
        return e == null ? "? ms" : e.getLatency() + " ms";
    }
}
