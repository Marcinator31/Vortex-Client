package com.vortex.legacy.module.hud;

public class ServerAddressModule extends SimpleText {
    public ServerAddressModule() { super("Server Address", "The server you are playing on.", 4, 262); }
    @Override protected String text(boolean editor) {
        if (mc.getCurrentServerEntry() != null) return mc.getCurrentServerEntry().address;
        return mc.isInSingleplayer() ? "Singleplayer" : (editor ? "play.example.net" : null);
    }
}
