package com.vortex.legacy.module.misc;

import com.vortex.legacy.core.Module;
import com.vortex.legacy.core.ModeSetting;
import com.vortex.legacy.core.NumberSetting;

/** Schreibt "gg", wenn ein Spiel endet (Hypixel, Minemen, Mineplex, ... erkannt am Chat). */
public class AutoGG extends Module {
    public final ModeSetting message = add(new ModeSetting("Message", 0, "gg", "GG", "good game", "gf"));
    public final NumberSetting delay = add(new NumberSetting("Delay (s)", 1, 0, 5, 0.5));
    private long lastGg, sendAt;

    public AutoGG() { super("Auto GG", Category.MISC, "Says gg when a game ends."); }

    private static final String[] END = {
            "1st Killer - ", "1st Place - ", "Winner: ", " - Damage Dealt - ", "Winning Team -", "1st - ", "Winners: ",
            "Winner - ", "Victory!", "won the game", "Top Killer", "Reward Summary", "WINNER!", "Most Kills"
    };

    public void onChat(String plain) {
        long now = System.currentTimeMillis();
        if (now - lastGg < 10000) return;
        for (String e : END) {
            if (plain.contains(e)) { lastGg = now; sendAt = now + (long) (delay.get() * 1000); return; }
        }
    }

    @Override
    public void onTick() {
        if (sendAt > 0 && System.currentTimeMillis() >= sendAt && mc.player != null) {
            sendAt = 0;
            String m = message.get();
            if (mc.getCurrentServerEntry() != null && mc.getCurrentServerEntry().address.toLowerCase().contains("hypixel")) mc.player.sendChatMessage("/ac " + m);
            else mc.player.sendChatMessage(m);
        }
    }
}
