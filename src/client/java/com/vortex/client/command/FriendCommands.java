package com.vortex.client.command;

import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.vortex.client.social.Social;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

/**
 * Freunde per Chat-Befehl (laufen nur lokal, gehen nicht an den Server):
 *
 *   /vf                    Freunde-Fenster oeffnen
 *   /vf msg <name> <text>  Nachricht schreiben      /vf r <text>  antworten
 *   /vf add <name>         Anfrage senden           /vf accept <name>
 *   /vf remove <name>      Freund entfernen         /vf list
 *   /vf invite <name>      auf meinen Server einladen
 *   /vf join <name>        dem Freund nachjoinen
 *   /vf yes | no           letzte Einladung annehmen/ablehnen
 *   /vf status <online|away|dnd|invisible>
 *   /vf block <name>       /vf unblock <name>
 *
 * "/vf" statt "/f": /f ist auf vielen Servern schon vergeben (Factions).
 */
public final class FriendCommands {

    private FriendCommands() {}

    private static final SuggestionProvider<FabricClientCommandSource> FRIENDS = (ctx, b) -> {
        for (JsonObject f : Social.friends()) {
            String n = Social.str(f, "name");
            if (n.toLowerCase().startsWith(b.getRemaining().toLowerCase())) b.suggest(n);
        }
        return b.buildFuture();
    };

    private static final SuggestionProvider<FabricClientCommandSource> REQUESTERS = (ctx, b) -> {
        for (JsonObject f : Social.incoming()) b.suggest(Social.str(f, "name"));
        return b.buildFuture();
    };

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((d, access) -> d.register(literal("vf")
                .executes(c -> { Social.openScreenSoon(); return 1; })
                .then(literal("msg").then(argument("name", StringArgumentType.word()).suggests(FRIENDS)
                        .then(argument("text", StringArgumentType.greedyString()).executes(c -> {
                            JsonObject f = friendOrSay(c.getSource(), StringArgumentType.getString(c, "name"));
                            if (f == null) return 0;
                            String text = StringArgumentType.getString(c, "text");
                            done(c.getSource(), Social.request("chat.send", Social.args("to", Social.str(f, "uuid"), "body", text)),
                                    "§d[Friends] §7You §8» §f" + Social.label(f, Social.str(f, "name")) + "§7: " + text);
                            return 1;
                        }))))
                .then(literal("r").then(argument("text", StringArgumentType.greedyString()).executes(c -> {
                    String to = Social.lastDmFrom();
                    if (to == null) { c.getSource().sendError(Component.literal("Nobody wrote you yet.")); return 0; }
                    String text = StringArgumentType.getString(c, "text");
                    done(c.getSource(), Social.request("chat.send", Social.args("to", to, "body", text)),
                            "§d[Friends] §7You §8» §f" + Social.nameOf(to) + "§7: " + text);
                    return 1;
                })))
                .then(literal("add").then(argument("name", StringArgumentType.word()).executes(c -> {
                    String name = StringArgumentType.getString(c, "name");
                    Social.request("friend.request", Social.args("name", name)).thenAccept(r -> say(c.getSource(),
                            Social.bool(r, "friends") ? "§aYou and " + Social.str(r, "name") + " are now friends."
                                    : "§aFriend request sent to " + Social.str(r, "name") + "."))
                            .exceptionally(e -> fail(c.getSource(), e));
                    return 1;
                })))
                .then(literal("accept").then(argument("name", StringArgumentType.word()).suggests(REQUESTERS).executes(c -> {
                    String name = StringArgumentType.getString(c, "name");
                    JsonObject r = null;
                    for (JsonObject x : Social.incoming()) if (Social.str(x, "name").equalsIgnoreCase(name)) r = x;
                    if (r == null) { c.getSource().sendError(Component.literal(name + " did not send you a request.")); return 0; }
                    done(c.getSource(), Social.request("friend.accept", Social.args("uuid", Social.str(r, "uuid"))), "§aYou and " + Social.str(r, "name") + " are now friends.");
                    return 1;
                })))
                .then(literal("remove").then(argument("name", StringArgumentType.word()).suggests(FRIENDS).executes(c -> {
                    JsonObject f = friendOrSay(c.getSource(), StringArgumentType.getString(c, "name"));
                    if (f == null) return 0;
                    done(c.getSource(), Social.request("friend.remove", Social.args("uuid", Social.str(f, "uuid"))), "§7" + Social.str(f, "name") + " removed.");
                    return 1;
                })))
                .then(literal("block").then(argument("name", StringArgumentType.word()).executes(c -> {
                    String name = StringArgumentType.getString(c, "name");
                    done(c.getSource(), Social.request("block", Social.args("name", name)), "§7" + name + " blocked.");
                    return 1;
                })))
                .then(literal("unblock").then(argument("name", StringArgumentType.word()).executes(c -> {
                    String name = StringArgumentType.getString(c, "name");
                    JsonObject b = null;
                    for (JsonObject x : Social.blocked()) if (Social.str(x, "name").equalsIgnoreCase(name)) b = x;
                    if (b == null) { c.getSource().sendError(Component.literal(name + " is not blocked.")); return 0; }
                    done(c.getSource(), Social.request("unblock", Social.args("uuid", Social.str(b, "uuid"))), "§7" + name + " unblocked.");
                    return 1;
                })))
                .then(literal("list").executes(c -> { list(c.getSource()); return 1; }))
                .then(literal("invite").then(argument("name", StringArgumentType.word()).suggests(FRIENDS).executes(c -> {
                    JsonObject f = friendOrSay(c.getSource(), StringArgumentType.getString(c, "name"));
                    if (f == null) return 0;
                    String addr = Social.currentAddress();
                    if (addr.isEmpty()) { c.getSource().sendError(Component.literal("Join a server first.")); return 0; }
                    var sd = Minecraft.getInstance().getCurrentServer();
                    done(c.getSource(), Social.request("invite.send", Social.args("to", Social.str(f, "uuid"), "address", addr,
                            "serverName", sd == null ? "" : sd.name, "version", Social.mcVersion())), "§aInvite sent to " + Social.str(f, "name") + ".");
                    return 1;
                })))
                .then(literal("join").then(argument("name", StringArgumentType.word()).suggests(FRIENDS).executes(c -> {
                    JsonObject f = friendOrSay(c.getSource(), StringArgumentType.getString(c, "name"));
                    if (f == null) return 0;
                    Social.joinFriend(Social.str(f, "uuid")).exceptionally(e -> fail(c.getSource(), e));
                    return 1;
                })))
                .then(literal("yes").executes(c -> answer(c.getSource(), true)))
                .then(literal("no").executes(c -> answer(c.getSource(), false)))
                .then(literal("status").then(argument("mode", StringArgumentType.word())
                        .suggests((ctx, b) -> { for (String m : List.of("online", "away", "dnd", "invisible")) b.suggest(m); return b.buildFuture(); })
                        .executes(c -> {
                            String m = StringArgumentType.getString(c, "mode").toLowerCase();
                            done(c.getSource(), Social.request("status.set", Social.args("mode", m)), "§7Status: " + m);
                            return 1;
                        })))));
    }

    private static int answer(FabricClientCommandSource src, boolean accept) {
        List<JsonObject> inv = Social.invites();
        if (inv.isEmpty()) { src.sendError(Component.literal("No open invite.")); return 0; }
        JsonObject last = inv.get(inv.size() - 1);
        Social.answerInvite(Social.str(last, "id"), accept).exceptionally(e -> fail(src, e));
        if (!accept) say(src, "§7Invite declined.");
        return 1;
    }

    private static void list(FabricClientCommandSource src) {
        List<JsonObject> fs = new ArrayList<>(Social.friends());
        if (Social.conn() != Social.Conn.ONLINE) { src.sendError(Component.literal("Not connected to the friends server.")); return; }
        if (fs.isEmpty()) { say(src, "§7No friends yet. /vf add <name>"); return; }
        fs.sort((a, b) -> Integer.compare(rank(a), rank(b)));
        say(src, "§d[Friends] §7" + fs.size() + " friend(s):");
        for (JsonObject f : fs) {
            JsonObject p = Social.obj(f, "presence");
            String col = rank(f) == 0 ? "§b" : rank(f) == 1 ? "§a" : "§8";
            say(src, " " + col + "● §f" + Social.label(f, Social.str(f, "name")) + " §7" + Social.presenceText(p, false));
        }
    }

    private static int rank(JsonObject f) {
        String s = Social.str(Social.obj(f, "presence"), "state");
        return "playing".equals(s) ? 0 : "online".equals(s) ? 1 : 2;
    }

    private static JsonObject friendOrSay(FabricClientCommandSource src, String name) {
        JsonObject f = Social.friendByName(name);
        if (f == null) src.sendError(Component.literal(name + " is not your friend." + (Social.conn() != Social.Conn.ONLINE ? " (Not connected to the friends server.)" : "")));
        return f;
    }

    private static void done(FabricClientCommandSource src, CompletableFuture<JsonObject> f, String ok) {
        f.thenAccept(r -> say(src, ok)).exceptionally(e -> fail(src, e));
    }

    private static void say(FabricClientCommandSource src, String text) {
        src.sendFeedback(Component.literal(text));
    }

    private static Void fail(FabricClientCommandSource src, Throwable e) {
        src.sendError(Component.literal(Social.errorOf(e)));
        return null;
    }
}
