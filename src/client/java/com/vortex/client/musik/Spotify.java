package com.vortex.client.musik;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.vortex.client.core.ConfigManager;
import com.vortex.client.core.Errors;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.InetAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Spotify-Konto und Web-API.
 *
 * Anmeldung: "Authorization Code mit PKCE" -- der offizielle Weg fuer Apps,
 * die kein Geheimnis sicher aufbewahren koennen. Es gibt KEINEN Client-Secret
 * im Client. Spotify schickt den Browser nach der Anmeldung an
 * http://127.0.0.1:47819/callback zurueck; dort lauscht kurz ein winziger
 * Web-Server in Minecraft und nimmt den Code an.
 *
 * Gespeichert (config/vortexclient/spotify.json, nur auf diesem PC): Refresh-
 * Token, Anzeigename, ob Premium, ggf. eigene Client-ID. Das Access-Token
 * lebt nur im Speicher (1 Stunde) und wird bei Bedarf erneuert.
 *
 * Spotify-Regeln (Stand 2026): Eine App im "Development Mode" darf von
 * hoechstens 5 eingetragenen Spotify-Konten benutzt werden. Darum kann jeder
 * auch eine EIGENE Spotify-App (Client-ID) eintragen.
 */
public final class Spotify {
    private Spotify() {}

    /** Client-ID der Vortex-App. Leer = noch keine eingetragen (dann eigene Client-ID noetig). */
    public static final String STANDARD_CLIENT_ID = "3f6adff6d83a4df0af6433576d88497b";
    public static final int PORT = 47819;
    public static final String REDIRECT = "http://127.0.0.1:" + PORT + "/callback";
    static final String SCOPES = "user-read-playback-state user-read-currently-playing user-modify-playback-state user-read-private";
    /** Adressen (per -Dvortex.spotify.api / .accounts fuer Tests umstellbar). */
    private static String api() { return System.getProperty("vortex.spotify.api", "https://api.spotify.com/v1"); }
    private static String accounts() { return System.getProperty("vortex.spotify.accounts", "https://accounts.spotify.com"); }
    private static volatile String letzteAnmeldeUrl;

    /** Zuletzt im Browser geoeffnete Anmelde-Adresse (fuer Tests). */
    public static String letzteAnmeldeUrl() { return letzteAnmeldeUrl; }

    public enum Status { AUS, ANMELDEN, VERBUNDEN, FEHLER }

    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL).build();
    static final ExecutorService POOL = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "Vortex-Spotify");
        t.setDaemon(true);
        return t;
    });

    private static volatile Status status = Status.AUS;
    private static volatile String fehler = null;
    private static volatile String eigeneClientId = "";
    private static volatile String refreshToken = null;
    private static volatile String accessToken = null;
    private static volatile long ablauf = 0;
    private static volatile String name = "";
    private static volatile Boolean premium = null;
    /** Spotify bremst (429): bis dahin nichts fragen. */
    private static volatile long gesperrtBis = 0;
    private static volatile ServerSocket warter;
    private static boolean geladen;

    // ------------------------------------------------------------------
    // Zustand
    // ------------------------------------------------------------------

    public static Status status() { laden(); return status; }
    public static boolean verbunden() { return status() == Status.VERBUNDEN; }
    public static String fehler() { return fehler; }
    public static String name() { return name; }
    /** null = unbekannt */
    public static Boolean premium() { return premium; }
    public static String eigeneClientId() { laden(); return eigeneClientId; }
    public static String clientId() {
        laden();
        return !eigeneClientId.isBlank() ? eigeneClientId.trim() : STANDARD_CLIENT_ID;
    }
    public static boolean clientIdDa() { return !clientId().isBlank(); }

    public static void setzeEigeneClientId(String id) {
        String neu = id == null ? "" : id.replaceAll("[^0-9a-fA-F]", "");
        if (neu.equals(eigeneClientId)) return;
        eigeneClientId = neu;
        // Andere App = andere Anmeldung
        abmelden();
    }

    private static Path datei() { return ConfigManager.dataDir().resolve("spotify.json"); }

    private static synchronized void laden() {
        if (geladen) return;
        geladen = true;
        try {
            Path f = datei();
            if (!Files.exists(f)) return;
            JsonObject o = JsonParser.parseString(Files.readString(f)).getAsJsonObject();
            eigeneClientId = text(o, "clientId");
            refreshToken = text(o, "refreshToken");
            name = text(o, "name");
            premium = o.has("premium") ? o.get("premium").getAsBoolean() : null;
            if (refreshToken.isEmpty()) refreshToken = null;
            if (refreshToken != null) status = Status.VERBUNDEN;
        } catch (Throwable t) {
            Errors.report("Spotify.laden", t);
        }
    }

    private static synchronized void speichern() {
        try {
            JsonObject o = new JsonObject();
            o.addProperty("clientId", eigeneClientId);
            if (refreshToken != null) o.addProperty("refreshToken", refreshToken);
            o.addProperty("name", name);
            if (premium != null) o.addProperty("premium", premium);
            Path f = datei();
            Files.createDirectories(f.getParent());
            Path tmp = f.resolveSibling("spotify.json.tmp");
            Files.writeString(tmp, o.toString());
            Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (Throwable t) {
            Errors.report("Spotify.speichern", t);
        }
    }

    public static void abmelden() {
        refreshToken = null;
        accessToken = null;
        ablauf = 0;
        name = "";
        premium = null;
        fehler = null;
        status = Status.AUS;
        speichern();
    }

    // ------------------------------------------------------------------
    // Anmelden (PKCE)
    // ------------------------------------------------------------------

    /** Browser mit der Spotify-Anmeldung oeffnen und auf die Rueckkehr warten. */
    public static void anmelden() {
        laden();
        if (!clientIdDa()) {
            fehler = "No Spotify Client ID yet -- enter one below.";
            status = Status.FEHLER;
            return;
        }
        stoppeWarter();
        try {
            SecureRandom rnd = new SecureRandom();
            byte[] v = new byte[48];
            rnd.nextBytes(v);
            String verifier = Base64.getUrlEncoder().withoutPadding().encodeToString(v);
            String challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(
                    MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)));
            byte[] s = new byte[16];
            rnd.nextBytes(s);
            String state = Base64.getUrlEncoder().withoutPadding().encodeToString(s);
            String cid = clientId();

            ServerSocket srv = new ServerSocket(PORT, 8, InetAddress.getLoopbackAddress());
            warter = srv;
            Thread lauscher = new Thread(() -> {
                while (!srv.isClosed()) {
                    try (Socket c = srv.accept()) {
                        c.setSoTimeout(5000);
                        BufferedReader in = new BufferedReader(new InputStreamReader(c.getInputStream(), StandardCharsets.US_ASCII));
                        String zeile = in.readLine();          // "GET /callback?code=...&state=... HTTP/1.1"
                        if (zeile == null) continue;
                        String[] teile = zeile.split(" ");
                        String pfad = teile.length > 1 ? teile[1] : "/";
                        if (!pfad.startsWith("/callback")) { antworte(c, 404, "Not found"); continue; }
                        int qi = pfad.indexOf('?');
                        Map<String, String> q = query(qi < 0 ? "" : pfad.substring(qi + 1));
                        boolean ok = state.equals(q.get("state")) && q.containsKey("code");
                        antworte(c, 200, ok
                                ? "<h2>Spotify is connected.</h2><p>You can close this tab and go back to Minecraft.</p>"
                                : "<h2>Spotify sign-in did not work.</h2><p>" + escape(q.getOrDefault("error", "Try again in Minecraft.")) + "</p>");
                        if (ok) {
                            POOL.execute(() -> codeEinloesen(cid, q.get("code"), verifier));
                        } else if (state.equals(q.get("state"))) {
                            fehler = "access_denied".equals(q.get("error"))
                                    ? "Spotify refused the sign-in. Only accounts on the app's user list can connect (Spotify Developer Dashboard > User Management, max. 5) -- or use your own Client ID."
                                    : "Spotify: " + q.getOrDefault("error", "sign-in cancelled");
                            status = Status.FEHLER;
                        } else {
                            continue;    // fremde Anfrage (z. B. Favicon): weiter warten
                        }
                        stoppeWarter();
                        return;
                    } catch (Throwable t) {
                        if (srv.isClosed()) return;
                    }
                }
            }, "Vortex-Spotify-Login");
            lauscher.setDaemon(true);
            lauscher.start();
            // Nach 5 Minuten aufgeben
            POOL.execute(() -> {
                try { Thread.sleep(5 * 60_000); } catch (InterruptedException ignored) { }
                if (warter == srv) {
                    stoppeWarter();
                    if (status == Status.ANMELDEN) { status = Status.FEHLER; fehler = "Sign-in timed out. Try again."; }
                }
            });

            String url = accounts() + "/authorize?response_type=code"
                    + "&client_id=" + enc(cid)
                    + "&scope=" + enc(SCOPES)
                    + "&redirect_uri=" + enc(REDIRECT)
                    + "&code_challenge_method=S256&code_challenge=" + enc(challenge)
                    + "&state=" + enc(state);
            status = Status.ANMELDEN;
            fehler = null;
            letzteAnmeldeUrl = url;
            if (!Boolean.getBoolean("vortex.spotify.keinBrowser")) net.minecraft.util.Util.getPlatform().openUri(url);
        } catch (java.net.BindException e) {
            status = Status.FEHLER;
            fehler = "Port " + PORT + " is in use -- close the other Minecraft and try again.";
        } catch (Throwable t) {
            status = Status.FEHLER;
            fehler = "Could not start the Spotify sign-in.";
            Errors.report("Spotify.anmelden", t);
        }
    }

    private static void stoppeWarter() {
        ServerSocket s = warter;
        warter = null;
        if (s != null) { try { s.close(); } catch (Throwable ignored) { } }
    }

    private static void antworte(Socket c, int code, String inhalt) throws java.io.IOException {
        byte[] b = ("<!doctype html><meta charset=utf-8><title>Vortex Client</title><body style=\"font-family:sans-serif;"
                + "background:#0e0b16;color:#f2f0f8;text-align:center;padding-top:15vh\">" + inhalt + "</body>")
                .getBytes(StandardCharsets.UTF_8);
        OutputStream os = c.getOutputStream();
        os.write(("HTTP/1.1 " + code + (code == 200 ? " OK" : " Not Found") + "\r\nContent-Type: text/html; charset=utf-8\r\n"
                + "Content-Length: " + b.length + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
        os.write(b);
        os.flush();
    }

    private static void codeEinloesen(String cid, String code, String verifier) {
        try {
            JsonObject r = token("grant_type=authorization_code&code=" + enc(code) + "&redirect_uri=" + enc(REDIRECT)
                    + "&client_id=" + enc(cid) + "&code_verifier=" + enc(verifier));
            uebernehmen(r);
            profilLaden();
            status = Status.VERBUNDEN;
            fehler = null;
            speichern();
        } catch (Throwable t) {
            status = Status.FEHLER;
            fehler = "Spotify sign-in failed: " + kurz(t);
        }
    }

    private static JsonObject token(String form) throws Exception {
        HttpResponse<String> r = HTTP.send(HttpRequest.newBuilder(URI.create(accounts() + "/api/token"))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form)).build(), HttpResponse.BodyHandlers.ofString());
        JsonObject o = r.body().isBlank() ? new JsonObject() : JsonParser.parseString(r.body()).getAsJsonObject();
        if (r.statusCode() != 200) {
            String e = text(o, "error_description");
            if (e.isEmpty()) e = text(o, "error");
            throw new IllegalStateException(e.isEmpty() ? "HTTP " + r.statusCode() : e);
        }
        return o;
    }

    private static void uebernehmen(JsonObject r) {
        accessToken = text(r, "access_token");
        ablauf = System.currentTimeMillis() + Math.max(60, r.has("expires_in") ? r.get("expires_in").getAsLong() : 3600) * 1000L - 60_000L;
        String neu = text(r, "refresh_token");
        if (!neu.isEmpty()) refreshToken = neu;
    }

    /** Gueltiges Access-Token (erneuert es bei Bedarf). Blockiert -- nur im POOL aufrufen. */
    private static synchronized String zugang() throws Exception {
        laden();
        if (refreshToken == null) throw new IllegalStateException("Not connected to Spotify.");
        if (accessToken != null && System.currentTimeMillis() < ablauf) return accessToken;
        try {
            uebernehmen(token("grant_type=refresh_token&refresh_token=" + enc(refreshToken) + "&client_id=" + enc(clientId())));
            speichern();
            if (premium == null) profilLaden();
            return accessToken;
        } catch (IllegalStateException e) {
            // Abgelaufen (Spotify: Anmeldung gilt seit 2026 hoechstens 6 Monate) oder widerrufen
            String m = String.valueOf(e.getMessage());
            if (m.contains("invalid_grant") || m.toLowerCase().contains("revoked") || m.toLowerCase().contains("invalid refresh")) {
                abmelden();
                status = Status.FEHLER;
                fehler = "Spotify signed you out -- connect again.";
            }
            throw e;
        }
    }

    private static void profilLaden() {
        try {
            Antwort a = aufruf("GET", "/me", null);
            if (a.code == 200 && a.json != null) {
                name = text(a.json, "display_name");
                if (name.isEmpty()) name = text(a.json, "id");
                if (a.json.has("product")) premium = "premium".equalsIgnoreCase(text(a.json, "product"));
            }
        } catch (Throwable ignored) { }
    }

    // ------------------------------------------------------------------
    // Web-API
    // ------------------------------------------------------------------

    /** Antwort eines Aufrufs: HTTP-Code, JSON (oder null), Spotify-Grund (z. B. PREMIUM_REQUIRED). */
    public record Antwort(int code, JsonObject json, String grund) {
        public boolean ok() { return code >= 200 && code < 300; }
        /** Spotifys eigene Fehlermeldung (error.message), oder "". */
        public String nachricht() {
            try {
                if (json != null && json.has("error")) {
                    var e = json.get("error");
                    if (e.isJsonObject()) return text(e.getAsJsonObject(), "message");
                    if (e.isJsonPrimitive()) return e.getAsString();
                }
            } catch (Throwable ignored) { }
            return "";
        }
    }

    /** Blockierender Aufruf (nur im POOL). */
    static Antwort aufruf(String methode, String pfad, String body) throws Exception {
        if (System.currentTimeMillis() < gesperrtBis) return new Antwort(429, null, "RATE_LIMITED");
        String tok = zugang();
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(api() + pfad)).timeout(Duration.ofSeconds(12))
                .header("Authorization", "Bearer " + tok);
        HttpRequest.BodyPublisher pub = body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body);
        if (body != null) b.header("Content-Type", "application/json");
        switch (methode) {
            case "GET" -> b.GET();
            case "PUT" -> b.PUT(pub);
            case "POST" -> b.POST(pub);
            default -> b.method(methode, pub);
        }
        HttpResponse<String> r = HTTP.send(b.build(), HttpResponse.BodyHandlers.ofString());
        if (r.statusCode() == 401) {           // Token kurz vor Ablauf ungueltig: einmal erneuern
            synchronized (Spotify.class) { accessToken = null; }
            return aufrufNochmal(methode, pfad, body);
        }
        return antwort(r);
    }

    private static Antwort aufrufNochmal(String methode, String pfad, String body) throws Exception {
        String tok = zugang();
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(api() + pfad)).timeout(Duration.ofSeconds(12))
                .header("Authorization", "Bearer " + tok);
        HttpRequest.BodyPublisher pub = body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body);
        if (body != null) b.header("Content-Type", "application/json");
        b.method(methode, methode.equals("GET") ? HttpRequest.BodyPublishers.noBody() : pub);
        return antwort(HTTP.send(b.build(), HttpResponse.BodyHandlers.ofString()));
    }

    private static Antwort antwort(HttpResponse<String> r) {
        JsonObject j = null;
        String grund = "";
        try {
            if (r.body() != null && !r.body().isBlank()) {
                JsonElement e = JsonParser.parseString(r.body());
                if (e.isJsonObject()) j = e.getAsJsonObject();
            }
        } catch (Throwable ignored) { }
        if (j != null && j.has("error") && j.get("error").isJsonObject()) grund = text(j.getAsJsonObject("error"), "reason");
        if (r.statusCode() == 429) {
            long s = r.headers().firstValueAsLong("Retry-After").orElse(5);
            gesperrtBis = System.currentTimeMillis() + Math.min(600, Math.max(1, s)) * 1000L;
            if (grund.isEmpty() && j != null) grund = text(j, "reason");
        }
        if (r.statusCode() == 403 && "PREMIUM_REQUIRED".equals(grund)) premium = false;
        return new Antwort(r.statusCode(), j, grund);
    }

    /** Asynchron (im POOL); Fehler werden zur Antwort mit Code 0. */
    public static CompletableFuture<Antwort> async(String methode, String pfad, String body) {
        return CompletableFuture.supplyAsync(() -> {
            try { return aufruf(methode, pfad, body); }
            catch (Throwable t) { return new Antwort(0, null, kurz(t)); }
        }, POOL);
    }

    // --- Bequeme Aufrufe -------------------------------------------------

    public static CompletableFuture<Antwort> abspielen(String trackId, long positionMs) {
        JsonObject o = new JsonObject();
        JsonArray u = new JsonArray();
        u.add("spotify:track:" + trackId);
        o.add("uris", u);
        o.addProperty("position_ms", Math.max(0, positionMs));
        return async("PUT", "/me/player/play", o.toString());
    }

    /** Eigene Musik von vorher fortsetzen (Kontext = Playlist/Album, sonst nur der Song). */
    public static CompletableFuture<Antwort> fortsetzen(String kontextUri, String trackId, long positionMs) {
        JsonObject o = new JsonObject();
        if (kontextUri != null && !kontextUri.isBlank()) {
            o.addProperty("context_uri", kontextUri);
            JsonObject off = new JsonObject();
            off.addProperty("uri", "spotify:track:" + trackId);
            o.add("offset", off);
        } else {
            JsonArray u = new JsonArray();
            u.add("spotify:track:" + trackId);
            o.add("uris", u);
        }
        o.addProperty("position_ms", Math.max(0, positionMs));
        return async("PUT", "/me/player/play", o.toString());
    }

    public static CompletableFuture<Antwort> weiter() { return async("PUT", "/me/player/play", null); }
    public static CompletableFuture<Antwort> pause() { return async("PUT", "/me/player/pause", null); }
    public static CompletableFuture<Antwort> naechster() { return async("POST", "/me/player/next", null); }
    public static CompletableFuture<Antwort> vorheriger() { return async("POST", "/me/player/previous", null); }
    public static CompletableFuture<Antwort> lautstaerke(int prozent) {
        return async("PUT", "/me/player/volume?volume_percent=" + Math.max(0, Math.min(100, prozent)), null);
    }
    public static CompletableFuture<Antwort> springen(long positionMs) {
        return async("PUT", "/me/player/seek?position_ms=" + Math.max(0, positionMs), null);
    }

    /** Song suchen (fuer Mithoeren bei Spielern ohne Spotify-Anmeldung: nur Titel + Kuenstler bekannt). */
    public static CompletableFuture<String> suche(String titel, String kuenstler) {
        String q = "track:" + titel + (kuenstler == null || kuenstler.isBlank() ? "" : " artist:" + kuenstler);
        return async("GET", "/search?type=track&limit=1&q=" + enc(q), null).thenApply(a -> {
            try {
                JsonArray items = a.json().getAsJsonObject("tracks").getAsJsonArray("items");
                return items.isEmpty() ? null : text(items.get(0).getAsJsonObject(), "id");
            } catch (Throwable t) {
                return null;
            }
        });
    }

    /** Fehler fuer die Anzeige */
    public static String grundText(Antwort a) {
        if (a == null) return "No answer from Spotify.";
        if ("PREMIUM_REQUIRED".equals(a.grund())) return "Spotify only allows this with Premium.";
        if ("NO_ACTIVE_DEVICE".equals(a.grund()) || a.code() == 404) return "Open Spotify on your PC or phone first (play anything once).";
        if (a.code() == 429) return "Spotify says: too many requests -- wait a moment.";
        if (a.code() == 403) {
            String m = a.nachricht(), kl = m.toLowerCase(java.util.Locale.ROOT);
            if (kl.contains("premium")) return "Spotify only allows controlling playback with Premium.";
            if (kl.contains("not registered") || kl.contains("developer") || kl.contains("user may not"))
                return "This Spotify account is not on the app's user list (Spotify Developer Dashboard > User Management, max. 5).";
            return "Spotify refused it" + (m.isEmpty() ? "." : ": " + m);
        }
        if (a.code() == 0) return a.grund();
        return "Spotify error " + a.code() + (a.grund().isEmpty() ? "" : " (" + a.grund() + ")");
    }

    // ------------------------------------------------------------------

    static String text(JsonObject o, String k) {
        return o != null && o.has(k) && o.get(k).isJsonPrimitive() ? o.get(k).getAsString() : "";
    }

    static String enc(String s) { return URLEncoder.encode(s, StandardCharsets.UTF_8); }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static Map<String, String> query(String raw) {
        Map<String, String> m = new HashMap<>();
        if (raw == null) return m;
        for (String p : raw.split("&")) {
            int i = p.indexOf('=');
            if (i <= 0) continue;
            m.put(URLDecoder.decode(p.substring(0, i), StandardCharsets.UTF_8), URLDecoder.decode(p.substring(i + 1), StandardCharsets.UTF_8));
        }
        return m;
    }

    static String kurz(Throwable t) {
        Throwable c = t;
        while (c.getCause() != null && c.getCause() != c) c = c.getCause();
        String m = c.getMessage();
        return m == null || m.isBlank() ? c.getClass().getSimpleName() : (m.length() > 120 ? m.substring(0, 120) : m);
    }
}
