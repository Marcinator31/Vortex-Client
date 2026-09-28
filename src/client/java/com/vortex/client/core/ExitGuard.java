package com.vortex.client.core;

/**
 * Sorgt dafuer, dass Java nach dem Schliessen von Minecraft wirklich endet.
 *
 * Minecraft beendet sich nicht mit System.exit: Java stoppt erst, wenn kein
 * "normaler" (nicht-Daemon-)Thread mehr laeuft. Laesst irgendeine Mod einen
 * solchen Thread zurueck -- oder haengt das Herunterfahren --, bleibt der
 * Prozess unsichtbar im Hintergrund. Er haelt dann alle Mod-Jars offen:
 * Windows meldet "Datei ist in OpenJDK Platform binary geoeffnet", alte
 * Vortex-Dateien lassen sich nicht mehr entfernen und sammeln sich an.
 *
 * Ablauf (ein Daemon-Thread, stoert also selbst nichts):
 *  1. Warten, bis der Hauptthread von Minecraft fertig ist (hoechstens 3 min
 *     -- das Speichern einer Welt dauert normal Sekunden).
 *  2. Dann 5 s Gnadenfrist. Laeuft Java danach noch: die schuldigen Threads
 *     ins Log schreiben und System.exit(0).
 *  3. Haengt selbst das (ein Shutdown-Hook blockiert), nach 10 s hart beenden.
 */
public final class ExitGuard {

    private ExitGuard() {}

    private static volatile boolean gestartet = false;

    public static void start() {
        if (gestartet) return;
        gestartet = true;
        final Thread haupt = Thread.currentThread();
        Thread t = new Thread(() -> {
            try {
                haupt.join(180_000);
                if (haupt.isAlive()) {
                    System.out.println("[vortexclient] Minecraft did not finish shutting down within 3 minutes -- ending Java.");
                    threadsLoggen();
                } else {
                    Thread.sleep(5_000);
                    System.out.println("[vortexclient] Minecraft has stopped but Java is still running -- ending it.");
                    threadsLoggen();
                }
                Thread hart = new Thread(() -> {
                    try { Thread.sleep(10_000); } catch (InterruptedException ignored) { }
                    Runtime.getRuntime().halt(0);
                }, "vortex-exit-halt");
                hart.setDaemon(true);
                hart.start();
                System.exit(0);
            } catch (InterruptedException ignored) {
            }
        }, "vortex-exit-guard");
        t.setDaemon(true);
        t.start();
    }

    /** Welche Nicht-Daemon-Threads halten Java am Leben? Fuer die Fehlersuche. */
    private static void threadsLoggen() {
        try {
            for (Thread th : Thread.getAllStackTraces().keySet()) {
                if (th.isDaemon() || th == Thread.currentThread() || !th.isAlive()) continue;
                System.out.println("[vortexclient]   still running: \"" + th.getName() + "\" (" + th.getClass().getName() + ")");
            }
        } catch (Throwable ignored) {
        }
    }
}
