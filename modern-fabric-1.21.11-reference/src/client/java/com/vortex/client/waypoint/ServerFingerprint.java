package com.vortex.client.waypoint;

/**
 * A short fingerprint of the server currently joined.
 *
 * Built from what the join packet tells us: how many dimensions the server
 * offers, its player limit, and whether it is hardcore. Those are stable for a
 * given backend server and differ between most of them — a lobby usually
 * offers one dimension and a small player cap, a survival server three
 * dimensions and a large one.
 *
 * This exists because the world seed alone is not always enough. Many lobby
 * worlds are flat worlds built from the same fixed seed, which makes two
 * different servers look identical. Seed and fingerprint together are far
 * harder to confuse.
 *
 * Deliberately coarse: it is meant to separate servers, not to identify them.
 */
public final class ServerFingerprint {

    private static String current = null;

    private ServerFingerprint() {}

    /** Called when a join packet arrives. */
    public static synchronized void record(int dimensions, int maxPlayers, boolean hardcore) {
        // Player limits are rounded, because some networks report slightly
        // different numbers over time. Rounding keeps the fingerprint stable
        // while still separating a 50-slot lobby from a 2000-slot survival.
        int bucket = (maxPlayers <= 0) ? 0 : Integer.highestOneBit(maxPlayers);
        current = dimensions + "-" + bucket + (hardcore ? "h" : "");
        // The world key is built from this, so it has to be rebuilt now.
        com.vortex.client.hud.WaypointRenderer.invalidateWorldKey();
    }

    /** Current fingerprint, or null if nothing has been recorded yet. */
    public static synchronized String get() {
        return current;
    }

    public static synchronized void clear() {
        current = null;
    }
}
