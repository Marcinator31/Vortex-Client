package com.vortex.client.hud;

import com.vortex.client.module.ModuleManager;
import com.vortex.client.module.modules.DamageNumbersModule;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Schadenszahlen (siehe DamageNumbersModule).
 *
 * Jeden Tick: Leben + Absorption jedes Wesens in Reichweite merken. Sinkt der
 * Wert, entsteht eine Zahl ueber dem Kopf, die nach oben schwebt und verblasst.
 * Gezeichnet im HUD (Weltpunkt -> Bildschirm), damit sie immer lesbar bleibt.
 */
public final class DamageNumbers {

    private DamageNumbers() {}

    private static final class Num {
        final double x, y, z;
        final float amount;
        final boolean heal;
        final long born = System.currentTimeMillis();
        final double drift = ThreadLocalRandom.current().nextDouble(-0.35, 0.35);
        Num(double x, double y, double z, float amount, boolean heal) { this.x = x; this.y = y; this.z = z; this.amount = amount; this.heal = heal; }
    }

    private static final Map<Integer, Float> LAST = new HashMap<>();
    private static final List<Num> NUMS = new ArrayList<>();

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(DamageNumbers::tick);
    }

    private static void tick(Minecraft mc) {
        DamageNumbersModule m = ModuleManager.INSTANCE.get(DamageNumbersModule.class);
        if (m == null || !m.isEnabled() || mc.player == null || mc.level == null) {
            LAST.clear();
            synchronized (NUMS) { NUMS.clear(); }
            return;
        }
        double range = m.range.get();
        Map<Integer, Float> seen = new HashMap<>();
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof LivingEntity le)) continue;
            if (le == mc.player && !m.showSelf.get()) continue;
            if (m.onlyPlayers.get() && !(le instanceof Player)) continue;
            if (le.distanceToSqr(mc.player) > range * range) continue;
            if (le.isInvisible() && le != mc.player) continue;
            float now = le.getHealth() + le.getAbsorptionAmount();
            Float before = LAST.get(le.getId());
            seen.put(le.getId(), now);
            if (before == null) continue;
            float diff = before - now;
            if (Math.abs(diff) < 0.05f) continue;
            if (diff < 0 && !m.showHealing.get()) continue;
            if (diff > 0 && m.onlyMyHits.get() && !CombatFx.hitRecently(le, 600)) continue;
            synchronized (NUMS) {
                NUMS.add(new Num(le.getX(), le.getY() + le.getBbHeight() + 0.25, le.getZ(), Math.abs(diff), diff < 0));
                while (NUMS.size() > 60) NUMS.remove(0);
            }
        }
        LAST.clear();
        LAST.putAll(seen);
    }

    public static void render(GuiGraphicsExtractor ctx, Minecraft mc) {
        DamageNumbersModule m = ModuleManager.INSTANCE.get(DamageNumbersModule.class);
        if (m == null || !m.isEnabled() || mc.player == null || mc.font == null) return;
        List<Num> copy;
        synchronized (NUMS) { if (NUMS.isEmpty()) return; copy = new ArrayList<>(NUMS); }
        long now = System.currentTimeMillis();
        long life = Math.round(m.duration.get() * 1000);
        ScreenProject.begin(mc, ctx.guiWidth(), ctx.guiHeight());
        boolean hearts = m.unit.getIndex() == 1;
        for (Num n : copy) {
            long age = now - n.born;
            if (age > life) continue;
            float t = age / (float) life;
            double[] p = ScreenProject.project(n.x + n.drift * t, n.y + 0.9 * t, n.z + n.drift * t * 0.5);
            if (p == null) continue;
            float v = hearts ? n.amount / 2f : n.amount;
            String text = (n.heal ? "+" : "") + (v >= 10 ? String.valueOf(Math.round(v)) : String.format(Locale.ROOT, "%.1f", v)) + (hearts ? "❤" : "");
            int base = n.heal ? 0x55FF77 : n.amount >= 8 ? 0xFF4040 : n.amount >= 4 ? 0xFFB23F : 0xFFFFFF;
            float alpha = t < 0.7f ? 1f : Math.max(0f, 1f - (t - 0.7f) / 0.3f);
            // Kurz "aufploppen", dann normal; weiter weg = kleiner (aber lesbar)
            float pop = age < 120 ? 1f + 0.35f * (1f - age / 120f) : 1f;
            float s = (float) (m.size.get() * pop * Math.max(0.55, Math.min(1.6, 6.0 / Math.max(1.0, p[2]))));
            int argb = ((int) (alpha * 255) << 24) | base;
            if (alpha < 0.05f) continue;
            var pose = ctx.pose();
            pose.pushMatrix();
            pose.translate((float) p[0], (float) p[1]);
            pose.scale(s, s);
            ctx.text(mc.font, Component.literal(text), -mc.font.width(text) / 2, -4, argb, true);
            pose.popMatrix();
        }
        synchronized (NUMS) {
            for (Iterator<Num> it = NUMS.iterator(); it.hasNext();) if (now - it.next().born > life) it.remove();
        }
    }
}
