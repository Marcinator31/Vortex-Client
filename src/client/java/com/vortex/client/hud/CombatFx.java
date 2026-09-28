package com.vortex.client.hud;

import com.vortex.client.module.ModuleManager;
import com.vortex.client.module.modules.HitEffectsModule;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;

/**
 * Treffer- und Kill-Effekte (siehe HitEffectsModule). Nur lokal: Toene
 * direkt ueber den Sound-Manager, Effekte als Client-Partikel bzw. ein Blitz,
 * den nur diese Welt-Kopie kennt (negative ID, kein Schaden, kein Feuer).
 */
public final class CombatFx {

    private CombatFx() {}

    private static final class Hit { long at; boolean confirmed; int hurtBefore; }
    private static final Map<Integer, Hit> HITS = new HashMap<>();
    private static final Map<Integer, Long> KILLED = new HashMap<>();
    private static int nextId = -2_000_000;

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(CombatFx::tick);
    }

    /** Aus GameModeHookMixin: Moment des Schlags. */
    public static void schlag(Entity target) {
        if (!(target instanceof LivingEntity le)) return;
        Hit h = new Hit();
        h.at = System.currentTimeMillis();
        h.hurtBefore = le.hurtTime;
        HITS.put(le.getId(), h);
    }

    /** Wurde dieses Wesen in den letzten ms Millisekunden von mir geschlagen? */
    public static boolean hitRecently(Entity e, long ms) {
        Hit h = HITS.get(e.getId());
        return h != null && System.currentTimeMillis() - h.at <= ms;
    }

    private static void tick(Minecraft mc) {
        if (mc.level == null || mc.player == null) { HITS.clear(); KILLED.clear(); return; }
        HitEffectsModule m = ModuleManager.INSTANCE.get(HitEffectsModule.class);
        if (m == null || !m.isEnabled()) { HITS.clear(); return; }
        long now = System.currentTimeMillis();
        KILLED.values().removeIf(t -> now - t > 10000);
        for (Iterator<Map.Entry<Integer, Hit>> it = HITS.entrySet().iterator(); it.hasNext();) {
            var e = it.next();
            Hit h = e.getValue();
            if (now - h.at > 3000) { it.remove(); continue; }
            Entity ent = mc.level.getEntity(e.getKey());
            if (!(ent instanceof LivingEntity le)) continue;
            if (m.onlyPlayers.get() && !(le instanceof Player)) { it.remove(); continue; }
            // Treffer bestaetigt: das Ziel zuckt (hurtTime springt auf 10) kurz nach dem Schlag
            if (!h.confirmed && now - h.at < 500 && le.hurtTime > 0 && le.hurtTime >= h.hurtBefore) {
                h.confirmed = true;
                hitSound(mc, m);
            }
            if (h.confirmed && (le.isDeadOrDying() || le.getHealth() <= 0f) && !KILLED.containsKey(le.getId())) {
                KILLED.put(le.getId(), now);
                it.remove();
                killSound(mc, m);
                killEffect(mc, m, le);
            }
        }
    }

    private static void hitSound(Minecraft mc, HitEffectsModule m) {
        Object s = switch (m.hitSound.getIndex()) {
            case 1 -> SoundEvents.UI_BUTTON_CLICK;
            case 2 -> SoundEvents.NOTE_BLOCK_PLING;
            case 3 -> SoundEvents.NOTE_BLOCK_BELL;
            case 4 -> SoundEvents.NOTE_BLOCK_BASS;
            case 5 -> SoundEvents.NOTE_BLOCK_HAT;
            case 6 -> SoundEvents.NOTE_BLOCK_CHIME;
            case 7 -> SoundEvents.EXPERIENCE_ORB_PICKUP;
            case 8 -> SoundEvents.PLAYER_ATTACK_CRIT;
            default -> null;
        };
        play(mc, s, m.hitPitch.getFloat(), m.hitVolume.getFloat());
    }

    private static void killSound(Minecraft mc, HitEffectsModule m) {
        Object s = switch (m.killSound.getIndex()) {
            case 1 -> SoundEvents.PLAYER_LEVELUP;
            case 2 -> SoundEvents.UI_TOAST_CHALLENGE_COMPLETE;
            case 3 -> SoundEvents.TOTEM_USE;
            case 4 -> SoundEvents.LIGHTNING_BOLT_THUNDER;
            case 5 -> SoundEvents.ANVIL_LAND;
            case 6 -> SoundEvents.FIREWORK_ROCKET_BLAST;
            case 7 -> SoundEvents.NOTE_BLOCK_BELL;
            default -> null;
        };
        play(mc, s, 1.0f, m.killVolume.getFloat());
    }

    @SuppressWarnings("unchecked")
    private static void play(Minecraft mc, Object s, float pitch, float volume) {
        if (s == null) return;
        SoundEvent ev = s instanceof Holder<?> h ? ((Holder<SoundEvent>) h).value() : (SoundEvent) s;
        mc.getSoundManager().play(SimpleSoundInstance.forUI(ev, pitch, volume));
    }

    private static void killEffect(Minecraft mc, HitEffectsModule m, LivingEntity le) {
        ClientLevel level = mc.level;
        double x = le.getX(), y = le.getY(), z = le.getZ(), hgt = le.getBbHeight();
        switch (m.killEffect.getIndex()) {
            case 1 -> lightning(level, x, y, z);
            case 2 -> burst(level, ParticleTypes.TOTEM_OF_UNDYING, x, y + hgt / 2, z, 60, 0.6);
            case 3 -> burst(level, ParticleTypes.FLAME, x, y + hgt / 2, z, 40, 0.12);
            case 4 -> burst(level, ParticleTypes.SOUL_FIRE_FLAME, x, y + hgt / 2, z, 40, 0.12);
            case 5 -> burst(level, ParticleTypes.HEART, x, y + hgt, z, 12, 0.15);
            case 6 -> burst(level, ParticleTypes.EXPLOSION, x, y + hgt / 2, z, 4, 0.0);
            case 7 -> burst(level, new BlockParticleOption(ParticleTypes.BLOCK, Blocks.REDSTONE_BLOCK.defaultBlockState()), x, y + hgt / 2, z, 50, 0.2);
            case 8 -> burst(level, ParticleTypes.FIREWORK, x, y + hgt, z, 50, 0.25);
            default -> { }
        }
    }

    private static void burst(ClientLevel level, ParticleOptions p, double x, double y, double z, int n, double speed) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int i = 0; i < n; i++) {
            level.addParticle(p, x + r.nextDouble(-0.3, 0.3), y + r.nextDouble(-0.5, 0.5), z + r.nextDouble(-0.3, 0.3),
                    r.nextDouble(-1, 1) * speed, r.nextDouble(0, 1) * speed + 0.05, r.nextDouble(-1, 1) * speed);
        }
    }

    /** Ein Blitz nur fuer dich: visualOnly, eigene negative ID (kollidiert nie mit Server-IDs). */
    private static void lightning(ClientLevel level, double x, double y, double z) {
        try {
            // Ueber die Registry statt ueber das Feld: der Feldname hat sich in 26.2 geaendert.
            @SuppressWarnings("unchecked")
            EntityType<LightningBolt> type = (EntityType<LightningBolt>) net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE
                    .getValue(net.minecraft.resources.Identifier.withDefaultNamespace("lightning_bolt"));
            LightningBolt bolt = new LightningBolt(type, level);
            bolt.setVisualOnly(true);
            bolt.setId(nextId--);
            bolt.snapTo(x, y, z);
            level.addEntity(bolt);
        } catch (Throwable e) {
            burst(level, ParticleTypes.END_ROD, x, y + 1, z, 30, 0.2);
        }
    }
}
