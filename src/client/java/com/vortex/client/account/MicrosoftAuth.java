package com.vortex.client.account;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.function.Consumer;

/**
 * Echter Microsoft-Login ueber den OAuth2 Device Code Flow.
 *
 * Ablauf:
 *   1) Device-Code anfordern -> der Nutzer bekommt eine URL + einen Code.
 *   2) Nutzer loggt sich im Browser ein (microsoft.com/link, Code eingeben).
 *   3) Wir pollen, bis Microsoft uns einen Token gibt.
 *   4) MS-Token -> Xbox Live -> XSTS -> Minecraft-Token -> Profil.
 *
 * WICHTIG -- Client-ID: Ein Microsoft-Login aus einer Dritt-App heraus braucht
 * eine EIGENE, bei Azure registrierte Client-ID (Public Client, "Allow public
 * client flows" = Yes, Consumers-Tenant, Scope XboxLive.signin offline_access).
 * Die alte "00000000402b5328" funktioniert NICHT mit dem v2.0-Device-Code-
 * Endpoint -> dann erscheint gar kein Login.
 *
 * Damit man zum Testen nicht jedes Mal neu bauen muss, wird die Client-ID aus
 * einer Textdatei gelesen:  <spielordner>/config/vortexclient-clientid.txt
 * Steht dort eine gueltige ID, wird sie benutzt; sonst der Fallback unten.
 */
public final class MicrosoftAuth {

    // Fallback-Client-ID (die alte Launcher-ID -- funktioniert i.d.R. NICHT mit
    // dem Device-Code-Endpoint; bitte eigene Azure-ID in die Textdatei eintragen).
    private static final String FALLBACK_CLIENT_ID = "00000000402b5328";

    // Zwischengespeicherte, tatsaechlich benutzte Client-ID.
    private static volatile String cachedClientId = null;

    private static final org.slf4j.Logger LOGGER =
            org.slf4j.LoggerFactory.getLogger("pvpclient-auth");

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .build();

    private MicrosoftAuth() {}

    /**
     * Liest die Client-ID aus <config>/vortexclient-clientid.txt. Existiert die
     * Datei nicht, wird eine Vorlage mit Anleitung angelegt und die Fallback-ID
     * benutzt. So kann man die eigene Azure-ID eintragen, ohne neu zu bauen.
     */
    private static String clientId() {
        String cached = cachedClientId;
        if (cached != null) return cached;

        String id = FALLBACK_CLIENT_ID;
        try {
            Path cfg = net.fabricmc.loader.api.FabricLoader.getInstance()
                    .getConfigDir().resolve("vortexclient-clientid.txt");
            if (Files.exists(cfg)) {
                for (String line : Files.readAllLines(cfg, StandardCharsets.UTF_8)) {
                    String t = line.trim();
                    if (t.isEmpty() || t.startsWith("#")) continue;
                    id = t; // erste echte Zeile = Client-ID
                    break;
                }
            } else {
                // Vorlage anlegen, damit der Nutzer weiss wohin mit der ID.
                String tmpl = ""
                        + "# EIGENE AZURE-CLIENT-ID EINTRAGEN\n"
                        + "#\n"
                        + "# Ohne eigene ID lehnt Microsoft den Login ab mit:\n"
                        + "#   AADSTS700016: Application ... was not found in the directory\n"
                        + "# Das ist kein Fehler des Clients -- jede Dritt-App braucht eine eigene ID.\n"
                        + "#\n"
                        + "# So bekommst du eine (kostenlos, etwa 5 Minuten):\n"
                        + "#  1. portal.azure.com oeffnen und anmelden\n"
                        + "#  2. Oben nach 'App registrations' suchen -> 'New registration'\n"
                        + "#  3. Name frei waehlen. Bei 'Supported account types':\n"
                        + "#     'Personal Microsoft accounts only' waehlen\n"
                        + "#  4. Nach dem Erstellen links 'Authentication' oeffnen:\n"
                        + "#     - 'Add a platform' -> 'Mobile and desktop applications'\n"
                        + "#     - Redirect-URI ankreuzen:\n"
                        + "#       https://login.microsoftonline.com/common/oauth2/nativeclient\n"
                        + "#     - Ganz unten 'Allow public client flows' auf 'Yes' -> Speichern\n"
                        + "#  5. Links 'Overview' -> 'Application (client) ID' kopieren\n"
                        + "#  6. Diese ID unten in eine eigene Zeile schreiben (statt der alten)\n"
                        + "#\n"
                        + FALLBACK_CLIENT_ID + "\n";
                Files.writeString(cfg, tmpl, StandardCharsets.UTF_8);
            }
        } catch (Throwable pvpErr) {
                com.vortex.client.core.Errors.report("MicrosoftAuth", pvpErr);
            }
        cachedClientId = id;
        return id;
    }

    /** Info, die dem Nutzer angezeigt wird, damit er sich einloggen kann. */
    public static final class DeviceCode {
        public final String userCode;
        public final String verificationUri;
        DeviceCode(String userCode, String verificationUri) {
            this.userCode = userCode;
            this.verificationUri = verificationUri;
        }
    }

    /**
     * Fuehrt den kompletten Login durch. onCode wird aufgerufen, sobald der
     * Geraetecode da ist (zum Anzeigen). Gibt am Ende einen fertigen Account
     * zurueck. Blockiert -> in eigenem Thread aufrufen.
     */
    public static Account login(Consumer<DeviceCode> onCode) throws Exception {
        // --- Schritt 1: Device-Code anfordern ---
        String body = "client_id=" + enc(clientId())
                + "&scope=" + enc("XboxLive.signin offline_access");
        LOGGER.info("[pvpclient] Device-Code anfordern, client_id={}", clientId());
        JsonObject dc = postForm(
                "https://login.microsoftonline.com/consumers/oauth2/v2.0/devicecode",
                body);
        LOGGER.info("[pvpclient] Device-Code erhalten");

        String deviceCode = dc.get("device_code").getAsString();
        String userCode = dc.get("user_code").getAsString();
        String verUri = dc.has("verification_uri")
                ? dc.get("verification_uri").getAsString()
                : dc.get("verification_uri_complete").getAsString();
        int interval = dc.has("interval") ? dc.get("interval").getAsInt() : 5;

        // GUI ueber den Code informieren.
        onCode.accept(new DeviceCode(userCode, verUri));

        // --- Schritt 2: Auf den Token pollen ---
        String msAccessToken = null;
        long deadline = System.currentTimeMillis() + 15 * 60 * 1000L; // 15 min
        while (System.currentTimeMillis() < deadline) {
            Thread.sleep(Math.max(interval, 1) * 1000L);

            String pollBody = "grant_type=" + enc("urn:ietf:params:oauth:grant-type:device_code")
                    + "&client_id=" + enc(clientId())
                    + "&device_code=" + enc(deviceCode);
            JsonObject poll = postFormAllowError(
                    "https://login.microsoftonline.com/consumers/oauth2/v2.0/token",
                    pollBody);

            if (poll.has("access_token")) {
                msAccessToken = poll.get("access_token").getAsString();
                break;
            }
            if (poll.has("error")) {
                String err = poll.get("error").getAsString();
                // authorization_pending / slow_down -> weiter warten.
                if (err.equals("authorization_pending") || err.equals("slow_down")) {
                    if (err.equals("slow_down")) interval += 5;
                    continue;
                }
                // Alles andere ist ein echter Fehler.
                throw new RuntimeException("Microsoft-Login abgebrochen: " + err);
            }
        }
        if (msAccessToken == null) {
            throw new RuntimeException("Zeitueberschreitung beim Microsoft-Login.");
        }

        return finishLogin(msAccessToken, false);
    }

    /**
     * Gemeinsamer Teil beider Login-Wege: Xbox Live -> XSTS -> Minecraft ->
     * Profil. Bekommt nur den fertigen Microsoft-Token.
     */
    private static Account finishLogin(String msAccessToken, boolean legacyTicket)
            throws Exception {
        // --- Schritt 3: Xbox Live (XBL) ---
        JsonObject xblReq = new JsonObject();
        JsonObject xblProps = new JsonObject();
        xblProps.addProperty("AuthMethod", "RPS");
        xblProps.addProperty("SiteName", "user.auth.xboxlive.com");
        // RpsTicket-Format haengt vom Login-Weg ab:
        //   - moderner Weg (login.microsoftonline.com): "d=" + Token
        //   - klassischer Weg (login.live.com, MBI_SSL): Token pur
        xblProps.addProperty("RpsTicket",
                legacyTicket ? msAccessToken : ("d=" + msAccessToken));
        xblReq.add("Properties", xblProps);
        xblReq.addProperty("RelyingParty", "http://auth.xboxlive.com");
        xblReq.addProperty("TokenType", "JWT");
        JsonObject xbl = postJson("https://user.auth.xboxlive.com/user/authenticate",
                xblReq.toString());
        String xblToken = xbl.get("Token").getAsString();
        String uhs = xbl.getAsJsonObject("DisplayClaims")
                .getAsJsonArray("xui").get(0).getAsJsonObject()
                .get("uhs").getAsString();

        // --- Schritt 4: XSTS ---
        JsonObject xstsReq = new JsonObject();
        JsonObject xstsProps = new JsonObject();
        xstsProps.addProperty("SandboxId", "RETAIL");
        JsonArray tokens = new JsonArray();
        tokens.add(xblToken);
        xstsProps.add("UserTokens", tokens);
        xstsReq.add("Properties", xstsProps);
        xstsReq.addProperty("RelyingParty", "rp://api.minecraftservices.com/");
        xstsReq.addProperty("TokenType", "JWT");
        JsonObject xsts = postJsonAllowError(
                "https://xsts.auth.xboxlive.com/xsts/authorize", xstsReq.toString());
        if (xsts.has("XErr")) {
            long xerr = xsts.get("XErr").getAsLong();
            throw new RuntimeException(xstsErrorMessage(xerr));
        }
        String xstsToken = xsts.get("Token").getAsString();

        // --- Schritt 5: Minecraft-Login ---
        JsonObject mcReq = new JsonObject();
        mcReq.addProperty("identityToken", "XBL3.0 x=" + uhs + ";" + xstsToken);
        JsonObject mc = postJson(
                "https://api.minecraftservices.com/authentication/login_with_xbox",
                mcReq.toString());
        String mcToken = mc.get("access_token").getAsString();

        // --- Schritt 6: Profil holen ---
        HttpRequest profReq = HttpRequest.newBuilder()
                .uri(URI.create("https://api.minecraftservices.com/minecraft/profile"))
                .header("Authorization", "Bearer " + mcToken)
                .GET().build();
        HttpResponse<String> profResp = HTTP.send(profReq,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        JsonObject profile = JsonParser.parseString(profResp.body()).getAsJsonObject();
        if (!profile.has("id")) {
            throw new RuntimeException(
                "Kein Minecraft-Profil gefunden (besitzt dieser Account das Spiel?).");
        }
        String uuid = profile.get("id").getAsString();
        String name = profile.get("name").getAsString();

        // Fertiger Account mit echtem Token.
        Account acc = new Account(name, formatUuid(uuid));
        acc.accessToken = mcToken;
        return acc;
    }

    // ---- Klassischer Browser-Login (login.live.com) ----
    //
    // Dieser Weg funktioniert mit der alten Minecraft-Client-ID und braucht KEINE
    // eigene Azure-Registrierung -- anders als der Device-Code-Flow oben.
    //
    // Ablauf:
    //   1) Browser oeffnet die Login-Seite (getAuthUrl()).
    //   2) Nach dem Login landet der Browser auf einer leeren Seite; in deren
    //      Adresszeile steht ein "code=..." Parameter.
    //   3) Der Nutzer kopiert diese komplette Adresse in den Client.
    //   4) loginWithCode() tauscht den Code gegen einen Token und macht weiter.

    private static final String LEGACY_CLIENT_ID = "00000000402b5328";
    private static final String LEGACY_REDIRECT =
            "https://login.live.com/oauth20_desktop.srf";

    /** Adresse der Microsoft-Login-Seite fuer den Browser-Weg. */
    public static String getAuthUrl() {
        return "https://login.live.com/oauth20_authorize.srf"
                + "?client_id=" + enc(LEGACY_CLIENT_ID)
                + "&response_type=code"
                + "&scope=" + enc("service::user.auth.xboxlive.com::MBI_SSL")
                + "&redirect_uri=" + enc(LEGACY_REDIRECT);
    }

    /**
     * Loest den aus dem Browser kopierten Code (oder die ganze Adresse) ein und
     * liefert den fertigen Account.
     */
    public static Account loginWithCode(String pasted) throws Exception {
        String code = extractCode(pasted);
        if (code == null || code.isEmpty()) {
            throw new RuntimeException(
                    "Kein Code gefunden. Bitte die komplette Adresse aus dem "
                    + "Browser einfuegen (sie enthaelt 'code=').");
        }
        LOGGER.info("[pvpclient] Tausche Browser-Code gegen Token");

        String body = "client_id=" + enc(LEGACY_CLIENT_ID)
                + "&code=" + enc(code)
                + "&grant_type=authorization_code"
                + "&redirect_uri=" + enc(LEGACY_REDIRECT);
        JsonObject tok = postForm("https://login.live.com/oauth20_token.srf", body);
        if (!tok.has("access_token")) {
            throw new RuntimeException("Microsoft lieferte keinen Token zurueck.");
        }
        String msToken = tok.get("access_token").getAsString();
        LOGGER.info("[pvpclient] Token erhalten, fahre mit Xbox Live fort");
        // MBI_SSL -> RpsTicket ohne "d=" Praefix.
        return finishLogin(msToken, true);
    }

    /** Holt den code-Parameter aus einer eingefuegten Adresse (oder nimmt sie pur). */
    private static String extractCode(String input) {
        if (input == null) return null;
        String t = input.trim();
        int i = t.indexOf("code=");
        if (i < 0) return t.isEmpty() ? null : t; // evtl. wurde nur der Code eingefuegt
        String rest = t.substring(i + 5);
        int amp = rest.indexOf('&');
        if (amp >= 0) rest = rest.substring(0, amp);
        int hash = rest.indexOf('#');
        if (hash >= 0) rest = rest.substring(0, hash);
        return rest;
    }

    // ---- HTTP-Helfer ----

    private static JsonObject postForm(String url, String body) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> resp = HTTP.send(req,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        String raw = resp.body();
        JsonObject o;
        try {
            o = JsonParser.parseString(raw).getAsJsonObject();
        } catch (Throwable parseErr) {
            String snippet = raw == null ? "(leer)"
                    : raw.substring(0, Math.min(raw.length(), 180));
            throw new RuntimeException("HTTP " + resp.statusCode()
                    + " ohne JSON von " + shortHost(url) + ": " + snippet);
        }
        // Fehler ODER kein 2xx-Status -> aussagekraeftig melden (statt spaeter NPE).
        if (o.has("error") || resp.statusCode() / 100 != 2) {
            String err = o.has("error") ? o.get("error").getAsString()
                    : ("HTTP " + resp.statusCode());
            String desc = o.has("error_description")
                    ? o.get("error_description").getAsString() : "";
            throw new RuntimeException("Microsoft (" + shortHost(url) + "): "
                    + err + (desc.isEmpty() ? "" : " - " + desc));
        }
        return o;
    }

    /** Kurzer Host-Name fuer verstaendliche Fehlermeldungen. */
    private static String shortHost(String url) {
        try { return URI.create(url).getHost(); }
        catch (Throwable t) { return url; }
    }

    private static JsonObject postFormAllowError(String url, String body) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> resp = HTTP.send(req,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        try {
            return JsonParser.parseString(resp.body()).getAsJsonObject();
        } catch (Throwable t) {
            JsonObject err = new JsonObject();
            err.addProperty("error", "invalid_response");
            return err;
        }
    }

    private static JsonObject postJson(String url, String json) throws Exception {
        JsonObject o = postJsonAllowError(url, json);
        return o;
    }

    private static JsonObject postJsonAllowError(String url, String json) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> resp = HTTP.send(req,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        return JsonParser.parseString(resp.body()).getAsJsonObject();
    }

    private static String enc(String s) {
        return java.net.URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    /** Wandelt eine UUID ohne Bindestriche ins Standardformat. */
    private static String formatUuid(String raw) {
        if (raw.length() != 32) return raw;
        return raw.substring(0, 8) + "-" + raw.substring(8, 12) + "-"
                + raw.substring(12, 16) + "-" + raw.substring(16, 20) + "-"
                + raw.substring(20);
    }

    private static String xstsErrorMessage(long xerr) {
        if (xerr == 2148916233L) return "Dieser Microsoft-Account hat kein Xbox-Profil.";
        if (xerr == 2148916235L) return "Xbox Live ist in deiner Region nicht verfuegbar.";
        if (xerr == 2148916238L) return "Kinderkonto -- muss einer Familie hinzugefuegt werden.";
        return "Xbox-Login fehlgeschlagen (XErr " + xerr + ").";
    }
}
