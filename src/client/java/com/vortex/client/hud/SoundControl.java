package com.vortex.client.hud;

import com.vortex.client.core.ListFile;
import com.vortex.client.core.setting.NumberSetting;
import com.vortex.client.module.ModuleManager;
import com.vortex.client.module.modules.SoundControlModule;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import net.minecraft.client.resources.sounds.SoundInstance;

/**
 * Sound Control (siehe SoundControlModule).
 *
 * Der SoundVolumeMixin fragt hier bei jedem Geraeusch nach einem Faktor. Die
 * Regler des Moduls gelten fuer Gruppen, die Datei sound-volumes.txt
 * (minecraft:id=prozent) fuer einzelne Geraeusche und hat Vorrang.
 */
public final class SoundControl {

    private SoundControl() {}

    public static final ListFile EIGENE = new ListFile("sound-volumes.txt");

    /** Zuletzt gehoerte Geraeusche (neueste zuletzt), fuer /vsound recent. */
    private static final LinkedHashSet<String> ZULETZT = new LinkedHashSet<>();

    private static Map<String, String> tabelle = Map.of();
    private static long tabelleStand = 0;

    public static float faktor(SoundInstance s) {
        try {
            if (s == null) return 1f;
            String id = String.valueOf(s.getIdentifier());
            merken(id);
            SoundControlModule m = ModuleManager.INSTANCE.get(SoundControlModule.class);
            if (m == null || !m.isEnabled()) return 1f;
            long jetzt = System.currentTimeMillis();
            if (jetzt - tabelleStand > 1000) {
                tabelle = EIGENE.tabelle();
                tabelleStand = jetzt;
            }
            String eigen = tabelle.get(id);
            if (eigen != null) {
                try { return Math.max(0f, Float.parseFloat(eigen) / 100f); } catch (NumberFormatException ignored) { }
            }
            String pfad = id.startsWith("minecraft:") ? id.substring(10) : id;
            NumberSetting r = m.fuer(pfad);
            return r == null ? 1f : (float) (r.get() / 100.0);
        } catch (Throwable e) {
            return 1f;
        }
    }

    private static void merken(String id) {
        synchronized (ZULETZT) {
            ZULETZT.remove(id);
            ZULETZT.add(id);
            if (ZULETZT.size() > 30) ZULETZT.remove(ZULETZT.iterator().next());
        }
    }

    public static List<String> zuletzt() {
        synchronized (ZULETZT) {
            List<String> l = new ArrayList<>(ZULETZT);
            java.util.Collections.reverse(l);
            return l;
        }
    }

    /** Setzt die Lautstaerke eines Geraeuschs (null = zuruecksetzen). */
    public static void setze(String id, Integer prozent) {
        String k = id.contains(":") ? id : "minecraft:" + id;
        EIGENE.setze(k, prozent == null ? null : String.valueOf(prozent));
        tabelleStand = 0;
    }
}
