package com.vortex.client.gui;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Which of the two marks to show.
 *
 * Blue is the client on its own; red means the Vortex Plus addon is installed.
 * The point is that you can see at a glance which of the two you are running --
 * they behave rather differently, and finding that out the hard way on a server
 * is not the moment for the discovery.
 *
 * The mark in the menu is drawn from code rather than from an image: drawing a
 * texture needs a render pipeline argument in this version, which is one more
 * thing to get wrong for a shape that is two circles. The images are used where
 * they belong -- in the mod list, one per mod.
 *
 * Checked once and remembered. Mods cannot appear or disappear while the game
 * is running, so asking again every frame would be work for nothing.
 */
public final class Branding {

    /** The addon's mod id, as declared in its fabric.mod.json. */
    private static final String ADDON_ID = "vortex_plus_addon";

    private static Boolean addonPresent = null;

    private Branding() {}

    /** Is the addon installed? */
    public static boolean hasAddon() {
        if (addonPresent == null) {
            try {
                addonPresent = FabricLoader.getInstance().isModLoaded(ADDON_ID);
            } catch (Throwable pvpErr) {
                com.vortex.client.core.Errors.report("Branding", pvpErr);
                addonPresent = Boolean.FALSE;
            }
        }
        return addonPresent;
    }

    /** Colour of the mark: red with the addon, blue without. */
    public static int accent() {
        return hasAddon() ? 0xFFFF3B3B : 0xFF2B7BFF;
    }

    /** Name shown in the menu header. */
    public static String title() {
        return hasAddon() ? "VORTEX PLUS" : "VORTEX";
    }
}
