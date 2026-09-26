package com.vortex.client.hud;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Merkt sich Treffer fuer die Reichweiten- und die Combo-Anzeige.
 *
 * REICHWEITE: gemessen im Moment des Schlags, von den Augen bis zum naechsten
 * Punkt der Hitbox -- genau die Strecke, die auch der Server prueft. Die Mitte
 * des Gegners zu nehmen waere bequemer, zeigt aber bis zu 0,3 Bloecke zu viel.
 *
 * COMBO: zaehlt nur BESTAETIGTE Treffer. Ein Schlag zaehlt, wenn der Gegner
 * kurz danach sichtbar Schaden nimmt (seine Treffer-Animation beginnt). Ein
 * Schlag in die Unverwundbarkeit hinein zaehlt also nicht -- sonst waere die
 * Zahl beim Klicken einfach die Klickrate. Nimmst du selbst Schaden, ist die
 * Combo vorbei.
 */
public final class CombatTracker {

    private CombatTracker() {}

    private static double letzteReichweite = -1;
    private static long reichweiteZeit = 0;

    private static int combo = 0;
    private static long comboZeit = 0;

    private static Entity ziel = null;
    private static long zielSchlag = 0;
    private static int zielHurtVorher = 0;
    private static int eigenHurtVorher = 0;

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(CombatTracker::tick);
    }

    /** Vom Mixin: der Spieler schlaegt gerade zu. */
    public static void schlag(Entity target) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || target == null) return;
        Vec3 auge = mc.player.getEyePosition();
        AABB box = target.getBoundingBox();
        double x = Math.max(box.minX, Math.min(auge.x, box.maxX));
        double y = Math.max(box.minY, Math.min(auge.y, box.maxY));
        double z = Math.max(box.minZ, Math.min(auge.z, box.maxZ));
        letzteReichweite = auge.distanceTo(new Vec3(x, y, z));
        reichweiteZeit = System.currentTimeMillis();

        ziel = target;
        zielSchlag = System.currentTimeMillis();
        zielHurtVorher = (target instanceof LivingEntity le) ? le.hurtTime : 0;
    }

    private static void tick(Minecraft mc) {
        if (mc.player == null) {
            combo = 0;
            ziel = null;
            return;
        }
        long jetzt = System.currentTimeMillis();

        // Eigener Treffer bestaetigt: Animation des Gegners beginnt neu.
        if (ziel instanceof LivingEntity le && jetzt - zielSchlag < 600) {
            int h = le.hurtTime;
            if (h > zielHurtVorher) {
                combo++;
                comboZeit = jetzt;
                ziel = null;   // pro Schlag hoechstens einmal zaehlen
            } else {
                zielHurtVorher = h;
            }
        }

        // Selbst getroffen: Combo vorbei.
        int eigen = mc.player.hurtTime;
        if (eigen > eigenHurtVorher) combo = 0;
        eigenHurtVorher = eigen;
    }

    /** Reichweite des letzten Schlags, -1 wenn keiner. */
    public static double reichweite() {
        return letzteReichweite;
    }

    public static long reichweiteAlter() {
        return System.currentTimeMillis() - reichweiteZeit;
    }

    /** Aktuelle Combo; laeuft nach der eingestellten Zeit ab. */
    public static int combo(long ablaufMs) {
        if (combo > 0 && System.currentTimeMillis() - comboZeit > ablaufMs) combo = 0;
        return combo;
    }

    public static long comboAlter() {
        return System.currentTimeMillis() - comboZeit;
    }
}
