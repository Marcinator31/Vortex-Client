package com.vortex.client.module.modules;

import com.vortex.client.core.setting.NumberSetting;
import com.vortex.client.module.Module;

/**
 * Sound Control: Lautstaerke fuer einzelne Geraeusche, in Prozent (0 = stumm).
 *
 * Die Regler decken die Geraeusche ab, die am haeufigsten stoeren. Fuer jedes
 * andere gibt es /vsound: "/vsound recent" zeigt, was zuletzt zu hoeren war,
 * "/vsound set <id> <prozent>" stellt es ein.
 *
 * Wirkt nur auf das, was du hoerst -- andere Spieler merken nichts.
 */
public class SoundControlModule extends Module {

    public final NumberSetting explosions = pct("Explosions");
    public final NumberSetting totem = pct("Totem Pop");
    public final NumberSetting hits = pct("Hits");
    public final NumberSetting hurt = pct("Player Hurt");
    public final NumberSetting eating = pct("Eating & Drinking");
    public final NumberSetting fireworks = pct("Fireworks");
    public final NumberSetting anvil = pct("Anvils");
    public final NumberSetting bosses = pct("Wither & Dragon");
    public final NumberSetting weather = pct("Rain & Thunder");
    public final NumberSetting portal = pct("Portals");
    public final NumberSetting villagers = pct("Villagers");
    public final NumberSetting pistons = pct("Pistons");
    public final NumberSetting lava = pct("Lava");
    public final NumberSetting minecarts = pct("Minecarts");

    private static NumberSetting pct(String name) {
        return new NumberSetting(name, 100, 0, 100, 5);
    }

    public SoundControlModule() {
        super("Sound Control", Category.MISC);
        addSetting(explosions);
        addSetting(totem);
        addSetting(hits);
        addSetting(hurt);
        addSetting(eating);
        addSetting(fireworks);
        addSetting(anvil);
        addSetting(bosses);
        addSetting(weather);
        addSetting(portal);
        addSetting(villagers);
        addSetting(pistons);
        addSetting(lava);
        addSetting(minecarts);
    }

    /** Regler fuer diese Sound-ID (Pfad ohne "minecraft:"), sonst null. */
    public NumberSetting fuer(String p) {
        if (p.contains("explode") || p.equals("entity.generic.explode")) return explosions;
        if (p.equals("item.totem.use")) return totem;
        if (p.startsWith("entity.player.attack")) return hits;
        if (p.equals("entity.player.hurt") || p.startsWith("entity.player.hurt_")) return hurt;
        if (p.equals("entity.generic.eat") || p.equals("entity.generic.drink") || p.equals("entity.player.burp")
                || p.equals("item.honey_bottle.drink")) return eating;
        if (p.startsWith("entity.firework_rocket")) return fireworks;
        if (p.startsWith("block.anvil")) return anvil;
        if (p.startsWith("entity.wither.") || p.startsWith("entity.ender_dragon.")) return bosses;
        if (p.startsWith("weather.")) return weather;
        if (p.startsWith("block.portal") || p.startsWith("block.end_portal")) return portal;
        if (p.startsWith("entity.villager") || p.startsWith("entity.wandering_trader")) return villagers;
        if (p.startsWith("block.piston")) return pistons;
        if (p.startsWith("block.lava")) return lava;
        if (p.startsWith("entity.minecart")) return minecarts;
        return null;
    }
}
