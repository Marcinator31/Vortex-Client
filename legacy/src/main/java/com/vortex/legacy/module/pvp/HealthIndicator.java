package com.vortex.legacy.module.pvp;

import com.vortex.legacy.core.Module;
import com.vortex.legacy.core.ModeSetting;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;

/** Leben neben dem Namen anderer Spieler. */
public class HealthIndicator extends Module {
    public final ModeSetting format = add(new ModeSetting("Format", 0, "Hearts", "HP", "Percent"));
    public HealthIndicator() { super("Health Indicator", Category.PVP, "Shows the health of players next to their name."); }

    public String label(LivingEntity e, String name) {
        if (!(e instanceof PlayerEntity)) return name;
        float hp = e.getHealth() + e.getAbsorption(), max = e.getMaxHealth();
        float f = e.getHealth() / Math.max(1, max);
        String col = f > 0.6f ? "§a" : f > 0.3f ? "§e" : "§c";
        String v;
        if (format.is("HP")) v = String.format("%.0f", hp);
        else if (format.is("Percent")) v = Math.round(hp * 100 / Math.max(1, max)) + "%";
        else v = String.format("%.1f ❤", hp / 2f);
        return name + " " + col + v;
    }
}
