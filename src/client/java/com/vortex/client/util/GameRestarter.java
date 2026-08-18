package com.vortex.client.util;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.Minecraft;

/**
 * Startet das Spiel neu: baut die Kommandozeile des laufenden Java-Prozesses
 * nach, startet damit einen neuen Prozess und faehrt den aktuellen sauber
 * herunter.
 *
 * Vorgehen:
 *   1) Bevorzugt ueber ProcessHandle (Java 9+): liefert das echte Programm und
 *      die echten Argumente des laufenden Prozesses -- das ist am genauesten.
 *   2) Falls das Betriebssystem die Argumente nicht herausgibt (kommt vor),
 *      bauen wir die Kommandozeile aus JVM-Argumenten, Classpath und
 *      Hauptklasse zusammen.
 *
 * EINSCHRAENKUNG: Manche Launcher starten das Spiel in einer eigenen Umgebung
 * (eigene Argumente, Arbeitsverzeichnis, Session-Token). Dann kann der
 * nachgebaute Start abweichen. Deshalb wird jeder Fehler klar gemeldet, statt
 * das Spiel einfach zu schliessen.
 */
public final class GameRestarter {

    private GameRestarter() {}

    /** Startet einen neuen Spiel-Prozess und beendet den aktuellen. */
    public static void restart() throws Exception {
        List<String> command = buildCommand();
        if (command.isEmpty()) {
            throw new IllegalStateException(
                    "Could not determine the command line");
        }

        ProcessBuilder pb = new ProcessBuilder(command);
        // Im selben Arbeitsverzeichnis starten wie der aktuelle Prozess.
        String dir = System.getProperty("user.dir");
        if (dir != null) pb.directory(new File(dir));
        pb.start();

        // Erst nach erfolgreichem Start herunterfahren (scheduleStop speichert
        // Optionen und schliesst sauber).
        Minecraft client = Minecraft.getInstance();
        if (client != null) client.stop();
    }

    /** Baut die Kommandozeile des laufenden Prozesses nach. */
    private static List<String> buildCommand() {
        // 1) Echte Prozess-Infos (am zuverlaessigsten).
        try {
            ProcessHandle.Info info = ProcessHandle.current().info();
            Optional<String> cmd = info.command();
            Optional<String[]> args = info.arguments();
            if (cmd.isPresent() && args.isPresent()) {
                List<String> list = new ArrayList<>();
                list.add(cmd.get());
                list.addAll(Arrays.asList(args.get()));
                return list;
            }
        } catch (Throwable ignored) {
        }

        // 2) Fallback: aus JVM-Argumenten + Classpath + Hauptklasse bauen.
        List<String> list = new ArrayList<>();
        list.add(javaBinary());
        try {
            list.addAll(java.lang.management.ManagementFactory
                    .getRuntimeMXBean().getInputArguments());
        } catch (Throwable ignored) {
            // java.management nicht verfuegbar -> ohne JVM-Argumente versuchen.
        }
        String cp = System.getProperty("java.class.path");
        if (cp != null && !cp.isEmpty()) {
            list.add("-cp");
            list.add(cp);
        }
        String main = System.getProperty("sun.java.command");
        if (main != null && !main.isEmpty()) {
            list.addAll(Arrays.asList(main.split(" ")));
        }
        return list;
    }

    /** Pfad zur java-Programmdatei der laufenden JVM. */
    private static String javaBinary() {
        String home = System.getProperty("java.home");
        String name = System.getProperty("os.name", "");
        boolean windows = name.toLowerCase(java.util.Locale.ROOT).contains("win");
        return home + File.separator + "bin" + File.separator
                + (windows ? "java.exe" : "java");
    }
}
