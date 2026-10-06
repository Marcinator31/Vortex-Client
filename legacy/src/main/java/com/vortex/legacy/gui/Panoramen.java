package com.vortex.legacy.gui;

import com.vortex.legacy.VortexLegacy;
import com.vortex.legacy.core.ClientSettings;
import com.vortex.legacy.core.Config;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Random;
import javax.imageio.ImageIO;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

/**
 * Eigene Panoramen fuer das Hauptmenue (wie im grossen Client): echte
 * Minecraft-Szenen, beim ersten Bedarf geladen und unter
 * config/vortexclient/panoramas gemerkt. Ohne Internet: Vanilla.
 */
public final class Panoramen {
    private Panoramen() {}

    static final String[][] ORTE = {
            { "cherry", "Cherry Grove" }, { "village", "Village" }, { "flowers", "Flower River" }, { "jungle", "Bamboo Coast" },
            { "badlands", "Badlands" }, { "ocean", "Warm Ocean" }, { "taiga", "Snowy Taiga" }, { "peaks", "Frozen Peaks" }
    };
    private static final String BASIS = System.getProperty("vortex.panorama.url",
            "https://raw.githubusercontent.com/Marcinator31/Vortex-Launcher/main/wallpapers/panorama/");
    private static final String MIX = ORTE[new Random().nextInt(ORTE.length)][0];

    public static String[] optionen() {
        String[] o = new String[ORTE.length + 2];
        o[0] = "Vortex Mix";
        for (int i = 0; i < ORTE.length; i++) o[i + 1] = ORTE[i][1];
        o[o.length - 1] = "Minecraft";
        return o;
    }

    private static String gewaehlt() {
        int i = ClientSettings.INSTANCE.menuPanorama.getIndex();
        if (i == 0) return MIX;
        if (i >= 1 && i <= ORTE.length) return ORTE[i - 1][0];
        return null;
    }

    private static String fertigFuer;
    private static final Identifier[] IDS = new Identifier[6];
    private static volatile String laedt;

    private static File seite(String id, int i) { return new File(new File(Config.dir(), "panoramas/" + id), "panorama_" + i + ".jpg"); }
    private static boolean vorhanden(String id) { for (int i = 0; i < 6; i++) if (!seite(id, i).isFile()) return false; return true; }

    /** Textur fuer Seite i (0-5) oder null = Vanilla. Nur im Render-Thread. */
    public static Identifier seite(int i) {
        String id = gewaehlt();
        if (id == null) return null;
        if (id.equals(fertigFuer)) return IDS[i];
        if (vorhanden(id)) { if (laden(id)) return IDS[i]; return null; }
        if (laedt == null) {
            laedt = id;
            final String wahl = id;
            Thread t = new Thread(new Runnable() { public void run() { herunterladen(wahl); laedt = null; } }, "Vortex Panorama");
            t.setDaemon(true);
            t.start();
        }
        return null;
    }

    private static boolean laden(String id) {
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            for (int i = 0; i < 6; i++) {
                BufferedImage b = ImageIO.read(seite(id, i));
                if (b == null) throw new IOException("unreadable face " + i);
                if (IDS[i] != null) mc.getTextureManager().close(IDS[i]);
                IDS[i] = mc.getTextureManager().registerDynamicTexture("vortex_panorama_" + i, new NativeImageBackedTexture(b));
            }
            fertigFuer = id;
            return true;
        } catch (Throwable e) {
            VortexLegacy.LOG.warn("[Vortex] Panorama " + id + " broken, using vanilla: " + e);
            for (int i = 0; i < 6; i++) seite(id, i).delete();
            return false;
        }
    }

    private static void herunterladen(String id) {
        try {
            File dir = seite(id, 0).getParentFile();
            dir.mkdirs();
            for (int i = 0; i < 6; i++) {
                File ziel = seite(id, i);
                if (ziel.isFile()) continue;
                HttpURLConnection c = (HttpURLConnection) new URL(BASIS + id + "/panorama_" + i + ".jpg").openConnection();
                c.setConnectTimeout(10000);
                c.setReadTimeout(30000);
                c.setRequestProperty("User-Agent", "VortexClient-Legacy");
                if (c.getResponseCode() != 200) return;
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                InputStream in = c.getInputStream();
                try {
                    byte[] buf = new byte[16384];
                    int n;
                    while ((n = in.read(buf)) > 0) { out.write(buf, 0, n); if (out.size() > 8000000) return; }
                } finally { in.close(); }
                byte[] data = out.toByteArray();
                BufferedImage b = ImageIO.read(new ByteArrayInputStream(data));
                if (b == null || b.getWidth() != b.getHeight()) return;
                File tmp = new File(dir, "panorama_" + i + ".part");
                Files.write(tmp.toPath(), data);
                Files.move(tmp.toPath(), ziel.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            VortexLegacy.LOG.info("[Vortex] Panorama " + id + " not available right now: " + e);
        }
    }
}
