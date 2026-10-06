package com.vortex.client.musik;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Album-Cover als Textur. Nur Adressen von Spotify (i.scdn.co); geladen im
 * Hintergrund, auf 128x128 verkleinert, die letzten 48 bleiben im Speicher.
 */
public final class Cover {
    private Cover() {}

    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.NORMAL).build();
    private static final int MAX = 48;
    private static final Map<String, Identifier> FERTIG = new LinkedHashMap<>(16, 0.75f, true);
    private static final Set<String> LAEDT = ConcurrentHashMap.newKeySet();
    private static final Set<String> KAPUTT = ConcurrentHashMap.newKeySet();
    private static final Map<String, Integer> FARBE = new ConcurrentHashMap<>();
    private static int nr;

    /** Nur fuer den Test im Spiel: ein Bild als Cover fuer diese Adresse eintragen. */
    public static void testBild(String url, byte[] png, int farbe) {
        FARBE.put(url, farbe);
        anmelden(url, png);
    }

    /** Kraeftige Durchschnittsfarbe des Covers (fuer Leuchten hinter dem Bild), oder -1. */
    public static int farbe(String url) {
        return url == null ? -1 : FARBE.getOrDefault(url, -1);
    }

    /** Textur des Covers, oder null (wird dann im Hintergrund geladen). Nur Render-Thread. */
    public static Identifier von(String url) {
        if (url == null || !url.startsWith("https://i.scdn.co/image/")) return null;
        Identifier id = FERTIG.get(url);
        if (id != null || KAPUTT.contains(url) || !LAEDT.add(url)) return id;
        Spotify.POOL.execute(() -> {
            try {
                HttpResponse<byte[]> r = HTTP.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(15)).build(),
                        HttpResponse.BodyHandlers.ofByteArray());
                if (r.statusCode() != 200 || r.body().length > 2_000_000) throw new IllegalStateException("HTTP " + r.statusCode());
                BufferedImage bild = ImageIO.read(new ByteArrayInputStream(r.body()));
                if (bild == null) throw new IllegalStateException("no image");
                BufferedImage klein = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g = klein.createGraphics();
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                g.drawImage(bild, 0, 0, 128, 128, null);
                g.dispose();
                FARBE.put(url, mittel(klein));
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                ImageIO.write(klein, "png", out);
                byte[] png = out.toByteArray();
                Minecraft.getInstance().execute(() -> anmelden(url, png));
            } catch (Throwable t) {
                KAPUTT.add(url);
                LAEDT.remove(url);
            }
        });
        return null;
    }

    /** Mittelwert der Farben, etwas gesaettigt und aufgehellt -- sonst wirkt das Leuchten grau. */
    private static int mittel(BufferedImage b) {
        long r = 0, g = 0, bl = 0; int n = 0;
        for (int y = 0; y < b.getHeight(); y += 4) for (int x = 0; x < b.getWidth(); x += 4) {
            int c = b.getRGB(x, y);
            r += (c >> 16) & 255; g += (c >> 8) & 255; bl += c & 255; n++;
        }
        float[] hsb = java.awt.Color.RGBtoHSB((int) (r / n), (int) (g / n), (int) (bl / n), null);
        return java.awt.Color.HSBtoRGB(hsb[0], Math.min(1f, hsb[1] * 1.5f + 0.1f), Math.max(0.55f, hsb[2])) & 0xFFFFFF;
    }

    private static void anmelden(String url, byte[] png) {
        try (var in = new ByteArrayInputStream(png)) {
            NativeImage image = NativeImage.read(in);
            Identifier id = Identifier.fromNamespaceAndPath("vortexclient", "music/cover_" + (nr++));
            Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> "vortexclient-cover", image));
            FERTIG.put(url, id);
            while (FERTIG.size() > MAX) {
                var alt = FERTIG.entrySet().iterator().next();
                FERTIG.remove(alt.getKey());
                Minecraft.getInstance().getTextureManager().release(alt.getValue());
            }
        } catch (Throwable t) {
            KAPUTT.add(url);
        } finally {
            LAEDT.remove(url);
        }
    }
}
