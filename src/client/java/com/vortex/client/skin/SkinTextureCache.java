package com.vortex.client.skin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import java.io.InputStream;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

/**
 * Laedt Skin-PNGs als Texturen, damit sie in der Oberflaeche angezeigt werden
 * koennen.
 *
 * Jede Datei wird nur EINMAL geladen und danach unter einer festen Kennung
 * behalten. Ohne diese Zwischenspeicherung wuerde bei jedem Bild neu von der
 * Festplatte gelesen und eine neue Textur auf der Grafikkarte angelegt -- das
 * waere eine sichere Methode, den Speicher volllaufen zu lassen.
 *
 * Kennungen muessen kleingeschrieben sein und duerfen nur bestimmte Zeichen
 * enthalten, deshalb wird der Dateiname entsprechend umgeschrieben.
 */
public final class SkinTextureCache {

    private static final Map<String, Identifier> CACHE = new HashMap<>();
    /** Dateien, die sich nicht laden liessen -- nicht endlos erneut versuchen. */
    private static final Map<String, Boolean> FAILED = new HashMap<>();

    private SkinTextureCache() {}

    /**
     * Liefert die Textur-Kennung fuer einen Skin, oder null wenn die Datei
     * fehlt oder unlesbar ist.
     */
    public static Identifier get(SkinWardrobe.Skin skin) {
        if (skin == null) return null;
        String key = skin.fileName;
        Identifier cached = CACHE.get(key);
        if (cached != null) return cached;
        if (FAILED.containsKey(key)) return null;

        try {
            if (!skin.exists()) {
                FAILED.put(key, true);
                return null;
            }
            NativeImage image;
            try (InputStream in = Files.newInputStream(skin.path())) {
                image = NativeImage.read(in);
            }
            // Sinnvolle Skin-Groessen sind 64x64 (neu) oder 64x32 (alt).
            if (image.getWidth() != 64 || (image.getHeight() != 64 && image.getHeight() != 32)) {
                image.close();
                FAILED.put(key, true);
                com.vortex.client.core.Errors.note("SkinTextureCache",
                        skin.fileName + ": ungewoehnliche Groesse, erwartet 64x64");
                return null;
            }

            Identifier id = Identifier.of("vortexclient", "skins/" + safeKey(key));
            MinecraftClient.getInstance().getTextureManager().registerTexture(
                    id, new NativeImageBackedTexture(() -> "vortexclient-skin", image));
            CACHE.put(key, id);
            return id;
        } catch (Throwable pvpErr) {
            FAILED.put(key, true);
            com.vortex.client.core.Errors.report("SkinTextureCache.get", pvpErr);
            return null;
        }
    }

    /** Nach dem Loeschen einer Datei aufraeumen. */
    public static void forget(String fileName) {
        CACHE.remove(fileName);
        FAILED.remove(fileName);
    }

    /** Erlaubte Zeichen fuer eine Kennung: klein, Ziffern, Unterstrich, Punkt. */
    private static String safeKey(String s) {
        StringBuilder sb = new StringBuilder();
        for (char c : s.toLowerCase(java.util.Locale.ROOT).toCharArray()) {
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
                    || c == '_' || c == '.' || c == '-') {
                sb.append(c);
            } else {
                sb.append('_');
            }
        }
        return sb.toString();
    }
}
