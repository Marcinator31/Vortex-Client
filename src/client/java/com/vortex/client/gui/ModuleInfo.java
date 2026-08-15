package com.vortex.client.gui;

import java.util.HashMap;
import java.util.Map;

/**
 * Short descriptions shown in the module list.
 *
 * One sentence per module, plain and to the point: what it does, and -- where
 * it matters -- how risky it is on a server. A description that only repeats
 * the module name is worse than none at all, so each one adds something the
 * name does not already say.
 */
public final class ModuleInfo {

    private static final Map<String, String> DESCRIPTIONS = new HashMap<>();

    static {
        put("FPS", "Shows your current frame rate.");
        put("Ping", "Shows your connection delay to the server.");
        put("CPS", "Counts your clicks per second.");
        put("Coordinates", "Shows your position and the direction you are facing.");
        put("Potion Effects", "Lists your active effects and how long they last.");
        put("ArmorHUD", "Shows your armour and its remaining durability.");
        put("Totem Counter", "Shows how many totems you are carrying.");
        put("Radar", "A small map of nearby players and mobs.");
        put("AppleSkin", "Shows saturation and how much a food item will restore.");
        put("Player List", "Lists nearby players with their distance.");
        put("Keystrokes", "Shows which movement keys and mouse buttons you press.");
        put("Totem Popper", "Counts how many totems the players around you have used.");
        put("Session Stats", "Playtime, deaths, totems used and your best click rate.");
        put("HUD Color", "Sets one shared colour for every HUD element at once.");
        put("Hitboxes", "Draws the collision box around entities.");
        put("Shield Status", "Shows whether an opponent's shield is raised.");
        put("Toggle Sprint", "Keeps sprinting without holding the key.");
        put("Health Indicator", "Shows an opponent's health above their head.");
        put("Target Info", "Shows an opponent's gear and whether they are in attack range.");
        put("Projectile Path", "Previews where a pearl, potion or arrow will land.");
        put("ESP", "Highlights mobs through walls.");
        put("Block-ESP", "Highlights selected blocks, such as ores, through walls.");
        put("Container ESP", "Highlights chests, barrels and shulker boxes.");
        put("Spawner ESP", "Highlights monster spawners.");
        put("Item ESP", "Highlights dropped items on the ground.");
        put("Aimbot", "Aims at opponents automatically. Very high ban risk.");
        put("Auto Hit", "Attacks automatically when a target is in range. Very high ban risk.");
        put("Auto Totem", "Moves a totem into your off hand automatically. High ban risk.");
        put("Fly", "Lets you fly. Detected almost immediately on most servers.");
        put("Crystal Macro", "Places end crystals on obsidian and breaks them instantly. Extreme ban risk.");
        put("Potato Mode", "Turns off expensive effects for more frames per second.");
        put("Item Counter", "Counts chosen items. Several displays, each with its own place and items.");
        put("Armor Warning", "Says something before a piece of armour breaks, with a sound.");
        put("Toggle Sneak", "Keeps you crouching without holding the key.");
        put("Crosshair", "Your own crosshair: shape, size, colour, and a gap in the middle.");
        put("Chat", "Timestamps, a much longer history, and a key to copy it all.");
        put("Nametags", "Size, transparency and range of the names above players.");
        put("Zoom", "Hold a key and zoom with the wheel. The hotbar stays put while you do.");
        put("Auto Reconnect", "Counts down on the disconnect screen and dials back in.");
        put("No Render Blocks", "Hides the block types you pick, as if they were air. Nothing else changes.");
        put("Anti Render", "Hides selected entity types completely.");
        put("Fullbright", "Brightens everything, no torches needed.");
        put("No Particles", "Hides particle effects.");
        put("Small Totem", "Shrinks the totem animation so it blocks less of the screen.");
        put("No Pumpkin Blur", "Removes the overlay when wearing a carved pumpkin.");
        put("Low Fire", "Lowers the flame overlay so you can still see while burning.");
        put("Low Shield", "Moves the shield out of the way while blocking.");
        put("No Fog", "Removes fog for a clearer view.");
        put("Clear Water", "Removes the underwater tint.");
        put("Clear Lava", "Makes lava see-through.");
        put("Hand Item Size", "Resizes the item held in your hand.");
        put("Freecam", "Detaches the camera and lets you fly around freely.");
        put("Stash Finder", "Finds unusual accumulations of chests -- useful for locating bases.");
        put("Sus Chunks", "Marks chunks that look suspicious based on their contents.");
        put("Tunnel Detector", "Finds long straight tunnels that were dug by players.");
        put("No Fall", "Prevents fall damage. High ban risk.");
        put("Chunk Borders", "Shows the edges of the chunk you are standing in.");
    }

    private ModuleInfo() {}

    private static void put(String module, String description) {
        DESCRIPTIONS.put(module, description);
    }

    /** Description for a module, or an empty string if none is known. */
    public static String get(String moduleName) {
        String d = DESCRIPTIONS.get(moduleName);
        return (d == null) ? "" : d;
    }
}
