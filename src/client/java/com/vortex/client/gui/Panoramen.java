package com.vortex.client.gui;

import com.mojang.blaze3d.platform.NativeImage;
import com.vortex.client.core.ClientSettings;
import com.vortex.client.core.ConfigManager;
import com.vortex.client.core.Errors;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import javax.imageio.ImageIO;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureContents;
import net.minecraft.resources.Identifier;

/**
 * Eigene Panoramen fuer die Menues (Hauptmenue, Einzel-/Mehrspieler, Optionen):
 * echte Minecraft-Szenen, im Spiel aufgenommen (6 Wuerfelseiten), statt des
 * Vanilla-Panoramas.
 *
 * Die Bilder liegen NICHT in der Jar (je ~1,5 MB), sondern werden beim ersten
 * Bedarf geladen und unter config/vortexclient/panoramas/ gemerkt. Bis sie da
 * sind -- oder ohne Internet -- bleibt das Vanilla-Panorama.
 *
 * Ausgetauscht wird nur der Inhalt der Panorama-Textur (siehe
 * PanoramaTexturMixin): Drehen, Unschaerfe und alles andere bleibt Vanilla,
 * Resource Packs mit eigenem Panorama gewinnen bei "Minecraft".
 */
public final class Panoramen {
    private Panoramen() {}

    public record Ort(String id, String name) {}

    /** Reihenfolge = Reihenfolge in der Einstellung. */
    public static final List<Ort> ORTE = List.of(
            new Ort("cherry", "Cherry Grove"),
            new Ort("village", "Village"),
            new Ort("flowers", "Flower River"),
            new Ort("jungle", "Bamboo Coast"),
            new Ort("badlands", "Badlands"),
            new Ort("ocean", "Warm Ocean"),
            new Ort("taiga", "Snowy Taiga"),
            new Ort("peaks", "Frozen Peaks")
    );

    /** Einstellungs-Optionen: Mix, die Orte, Minecraft (Vanilla). */
    public static String[] optionen() {
        String[] o = new String[ORTE.size() + 2];
        o[0] = "Vortex Mix";
        for (int i = 0; i < ORTE.size(); i++) o[i + 1] = ORTE.get(i).name();
        o[o.length - 1] = "Minecraft";
        return o;
    }

    static final String BASIS = System.getProperty("vortex.panorama.url",
            "https://raw.githubusercontent.com/Marcinator31/Vortex-Launcher/main/wallpapers/panorama/");
    public static final Identifier TEXTUR = Identifier.withDefaultNamespace("textures/gui/title/background/panorama");
    private static final int MAX_SEITE = 4096;
    private static final int[] REIHENFOLGE = {1, 3, 5, 4, 0, 2};
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger("vortexclient");

    /** Fuer "Vortex Mix": einmal je Spielstart gewuerfelt. */
    private static final String MIX = ORTE.get(ThreadLocalRandom.current().nextInt(ORTE.size())).id();

    /** Ort, dessen Bilder gerade benutzt werden sollen (null = Vanilla). Liest der Mixin. */
    private static volatile String aktiv;
    /** Zuletzt verarbeitete Wahl (null = Vanilla, "" = noch nie). */
    private static String verarbeitet = "";
    private static volatile String laedt;
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL).connectTimeout(Duration.ofSeconds(10)).build();

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(Panoramen::tick);
    }

    /** Gewaehlter Ort (id) oder null fuer Vanilla. */
    static String gewaehlt() {
        int i;
        try { i = ClientSettings.INSTANCE.menuPanorama.getIndex(); } catch (Throwable t) { return null; }
        if (i == 0) return MIX;
        if (i >= 1 && i <= ORTE.size()) return ORTE.get(i - 1).id();
        return null;
    }

    public static String aktiv() { return aktiv; }

    private static void tick(Minecraft mc) {
        try {
            // Waehrend des Ladebildschirms NICHT: Minecraft laedt die Texturen dann im
            // Hintergrund und wendet sie spaeter an -- das wuerde unser Bild wieder
            // mit dem alten ueberschreiben. Danach laedt ein Neuladen (F3+T, Resource
            // Packs) ueber den Mixin ohnehin unser Bild.
            if (mc.gui.overlay() != null) return;
            String wahl = gewaehlt();
            if (Objects.equals(wahl, verarbeitet)) return;
            verarbeitet = wahl;
            if (wahl == null) { setzen(mc, null); return; }
            if (vorhanden(wahl)) { setzen(mc, wahl); return; }
            if (wahl.equals(laedt)) return;
            laedt = wahl;
            Thread t = new Thread(() -> {
                boolean ok = herunterladen(wahl);
                laedt = null;
                if (ok) mc.execute(() -> { if (wahl.equals(verarbeitet)) setzen(mc, wahl); });
            }, "Vortex Panorama");
            t.setDaemon(true);
            t.start();
        } catch (Throwable e) {
            Errors.report("Panoramen.tick", e);
        }
    }

    private static Path ordner(String id) { return ConfigManager.dataDir().resolve("panoramas").resolve(id); }
    private static Path seite(String id, int i) { return ordner(id).resolve("panorama_" + i + ".jpg"); }

    static boolean vorhanden(String id) {
        for (int i = 0; i < 6; i++) if (!Files.isRegularFile(seite(id, i))) return false;
        return true;
    }

    private static boolean herunterladen(String id) {
        try {
            Files.createDirectories(ordner(id));
            for (int i = 0; i < 6; i++) {
                Path ziel = seite(id, i);
                if (Files.isRegularFile(ziel)) continue;
                HttpRequest req = HttpRequest.newBuilder(URI.create(BASIS + id + "/panorama_" + i + ".jpg"))
                        .timeout(Duration.ofSeconds(30)).header("User-Agent", "VortexClient").GET().build();
                HttpResponse<byte[]> res = HTTP.send(req, HttpResponse.BodyHandlers.ofByteArray());
                if (res.statusCode() != 200 || res.body().length < 1000 || res.body().length > 8_000_000) {
                    LOG.warn("[Vortex] Panorama {}: face {} -> HTTP {}", id, i, res.statusCode());
                    return false;
                }
                // Erst pruefen, dann ablegen: ein kaputtes Bild soll nie im Cache landen.
                BufferedImage b = ImageIO.read(new java.io.ByteArrayInputStream(res.body()));
                if (b == null || b.getWidth() != b.getHeight() || b.getWidth() > MAX_SEITE) return false;
                Path tmp = ziel.resolveSibling(ziel.getFileName() + ".part");
                Files.write(tmp, res.body());
                Files.move(tmp, ziel, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException | InterruptedException e) {
            LOG.info("[Vortex] Panorama {} not available right now: {}", id, e.toString());
            return false;
        }
    }

    /** Panorama-Textur neu befuellen (Render-Thread). */
    private static void setzen(Minecraft mc, String id) {
        if (Objects.equals(id, aktiv) && id != null) return;
        aktiv = id;
        try {
            AbstractTexture t = mc.getTextureManager().getTexture(TEXTUR);
            if (t instanceof net.minecraft.client.renderer.texture.ReloadableTexture r) {
                try (TextureContents c = r.loadContents(mc.getResourceManager())) {
                    r.apply(c);
                }
                LOG.info("[Vortex] Menu panorama: {}", id == null ? "Minecraft" : id);
            }
        } catch (Throwable e) {
            // Kaputt? Dann lieber Vanilla.
            aktiv = null;
            Errors.report("Panoramen.setzen", e);
        }
    }

    /**
     * Fuer den Mixin: die 6 Seiten als ein Bild (Breite x 6*Hoehe, wie
     * CubeMapTexture es baut) -- oder null, dann laedt Vanilla.
     */
    public static TextureContents inhalt() {
        String id = aktiv;
        if (id == null || !vorhanden(id)) return null;
        NativeImage ziel = null;
        try {
            int w = -1;
            for (int i = 0; i < 6; i++) {
                BufferedImage b;
                // Reihenfolge wie CubeMapTexture.SUFFIXES: _1, _3, _5, _4, _0, _2
                try (InputStream in = Files.newInputStream(seite(id, REIHENFOLGE[i]))) { b = ImageIO.read(in); }
                if (b == null) throw new IOException("unreadable face " + i);
                if (w < 0) { w = b.getWidth(); ziel = new NativeImage(w, w * 6, false); }
                if (b.getWidth() != w || b.getHeight() != w) throw new IOException("face " + i + " has a different size");
                int[] zeile = new int[w];
                for (int y = 0; y < w; y++) {
                    b.getRGB(0, y, w, 1, zeile, 0, w);
                    for (int x = 0; x < w; x++) ziel.setPixel(x, i * w + y, zeile[x] | 0xFF000000);
                }
            }
            LOG.info("[Vortex] Menu panorama {} read ({} px faces)", id, w);
            return new TextureContents(ziel, null);
        } catch (Throwable e) {
            if (ziel != null) ziel.close();
            LOG.warn("[Vortex] Panorama {} broken, using vanilla: {}", id, e.toString());
            try { for (int i = 0; i < 6; i++) Files.deleteIfExists(seite(id, i)); } catch (IOException ignored) {}
            return null;
        }
    }
}
