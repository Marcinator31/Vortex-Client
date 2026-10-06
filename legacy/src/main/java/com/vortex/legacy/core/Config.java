package com.vortex.legacy.core;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import com.vortex.legacy.VortexLegacy;
import net.minecraft.client.MinecraftClient;

/**
 * Speichern: config/vortexclient/legacy.txt, eine Zeile je Einstellung:
 *   Modul|Einstellung=Wert   (Modul an/aus: Modul|Enabled=true)
 * Allgemeine Client-Einstellungen unter "__client__".
 */
public final class Config {
    private Config() {}
    private static boolean dirty;
    private static long last;

    public static File dir() {
        File d = new File(MinecraftClient.getInstance().runDirectory, "config/vortexclient");
        d.mkdirs();
        return d;
    }
    private static File file() { return new File(dir(), "legacy.txt"); }

    public static void markDirty() { dirty = true; }

    public static void load() {
        File f = file();
        boolean firstStart = !f.exists();
        for (Module m : ModuleManager.INSTANCE.all()) if (firstStart && m.defaultEnabled()) m.setEnabled(true);
        if (firstStart) return;
        try {
            for (String line : Files.readAllLines(f.toPath(), StandardCharsets.UTF_8)) {
                int bar = line.indexOf('|'), eq = line.indexOf('=');
                if (bar <= 0 || eq <= bar) continue;
                String mod = line.substring(0, bar), set = line.substring(bar + 1, eq), val = line.substring(eq + 1);
                if (mod.equals("__client__")) { ClientSettings.INSTANCE.load(set, val); continue; }
                Module m = ModuleManager.INSTANCE.byName(mod);
                if (m == null) continue;
                if (set.equals("Enabled")) { m.setEnabled(Boolean.parseBoolean(val)); continue; }
                if (set.equals("Key")) { m.key.deserialize(val); continue; }
                for (Setting s : m.getSettings()) if (s.getName().equals(set)) s.deserialize(val);
            }
        } catch (IOException e) {
            VortexLegacy.LOG.warn("[Vortex] Could not read config: " + e);
        }
    }

    public static void save() {
        List<String> out = new ArrayList<String>();
        for (Setting s : ClientSettings.INSTANCE.all()) out.add("__client__|" + s.getName() + "=" + s.serialize());
        for (Module m : ModuleManager.INSTANCE.all()) {
            out.add(m.getName() + "|Enabled=" + m.isEnabled());
            out.add(m.getName() + "|Key=" + m.key.serialize());
            for (Setting s : m.getSettings()) out.add(m.getName() + "|" + s.getName() + "=" + s.serialize());
        }
        try {
            File tmp = new File(dir(), "legacy.txt.tmp");
            Files.write(tmp.toPath(), out, StandardCharsets.UTF_8);
            Files.move(tmp.toPath(), file().toPath(), StandardCopyOption.REPLACE_EXISTING);
            dirty = false;
        } catch (IOException e) {
            VortexLegacy.LOG.warn("[Vortex] Could not save config: " + e);
        }
    }

    /** Hoechstens alle 2 s speichern, wenn sich etwas geaendert hat. */
    public static void tick() {
        long now = System.currentTimeMillis();
        if (dirty && now - last > 2000) { last = now; save(); }
    }
}
