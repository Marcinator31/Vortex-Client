package com.vortex.client.hud;

import com.vortex.client.module.ModuleManager;
import com.vortex.client.module.modules.SessionStatsModule;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.player.LocalPlayer;

/**
 * Sammelt die Werte fuer die Session-Statistik.
 *
 * Alles wird aus dem beobachtet, was der Client ohnehin weiss:
 *   - Spielzeit: seit dem Start des Spiels
 *   - Tode: Uebergang von "lebendig" zu "tot"
 *   - eigene Totems: wenn die Anzahl der Totems im Inventar sinkt, waehrend die
 *     Lebenspunkte gerade wieder aufgefuellt wurden
 *   - hoechste Klickrate: Hoechstwert des vorhandenen CPS-Zaehlers
 */
public final class SessionStats {

    private static final long START = System.currentTimeMillis();

    private static int deaths = 0;
    private static int ownTotems = 0;
    private static int maxCps = 0;

    private static boolean wasAlive = true;
    private static int lastTotemCount = -1;

    private SessionStats() {}

    public static int getDeaths() { return deaths; }
    public static int getOwnTotems() { return ownTotems; }
    public static int getMaxCps() { return maxCps; }

    /** Spielzeit als "1h 23m" oder "23m 04s". */
    public static String playtime() {
        long sec = (System.currentTimeMillis() - START) / 1000L;
        long h = sec / 3600;
        long m = (sec % 3600) / 60;
        long s = sec % 60;
        if (h > 0) return h + "h " + m + "m";
        return m + "m " + String.format(java.util.Locale.ROOT, "%02d", s) + "s";
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            long pvpT0 = System.nanoTime();
            try {
            // Hoechste Klickrate immer mitfuehren (kostet nichts).
            int cps = CpsCounter.LEFT.getCps();
            if (cps > maxCps) maxCps = cps;

            SessionStatsModule mod =
                    ModuleManager.INSTANCE.get(SessionStatsModule.class);
            if (mod == null || !mod.isEnabled()) return;
            if (client.player == null) {
                wasAlive = true;
                lastTotemCount = -1;
                return;
            }

            LocalPlayer self = client.player;

            // Tod erkennen: Uebergang lebendig -> tot (nicht jeden Tick zaehlen).
            boolean alive = self.isAlive();
            if (wasAlive && !alive) deaths++;
            wasAlive = alive;

            // Eigene Totems: Bestand im Blick behalten und Rueckgang zaehlen.
            int totems = countTotems(self);
            if (lastTotemCount >= 0 && totems < lastTotemCount) {
                ownTotems += (lastTotemCount - totems);
            }
            lastTotemCount = totems;
                    } finally {
                com.vortex.client.core.Profiler.record("SessionStats",
                        System.nanoTime() - pvpT0);
            }
        });
    }

    /** Zaehlt Totems in Inventar und Nebenhand. */
    private static int countTotems(LocalPlayer self) {
        int n = 0;
        try {
            var inv = self.getInventory();
            for (int i = 0; i < inv.getContainerSize(); i++) {
                var stack = inv.getItem(i);
                if (stack != null && !stack.isEmpty()
                        && stack.is(net.minecraft.world.item.Items.TOTEM_OF_UNDYING)) {
                    n += stack.getCount();
                }
            }
            var off = self.getOffhandItem();
            if (off != null && !off.isEmpty()
                    && off.is(net.minecraft.world.item.Items.TOTEM_OF_UNDYING)) {
                n += off.getCount();
            }
        } catch (Throwable ignored) {
        }
        return n;
    }
}
