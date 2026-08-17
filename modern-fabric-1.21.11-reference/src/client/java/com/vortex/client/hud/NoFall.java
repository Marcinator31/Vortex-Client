package com.vortex.client.hud;

/**
 * No Fall lives entirely in NoFallMixin now.
 *
 * This class used to do the work on the client tick, and that was the problem:
 * the tick runs AFTER the movement packet has gone out. Setting the state there
 * was always too late for the server to see it, and clearing the fall distance
 * quietly defeated the module altogether -- the next tick started counting from
 * zero, a fall covers a block or two per tick, and the three block threshold
 * was therefore never reached.
 *
 * The mixin sits where the packet is built, which is the only moment that
 * decides anything. Nothing is left for this class to do, and it registers no
 * handler -- an empty one running twenty times a second would be pure waste.
 */
public final class NoFall {

    private NoFall() {}

    /** Kept so the call in VortexClientMod stays valid. Does nothing. */
    public static void register() {
        // Intentionally empty -- see the note above.
    }
}
