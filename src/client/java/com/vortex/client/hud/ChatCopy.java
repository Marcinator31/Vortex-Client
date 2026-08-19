package com.vortex.client.hud;

import com.vortex.client.core.Errors;
import com.vortex.client.module.ModuleManager;
import com.vortex.client.module.modules.ChatModule;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.platform.InputConstants;

/**
 * Copies recent chat to the clipboard on a key.
 *
 * The lines are collected as they arrive rather than read out of the chat
 * afterwards -- the chat keeps its lines in a form that is awkward to get at,
 * and keeping our own copy is both simpler and unaffected by what the chat
 * decides to drop.
 */
public final class ChatCopy {

    /** Recent lines, oldest first. */
    private static final java.util.ArrayDeque<String> LINES = new java.util.ArrayDeque<>();

    /** Hard ceiling, so this cannot grow without bound over a long session. */
    private static final int LIMIT = 600;

    private static boolean keyWasDown = false;

    private ChatCopy() {}

    /** Called for every arriving chat message. */
    public static synchronized void add(String line) {
        if (line == null || line.isEmpty()) return;
        LINES.addLast(line);
        while (LINES.size() > LIMIT) LINES.removeFirst();
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            try {
                ChatModule mod = ModuleManager.INSTANCE.get(ChatModule.class);
                if (mod == null || !mod.isEnabled() || !mod.copyKey.isBound()) return;
                if (client.gui.screen() != null) {
                    keyWasDown = false;
                    return;
                }

                boolean down = InputConstants.isKeyDown(
                        client.getWindow(), mod.copyKey.getKeyCode());
                if (down && !keyWasDown) {
                    copy(client, mod.copyLines.getInt());
                }
                keyWasDown = down;
            } catch (Throwable pvpErr) {
                Errors.report("ChatCopy", pvpErr);
            }
        });
    }

    private static synchronized void copy(Minecraft client, int count) {
        if (LINES.isEmpty()) {
            info(client, "Nothing in the chat yet.");
            return;
        }
        java.util.List<String> all = new java.util.ArrayList<>(LINES);
        int from = Math.max(0, all.size() - count);
        String text = String.join("\\n", all.subList(from, all.size()));

        try {
            client.keyboardHandler.setClipboard(text);
            info(client, (all.size() - from) + " lines copied.");
        } catch (Throwable pvpErr) {
            Errors.report("ChatCopy.clipboard", pvpErr);
        }
    }

    private static void info(Minecraft client, String text) {
        if (client.player == null) return;
        client.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("[Chat] " + text));
    }
}
