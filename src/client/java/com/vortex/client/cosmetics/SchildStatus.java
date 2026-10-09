package com.vortex.client.cosmetics;

import com.vortex.client.module.ModuleManager;
import com.vortex.client.module.modules.ShieldStatusModule;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;

/**
 * Shield Status (eigener Nachbau, seit 4.30): faerbt Schilde nach Zustand ein.
 *
 *   gruen  = Schild oben und blockt
 *   gelb   = wird gerade hochgenommen (die ersten Ticks blockt er noch nicht)
 *   rot    = von einer Axt gebrochen (Abklingzeit); mit "Smooth Gradient"
 *            wird er waehrend der Abklingzeit langsam wieder normal,
 *            mit "Grey Out Broken" grau statt rot
 *
 * Funktioniert zusammen mit den Shield Skins: die Farbe wird beim Zeichnen
 * ueber den Skin gelegt. Gebrochene Schilde anderer Spieler erkennen wir am
 * Geraeusch "Schild bricht", das der Server an ihrer Position abspielt (die
 * Abklingzeit selbst schickt der Server nur dem eigenen Spieler).
 */
public final class SchildStatus {
    private SchildStatus() {}

    /** Abklingzeit nach einem Axt-Treffer (5 Sekunden). */
    private static final int GEBROCHEN_TICKS = 100;

    public static final int GRUEN = 0xFF6CFF6C, GELB = 0xFFFFD84A, ROT = 0xFFFF4A4A, GRAU = 0xFF7A7A7A;

    /** Entity-ID -> Spielzeit, bis zu der der Schild gebrochen ist. */
    private static final Map<Integer, Long> GEBROCHEN = new HashMap<>();

    /** Geraeusch "Schild bricht" an einer Position: naechsten Spieler dort merken. */
    public static void schildBricht(double x, double y, double z) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Player best = null;
        double bestD = 1.0;   // Position im Paket ist auf 1/8 Block gerundet
        for (Player p : mc.level.players()) {
            double d = p.distanceToSqr(x, y, z);
            if (d < bestD) { bestD = d; best = p; }
        }
        if (best == null) return;
        long jetzt = mc.level.getGameTime();
        GEBROCHEN.entrySet().removeIf(e -> e.getValue() < jetzt);
        GEBROCHEN.put(best.getId(), jetzt + GEBROCHEN_TICKS);
    }

    private static ShieldStatusModule modul() {
        try {
            ShieldStatusModule m = ModuleManager.INSTANCE.get(ShieldStatusModule.class);
            return m != null && m.isEnabled() ? m : null;
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Farbe fuer diesen Schild in der Hand dieses Wesens; 0 = normal.
     * Auf Skins schwaecher, damit das Motiv sichtbar bleibt (gebrochen bleibt deutlich).
     */
    public static int farbe(LivingEntity e, ItemStack stack, boolean skin) {
        if (e == null || stack == null || stack.isEmpty()) return 0;
        ShieldStatusModule m = modul();
        if (m == null) return 0;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return 0;
        boolean ich = e == mc.player;
        if (!ich && !m.opponentShields.get()) return 0;

        // Gebrochen? (Rest der Abklingzeit 1..0)
        float rest = 0;
        if (ich && e instanceof Player p) {
            rest = p.getCooldowns().getCooldownPercent(stack, 0f);
        } else {
            Long bis = GEBROCHEN.get(e.getId());
            if (bis != null) rest = Math.max(0, (bis - mc.level.getGameTime()) / (float) GEBROCHEN_TICKS);
        }
        if (rest > 0) {
            int voll = m.grayscaleBroken.get() ? GRAU : ROT;
            float staerke = m.smoothColor.get() ? Math.min(1f, 0.25f + rest) : 1f;
            return mischen(0xFFFFFFFF, voll, staerke * (skin ? 0.8f : 1f));
        }
        if (e.isUsingItem() && e.getUseItem() == stack) {
            int f = e.isBlocking() ? GRUEN : GELB;
            return skin ? mischen(0xFFFFFFFF, f, 0.5f) : f;
        }
        return 0;
    }

    public static int mischen(int a, int b, float t) {
        int r = 0xFF000000;
        for (int s = 0; s < 24; s += 8) {
            int ca = (a >> s) & 0xFF, cb = (b >> s) & 0xFF;
            r |= Math.round(ca + (cb - ca) * t) << s;
        }
        return r;
    }
}
