package com.vortex.client.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.vortex.client.core.ListFile;
import com.vortex.client.hud.AutoGG;
import com.vortex.client.hud.ChatFilter;
import com.vortex.client.hud.SoundControl;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

/**
 * Befehle fuer die Listen der 4.11-Module (laufen nur lokal):
 *
 *   /vchat filter|highlight add <text> | remove <text> | list | clear
 *   /vsound set <id> <prozent> | reset <id> | list | recent
 *   /autogg set <text> | default <text> | remove | list
 */
public final class ListCommands {

    private ListCommands() {}

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((d, access) -> {
            d.register(literal("vchat")
                    .then(liste("filter", ChatFilter.FILTER, "Filter words"))
                    .then(liste("highlight", ChatFilter.HIGHLIGHT, "Highlight words")));

            d.register(literal("vsound")
                    .then(literal("set").then(argument("id", StringArgumentType.word())
                            .then(argument("percent", StringArgumentType.word()).executes(c -> {
                                String id = StringArgumentType.getString(c, "id");
                                int p;
                                try {
                                    p = Integer.parseInt(StringArgumentType.getString(c, "percent").replace("%", ""));
                                } catch (NumberFormatException e) {
                                    c.getSource().sendError(Component.literal("Percent must be a number from 0 to 100."));
                                    return 0;
                                }
                                p = Math.max(0, Math.min(100, p));
                                SoundControl.setze(id, p);
                                ok(c.getSource(), id + " -> " + p + "%" + (p == 0 ? " (muted)" : ""));
                                return 1;
                            }))))
                    .then(literal("reset").then(argument("id", StringArgumentType.word()).executes(c -> {
                        String id = StringArgumentType.getString(c, "id");
                        SoundControl.setze(id, null);
                        ok(c.getSource(), id + " is back to normal.");
                        return 1;
                    })))
                    .then(literal("list").executes(c -> {
                        Map<String, String> t = SoundControl.EIGENE.tabelle();
                        ok(c.getSource(), t.isEmpty() ? "No single sounds set. /vsound recent shows what you heard last."
                                : "Sounds: " + String.join(", ", t.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue() + "%").toList()));
                        return 1;
                    }))
                    .then(literal("recent").executes(c -> {
                        List<String> l = SoundControl.zuletzt();
                        ok(c.getSource(), l.isEmpty() ? "Nothing heard yet." : "Last sounds (newest first): " + String.join(", ", l.subList(0, Math.min(15, l.size()))));
                        return 1;
                    })));

            d.register(literal("autogg")
                    .then(literal("set").then(argument("text", StringArgumentType.greedyString()).executes(c -> {
                        String s = AutoGG.server();
                        AutoGG.NACHRICHTEN.setze(s, StringArgumentType.getString(c, "text"));
                        ok(c.getSource(), "Auto GG on " + s + ": " + AutoGG.nachricht());
                        return 1;
                    })))
                    .then(literal("default").then(argument("text", StringArgumentType.greedyString()).executes(c -> {
                        AutoGG.NACHRICHTEN.setze("default", StringArgumentType.getString(c, "text"));
                        ok(c.getSource(), "Auto GG default: " + StringArgumentType.getString(c, "text"));
                        return 1;
                    })))
                    .then(literal("remove").executes(c -> {
                        String s = AutoGG.server();
                        AutoGG.NACHRICHTEN.setze(s, null);
                        ok(c.getSource(), "Removed the message for " + s + ". Now: " + AutoGG.nachricht());
                        return 1;
                    }))
                    .then(literal("list").executes(c -> {
                        Map<String, String> t = AutoGG.NACHRICHTEN.tabelle();
                        ok(c.getSource(), "This server (" + AutoGG.server() + "): " + AutoGG.nachricht()
                                + (t.isEmpty() ? "" : " | saved: " + String.join(", ", t.entrySet().stream().map(e -> e.getKey() + " = " + e.getValue()).toList())));
                        return 1;
                    })));
        });
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> liste(String name, ListFile datei, String titel) {
        return literal(name)
                .then(literal("add").then(argument("text", StringArgumentType.greedyString()).executes(c -> {
                    String t = StringArgumentType.getString(c, "text");
                    ok(c.getSource(), datei.hinzu(t) ? "Added: " + t : t + " is already on the list.");
                    return 1;
                })))
                .then(literal("remove").then(argument("text", StringArgumentType.greedyString()).executes(c -> {
                    String t = StringArgumentType.getString(c, "text");
                    ok(c.getSource(), datei.weg(t) ? "Removed: " + t : t + " was not on the list.");
                    return 1;
                })))
                .then(literal("list").executes(c -> {
                    List<String> l = datei.alle();
                    ok(c.getSource(), l.isEmpty() ? titel + ": none yet." : titel + " (" + l.size() + "): " + String.join(", ", l));
                    return 1;
                }))
                .then(literal("clear").executes(c -> {
                    datei.leeren();
                    ok(c.getSource(), titel + " cleared.");
                    return 1;
                }));
    }

    private static void ok(FabricClientCommandSource src, String text) {
        src.sendFeedback(Component.literal("§d[Vortex]§r " + text));
    }
}
