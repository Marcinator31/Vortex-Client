package com.vortex.client.core;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import java.util.ArrayList;
import java.util.List;

/**
 * The world's entities, gathered once per tick.
 *
 * WHY THIS EXISTS: several renderers walked the entire entity list on every
 * frame. At two hundred frames a second that is the same work done two hundred
 * times to produce an answer that changes twenty times a second at most --
 * entities move on ticks, not on frames.
 *
 * The list is built once per tick and handed out unchanged. Ten renderers now
 * share one walk instead of doing ten of their own.
 *
 * The lists are rebuilt rather than cleared and refilled, because a renderer
 * may still be reading the previous one on another thread. Handing out a list
 * that is being emptied underneath the reader is the sort of fault that shows
 * up once a week and never where you are looking.
 */
public final class EntityCache {

    private static volatile List<Entity> all = List.of();
    private static volatile List<net.minecraft.world.entity.item.ItemEntity> items = List.of();
    private static volatile List<net.minecraft.world.entity.LivingEntity> living = List.of();

    /** Ticks since the last rebuild, so a slow frame cannot starve it. */
    private static long tick = 0;

    private EntityCache() {}

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            long t0 = System.nanoTime();
            try {
                rebuild(client);
            } catch (Throwable pvpErr) {
                Errors.report("EntityCache", pvpErr);
            } finally {
                Profiler.record("EntityCache", System.nanoTime() - t0);
            }
        });
    }

    private static void rebuild(Minecraft client) {
        tick++;
        if (client.level == null) {
            all = List.of();
            items = List.of();
            living = List.of();
            return;
        }

        List<Entity> newAll = new ArrayList<>();
        List<net.minecraft.world.entity.item.ItemEntity> newItems = new ArrayList<>();
        List<net.minecraft.world.entity.LivingEntity> newLiving = new ArrayList<>();

        for (Entity e : client.level.entitiesForRendering()) {
            if (e == null) continue;
            newAll.add(e);
            // Sorted here, once, rather than by every renderer separately --
            // the instanceof checks were being repeated in half a dozen loops.
            if (e instanceof net.minecraft.world.entity.item.ItemEntity item) {
                newItems.add(item);
            } else if (e instanceof net.minecraft.world.entity.LivingEntity le) {
                newLiving.add(le);
            }
        }

        all = newAll;
        items = newItems;
        living = newLiving;
    }

    /** Every entity, as of the last tick. */
    public static List<Entity> all() {
        return all;
    }

    /** Dropped items only. */
    public static List<net.minecraft.world.entity.item.ItemEntity> items() {
        return items;
    }

    /** Living entities only -- players and mobs. */
    public static List<net.minecraft.world.entity.LivingEntity> living() {
        return living;
    }

    /** How many ticks have been counted. Useful for staggering work. */
    public static long tick() {
        return tick;
    }
}
