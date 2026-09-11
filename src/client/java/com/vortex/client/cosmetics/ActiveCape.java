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

    private ActiveCape() {}

    public static synchronized Identifier textureId() {
        ensureLoaded();
        return textureId;
    }

    public static synchronized void init() {
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

    /** Liest die Auswahl aus der Launcher-Datei. Die hat genau ein Feld. */
    private static String leseAuswahl() {
        try {
            Path datei = FabricLoader.getInstance().getConfigDir()
                    .resolve("vortex-client").resolve("cosmetics.json");
            if (!Files.exists(datei)) return null;
            String json = Files.readString(datei, StandardCharsets.UTF_8);
            Matcher m = Pattern.compile("\"cape\"\\s*:\\s*\"([^\"]+)\"").matcher(json);
            return m.find() ? m.group(1) : null;
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

    private static String sucheTexturAdresse(String id) {
        byte[] roh = lade(CATALOGUE);
        if (roh == null) return null;
        String json = new String(roh, StandardCharsets.UTF_8);
        Matcher block = Pattern.compile(
                "\\{[^{}]*\"id\"\\s*:\\s*\"" + Pattern.quote(id) + "\"[^{}]*\\}").matcher(json);
        if (!block.find()) return null;
        Matcher tex = Pattern.compile("\"texture\"\\s*:\\s*\"([^\"]+)\"").matcher(block.group());
        return tex.find() ? tex.group(1) : null;
    }

    private static byte[] lade(String url) {
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
            // 64x32 ist Pflicht: bei anderer Groesse sitzen die Flaechen falsch.
            if (image.getWidth() != 64 || image.getHeight() != 32) {
                image.close();
                com.vortex.client.core.Errors.note("ActiveCape",
                        capeId + ": falsche Groesse (erwartet 64x32, ist "
                                + image.getWidth() + "x" + image.getHeight() + ")");
                return;
            }
            Identifier id = Identifier.fromNamespaceAndPath(
                    "vortexclient", "cape/" + safe(capeId));
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
