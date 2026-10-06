package com.vortex.legacy.core;

import java.util.ArrayDeque;
import java.util.Deque;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/** Klicks (CPS), Treffer, Combo, Reichweite -- gefuettert von Mixins. */
public final class Combat {
    private Combat() {}

    private static final Deque<Long> LEFT = new ArrayDeque<Long>(), RIGHT = new ArrayDeque<Long>();
    public static int combo;
    private static long lastHit;
    public static double lastReach = -1;
    private static long lastReachAt;
    public static Entity lastTarget;
    public static long lastTargetAt;
    private static int lastHurt;

    public static void click(boolean left) {
        Deque<Long> d = left ? LEFT : RIGHT;
        d.addLast(System.currentTimeMillis());
        while (d.size() > 100) d.removeFirst();
    }

    public static int cps(boolean left) {
        Deque<Long> d = left ? LEFT : RIGHT;
        long cut = System.currentTimeMillis() - 1000;
        while (!d.isEmpty() && d.peekFirst() < cut) d.removeFirst();
        return d.size();
    }

    /** Wir schlagen ein Wesen (aus ClientPlayerInteractionManager.attackEntity). */
    public static void attack(Entity target) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || target == null) return;
        long now = System.currentTimeMillis();
        // Reichweite: Augen -> naechster Punkt der Trefferbox
        Vec3d eye = mc.player.getCameraPosVec(1f);
        Box b = target.getBoundingBox();
        double cx = Math.max(b.minX, Math.min(eye.x, b.maxX));
        double cy = Math.max(b.minY, Math.min(eye.y, b.maxY));
        double cz = Math.max(b.minZ, Math.min(eye.z, b.maxZ));
        lastReach = Math.sqrt((eye.x - cx) * (eye.x - cx) + (eye.y - cy) * (eye.y - cy) + (eye.z - cz) * (eye.z - cz));
        lastReachAt = now;
        if (target instanceof LivingEntity && ((LivingEntity) target).hurtTime <= 0) {
            combo = (lastTarget == target && now - lastHit < 3000) ? combo + 1 : 1;
            lastHit = now;
        }
        lastTarget = target;
        lastTargetAt = now;
    }

    public static void tick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) { combo = 0; return; }
        // Selbst getroffen -> Combo vorbei
        if (mc.player.hurtTime > lastHurt) combo = 0;
        lastHurt = mc.player.hurtTime;
        if (System.currentTimeMillis() - lastHit > 3000) combo = 0;
    }

    public static boolean reachFresh(long ms) { return lastReach >= 0 && System.currentTimeMillis() - lastReachAt < ms; }
}
