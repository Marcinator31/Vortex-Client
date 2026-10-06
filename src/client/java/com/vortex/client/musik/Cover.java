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
    private static int nr;

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
