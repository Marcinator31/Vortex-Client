package com.vortex.client.cosmetics;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cape-Texturen ANDERER Spieler: Capes aus dem Vortex-Verzeichnis (nach Id)
 * und eigene Bilder vom Freunde-Server (nach Hash). Geladen wird im
 * Hintergrund; bis es da ist, zeigt der Spieler sein normales Cape.
 *
 * Fremde Bilder sind nicht vertrauenswuerdig: vor dem Dekodieren wird die
 * Groesse aus dem Kopf gelesen (hoechstens 1024 x 512, 2:1), erst dann das
 * Bild selbst -- so kann niemand mit einem riesigen Bild den Speicher fluten.
 */
public final class FremdeCapes {
    private FremdeCapes() {}

    private static final Map<String, Identifier> FERTIG = new ConcurrentHashMap<>();
    private static final Set<String> LAEUFT = ConcurrentHashMap.newKeySet();
    private static final Set<String> KAPUTT = ConcurrentHashMap.newKeySet();

    /** Textur fuer das Cape dieses Spielers, oder null (dann: Vanilla-Cape). */
    public static Identifier textur(UUID spieler) {
        Cosmetics.Auswahl a = Cosmetics.fuer(spieler);
        if (a.cape().isEmpty()) return null;
        if (AnimCapes.ist(a.cape())) return AnimCapes.basis(a.cape());
        String schluessel = EigenesCape.ID.equals(a.cape()) ? "bild:" + CosmeticsSync.bildVon(spieler) : "id:" + a.cape();
        if (schluessel.endsWith(":null")) return null;
        Identifier fertig = FERTIG.get(schluessel);
        if (fertig != null || KAPUTT.contains(schluessel) || !LAEUFT.add(schluessel)) return fertig;
        Thread t = new Thread(() -> laden(schluessel), "Vortex-Remote-Cape");
        t.setDaemon(true);
        t.start();
        return null;
    }

    private static void laden(String schluessel) {
        try {
            byte[] roh;
            if (schluessel.startsWith("id:")) {
                String url = ActiveCape.sucheTexturAdresse(schluessel.substring(3));
                roh = url == null ? null : ActiveCape.lade(url);
            } else {
                String b64 = CosmeticsSync.bildHolen(schluessel.substring(5));
                roh = b64 == null ? null : java.util.Base64.getDecoder().decode(b64);
            }
            byte[] png = roh == null ? null : alsPng(roh);
            if (png == null) { KAPUTT.add(schluessel); return; }
            Minecraft.getInstance().execute(() -> anmelden(schluessel, png));
        } catch (Throwable t) {
            KAPUTT.add(schluessel);
            com.vortex.client.core.Errors.report("FremdeCapes.laden", t);
        } finally {
            LAEUFT.remove(schluessel);
        }
    }

    /** JPEG/PNG pruefen und als PNG liefern (NativeImage liest nur PNG). */
    private static byte[] alsPng(byte[] roh) throws Exception {
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(roh))) {
            Iterator<ImageReader> leser = ImageIO.getImageReaders(in);
            if (!leser.hasNext()) return null;
            ImageReader r = leser.next();
            try {
                r.setInput(in);
                int w = r.getWidth(0), h = r.getHeight(0);
                if (w > 1024 || w != h * 2 || w % 64 != 0) return null;
                BufferedImage bild = r.read(0);
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                ImageIO.write(bild, "png", out);
                return out.toByteArray();
            } finally {
                r.dispose();
            }
        }
    }

    private static void anmelden(String schluessel, byte[] png) {
        try (var in = new ByteArrayInputStream(png)) {
            NativeImage image = NativeImage.read(in);
            Identifier id = Identifier.fromNamespaceAndPath("vortexclient", "cape/remote_" + Integer.toHexString(schluessel.hashCode()) + "_" + FERTIG.size());
            Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> "vortexclient-remote-cape", image));
            FERTIG.put(schluessel, id);
        } catch (Throwable t) {
            KAPUTT.add(schluessel);
            com.vortex.client.core.Errors.report("FremdeCapes.anmelden", t);
        }
    }
}
