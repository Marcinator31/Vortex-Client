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
 * ABLAUF
 *  1. Der Launcher schreibt die Auswahl nach
 *     config/vortex-client/cosmetics.json
 *  2. Von dort wird die Kennung gelesen
 *  3. Die Textur kommt aus dem Cosmetics-Verzeichnis im Netz und wird
 *     lokal zwischengespeichert
 *  4. CapeOverrideMixin setzt sie beim eigenen Spieler ein
 *
 * Aufgebaut wie ActiveSkin: dieselbe Art, eine Textur anzumelden, damit sich
 * beide gleich verhalten und Fehler an einer Stelle nicht die andere treffen.
 *
 * WICHTIG: Das ist rein clientseitig. Nur du selbst siehst das Cape --
 * andere Spieler sehen nichts davon, weil nichts den Server erreicht.
 */
public final class ActiveCape {

    /** Verzeichnis der verfuegbaren Capes. Muss oeffentlich erreichbar sein. */
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

    /** Kennung der angemeldeten Cape-Textur, oder null. */
    public static synchronized Identifier textureId() {
        ensureLoaded();
        return textureId;
    }

    /** Beim Start aufrufen. Holt die Textur im Hintergrund nach. */
    public static synchronized void init() {
        ensureLoaded();
    }

    private static synchronized void ensureLoaded() {
        if (geladen) return;
        geladen = true;
        capeId = leseAuswahl();
        if (capeId == null || capeId.isBlank()) return;
        // Im Hintergrund: der Spielstart darf nicht auf das Netz warten.
        Thread t = new Thread(ActiveCape::holeUndMelde, "vortex-cape");
        t.setDaemon(true);
        t.start();
    }

    /**
     * Liest die Auswahl aus der Launcher-Datei.
     *
     * Bewusst ohne JSON-Bibliothek: die Datei hat genau ein Feld, und eine
     * zusaetzliche Abhaengigkeit fuer drei Zeilen waere unangemessen.
     */
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

    /** Holt die Textur (aus dem Zwischenspeicher oder dem Netz) und meldet sie an. */
    private static void holeUndMelde() {
        if (laeuft) return;
        laeuft = true;
        try {
            byte[] daten = null;
            Path cache = cacheDatei(capeId);

            // Zwischenspeicher zuerst: ohne Netz soll das Cape trotzdem da sein.
            if (Files.exists(cache)) {
                daten = Files.readAllBytes(cache);
            } else {
                String url = sucheTexturAdresse(capeId);
                if (url == null) {
                    com.vortex.client.core.Errors.note("ActiveCape",
                            "Kein Eintrag fuer " + capeId + " im Verzeichnis");
                    return;
                }
                daten = lade(url);
                if (daten == null) return;
                try {
                    Files.createDirectories(cache.getParent());
                    Files.write(cache, daten);
                } catch (Throwable ignored) {
                    // Ohne Zwischenspeicher laedt es beim naechsten Start neu --
                    // aergerlich, aber kein Grund abzubrechen.
                }
            }

            final byte[] fertig = daten;
            // Texturen duerfen nur im Render-Thread angemeldet werden.
            Minecraft.getInstance().execute(() -> melde(fertig));
        } catch (Throwable pvpErr) {
            com.vortex.client.core.Errors.report("ActiveCape.holeUndMelde", pvpErr);
        } finally {
            laeuft = false;
        }
    }

    /** Sucht im Verzeichnis die Texturadresse zur gewaehlten Kennung. */
    private static String sucheTexturAdresse(String id) {
        byte[] roh = lade(CATALOGUE);
        if (roh == null) return null;
        String json = new String(roh, StandardCharsets.UTF_8);
        // Den Block zur passenden id finden, dann dessen texture-Feld.
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
            // Cape-Texturen sind 64x32. Eine falsche Groesse wuerde im Renderer
            // zu verschobenen Flaechen fuehren -- lieber gar kein Cape.
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
            // Wie bei ActiveSkin: genau einmal unter dieser Kennung anmelden.
            tm.register(id, new DynamicTexture(() -> "vortexclient-cape", image));
            textureId = id;
            com.vortex.client.core.Errors.note("ActiveCape", "Cape angemeldet als " + id);
        } catch (Throwable pvpErr) {
            com.vortex.client.core.Errors.report("ActiveCape.melde", pvpErr);
        }
    }

    /** Nur Zeichen, die in einer Identifier-Kennung erlaubt sind. */
    private static String safe(String s) {
        StringBuilder b = new StringBuilder();
        for (char c : s.toLowerCase().toCharArray()) {
            b.append((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
                    || c == '_' || c == '-' || c == '.' ? c : '_');
        }
        return b.length() == 0 ? "cape" : b.toString();
    }
}
