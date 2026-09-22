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

        // --- 1) Argumentdatei statt Kommandozeile ---------------------------
        //
        // HIER LAG DER ABSTURZ.
        //
        // Ein Fabric-Classpath ist laenger als die Windows-Grenze von 32767
        // Zeichen fuer eine Kommandozeile. Der neue Prozess bekam einen
        // abgeschnittenen Classpath, fand seine Hauptklasse nicht ("Fehler:
        // Hauptklasse konnte nicht gefunden werden") und starb.
        //
        // Java liest Argumente seit Version 9 auch aus einer Datei: "java
        // @datei". Dort gibt es keine Laengengrenze. Also wird alles ausser
        // dem Programmpfad in eine Datei geschrieben.
        // NICHT "java" nennen: eine Variable dieses Namens verdeckt das
        // Paket java. Dann liest der Compiler "java.nio.file.Files" weiter
        // unten als Feld "nio" dieser Zeichenkette -- Build-Fehler. Genau das
        // war der Fehler in 3.4.0. Gleicher Name wie in der reparierten JAR.
        String javaExecutable = command.get(0);
        List<String> rest = command.subList(1, command.size());
        File argDatei = File.createTempFile("vortex-restart-", ".args");
        argDatei.deleteOnExit();
        StringBuilder b = new StringBuilder();
        for (String arg : rest) {
            // Anfuehrungszeichen und Backslashes maskieren -- Pfade unter
            // Windows enthalten beides, und ohne Maskierung liest Java sie
            // falsch ein.
            b.append('"').append(arg.replace("\\", "\\\\").replace("\"", "\\\""))
             .append('"').append(System.lineSeparator());
        }
        java.nio.file.Files.writeString(argDatei.toPath(), b.toString());

        ProcessBuilder pb = new ProcessBuilder(javaExecutable, "@" + argDatei.getAbsolutePath());
        String dir = System.getProperty("user.dir");
        if (dir != null) pb.directory(new File(dir));
        // Ausgabe des neuen Prozesses verwerfen, sonst blockiert er, sobald
        // der Puffer voll ist -- der alte Prozess liest sie ja nicht mehr.
        pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        pb.redirectError(ProcessBuilder.Redirect.DISCARD);
        Process neu = pb.start();

        // --- 2) Erst pruefen, DANN beenden -----------------------------------
        //
        // Vorher wurde das laufende Spiel sofort beendet, egal ob der neue
        // Prozess ueberlebte. Starb er, war danach gar nichts mehr da -- genau
        // dein Absturz.
        //
        // Jetzt: drei Sekunden warten. Lebt der neue Prozess dann noch, hat er
        // seine Hauptklasse gefunden und laedt. Ist er tot, bleibt das alte
        // Spiel offen und meldet den Fehler, statt sich wegzuwerfen.
        boolean beendet = neu.waitFor(3, java.util.concurrent.TimeUnit.SECONDS);
        if (beendet) {
            throw new IllegalStateException(
                    "Neustart fehlgeschlagen (Rueckgabe " + neu.exitValue()
                    + ") -- das laufende Spiel bleibt offen.");
        }

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
