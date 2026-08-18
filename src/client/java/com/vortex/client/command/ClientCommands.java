package com.vortex.client.command;

import com.vortex.client.util.GameRestarter;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.network.chat.Component;

/**
 * Eigene Client-Befehle (laufen nur lokal, gehen NICHT an den Server).
 *
 *   /relaunch  -- startet das Spiel neu
 */
public final class ClientCommands {

    private ClientCommands() {}

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) -> {
            dispatcher.register(net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal("relaunch")
                    .executes(ctx -> {
                        ctx.getSource().sendFeedback(
                                Component.literal("Restarting game..."));
                        try {
                            GameRestarter.restart();
                        } catch (Throwable t) {
                            ctx.getSource().sendError(Component.literal(
                                    "Restart failed: " + t));
                        }
                        return 1;
                    }));

            // /export <name> -- aktives Preset als Textdatei sichern
            dispatcher.register(net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal("export")
                    .then(net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument("name",
                            com.mojang.brigadier.arguments.StringArgumentType.word())
                    .executes(ctx -> {
                        String name = com.mojang.brigadier.arguments.StringArgumentType
                                .getString(ctx, "name");
                        java.nio.file.Path p =
                                com.vortex.client.core.ConfigManager.exportPreset(name);
                        if (p != null) {
                            ctx.getSource().sendFeedback(
                                    Component.literal("Saved: " + p));
                        } else {
                            ctx.getSource().sendError(
                                    Component.literal("Export failed."));
                        }
                        return 1;
                    })));

            // /import <name> -- gesicherte Datei ins aktive Preset laden
            dispatcher.register(net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal("import")
                    .then(net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument("name",
                            com.mojang.brigadier.arguments.StringArgumentType.word())
                    .executes(ctx -> {
                        String name = com.mojang.brigadier.arguments.StringArgumentType
                                .getString(ctx, "name");
                        if (com.vortex.client.core.ConfigManager.importPreset(name)) {
                            ctx.getSource().sendFeedback(
                                    Component.literal("Loaded: " + name));
                        } else {
                            ctx.getSource().sendError(
                                    Component.literal("Not found. Available: "
                                        + String.join(", ",
                                            com.vortex.client.core.ConfigManager.listExports())));
                        }
                        return 1;
                    })));

            // /wp add|del|list -- Waypoints verwalten
            dispatcher.register(net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal("wp")
                    .then(net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal("add")
                        .then(net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument("name",
                                com.mojang.brigadier.arguments.StringArgumentType.word())
                        .executes(ctx -> {
                            var mc = net.minecraft.client.Minecraft.getInstance();
                            if (mc.player == null) return 0;
                            String name = com.mojang.brigadier.arguments.StringArgumentType
                                    .getString(ctx, "name");
                            String dim = com.vortex.client.hud.WaypointRenderer
                                    .currentDimension(mc);
                            com.vortex.client.waypoint.WaypointManager.add(name,
                                    mc.player.getBlockX(), mc.player.getBlockY(),
                                    mc.player.getBlockZ(), dim);
                            com.vortex.client.core.ConfigManager.save();
                            ctx.getSource().sendFeedback(
                                    Component.literal("Marker added: " + name));
                            return 1;
                        })))
                    .then(net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal("del")
                        .then(net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument("name",
                                com.mojang.brigadier.arguments.StringArgumentType.word())
                        .executes(ctx -> {
                            String name = com.mojang.brigadier.arguments.StringArgumentType
                                    .getString(ctx, "name");
                            boolean ok = com.vortex.client.waypoint.WaypointManager
                                    .remove(name);
                            com.vortex.client.core.ConfigManager.save();
                            if (ok) {
                                ctx.getSource().sendFeedback(
                                        Component.literal("Removed: " + name));
                            } else {
                                ctx.getSource().sendError(
                                        Component.literal("No marker with that name."));
                            }
                            return 1;
                        })))
                    .then(net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal("list")
                        .executes(ctx -> {
                            var all = com.vortex.client.waypoint.WaypointManager.all();
                            if (all.isEmpty()) {
                                ctx.getSource().sendFeedback(
                                        Component.literal("No markers yet."));
                                return 1;
                            }
                            StringBuilder sb = new StringBuilder("Markers:");
                            for (var w : all) {
                                sb.append("\n  ").append(w.name).append("  ")
                                  .append(w.x).append(", ").append(w.y)
                                  .append(", ").append(w.z);
                            }
                            ctx.getSource().sendFeedback(Component.literal(sb.toString()));
                            return 1;
                        })));

            // /lag -- zeigt, welcher Teil des Clients wie viel Zeit braucht
            dispatcher.register(net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal("lag")
                    .executes(ctx -> {
                        ctx.getSource().sendFeedback(Component.literal(
                                com.vortex.client.core.Profiler.summary()));
                        return 1;
                    })
                    .then(net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal("reset")
                        .executes(ctx -> {
                            com.vortex.client.core.Profiler.reset();
                            ctx.getSource().sendFeedback(
                                    Component.literal("Measurements reset."));
                            return 1;
                        })));

            // /errors -- zeigt, wo im Client etwas schiefgelaufen ist
            dispatcher.register(net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal("errors")
                    .executes(ctx -> {
                        ctx.getSource().sendFeedback(Component.literal(
                                com.vortex.client.core.Errors.summary()));
                        return 1;
                    }));

            // /presets -- zeigt, was gesichert ist
            dispatcher.register(net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal("presets")
                    .executes(ctx -> {
                        var list = com.vortex.client.core.ConfigManager.listExports();
                        ctx.getSource().sendFeedback(Component.literal(
                                list.isEmpty() ? "No exports found."
                                               : "Exports: " + String.join(", ", list)));
                        return 1;
                    }));
        });
    }
}
