package com.vortex.legacy.module.pvp;

import com.vortex.legacy.core.BoolSetting;
import com.vortex.legacy.core.Module;
import com.vortex.legacy.core.ModuleManager;
import com.vortex.legacy.core.NumberSetting;
import net.minecraft.client.particle.ParticleType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;

/** Mehr Partikel bei Treffern: Kritisch-Funken und Verzauberungs-Glitzer bei jedem Schlag. */
public class HitEffects extends Module {
    public final BoolSetting crits = add(new BoolSetting("Always Crit Particles", true));
    public final BoolSetting sharp = add(new BoolSetting("Always Sharpness Particles", false));
    public final NumberSetting multiplier = add(new NumberSetting("Amount", 1, 1, 5, 1));

    public HitEffects() { super("Hit Effects", Category.PVP, "More particles when you hit something."); }

    public static void onAttack(Entity target) {
        HitEffects h = ModuleManager.INSTANCE.get(HitEffects.class);
        if (h == null || !h.isEnabled() || !(target instanceof LivingEntity)) return;
        for (int i = 0; i < h.multiplier.getInt(); i++) {
            if (h.crits.get()) mc.particleManager.addEmitter(target, ParticleType.CRIT);
            if (h.sharp.get()) mc.particleManager.addEmitter(target, ParticleType.CRIT_MAGIC);
        }
    }
}
