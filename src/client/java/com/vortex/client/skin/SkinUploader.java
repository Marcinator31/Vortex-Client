package com.vortex.client.skin;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import net.minecraft.client.Minecraft;

/**
 * Laedt einen Skin auf das eigene Minecraft-Konto hoch -- danach sehen ihn auch
 * ALLE ANDEREN Spieler, nicht nur du.
 *
 * Das ist der offizielle Weg ueber Mojangs eigene Schnittstelle, denselben, den
 * auch der Minecraft-Launcher und Skin-Webseiten benutzen. Veraendert wird
 * ausschliesslich das eigene Konto, und dafuer wird der Anmelde-Token der
 * gerade laufenden Sitzung verwendet -- also genau der Account, mit dem du
 * ohnehin spielst.
 *
 * WICHTIG: Anders als der clientseitige Wechsel ist das eine ECHTE, dauerhafte
 * Aenderung am Konto. Sie gilt ueberall und bleibt bestehen, bis man sie wieder
 * aendert.
 */
public final class SkinUploader {

    private static final String ENDPOINT =
            "https://api.minecraftservices.com/minecraft/profile/skins";

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .build();

    private SkinUploader() {}

    /**
     * Ist ein Anmelde-Token verfuegbar?
     *
     * Im Offline-Modus (z.B. gecrackte Starts) gibt es keinen gueltigen Token --
     * dann ist ein Hochladen grundsaetzlich nicht moeglich.
     */
    public static boolean canUpload() {
        try {
            String token = token();
            return token != null && token.length() > 20;
        } catch (Throwable pvpErr) {
            return false;
        }
    }

    private static String token() {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.getUser() == null) return null;
        return client.getUser().getAccessToken();
    }

    /**
     * Laedt die PNG-Datei als neuen Konto-Skin hoch.
     *
     * Die Schnittstelle erwartet eine Formular-Sendung mit zwei Teilen: der
     * Modell-Angabe (classic/slim) und der Bilddatei. Das Format wird hier von
     * Hand gebaut, weil die Standard-Bibliothek dafuer nichts mitbringt.
     *
     * Blockiert -- gehoert in einen eigenen Thread.
     */
    public static void upload(Path pngFile, boolean slim) throws Exception {
        String token = token();
        if (token == null || token.isEmpty()) {
            throw new RuntimeException(
                    "Not signed in \u2014 upload unavailable.");
        }
        if (!Files.exists(pngFile)) {
            throw new RuntimeException("File not found: " + pngFile.getFileName());
        }
        byte[] png = Files.readAllBytes(pngFile);
        if (png.length < 8 || png[0] != (byte) 0x89 || png[1] != 'P') {
            throw new RuntimeException("That file is not a valid PNG.");
        }
        if (png.length > 24576) {
            // Mojang lehnt zu grosse Dateien ab; ein normaler Skin ist winzig.
            throw new RuntimeException("File too large for a skin.");
        }

        String boundary = "----vortexclient" + System.currentTimeMillis();
        byte[] body = buildMultipart(boundary, slim ? "slim" : "classic",
                pngFile.getFileName().toString(), png);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(ENDPOINT))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();

        HttpResponse<String> resp = HTTP.send(req,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        int code = resp.statusCode();
        if (code / 100 == 2) return;   // fertig

        // Verstaendliche Meldungen statt roher Statuscodes.
        if (code == 401) {
            throw new RuntimeException(
                    "Sign-in rejected (401) \u2014 token expired? Restart the game.");
        }
        if (code == 403) {
            throw new RuntimeException(
                    "Not allowed (403) \u2014 the account does not own Minecraft or is banned.");
        }
        if (code == 429) {
            throw new RuntimeException("Too many changes \u2014 wait a moment.");
        }
        String b = resp.body();
        throw new RuntimeException("Upload failed (HTTP " + code + ")"
                + (b == null || b.isEmpty() ? "" : ": "
                    + b.substring(0, Math.min(b.length(), 140))));
    }

    /** Baut die Formular-Sendung mit Modell-Angabe und Bilddatei. */
    private static byte[] buildMultipart(String boundary, String variant,
                                         String fileName, byte[] png) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        // Teil 1: Modell
        out.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        out.write("Content-Disposition: form-data; name=\"variant\"\r\n\r\n"
                .getBytes(StandardCharsets.UTF_8));
        out.write((variant + "\r\n").getBytes(StandardCharsets.UTF_8));

        // Teil 2: Bilddatei
        out.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Disposition: form-data; name=\"file\"; filename=\""
                + fileName + "\"\r\n").getBytes(StandardCharsets.UTF_8));
        out.write("Content-Type: image/png\r\n\r\n".getBytes(StandardCharsets.UTF_8));
        out.write(png);
        out.write("\r\n".getBytes(StandardCharsets.UTF_8));

        // Abschluss
        out.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return out.toByteArray();
    }

    /** Setzt den Konto-Skin auf den Standard zurueck (loescht den eigenen). */
    public static void reset() throws Exception {
        String token = token();
        if (token == null || token.isEmpty()) {
            throw new RuntimeException("Not signed in.");
        }
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(ENDPOINT + "/active"))
                .header("Authorization", "Bearer " + token)
                .DELETE()
                .build();
        HttpResponse<String> resp = HTTP.send(req,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resp.statusCode() / 100 != 2) {
            throw new RuntimeException("Reset failed (HTTP "
                    + resp.statusCode() + ").");
        }
    }
}
