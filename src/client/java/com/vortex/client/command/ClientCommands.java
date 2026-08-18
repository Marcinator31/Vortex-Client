package com.vortex.client.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.vortex.client.util.GameRestarter;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.common.MinecraftForge;

/** Client-only Forge commands; none of these commands are sent to a server. */
public final class ClientCommands {
    private static boolean registered;
    private ClientCommands() {}

    public static void register() {
        if (registered) return;
        registered = true;
        MinecraftForge.EVENT_BUS.addListener((RegisterClientCommandsEvent event) -> {
            var dispatcher = event.getDispatcher();

            dispatcher.register(Commands.literal("relaunch").executes(ctx -> {
                success(ctx, "Restarting game...");
                try { GameRestarter.restart(); }
                catch (Throwable error) { failure(ctx, "Restart failed: " + error); }
                return 1;
            }));

            dispatcher.register(Commands.literal("export")
                    .then(Commands.argument("name", StringArgumentType.word()).executes(ctx -> {
                        String name = StringArgumentType.getString(ctx, "name");
                        java.nio.file.Path path = com.vortex.client.core.ConfigManager.exportPreset(name);
                        if (path != null) success(ctx, "Saved: " + path);
                        else failure(ctx, "Export failed.");
                        return 1;
                    })));

            dispatcher.register(Commands.literal("import")
                    .then(Commands.argument("name", StringArgumentType.word()).executes(ctx -> {
                        String name = StringArgumentType.getString(ctx, "name");
                        if (com.vortex.client.core.ConfigManager.importPreset(name)) success(ctx, "Loaded: " + name);
                        else failure(ctx, "Not found. Available: " + String.join(", ",
                                com.vortex.client.core.ConfigManager.listExports()));
                        return 1;
                    })));

            dispatcher.register(Commands.literal("wp")
                    .then(Commands.literal("add")
                            .then(Commands.argument("name", StringArgumentType.word()).executes(ctx -> {
                                Minecraft minecraft = Minecraft.getInstance();
                                if (minecraft.player == null) return 0;
                                String name = StringArgumentType.getString(ctx, "name");
                                String dimension = com.vortex.client.hud.WaypointRenderer.currentDimension(minecraft);
                                com.vortex.client.waypoint.WaypointManager.add(name,
                                        minecraft.player.getBlockX(), minecraft.player.getBlockY(),
                                        minecraft.player.getBlockZ(), dimension);
                                com.vortex.client.core.ConfigManager.save();
                                success(ctx, "Marker added: " + name);
                                return 1;
                            })))
                    .then(Commands.literal("del")
                            .then(Commands.argument("name", StringArgumentType.word()).executes(ctx -> {
                                String name = StringArgumentType.getString(ctx, "name");
                                boolean removed = com.vortex.client.waypoint.WaypointManager.remove(name);
                                com.vortex.client.core.ConfigManager.save();
                                if (removed) success(ctx, "Removed: " + name);
                                else failure(ctx, "No marker with that name.");
                                return 1;
                            })))
                    .then(Commands.literal("list").executes(ctx -> {
                        var waypoints = com.vortex.client.waypoint.WaypointManager.all();
                        if (waypoints.isEmpty()) { success(ctx, "No markers yet."); return 1; }
                        StringBuilder message = new StringBuilder("Markers:");
                        for (var waypoint : waypoints) message.append("\n  ").append(waypoint.name)
                                .append("  ").append(waypoint.x).append(", ").append(waypoint.y)
                                .append(", ").append(waypoint.z);
                        success(ctx, message.toString());
                        return 1;
                    })));

            dispatcher.register(Commands.literal("lag").executes(ctx -> {
                success(ctx, com.vortex.client.core.Profiler.summary());
                return 1;
            }).then(Commands.literal("reset").executes(ctx -> {
                com.vortex.client.core.Profiler.reset();
                success(ctx, "Measurements reset.");
                return 1;
            })));

            dispatcher.register(Commands.literal("errors").executes(ctx -> {
                success(ctx, com.vortex.client.core.Errors.summary());
                return 1;
            }));

            dispatcher.register(Commands.literal("presets").executes(ctx -> {
                var exports = com.vortex.client.core.ConfigManager.listExports();
                success(ctx, exports.isEmpty() ? "No exports found."
                        : "Exports: " + String.join(", ", exports));
                return 1;
            }));
        });
    }

    private static void success(com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> context,
                                String text) {
        context.getSource().sendSuccess(() -> Component.literal(text), false);
    }

    private static void failure(com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> context,
                                String text) {
        context.getSource().sendFailure(Component.literal(text));
    }
}
