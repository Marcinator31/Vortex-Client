package com.vortex.client.cosmetics;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * Eigenes Bild auf dem Cape.
 *
 * Aus einem beliebigen Bild (PNG/JPG) wird eine Cape-Textur im Minecraft-
 * Layout gebaut -- in 8-facher Aufloesung (512 x 256 statt 64 x 32), damit
 * das Bild scharf bleibt. Das Bild fuellt die Aussenseite (die man von
 * hinten sieht) und wird dafuer mittig zugeschnitten; Innenseite und Raender
 * bekommen eine dunklere Farbe aus dem Bild. Die Elytra nutzt dieselbe
 * Textur und wird in dieser Farbe eingefaerbt.
 *
 * Layout der Cape-Textur (in 64x32-Pixeln): Aussenseite x 1-11, y 1-17;
 * Innenseite x 12-22, y 1-17; Raender rundherum; Elytra ab x 22.
 */
public final class EigenesCape {
    private EigenesCape() {}

    public static final String ID = "custom";
    private static final int S = 8;   // Vergroesserung gegenueber 64x32

    /** Die fertige Cape-Textur. */
    public static Path datei() { return Cosmetics.ordner().resolve("capes").resolve("custom.png"); }

    public static boolean vorhanden() { return Files.exists(datei()); }

    /**
     * Dateiauswahl von Windows/macOS/Linux oeffnen (eigener Thread, damit das
     * Spiel weiterlaeuft). Danach: Fehlertext oder null bei Erfolg.
     */
    public static void waehlen(Consumer<String> fertig) {
        Thread t = new Thread(() -> {
            String pfad;
            try (MemoryStack stack = MemoryStack.stackPush()) {
                PointerBuffer filter = stack.mallocPointer(3);
                filter.put(stack.UTF8("*.png")).put(stack.UTF8("*.jpg")).put(stack.UTF8("*.jpeg")).flip();
                pfad = TinyFileDialogs.tinyfd_openFileDialog("Choose a picture for your cape", null, filter, "Pictures", false);
            } catch (Throwable e) {
                fertig.accept("The file picker could not be opened.");
                return;
            }
            if (pfad == null) { fertig.accept(null); return; }   // abgebrochen
            fertig.accept(bauen(Path.of(pfad)));
        }, "Vortex-Cape-Picker");
        t.setDaemon(true);
        t.start();
    }

    /** Cape-Textur aus dem Bild bauen und speichern. Liefert einen Fehlertext oder null. */
    public static String bauen(Path bild) {
        try {
            if (Files.size(bild) > 20L * 1024 * 1024) return "This picture is too big (max 20 MB).";
            BufferedImage src = ImageIO.read(bild.toFile());
            if (src == null) return "This file is not a picture Minecraft can read (use PNG or JPG).";

            BufferedImage cape = new BufferedImage(64 * S, 32 * S, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = cape.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

            Color mittel = mittelwert(src);
            Color rand = dunkler(mittel, 0.55f), innen = dunkler(mittel, 0.75f);

            // Raender und Innenseite, dazu die Elytra-Flaeche
            g.setColor(rand);
            g.fillRect(0, 0, 23 * S, 17 * S);
            g.setColor(innen);
            g.fillRect(12 * S, 1 * S, 10 * S, 16 * S);
            g.setColor(mittel);
            g.fillRect(22 * S, 0, 24 * S, 22 * S);

            // Aussenseite: Bild mittig zuschneiden (10 x 16 = Seitenverhaeltnis des Capes)
            int zw = 10 * S, zh = 16 * S;
            double s = Math.max(zw / (double) src.getWidth(), zh / (double) src.getHeight());
            int bw = (int) Math.ceil(src.getWidth() * s), bh = (int) Math.ceil(src.getHeight() * s);
            g.setClip(1 * S, 1 * S, zw, zh);
            g.drawImage(src, 1 * S + (zw - bw) / 2, 1 * S + (zh - bh) / 2, bw, bh, null);
            g.dispose();

            Files.createDirectories(datei().getParent());
            Path tmp = datei().resolveSibling("custom.png.tmp");
            ImageIO.write(cape, "png", tmp.toFile());
            Files.move(tmp, datei(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return null;
        } catch (Throwable e) {
            com.vortex.client.core.Errors.report("EigenesCape.bauen", e);
            return "The picture could not be used: " + e.getMessage();
        }
    }

    private static Color mittelwert(BufferedImage img) {
        long r = 0, gr = 0, b = 0, n = 0;
        int schritt = Math.max(1, Math.max(img.getWidth(), img.getHeight()) / 64);
        for (int y = 0; y < img.getHeight(); y += schritt) {
            for (int x = 0; x < img.getWidth(); x += schritt) {
                int c = img.getRGB(x, y);
                if ((c >>> 24) < 128) continue;
                r += (c >> 16) & 0xFF; gr += (c >> 8) & 0xFF; b += c & 0xFF; n++;
            }
        }
        if (n == 0) return new Color(0x8B5CF6);
        return new Color((int) (r / n), (int) (gr / n), (int) (b / n));
    }

    private static Color dunkler(Color c, float f) {
        return new Color(Math.round(c.getRed() * f), Math.round(c.getGreen() * f), Math.round(c.getBlue() * f));
    }
}
