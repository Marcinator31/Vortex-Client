package com.vortex.client.gui.menu;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Verbindung zum Hosting des Launchers (Launcher 2.5+): Hostet der Launcher
 * gerade eine Welt und hat dieses Spiel dafuer gestartet, steht sein
 * Austauschordner in -Dvortex.hosting.dir.
 *
 *   state.json   schreibt der Launcher bei jeder Aenderung (Adresse,
 *                Spieler, Einstellungen).
 *   inbox/*.json legen wir hier ab; der Launcher arbeitet sie alle 500 ms ab
 *                (Einstellungen, Spieleraktionen, Neustart).
 *
 * So braucht "Hosting Options" weder Operator-Befehle noch einen eigenen
 * Netzwerkkanal -- es ist dasselbe wie die Knoepfe im Launcher.
 */
public final class LauncherHosting {
    private LauncherHosting() {}

    public record Spieler(String name, boolean op, boolean host) {}

    public static final class Stand {
        public String status = "off", welt = "", version = "", adresse, lan, netz, host = "";
        public String gamemode = "survival", difficulty = "normal";
        public boolean pvp = true, cheats, whitelist, neustart;
        public int maxPlayers = 8;
        public List<Spieler> spieler = new ArrayList<>();
    }

    private static final Path DIR = pfad();
    private static final AtomicInteger NR = new AtomicInteger();
    private static Stand stand = new Stand();
    private static long gelesen, geaendert = -1;

    private static Path pfad() {
        String p = System.getProperty("vortex.hosting.dir");
        if (p == null || p.isBlank()) return null;
        try { return Path.of(p); } catch (Exception e) { return null; }
    }

    /** Hat der Launcher dieses Spiel fuers Hosting gestartet? */
    public static boolean verfuegbar() {
        return DIR != null;
    }

    /** Laeuft das Hosting gerade (Server an)? */
    public static boolean aktiv() {
        return verfuegbar() && !"off".equals(stand().status);
    }

    /** Aktueller Stand; liest state.json hoechstens alle 250 ms neu. */
    public static synchronized Stand stand() {
        if (DIR == null) return stand;
        long jetzt = System.currentTimeMillis();
        if (jetzt - gelesen < 250) return stand;
        gelesen = jetzt;
        Path f = DIR.resolve("state.json");
        try {
            long mod = Files.getLastModifiedTime(f).toMillis();
            if (mod == geaendert) return stand;
            JsonObject o = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject();
            Stand s = new Stand();
            s.status = str(o, "status", "off");
            s.welt = str(o, "world", "");
            s.version = str(o, "version", "");
            s.adresse = str(o, "address", null);
            s.lan = str(o, "lan", null);
            s.netz = str(o, "network", null);
            s.host = str(o, "host", "");
            s.neustart = o.has("restartNeeded") && o.get("restartNeeded").getAsBoolean();
            if (o.has("settings") && o.get("settings").isJsonObject()) {
                JsonObject e = o.getAsJsonObject("settings");
                s.gamemode = str(e, "gamemode", "survival");
                s.difficulty = str(e, "difficulty", "normal");
                s.pvp = !e.has("pvp") || e.get("pvp").getAsBoolean();
                s.cheats = e.has("cheats") && e.get("cheats").getAsBoolean();
                s.whitelist = e.has("whitelist") && e.get("whitelist").getAsBoolean();
                s.maxPlayers = e.has("maxPlayers") ? e.get("maxPlayers").getAsInt() : 8;
            }
            if (o.has("players") && o.get("players").isJsonArray()) {
                JsonArray a = o.getAsJsonArray("players");
                for (JsonElement el : a) {
                    if (!el.isJsonObject()) continue;
                    JsonObject p = el.getAsJsonObject();
                    s.spieler.add(new Spieler(str(p, "name", "?"), p.has("op") && p.get("op").getAsBoolean(), p.has("host") && p.get("host").getAsBoolean()));
                }
            }
            stand = s;
            geaendert = mod;
        } catch (Exception e) {
            // Datei fehlt (Launcher zu) oder wird gerade geschrieben -- alten Stand behalten
            if (!Files.exists(f)) stand = new Stand();
        }
        return stand;
    }

    private static String str(JsonObject o, String k, String def) {
        return o.has(k) && o.get(k).isJsonPrimitive() ? o.get(k).getAsString() : def;
    }

    /** Einstellung aendern (gamemode, difficulty, pvp, cheats, whitelist). */
    public static void einstellung(String key, Object wert) {
        JsonObject patch = new JsonObject();
        if (wert instanceof Boolean b) patch.addProperty(key, b);
        else if (wert instanceof Number n) patch.addProperty(key, n);
        else patch.addProperty(key, String.valueOf(wert));
        JsonObject m = new JsonObject();
        m.addProperty("type", "settings");
        m.add("patch", patch);
        senden(m);
        // Sofort anzeigen, der Launcher bestaetigt mit dem naechsten state.json
        synchronized (LauncherHosting.class) {
            switch (key) {
                case "gamemode" -> stand.gamemode = String.valueOf(wert);
                case "difficulty" -> stand.difficulty = String.valueOf(wert);
                case "pvp" -> stand.pvp = (Boolean) wert;
                case "cheats" -> stand.cheats = (Boolean) wert;
                case "whitelist" -> stand.whitelist = (Boolean) wert;
                default -> { }
            }
        }
    }

    /** Aktion fuer einen Spieler: kick, ban, op, deop, gamemode:creative ... */
    public static void spieler(String name, String aktion) {
        JsonObject m = new JsonObject();
        m.addProperty("type", "player");
        m.addProperty("name", name);
        m.addProperty("action", aktion);
        senden(m);
    }

    public static void neustart() {
        JsonObject m = new JsonObject();
        m.addProperty("type", "restart");
        senden(m);
    }

    private static void senden(JsonObject m) {
        if (DIR == null) return;
        try {
            Path inbox = DIR.resolve("inbox");
            Files.createDirectories(inbox);
            String name = System.currentTimeMillis() + "-" + NR.incrementAndGet();
            Path tmp = inbox.resolve(name + ".tmp");
            Files.writeString(tmp, m.toString(), StandardCharsets.UTF_8);
            Files.move(tmp, inbox.resolve(name + ".json"), StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception e) {
            com.vortex.client.core.Errors.report("LauncherHosting", e);
        }
    }
}
