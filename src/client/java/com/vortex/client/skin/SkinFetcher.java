package com.vortex.client.skin;

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
import java.util.Base64;

/**
 * Holt Skins ueber die oeffentlichen Mojang-Schnittstellen.
 *
 * Ablauf beim Suchen nach einem Spielernamen:
 *   1) Name -> UUID          (api.mojang.com)
 *   2) UUID -> Profil        (sessionserver.mojang.com)
 *      Das Profil enthaelt einen Base64-Block mit der Adresse der Skin-Datei.
 *   3) Skin-PNG herunterladen (textures.minecraft.net)
 *
 * Alle drei Schritte nutzen oeffentlich zugaengliche Endpunkte -- dieselben, die
 * jede Skin-Webseite auch verwendet. Es ist keine Anmeldung noetig, und es
 * werden keine fremden Daten veraendert: der Skin wird lediglich als Bilddatei
 * heruntergeladen.
 *
 * Alles blockiert, gehoert also in einen eigenen Thread.
 */
public final class SkinFetcher {

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    /** Ergebnis einer Suche. */
    public static final class Result {
        public final String userName;   // korrekt geschriebener Name
        public final String uuid;
        public final String textureUrl;
        public final boolean slim;
        Result(String userName, String uuid, String textureUrl, boolean slim) {
            this.userName = userName;
            this.uuid = uuid;
            this.textureUrl = textureUrl;
            this.slim = slim;
        }
    }

    private SkinFetcher() {}

    /** Sucht den Skin eines Spielernamens. Wirft mit klarer Meldung, wenn nichts geht. */
    public static Result lookup(String userName) throws Exception {
        if (userName == null || userName.trim().isEmpty()) {
            throw new IllegalArgumentException("Kein Name angegeben.");
        }
        String name = userName.trim();

        // 1) Name -> UUID
        JsonObject profile = getJson(
                "https://api.mojang.com/users/profiles/minecraft/" + enc(name));
        if (profile == null || !profile.has("id")) {
            throw new RuntimeException("Spieler \"" + name + "\" nicht gefunden.");
        }
        String uuid = profile.get("id").getAsString();
        String realName = profile.has("name") ? profile.get("name").getAsString() : name;

        // 2) UUID -> Profil mit Texturen
        JsonObject session = getJson(
                "https://sessionserver.mojang.com/session/minecraft/profile/" + uuid);
        if (session == null || !session.has("properties")) {
            throw new RuntimeException("Profil konnte nicht geladen werden.");
        }

        JsonArray props = session.getAsJsonArray("properties");
        String encoded = null;
        for (int i = 0; i < props.size(); i++) {
            JsonObject p = props.get(i).getAsJsonObject();
            if (p.has("name") && "textures".equals(p.get("name").getAsString())) {
                encoded = p.get("value").getAsString();
                break;
            }
        }
        if (encoded == null) {
            throw new RuntimeException("Dieser Spieler hat keinen eigenen Skin.");
        }

        // 3) Base64-Block auswerten
        String json = new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
        JsonObject tex = JsonParser.parseString(json).getAsJsonObject()
                .getAsJsonObject("textures");
        if (tex == null || !tex.has("SKIN")) {
            throw new RuntimeException("Dieser Spieler hat keinen eigenen Skin.");
        }
        JsonObject skin = tex.getAsJsonObject("SKIN");
        String url = skin.get("url").getAsString();

        // Schlankes Modell steht als Zusatzangabe im Block.
        boolean slim = false;
        if (skin.has("metadata")) {
            JsonObject meta = skin.getAsJsonObject("metadata");
            slim = meta.has("model") && "slim".equals(meta.get("model").getAsString());
        }
        return new Result(realName, uuid, url, slim);
    }

    /** Laedt die PNG-Datei herunter und legt sie im Skin-Ordner ab. */
    public static Path download(String textureUrl, String fileName) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(textureUrl))
                .GET()
                .build();
        HttpResponse<byte[]> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofByteArray());
        if (resp.statusCode() / 100 != 2) {
            throw new RuntimeException("Download fehlgeschlagen (HTTP "
                    + resp.statusCode() + ").");
        }
        byte[] data = resp.body();
        if (data == null || data.length < 8) {
            throw new RuntimeException("Leere Skin-Datei erhalten.");
        }
        // Kurze Plausibilitaetspruefung: PNG beginnt immer mit derselben Kennung.
        if (!(data[0] == (byte) 0x89 && data[1] == 'P' && data[2] == 'N' && data[3] == 'G')) {
            throw new RuntimeException("Die geladene Datei ist kein PNG.");
        }

        Files.createDirectories(SkinWardrobe.skinDir());
        Path target = SkinWardrobe.skinDir().resolve(fileName);
        Files.write(target, data);
        return target;
    }

    // ---- Hilfsmittel ----

    private static JsonObject getJson(String url) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Accept", "application/json")
                .GET()
                .build();
        HttpResponse<String> resp = HTTP.send(req,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resp.statusCode() == 204 || resp.statusCode() == 404) {
            return null;   // Name existiert nicht
        }
        if (resp.statusCode() == 429) {
            throw new RuntimeException("Zu viele Anfragen -- kurz warten.");
        }
        if (resp.statusCode() / 100 != 2) {
            throw new RuntimeException("Mojang antwortete mit HTTP " + resp.statusCode() + ".");
        }
        String body = resp.body();
        if (body == null || body.isEmpty()) return null;
        return JsonParser.parseString(body).getAsJsonObject();
    }

    private static String enc(String s) {
        return java.net.URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
