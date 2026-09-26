package com.vortex.client.community;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

/**
 * Verbindung zur Vortex-Website: Liste lesen, anmelden, Presets hochladen.
 *
 * WAS GESENDET WIRD -- und nur das:
 *  - beim Anmelden Benutzername/E-Mail und Passwort, direkt an die Website
 *    (HTTPS). Das Passwort wird NICHT gespeichert.
 *  - beim Hochladen Name, Beschreibung und die Mod-Einstellungen des Presets.
 *    Keine Makros, Waypoints, Freunde, Tasten, Server oder Kontodaten -- das
 *    Format "nur Mods" (ConfigManager.exportMods) enthaelt sie gar nicht.
 *
 * Nach dem Anmelden gibt die Website ein Sitzungs-Cookie aus (7 Tage gueltig).
 * Nur dieses wird gespeichert (community-session.txt im Config-Ordner), damit
 * man nicht bei jedem Start neu anmelden muss. Abmelden loescht es.
 */
public final class CommunityApi {

    public static final String SITE = "https://vortex-client.onrender.com";

    /**
     * Die Website schlaeft, wenn laenger niemand da war, und braucht zum
     * Aufwachen einen Moment. Genug Zeit dafuer, aber kein ewiges Warten.
     */
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(8);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(25);

    /** Antworten, die viel groesser sind als jede sinnvolle Liste, werden abgelehnt. */
    private static final int MAX_LENGTH = 512 * 1024;

    private static HttpClient client;
    private static volatile String sitzung = null;     // "connect.sid=..."
    private static volatile String benutzer = null;    // angemeldeter Name
    private static boolean geladen = false;

    private CommunityApi() {}

    private static synchronized HttpClient http() {
        if (client == null) {
            client = HttpClient.newBuilder()
                    .connectTimeout(CONNECT_TIMEOUT)
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();
        }
        return client;
    }

    // ------------------------------------------------------------------
    // Lesen
    // ------------------------------------------------------------------

    /** Holt eine URL als Text. @throws Exception bei Fehler oder Fehlercode */
    public static String get(String url) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(READ_TIMEOUT)
                .header("User-Agent", "VortexClient")
                .header("Accept", "application/json")
                .GET();
        String sid = sitzung();
        if (sid != null) b.header("Cookie", sid);
        HttpResponse<String> r = http().send(b.build(), HttpResponse.BodyHandlers.ofString());
        if (r.statusCode() != 200) {
            throw new IllegalStateException("Website answered " + r.statusCode());
        }
        String body = r.body();
        if (body != null && body.length() > MAX_LENGTH) {
            throw new IllegalStateException("Reply far larger than expected");
        }
        return body;
    }

    // ------------------------------------------------------------------
    // Anmelden
    // ------------------------------------------------------------------

    /** Angemeldeter Benutzername, oder null. */
    public static String benutzer() {
        sitzung();
        return benutzer;
    }

    /**
     * Meldet an. Laeuft im Hintergrund-Thread des Aufrufers.
     *
     * @return null bei Erfolg, sonst eine lesbare Fehlermeldung
     */
    public static String anmelden(String name, String passwort) {
        try {
            String json = "{\"username\":" + js(name) + ",\"password\":" + js(passwort) + "}";
            HttpResponse<String> r = post("/api/auth/login", json, false);
            if (r.statusCode() == 200) {
                String sid = null;
                for (String c : r.headers().allValues("set-cookie")) {
                    if (c.startsWith("connect.sid=")) {
                        int semi = c.indexOf(';');
                        sid = semi > 0 ? c.substring(0, semi) : c;
                    }
                }
                if (sid == null) return "The website did not start a session.";
                String n = feld(r.body(), "username");
                sitzung = sid;
                benutzer = n.isEmpty() ? name : n;
                speichern();
                return null;
            }
            String fehler = feld(r.body(), "error");
            if (r.statusCode() == 403) {
                return fehler.isEmpty() ? "Please confirm your e-mail address first." : fehler;
            }
            return fehler.isEmpty() ? "Login failed (" + r.statusCode() + ")." : fehler;
        } catch (Throwable pvpErr) {
            com.vortex.client.core.Errors.report("CommunityApi.anmelden", pvpErr);
            return "Could not reach the website.";
        }
    }

    public static void abmelden() {
        try {
            if (sitzung != null) post("/api/auth/logout", "{}", true);
        } catch (Throwable ignored) {
            // Offline abmelden geht trotzdem: lokal vergessen reicht.
        }
        sitzung = null;
        benutzer = null;
        try {
            Files.deleteIfExists(datei());
        } catch (Throwable ignored) {
        }
    }

    // ------------------------------------------------------------------
    // Hochladen
    // ------------------------------------------------------------------

    /**
     * Laedt ein Preset hoch.
     *
     * @param slot 0..2 -- die Website verlangt den Dateinamen preset1..3.txt
     * @return {@code {"ok", shareCode}} oder {@code {"error", Meldung}}
     */
    public static String[] hochladen(int slot, String name, String beschreibung,
                                     boolean oeffentlich, String inhalt) {
        try {
            if (sitzung() == null) return new String[]{"error", "Please log in first."};
            String json = "{\"name\":" + js(name)
                    + ",\"filename\":" + js("preset" + (slot + 1) + ".txt")
                    + ",\"description\":" + js(beschreibung)
                    + ",\"visibility\":" + js(oeffentlich ? "public" : "unlisted")
                    + ",\"content\":" + js(inhalt) + "}";
            HttpResponse<String> r = post("/api/presets", json, true);
            if (r.statusCode() == 200) {
                return new String[]{"ok", feld(r.body(), "shareCode")};
            }
            if (r.statusCode() == 401) {
                // Sitzung abgelaufen: vergessen, damit die Anmeldung wieder erscheint.
                sitzung = null;
                benutzer = null;
                Files.deleteIfExists(datei());
                return new String[]{"error", "Your login has expired. Please log in again."};
            }
            String fehler = feld(r.body(), "error");
            return new String[]{"error", fehler.isEmpty() ? "Upload failed (" + r.statusCode() + ")." : fehler};
        } catch (Throwable pvpErr) {
            com.vortex.client.core.Errors.report("CommunityApi.hochladen", pvpErr);
            return new String[]{"error", "Could not reach the website."};
        }
    }

    // ------------------------------------------------------------------
    // Hilfen
    // ------------------------------------------------------------------

    private static HttpResponse<String> post(String pfad, String json, boolean mitSitzung) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder()
                .uri(URI.create(SITE + pfad))
                .timeout(READ_TIMEOUT)
                .header("User-Agent", "VortexClient")
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8));
        String sid = mitSitzung ? sitzung() : null;
        if (sid != null) b.header("Cookie", sid);
        return http().send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static Path datei() {
        return com.vortex.client.core.ConfigManager.dataDir().resolve("community-session.txt");
    }

    private static synchronized String sitzung() {
        if (!geladen) {
            geladen = true;
            try {
                Path f = datei();
                if (Files.exists(f)) {
                    String[] z = Files.readString(f, StandardCharsets.UTF_8).split("\n", 2);
                    if (z.length == 2 && z[0].startsWith("connect.sid=")) {
                        sitzung = z[0].trim();
                        benutzer = z[1].trim();
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        return sitzung;
    }

    private static void speichern() {
        try {
            Files.createDirectories(datei().getParent());
            Files.writeString(datei(), sitzung + "\n" + benutzer, StandardCharsets.UTF_8);
        } catch (Throwable pvpErr) {
            com.vortex.client.core.Errors.report("CommunityApi.speichern", pvpErr);
        }
    }

    /** Text als JSON-Zeichenkette, sauber maskiert. */
    public static String js(String s) {
        if (s == null) return "null";
        StringBuilder sb = new StringBuilder(s.length() + 16).append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
            }
        }
        return sb.append('"').toString();
    }

    /** Einfacher Textwert "key":"..." aus einer JSON-Antwort, sonst "". */
    public static String feld(String json, String key) {
        if (json == null) return "";
        int i = json.indexOf("\"" + key + "\"");
        if (i < 0) return "";
        int c = json.indexOf(':', i);
        if (c < 0) return "";
        int q = json.indexOf('"', c + 1);
        if (q < 0) return "";
        StringBuilder sb = new StringBuilder();
        for (int k = q + 1; k < json.length(); k++) {
            char ch = json.charAt(k);
            if (ch == '\\' && k + 1 < json.length()) {
                char n = json.charAt(++k);
                switch (n) {
                    case 'n': sb.append('\n'); break;
                    case 't': sb.append('\t'); break;
                    case 'u':
                        if (k + 4 < json.length()) {
                            try {
                                sb.append((char) Integer.parseInt(json.substring(k + 1, k + 5), 16));
                                k += 4;
                            } catch (NumberFormatException e) {
                                sb.append('u');
                            }
                        }
                        break;
                    default: sb.append(n);
                }
            } else if (ch == '"') {
                break;
            } else {
                sb.append(ch);
            }
        }
        return sb.toString();
    }
}
