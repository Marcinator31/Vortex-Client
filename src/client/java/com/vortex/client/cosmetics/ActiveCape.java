package com.vortex.client.cosmetics;

import com.mojang.blaze3d.platform.NativeImage;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Das im Launcher gewaehlte Cape.
 *
 * Der Launcher schreibt die Auswahl nach config/vortex-client/cosmetics.json.
 * Die Textur kommt aus dem Cosmetics-Verzeichnis im Netz; CapeOverrideMixin
 * setzt sie beim eigenen Spieler ein.
 *
 * Rein clientseitig: nur du selbst siehst das Cape.
 */
public final class ActiveCape {

    private static final String CATALOGUE =
            "https://raw.githubusercontent.com/Marcinator31/Vortex-Client-Cosmetics/refs/heads/main/cosmetics.json";

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private static String capeId = null;
    private static Identifier textureId = null;
    private static boolean geladen = false;
    private static boolean laeuft = false;
    private static int zaehler = 0;

    private ActiveCape() {}

    public static synchronized Identifier textureId() {
        ensureLoaded();
        return textureId;
    }

    public static synchronized void init() {
        ensureLoaded();
    }

    /** Nach einer neuen Auswahl im Cosmetics-Menue: altes Cape weg, neues laden. */
    public static synchronized void neuLaden() {
        Identifier alt = textureId;
        textureId = null;
        geladen = false;
        if (alt != null) {
            Minecraft.getInstance().execute(() -> {
                try { Minecraft.getInstance().getTextureManager().release(alt); } catch (Throwable ignored) { }
            });
        }
        ensureLoaded();
    }

    private static synchronized void ensureLoaded() {
        if (geladen) return;
        geladen = true;
        capeId = leseAuswahl();
        if (capeId == null || capeId.isBlank()) return;
        Thread t = new Thread(ActiveCape::holeUndMelde, "vortex-cape");
        t.setDaemon(true);
        t.start();
    }

    /** Liest die Auswahl aus cosmetics.json (seit dem Cosmetics-Menue mit weiteren Feldern). */
    private static String leseAuswahl() {
        try {
            return Cosmetics.eigene().cape();
        } catch (Throwable pvpErr) {
            com.vortex.client.core.Errors.report("ActiveCape.leseAuswahl", pvpErr);
            return null;
        }
    }

    private static Path cacheDatei(String id) {
        return FabricLoader.getInstance().getConfigDir()
                .resolve("vortex-client").resolve("capes").resolve(safe(id) + ".png");
    }

    private static void holeUndMelde() {
        if (laeuft) return;
        laeuft = true;
        try {
            byte[] daten = null;
            // Eigenes Bild (EigenesCape) liegt nur lokal
            if (EigenesCape.ID.equals(capeId)) {
                if (!Files.exists(EigenesCape.datei())) return;
                final byte[] eigen = Files.readAllBytes(EigenesCape.datei());
                Minecraft.getInstance().execute(() -> melde(eigen));
                return;
            }
            Path cache = cacheDatei(capeId);

            // NETZ ZUERST, Zwischenspeicher nur als Rueckfall.
            //
            // Umgekehrt wurde eine einmal geholte Datei nie wieder erneuert --
            // Aenderungen am Cape im Verzeichnis sah dann niemand. Eine
            // Cape-Textur ist wenige Kilobyte gross; sie bei jedem Start zu
            // holen kostet nichts.
            String url = sucheTexturAdresse(capeId);
            if (url != null) daten = lade(url);
            if (daten != null) {
                try {
                    Files.createDirectories(cache.getParent());
                    Files.write(cache, daten);
                } catch (Throwable ignored) { }
            } else if (Files.exists(cache)) {
                daten = Files.readAllBytes(cache);
                com.vortex.client.core.Errors.note("ActiveCape",
                        "Verzeichnis nicht erreichbar -- benutze gespeicherte Textur");
            } else {
                com.vortex.client.core.Errors.note("ActiveCape",
                        "Cape " + capeId + " konnte nicht geladen werden");
                return;
            }

            final byte[] fertig = daten;
            Minecraft.getInstance().execute(() -> melde(fertig));
        } catch (Throwable pvpErr) {
            com.vortex.client.core.Errors.report("ActiveCape.holeUndMelde", pvpErr);
        } finally {
            laeuft = false;
        }
    }

    /** Alle Capes im Verzeichnis als {id, name} (fuer das Cosmetics-Menue). Laedt aus dem Netz. */
    public static java.util.List<String[]> katalog() {
        java.util.List<String[]> out = new java.util.ArrayList<>();
        byte[] roh = lade(CATALOGUE);
        if (roh == null) return out;
        try {
            var capes = com.google.gson.JsonParser.parseString(new String(roh, StandardCharsets.UTF_8))
                    .getAsJsonObject().getAsJsonArray("capes");
            for (var e : capes) {
                var o = e.getAsJsonObject();
                if (!o.has("id")) continue;
                out.add(new String[] { o.get("id").getAsString(), o.has("name") ? o.get("name").getAsString() : o.get("id").getAsString() });
            }
        } catch (Throwable pvpErr) {
            com.vortex.client.core.Errors.report("ActiveCape.katalog", pvpErr);
        }
        return out;
    }

    /** Textur-Adresse eines Capes im Verzeichnis (auch fuer andere Spieler, FremdeCapes). */
    static String sucheTexturAdresse(String id) {
        byte[] roh = lade(CATALOGUE);
        if (roh == null) return null;
        String json = new String(roh, StandardCharsets.UTF_8);
        Matcher block = Pattern.compile(
                "\\{[^{}]*\"id\"\\s*:\\s*\"" + Pattern.quote(id) + "\"[^{}]*\\}").matcher(json);
        if (!block.find()) return null;
        Matcher tex = Pattern.compile("\"texture\"\\s*:\\s*\"([^\"]+)\"").matcher(block.group());
        return tex.find() ? tex.group(1) : null;
    }

    static byte[] lade(String url) {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .header("User-Agent", "VortexClient")
                    .timeout(Duration.ofSeconds(20))
                    .GET().build();
            HttpResponse<byte[]> res = HTTP.send(req, HttpResponse.BodyHandlers.ofByteArray());
            if (res.statusCode() / 100 != 2) {
                com.vortex.client.core.Errors.note("ActiveCape",
                        "HTTP " + res.statusCode() + " bei " + url);
                return null;
            }
            return res.body();
        } catch (Throwable pvpErr) {
            com.vortex.client.core.Errors.report("ActiveCape.lade", pvpErr);
            return null;
        }
    }

    /** Meldet die Textur an. Laeuft im Render-Thread. */
    private static void melde(byte[] daten) {
        try {
            NativeImage image;
            try (InputStream in = new java.io.ByteArrayInputStream(daten)) {
                image = NativeImage.read(in);
            }
            // Seitenverhaeltnis 2:1 und Vielfaches von 64x32 (z. B. 512x256 fuer
            // eigene Bilder) -- bei anderer Form sitzen die Flaechen falsch.
            int w = image.getWidth(), h = image.getHeight();
            if (w % 64 != 0 || w != h * 2 || w > 2048) {
                image.close();
                com.vortex.client.core.Errors.note("ActiveCape",
                        capeId + ": falsche Groesse (erwartet 64x32, ist "
                                + image.getWidth() + "x" + image.getHeight() + ")");
                return;
            }
            // Jedes Mal eine neue Kennung: sonst behaelt Minecraft nach einem
            // Wechsel des eigenen Bildes die alte Textur im Speicher.
            Identifier id = Identifier.fromNamespaceAndPath(
                    "vortexclient", "cape/" + safe(capeId) + "_" + (++zaehler));
            var tm = Minecraft.getInstance().getTextureManager();
            tm.register(id, new DynamicTexture(() -> "vortexclient-cape", image));
            textureId = id;
            com.vortex.client.core.Errors.note("ActiveCape", "Cape angemeldet als " + id);
        } catch (Throwable pvpErr) {
            com.vortex.client.core.Errors.report("ActiveCape.melde", pvpErr);
        }
    }

    private static String safe(String s) {
        StringBuilder b = new StringBuilder();
        for (char c : s.toLowerCase().toCharArray()) {
            b.append((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
                    || c == '_' || c == '-' || c == '.' ? c : '_');
        }
        return b.length() == 0 ? "cape" : b.toString();
    }
}
