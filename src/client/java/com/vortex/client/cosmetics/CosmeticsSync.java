package com.vortex.client.cosmetics;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.vortex.client.social.Social;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Cosmetics ueber den Freunde-Server austauschen, damit andere Vortex-
 * Spieler sie sehen (und du ihre).
 *
 *   - Eigene Auswahl: nach jeder Aenderung und nach jedem (Wieder-)Verbinden
 *     hochladen; ein eigenes Cape-Bild als JPEG (klein genug fuers Netz).
 *   - Andere: alle 2 s die Spieler in der Naehe ansehen; wer laenger als eine
 *     Minute nicht abgefragt wurde, wird (gebuendelt) abgefragt.
 *
 * Ohne Freunde-Server passiert nichts -- dann sieht jeder nur seine eigenen.
 */
public final class CosmeticsSync {
    private CosmeticsSync() {}

    private static final long NEU_FRAGEN_MS = 60_000;
    private static final Map<UUID, String> BILD = new ConcurrentHashMap<>();   // Spieler -> Hash seines Bildes
    private static final Map<UUID, Long> GEFRAGT = new ConcurrentHashMap<>();
    private static Object verbindung;               // Social.data() beim letzten Hochladen
    private static volatile String hochgeladenesBild = "";
    private static volatile boolean serverKannEs = true;
    private static int tick;

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (++tick % 40 != 0) return;
            try { abgleichen(mc); } catch (Throwable t) { com.vortex.client.core.Errors.report("CosmeticsSync", t); }
        });
    }

    static String bildVon(UUID spieler) { return BILD.get(spieler); }

    /** Bild vom Server holen (blockiert -- nur aus Hintergrund-Threads aufrufen). */
    static String bildHolen(String hash) {
        try {
            JsonObject r = Social.request("cosmetics.imageGet", Social.args("hash", hash)).get(20, TimeUnit.SECONDS);
            return r.has("image") ? r.get("image").getAsString() : null;
        } catch (Throwable t) {
            return null;
        }
    }

    /** Eigenes Emote an die anderen Vortex-Spieler schicken (der Server reicht es weiter). */
    static void emoteGespielt(String id) {
        if (Social.conn() != Social.Conn.ONLINE || !serverKannEs) return;
        Social.request("emote.play", Social.args("emote", id)).whenComplete((r, e) -> { if (e != null) pruefeFehler(e); });
    }

    /** Aus Cosmetics.speichern: geaenderte Auswahl hochladen. */
    static void geaendert() {
        if (Social.conn() == Social.Conn.ONLINE && serverKannEs) hochladen();
    }

    private static void abgleichen(Minecraft mc) {
        if (Social.conn() != Social.Conn.ONLINE || !serverKannEs) return;
        if (verbindung != Social.data()) {   // neu verbunden (oder neuer Stand vom Server)
            verbindung = Social.data();
            hochladen();
        }
        if (mc.level == null || mc.player == null) return;
        long jetzt = System.currentTimeMillis();
        List<UUID> fragen = new ArrayList<>();
        for (Player p : mc.level.players()) {
            if (p == mc.player) continue;
            Long zuletzt = GEFRAGT.get(p.getUUID());
            if (zuletzt == null || jetzt - zuletzt > NEU_FRAGEN_MS) fragen.add(p.getUUID());
            if (fragen.size() >= 100) break;
        }
        if (fragen.isEmpty()) return;
        for (UUID u : fragen) GEFRAGT.put(u, jetzt);
        JsonObject args = new JsonObject();
        JsonArray liste = new JsonArray();
        for (UUID u : fragen) liste.add(u.toString().replace("-", ""));
        args.add("uuids", liste);
        Social.request("cosmetics.get", args).whenComplete((r, fehler) -> {
            if (fehler != null) { pruefeFehler(fehler); return; }
            JsonObject spieler = r.getAsJsonObject("players");
            for (UUID u : fragen) {
                JsonElement e = spieler == null ? null : spieler.get(u.toString().replace("-", ""));
                if (e == null || !e.isJsonObject()) { Cosmetics.setzeFremde(u, null); BILD.remove(u); continue; }
                JsonObject o = e.getAsJsonObject();
                Cosmetics.setzeFremde(u, new Cosmetics.Auswahl(text(o, "cape"), text(o, "hat"), Zubehoer.auraFuer(text(o, "particles")),
                        o.has("density") ? o.get("density").getAsInt() : 2,
                        text(o, "bandana"), text(o, "face"), text(o, "back"), text(o, "shield")));
                if (o.has("image") && o.get("image").isJsonPrimitive()) BILD.put(u, o.get("image").getAsString());
                else BILD.remove(u);
            }
        });
    }

    private static String text(JsonObject o, String k) {
        return o.has(k) && o.get(k).isJsonPrimitive() ? o.get(k).getAsString() : "";
    }

    /** Alter Freunde-Server ohne Cosmetics: nicht weiter fragen. */
    private static void pruefeFehler(Throwable fehler) {
        if (String.valueOf(Social.errorOf(fehler)).contains("Unknown request")) serverKannEs = false;
    }

    private static void hochladen() {
        Cosmetics.Auswahl a = Cosmetics.eigene();
        Thread t = new Thread(() -> {
            try {
                if (EigenesCape.ID.equals(a.cape()) && EigenesCape.vorhanden()) {
                    byte[] jpeg = alsJpeg();
                    String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(jpeg)).substring(0, 32);
                    if (!hash.equals(hochgeladenesBild)) {
                        Social.request("cosmetics.image", Social.args("image", Base64.getEncoder().encodeToString(jpeg))).get(30, TimeUnit.SECONDS);
                        hochgeladenesBild = hash;
                    }
                }
                Social.request("cosmetics.set", Social.args("cape", a.cape(), "hat", a.hut(), "particles", a.partikel(), "density", a.dichte(),
                        "bandana", a.bandana(), "face", a.gesicht(), "back", a.ruecken(), "shield", a.schild()))
                        .get(20, TimeUnit.SECONDS);
            } catch (Throwable e) {
                pruefeFehler(e.getCause() != null ? e.getCause() : e);
                com.vortex.client.core.Errors.note("CosmeticsSync", "Upload failed: " + Social.errorOf(e));
            }
        }, "Vortex-Cosmetics-Upload");
        t.setDaemon(true);
        t.start();
    }

    /** Das fertige Cape (512x256) als JPEG -- passt sicher unter die 80 KB des Servers. */
    private static byte[] alsJpeg() throws Exception {
        BufferedImage argb = ImageIO.read(EigenesCape.datei().toFile());
        BufferedImage rgb = new BufferedImage(argb.getWidth(), argb.getHeight(), BufferedImage.TYPE_INT_RGB);
        rgb.getGraphics().drawImage(argb, 0, 0, null);
        for (float q : new float[] { 0.88f, 0.7f, 0.5f }) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageWriter w = ImageIO.getImageWritersByFormatName("jpeg").next();
            try (MemoryCacheImageOutputStream ios = new MemoryCacheImageOutputStream(out)) {
                w.setOutput(ios);
                ImageWriteParam p = w.getDefaultWriteParam();
                p.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                p.setCompressionQuality(q);
                w.write(null, new IIOImage(rgb, null, null), p);
            } finally {
                w.dispose();
            }
            if (out.size() <= 78 * 1024) return out.toByteArray();
        }
        throw new IllegalStateException("The cape picture is too detailed to upload.");
    }
}
