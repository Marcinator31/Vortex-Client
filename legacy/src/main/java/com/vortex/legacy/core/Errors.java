package com.vortex.legacy.core;

import java.util.HashMap;
import java.util.Map;
import com.vortex.legacy.VortexLegacy;

/** Fehler in einem Modul duerfen nie das Spiel abstuerzen lassen: melden (einmal je Stelle) und weiter. */
public final class Errors {
    private Errors() {}
    private static final Map<String, Integer> COUNT = new HashMap<String, Integer>();

    public static synchronized void report(String where, Throwable t) {
        Integer n = COUNT.get(where);
        COUNT.put(where, n == null ? 1 : n + 1);
        if (n == null) VortexLegacy.LOG.error("[Vortex] Error in " + where, t);
    }

    /** Fuer den Test: wie viele Fehler gab es insgesamt? */
    public static synchronized Map<String, Integer> all() { return new HashMap<String, Integer>(COUNT); }
}
