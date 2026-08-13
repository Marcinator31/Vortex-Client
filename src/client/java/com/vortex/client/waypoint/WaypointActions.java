package com.vortex.client.waypoint;

import com.vortex.client.core.ConfigManager;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.glfw.GLFW;

/**
 * Die Bedienung des Waypoint-Systems: Tastenbelegungen, Markieren von Bloecken
 * und der automatische Todespunkt.
 *
 * Die Tasten werden direkt abgefragt (nicht ueber Minecrafts Tastensystem),
 * damit sie im Client-Menue zugewiesen werden koennen wie alles andere auch.
 * Jede Taste nutzt Flankenerkennung -- es zaehlt der Moment des Druckes, nicht
 * das Halten.
 */
public final class WaypointActions {

    // Merker fuer die Flankenerkennung je Taste.
    private static boolean addDown, markDown, toggleDown, manageDown, areaDown;

    /** Erste Ecke einer Bereichsmarkierung (null = noch keine gesetzt). */
    private static BlockPos areaCorner = null;
    /** War der Spieler im letzten Tick noch am Leben? */
    private static boolean wasAlive = true;

    /**
     * Marker, zu dem markierte Bloecke hinzugefuegt werden.
     *
     * So kann man mehrere Bloecke zu EINER benannten Gruppe zusammenfassen --
     * etwa die drei Stellen, an denen bei einer Falle etwas gesetzt werden muss.
     * Waehlbar in der Verwaltung; ohne Auswahl wird beim ersten Block eine neue
     * Gruppe angelegt.
     */
    private static WaypointManager.Waypoint blockGroup = null;

    public static void setBlockGroup(WaypointManager.Waypoint w) {
        blockGroup = w;
    }

    public static WaypointManager.Waypoint getBlockGroup() {
        return blockGroup;
    }

    private WaypointActions() {}

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            try {
                tick(client);
            } catch (Throwable pvpErr) {
                com.vortex.client.core.Errors.report("WaypointActions", pvpErr);
            }
        });
    }

    private static void tick(MinecraftClient client) {
        if (client.player == null || client.world == null) {
            wasAlive = true;
            return;
        }
        WaypointSettings cfg = WaypointSettings.INSTANCE;
        ClientPlayerEntity self = client.player;

        // --- Todespunkt: Uebergang lebendig -> tot ---
        boolean alive = self.isAlive();
        if (cfg.deathWaypoint.get() && wasAlive && !alive) {
            addWaypoint(client, self.getBlockX(), self.getBlockY(), self.getBlockZ(),
                    "Tod " + timeStamp(), WaypointManager.Kind.TOD, true);
        }
        wasAlive = alive;

        // Tasten nur ausserhalb von Menues auswerten.
        if (client.currentScreen != null) {
            addDown = markDown = toggleDown = manageDown = false;
            return;
        }

        // --- Marker an der aktuellen Position ---
        boolean d = pressed(client, cfg.keyAddHere.getKeyCode());
        if (d && !addDown) {
            addWaypoint(client, self.getBlockX(), self.getBlockY(), self.getBlockZ(),
                    null, WaypointManager.Kind.ALLGEMEIN, true);
        }
        addDown = d;

        // --- Block markieren, auf den das Fadenkreuz zeigt ---
        d = pressed(client, cfg.keyMarkBlock.getKeyCode());
        if (d && !markDown) {
            markLookedAtBlock(client);
        }
        markDown = d;

        // --- Bereich markieren: zwei Ecken, alles dazwischen kommt rein ---
        d = pressed(client, cfg.keyMarkArea.getKeyCode());
        if (d && !areaDown) {
            markArea(client);
        }
        areaDown = d;

        // --- Anzeige umschalten ---
        d = pressed(client, cfg.keyToggle.getKeyCode());
        if (d && !toggleDown) {
            cfg.enabled.toggle();
            info(client, cfg.enabled.get() ? "Waypoints an" : "Waypoints aus");
            ConfigManager.save();
        }
        toggleDown = d;

        // --- Verwaltung oeffnen ---
        d = pressed(client, cfg.keyManage.getKeyCode());
        if (d && !manageDown) {
            client.setScreen(new com.vortex.client.gui.WaypointScreen(null));
        }
        manageDown = d;
    }

    /** Taste gedrueckt? Nicht belegte Tasten zaehlen nie. */
    private static boolean pressed(MinecraftClient client, int keyCode) {
        if (keyCode == GLFW.GLFW_KEY_UNKNOWN) return false;
        try {
            return InputUtil.isKeyPressed(client.getWindow(), keyCode);
        } catch (Throwable pvpErr) {
            return false;
        }
    }

    /**
     * Markiert den Block unter dem Fadenkreuz.
     *
     * Praktisch, um etwas Bestimmtes festzuhalten, ohne hinzulaufen -- etwa eine
     * Kiste am anderen Ende der Halle oder einen Portal-Rahmen von weitem.
     */
    private static void markLookedAtBlock(MinecraftClient client) {
        HitResult hit = client.crosshairTarget;
        if (hit == null || hit.getType() != HitResult.Type.BLOCK
                || !(hit instanceof BlockHitResult bhr)) {
            info(client, "Kein Block im Visier.");
            return;
        }
        BlockPos pos = bhr.getBlockPos();

        // Noch keine Gruppe gewaehlt -> neue anlegen, benannt nach dem Block.
        if (blockGroup == null || !WaypointManager.all().contains(blockGroup)) {
            String blockName;
            try {
                blockName = client.world.getBlockState(pos)
                        .getBlock().getName().getString();
            } catch (Throwable pvpErr) {
                blockName = "Bloecke";
            }
            blockGroup = addWaypoint(client, pos.getX(), pos.getY(), pos.getZ(),
                    blockName, WaypointManager.Kind.BLOCK, false);
            blockGroup.blocks.add(pos);
            ConfigManager.save();
            info(client, "Neue Block-Gruppe \"" + blockGroup.name
                    + "\" -- weitere Bloecke landen hier. Umbenennen in der Verwaltung.");
            return;
        }

        // Bereits enthalten -> wieder entfernen (dieselbe Taste schaltet um).
        if (blockGroup.blocks.remove(pos)) {
            ConfigManager.save();
            info(client, "Block entfernt (" + blockGroup.blocks.size()
                    + " in \"" + blockGroup.name + "\")");
            return;
        }
        blockGroup.blocks.add(pos);
        ConfigManager.save();
        info(client, "Block markiert (" + blockGroup.blocks.size()
                + " in \"" + blockGroup.name + "\")");
    }

    /**
     * Bereichsmarkierung: erster Druck merkt die Ecke, zweiter fuellt den
     * gesamten Quader dazwischen in die Gruppe.
     *
     * So markiert man ein 4x4-Feld mit zwei Tastendruecken statt sechzehn --
     * und ebenso eine ganze Wand oder einen Raum.
     */
    private static void markArea(MinecraftClient client) {
        HitResult hit = client.crosshairTarget;
        if (hit == null || hit.getType() != HitResult.Type.BLOCK
                || !(hit instanceof BlockHitResult bhr)) {
            info(client, "Kein Block im Visier.");
            return;
        }
        BlockPos pos = bhr.getBlockPos();

        if (areaCorner == null) {
            areaCorner = pos;
            info(client, "Erste Ecke gesetzt (" + pos.getX() + ", " + pos.getY()
                    + ", " + pos.getZ() + ") -- jetzt die zweite waehlen.");
            return;
        }

        // Gruppe sicherstellen.
        if (blockGroup == null || !WaypointManager.all().contains(blockGroup)) {
            blockGroup = addWaypoint(client, pos.getX(), pos.getY(), pos.getZ(),
                    "Bereich", WaypointManager.Kind.BLOCK, false);
        }

        int x1 = Math.min(areaCorner.getX(), pos.getX());
        int x2 = Math.max(areaCorner.getX(), pos.getX());
        int y1 = Math.min(areaCorner.getY(), pos.getY());
        int y2 = Math.max(areaCorner.getY(), pos.getY());
        int z1 = Math.min(areaCorner.getZ(), pos.getZ());
        int z2 = Math.max(areaCorner.getZ(), pos.getZ());

        // Obergrenze, damit ein versehentlich riesiger Bereich nicht das Bild
        // (und den Speicher) zumuellt.
        long total = (long) (x2 - x1 + 1) * (y2 - y1 + 1) * (z2 - z1 + 1);
        if (total > 512) {
            info(client, "Bereich zu gross (" + total + " Bloecke, hoechstens 512).");
            areaCorner = null;
            return;
        }

        int added = 0;
        for (int x = x1; x <= x2; x++) {
            for (int y = y1; y <= y2; y++) {
                for (int z = z1; z <= z2; z++) {
                    BlockPos bp = new BlockPos(x, y, z);
                    if (!blockGroup.blocks.contains(bp)) {
                        blockGroup.blocks.add(bp);
                        added++;
                    }
                }
            }
        }
        areaCorner = null;
        ConfigManager.save();
        info(client, added + " Bloecke markiert (" + blockGroup.blocks.size()
                + " in \"" + blockGroup.name + "\")");
    }

    /** Legt einen Marker an und meldet es im Chat. */
    public static WaypointManager.Waypoint addWaypoint(MinecraftClient client,
                                                       int x, int y, int z,
                                                       String name,
                                                       WaypointManager.Kind kind,
                                                       boolean announce) {
        String dim = com.vortex.client.hud.WaypointRenderer.currentWorldKey(client);
        String finalName = (name == null || name.isBlank())
                ? (kind.label + " " + (WaypointManager.all().size() + 1))
                : name;
        WaypointManager.Waypoint w =
                WaypointManager.add(finalName, x, y, z, dim, kind);
        ConfigManager.save();
        if (announce) {
            info(client, "Marker gesetzt: " + finalName + "  ("
                    + x + ", " + y + ", " + z + ")");
        }
        return w;
    }

    /**
     * Rechnet einen Marker zwischen Oberwelt und Nether um (Faktor 8) und legt
     * das Gegenstueck an.
     *
     * Beim Portalbau spart das die Kopfrechnerei -- und beim Base-Hunting findet
     * man so die Stelle im Nether, die zu einer Basis in der Oberwelt gehoert.
     */
    public static WaypointManager.Waypoint createCounterpart(MinecraftClient client,
                                                             WaypointManager.Waypoint w) {
        boolean fromNether = w.dimension != null && w.dimension.contains("nether");
        int nx, nz;
        String targetDim;
        if (fromNether) {
            nx = w.x * 8;
            nz = w.z * 8;
            targetDim = "minecraft:overworld";
        } else {
            nx = Math.floorDiv(w.x, 8);
            nz = Math.floorDiv(w.z, 8);
            targetDim = "minecraft:the_nether";
        }
        WaypointManager.Waypoint c = WaypointManager.add(
                w.name + (fromNether ? " (Oberwelt)" : " (Nether)"),
                nx, w.y, nz, targetDim, WaypointManager.Kind.PORTAL);
        ConfigManager.save();
        info(client, "Gegenstueck angelegt: " + nx + ", " + w.y + ", " + nz);
        return c;
    }

    /** Koordinaten in die Zwischenablage legen. */
    public static void copyToClipboard(MinecraftClient client,
                                       WaypointManager.Waypoint w) {
        try {
            client.keyboard.setClipboard(w.x + " " + w.y + " " + w.z);
            info(client, "Koordinaten kopiert.");
        } catch (Throwable pvpErr) {
            com.vortex.client.core.Errors.report("WaypointActions.clipboard", pvpErr);
        }
    }

    private static String timeStamp() {
        java.time.LocalTime t = java.time.LocalTime.now();
        return String.format(java.util.Locale.ROOT, "%02d:%02d", t.getHour(), t.getMinute());
    }

    private static void info(MinecraftClient client, String msg) {
        if (client.player == null) return;
        try {
            client.player.sendMessage(Text.literal("[Waypoints] " + msg), false);
        } catch (Throwable pvpErr) {
            com.vortex.client.core.Errors.report("WaypointActions.info", pvpErr);
        }
    }
}
