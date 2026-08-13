package com.example.pvpclient.command;

import com.example.pvpclient.util.GameRestarter;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.text.Text;

/**
 * Eigene Client-Befehle (laufen nur lokal, gehen NICHT an den Server).
 *
 *   /relaunch  -- startet das Spiel neu
 */
public final class ClientCommands {

    private ClientCommands() {}

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) -> {
            dispatcher.register(ClientCommandManager.literal("relaunch")
                    .executes(ctx -> {
                        ctx.getSource().sendFeedback(
                                Text.literal("Starte Spiel neu ..."));
                        try {
                            GameRestarter.restart();
                        } catch (Throwable t) {
                            ctx.getSource().sendError(Text.literal(
                                    "Neustart fehlgeschlagen: " + t));
                        }
                        return 1;
                    }));

            // /export <name> -- aktives Preset als Textdatei sichern
            dispatcher.register(ClientCommandManager.literal("export")
                    .then(ClientCommandManager.argument("name",
                            com.mojang.brigadier.arguments.StringArgumentType.word())
                    .executes(ctx -> {
                        String name = com.mojang.brigadier.arguments.StringArgumentType
                                .getString(ctx, "name");
                        java.nio.file.Path p =
                                com.example.pvpclient.core.ConfigManager.exportPreset(name);
                        if (p != null) {
                            ctx.getSource().sendFeedback(
                                    Text.literal("Gespeichert: " + p));
                        } else {
                            ctx.getSource().sendError(
                                    Text.literal("Export fehlgeschlagen."));
                        }
                        return 1;
                    })));

            // /import <name> -- gesicherte Datei ins aktive Preset laden
            dispatcher.register(ClientCommandManager.literal("import")
                    .then(ClientCommandManager.argument("name",
                            com.mojang.brigadier.arguments.StringArgumentType.word())
                    .executes(ctx -> {
                        String name = com.mojang.brigadier.arguments.StringArgumentType
                                .getString(ctx, "name");
                        if (com.example.pvpclient.core.ConfigManager.importPreset(name)) {
                            ctx.getSource().sendFeedback(
                                    Text.literal("Geladen: " + name));
                        } else {
                            ctx.getSource().sendError(
                                    Text.literal("Nicht gefunden. Vorhanden: "
                                        + String.join(", ",
                                            com.example.pvpclient.core.ConfigManager.listExports())));
                        }
                        return 1;
                    })));

            // /wp add|del|list -- Waypoints verwalten
            dispatcher.register(ClientCommandManager.literal("wp")
                    .then(ClientCommandManager.literal("add")
                        .then(ClientCommandManager.argument("name",
                                com.mojang.brigadier.arguments.StringArgumentType.word())
                        .executes(ctx -> {
                            var mc = net.minecraft.client.MinecraftClient.getInstance();
                            if (mc.player == null) return 0;
                            String name = com.mojang.brigadier.arguments.StringArgumentType
                                    .getString(ctx, "name");
                            String dim = com.example.pvpclient.hud.WaypointRenderer
                                    .currentDimension(mc);
                            com.example.pvpclient.waypoint.WaypointManager.add(name,
                                    mc.player.getBlockX(), mc.player.getBlockY(),
                                    mc.player.getBlockZ(), dim);
                            com.example.pvpclient.core.ConfigManager.save();
                            ctx.getSource().sendFeedback(
                                    Text.literal("Marker gesetzt: " + name));
                            return 1;
                        })))
                    .then(ClientCommandManager.literal("del")
                        .then(ClientCommandManager.argument("name",
                                com.mojang.brigadier.arguments.StringArgumentType.word())
                        .executes(ctx -> {
                            String name = com.mojang.brigadier.arguments.StringArgumentType
                                    .getString(ctx, "name");
                            boolean ok = com.example.pvpclient.waypoint.WaypointManager
                                    .remove(name);
                            com.example.pvpclient.core.ConfigManager.save();
                            if (ok) {
                                ctx.getSource().sendFeedback(
                                        Text.literal("Entfernt: " + name));
                            } else {
                                ctx.getSource().sendError(
                                        Text.literal("Kein Marker mit diesem Namen."));
                            }
                            return 1;
                        })))
                    .then(ClientCommandManager.literal("list")
                        .executes(ctx -> {
                            var all = com.example.pvpclient.waypoint.WaypointManager.all();
                            if (all.isEmpty()) {
                                ctx.getSource().sendFeedback(
                                        Text.literal("Keine Marker gesetzt."));
                                return 1;
                            }
                            StringBuilder sb = new StringBuilder("Marker:");
                            for (var w : all) {
                                sb.append("\n  ").append(w.name).append("  ")
                                  .append(w.x).append(", ").append(w.y)
                                  .append(", ").append(w.z);
                            }
                            ctx.getSource().sendFeedback(Text.literal(sb.toString()));
                            return 1;
                        })));

            // /lag -- zeigt, welcher Teil des Clients wie viel Zeit braucht
            dispatcher.register(ClientCommandManager.literal("lag")
                    .executes(ctx -> {
                        ctx.getSource().sendFeedback(Text.literal(
                                com.example.pvpclient.core.Profiler.summary()));
                        return 1;
                    })
                    .then(ClientCommandManager.literal("reset")
                        .executes(ctx -> {
                            com.example.pvpclient.core.Profiler.reset();
                            ctx.getSource().sendFeedback(
                                    Text.literal("Messwerte zurueckgesetzt."));
                            return 1;
                        })));

            // /errors -- zeigt, wo im Client etwas schiefgelaufen ist
            dispatcher.register(ClientCommandManager.literal("errors")
                    .executes(ctx -> {
                        ctx.getSource().sendFeedback(Text.literal(
                                com.example.pvpclient.core.Errors.summary()));
                        return 1;
                    }));

            // /presets -- zeigt, was gesichert ist
            dispatcher.register(ClientCommandManager.literal("presets")
                    .executes(ctx -> {
                        var list = com.example.pvpclient.core.ConfigManager.listExports();
                        ctx.getSource().sendFeedback(Text.literal(
                                list.isEmpty() ? "Keine Exporte vorhanden."
                                               : "Exporte: " + String.join(", ", list)));
                        return 1;
                    }));
        });
    }
}
