package com.vortex.client.cosmetics;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.world.entity.player.Player;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Partikel, die um den Charakter fliegen.
 *
 * Jeder Client erzeugt sie selbst, bei sich und bei anderen Spielern mit
 * Vortex-Partikeln in der Naehe -- Partikel gehen nicht uebers Netz.
 *
 * Bewegungsarten:
 *   KREIS   -- zwei Punkte kreisen auf Brusthoehe um den Spieler
 *   SPIRALE -- steigen in einer Spirale von den Fuessen nach oben
 *   REGEN   -- fallen von ueber dem Kopf herab
 *   WOLKE   -- tauchen zufaellig um den Koerper herum auf
 *
 * In der Ich-Ansicht sind die eigenen aus (sie wuerden direkt vor der Kamera
 * schweben), ausser "Show in first person" ist an.
 */
public final class Partikel {
    private Partikel() {}

    public enum Art { KREIS, SPIRALE, REGEN, WOLKE }

    public record Effekt(String id, String name, SimpleParticleType typ, Art art, int alleTicks) {}

    private static final Map<String, Effekt> ALLE = new LinkedHashMap<>();

    private static void neu(String id, String name, SimpleParticleType typ, Art art, int alleTicks) {
        ALLE.put(id, new Effekt(id, name, typ, art, alleTicks));
    }

    static {
        neu("hearts", "Hearts", ParticleTypes.HEART, Art.KREIS, 6);
        neu("sparkles", "Sparkles", ParticleTypes.END_ROD, Art.KREIS, 3);
        neu("flames", "Flames", ParticleTypes.FLAME, Art.SPIRALE, 2);
        neu("soul_flames", "Soul Flames", ParticleTypes.SOUL_FIRE_FLAME, Art.SPIRALE, 2);
        neu("notes", "Music Notes", ParticleTypes.NOTE, Art.KREIS, 5);
        neu("cherry", "Cherry Blossoms", ParticleTypes.CHERRY_LEAVES, Art.REGEN, 2);
        neu("snow", "Snow", ParticleTypes.SNOWFLAKE, Art.REGEN, 2);
        neu("enchant", "Enchanting Runes", ParticleTypes.ENCHANT, Art.WOLKE, 1);
        neu("totem", "Totem", ParticleTypes.TOTEM_OF_UNDYING, Art.KREIS, 3);
        neu("emerald", "Emerald Sparks", ParticleTypes.HAPPY_VILLAGER, Art.WOLKE, 4);
        neu("magic", "Witch Magic", ParticleTypes.WITCH, Art.SPIRALE, 3);
        neu("glow", "Glow", ParticleTypes.GLOW, Art.WOLKE, 3);
        neu("electric", "Electric", ParticleTypes.ELECTRIC_SPARK, Art.KREIS, 2);
    }

    public static Map<String, Effekt> alle() { return ALLE; }

    private static long tick;

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            try { tick(mc); } catch (Throwable t) { com.vortex.client.core.Errors.report("Partikel", t); }
        });
    }

    private static void tick(Minecraft mc) {
        if (mc.level == null || mc.player == null || mc.isPaused()) return;
        tick++;
        for (Player p : mc.level.players()) {
            if (p.isInvisible() || p.isSpectator() || p.distanceToSqr(mc.player) > 48 * 48) continue;
            Cosmetics.Auswahl a = Cosmetics.fuer(p.getUUID());
            Effekt e = ALLE.get(a.partikel());
            if (e == null) continue;
            if (p == mc.player && mc.options.getCameraType().isFirstPerson() && !Cosmetics.partikelErstePerson()) continue;
            // Dichte 1-3: haeufiger erzeugen, nicht mehr auf einmal
            int alle = Math.max(1, Math.round(e.alleTicks() * (a.dichte() == 1 ? 2f : a.dichte() == 3 ? 0.5f : 1f)));
            if ((tick + p.getId()) % alle != 0) continue;
            erzeuge(mc, p, e);
        }
    }

    private static void erzeuge(Minecraft mc, Player p, Effekt e) {
        var r = p.getRandom();
        double x = p.getX(), y = p.getY(), z = p.getZ(), h = p.getBbHeight();
        double t = (tick + p.getId() * 13) * 0.18;
        switch (e.art()) {
            case KREIS -> {
                for (int i = 0; i < 2; i++) {
                    double w = t + i * Math.PI;
                    teil(mc, e, x + Math.cos(w) * 0.75, y + h * 0.55 + Math.sin(t * 0.7 + i) * 0.15, z + Math.sin(w) * 0.75, 0, 0, 0, r.nextFloat());
                }
            }
            case SPIRALE -> {
                double hoehe = (t * 0.35) % 1.0;
                teil(mc, e, x + Math.cos(t * 1.6) * 0.55, y + hoehe * h, z + Math.sin(t * 1.6) * 0.55, 0, 0.01, 0, 0);
            }
            case REGEN -> teil(mc, e, x + (r.nextDouble() - 0.5) * 1.2, y + h + 0.4, z + (r.nextDouble() - 0.5) * 1.2, 0, -0.02, 0, 0);
            case WOLKE -> teil(mc, e, x + (r.nextDouble() - 0.5) * 1.1, y + r.nextDouble() * h, z + (r.nextDouble() - 0.5) * 1.1, 0, 0.02, 0, 0);
        }
    }

    private static void teil(Minecraft mc, Effekt e, double x, double y, double z, double vx, double vy, double vz, float farbe) {
        // Noten: die erste Geschwindigkeit ist bei Minecraft die Farbe (0..1)
        if (e.typ() == ParticleTypes.NOTE) { vx = farbe; vy = 0; vz = 0; }
        mc.level.addParticle(e.typ(), x, y, z, vx, vy, vz);
    }
}
