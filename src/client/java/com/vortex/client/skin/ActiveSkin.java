package com.vortex.client.skin;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import com.mojang.blaze3d.platform.NativeImage;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
/**
 * Verwaltet den gerade angewendeten Skin.
 *
 * WICHTIG ZUM VERSTAENDNIS: Das ist ein rein clientseitiger Wechsel. Der Skin
 * wird nur bei dir dargestellt -- andere Spieler sehen weiterhin den Skin, der
 * auf deinem Konto hinterlegt ist. Ihn wirklich auf dem Konto zu aendern, wuerde
 * einen gueltigen Anmelde-Token brauchen (also den funktionierenden
 * Account-Wechsler) und ist etwas voellig anderes.
 *
 * Die Auswahl ueberlebt einen Neustart: sie wird in einer kleinen Textdatei
 * neben der Garderobe abgelegt.
 */
public final class ActiveSkin {

    private static SkinWardrobe.Skin active = null;
    private static Identifier textureId = null;
    private static boolean loaded = false;

    /**
     * Welche Einbindungs-Variante benutzt wird:
     *   1 = Textur-Referenz (Standard)
     *   2 = Kennung direkt
     * Umschaltbar in der Garderobe, falls die Darstellung nicht klappt --
     * so muss dafuer nicht neu gebaut werden.
     */
    private static int variant = 1;

    public static synchronized int getVariant() { return variant; }

    public static synchronized void toggleVariant() {
        variant = (variant == 1) ? 2 : 1;
        textureId = null;   // Textur neu anmelden
        saveState();
    }

    private ActiveSkin() {}

    private static Path stateFile() {
        Path neu = SkinWardrobe.skinDir().resolve("active.txt");
        // Alten Dateinamen uebernehmen, damit der gewaehlte Skin erhalten bleibt.
        try {
            Path alt = SkinWardrobe.skinDir().resolve("aktiv.txt");
            if (!Files.exists(neu) && Files.exists(alt)) Files.move(alt, neu);
        } catch (Throwable pvpErr) {
            com.vortex.client.core.Errors.report("ActiveSkin.stateFile", pvpErr);
        }
        return neu;
    }

    /** Der aktuell angewendete Skin, oder null wenn der eigene benutzt wird. */
    public static synchronized SkinWardrobe.Skin get() {
        ensureLoaded();
        return active;
    }

    /** Textur-Kennung des aktiven Skins, oder null. */
    public static synchronized Identifier textureId() {
        ensureLoaded();
        if (active == null) return null;
        if (textureId == null) textureId = upload(active);
        return textureId;
    }

    /** Schlankes Modell (Alex) statt klassisch? */
    public static synchronized boolean isSlim() {
        ensureLoaded();
        return active != null && active.slim;
    }

    /** Setzt den anzuwendenden Skin (null = eigener Skin). */
    public static synchronized void set(SkinWardrobe.Skin skin) {
        active = skin;
        textureId = null;      // wird beim naechsten Zugriff neu hochgeladen
        saveState();
    }

    public static synchronized void clear() {
        set(null);
    }

    /**
     * Laedt das PNG auf die Grafikkarte und liefert die Kennung.
     *
     * Bewusst eine eigene Kennung je Skin: so bleiben mehrere Skins nebeneinander
     * gueltig und ein Wechsel muss nichts wegwerfen.
     */
    private static Identifier upload(SkinWardrobe.Skin skin) {
        try {
            if (!skin.exists()) return null;
            NativeImage image;
            try (InputStream in = Files.newInputStream(skin.path())) {
                image = NativeImage.read(in);
            }
            if (image.getWidth() != 64 || (image.getHeight() != 64 && image.getHeight() != 32)) {
                image.close();
                com.vortex.client.core.Errors.note("ActiveSkin",
                        skin.fileName + ": wrong size (expected 64x64)");
                return null;
            }

            // Grundname OHNE Dateiendung.
            String base = "activeskin/" + safe(stripExt(skin.fileName));
            Identifier main = Identifier.fromNamespaceAndPath("vortexclient", base);

            var tm = Minecraft.getInstance().getTextureManager();
            var texture = new DynamicTexture(() -> "vortexclient-active-skin", image);

            // Die PlayerSkin-Ueberschreibung verwendet diese Kennung sowohl als
            // Asset-ID als auch als tatsaechlichen Texturpfad. Eine DynamicTexture
            // darf deshalb genau einmal unter dieser Kennung registriert werden;
            // Mehrfachregistrierungen derselben Instanz unter umgerechneten
            // Ressourcenpfaden koennen die Skin-UVs im Entity-Renderer verfälschen.
            tm.register(main, texture);
            com.vortex.client.core.Errors.note("ActiveSkin", "Textur angemeldet als " + main);
            return main;
        } catch (Throwable pvpErr) {
            com.vortex.client.core.Errors.report("ActiveSkin.upload", pvpErr);
            return null;
        }
    }

    /** Dateiendung abschneiden. */
    private static String stripExt(String fileName) {
        int i = fileName.lastIndexOf('.');
        return (i > 0) ? fileName.substring(0, i) : fileName;
    }

    private static String safe(String s) {
        StringBuilder sb = new StringBuilder();
        for (char c : s.toLowerCase(java.util.Locale.ROOT).toCharArray()) {
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
                    || c == '_' || c == '.' || c == '-') sb.append(c);
            else sb.append('_');
        }
        return sb.toString();
    }

    // ---- Speichern / Laden ----

    private static void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        try {
            Path f = stateFile();
            if (!Files.exists(f)) return;
            String raw = Files.readString(f, StandardCharsets.UTF_8);
            String[] parts = raw.split("\n");
            String name = parts.length > 0 ? parts[0].trim() : "";
            if (parts.length > 1) {
                try {
                    int v = Integer.parseInt(parts[1].trim());
                    if (v == 1 || v == 2) variant = v;
                } catch (Throwable ignored) { }
            }
            if (name.isEmpty()) return;
            for (SkinWardrobe.Skin s : SkinWardrobe.all()) {
                if (s.fileName.equalsIgnoreCase(name)) {
                    active = s;
                    return;
                }
            }
        } catch (Throwable pvpErr) {
            com.vortex.client.core.Errors.report("ActiveSkin.load", pvpErr);
        }
    }

    private static void saveState() {
        try {
            Files.createDirectories(SkinWardrobe.skinDir());
            Files.writeString(stateFile(),
                    (active == null ? "" : active.fileName) + "\n" + variant,
                    StandardCharsets.UTF_8);
        } catch (Throwable pvpErr) {
            com.vortex.client.core.Errors.report("ActiveSkin.save", pvpErr);
        }
    }
}
