package com.vortex.client.hud;

import com.vortex.client.module.ModuleManager;
import com.vortex.client.module.modules.RadarModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Zeichnet den runden Entity-Radar (seit Client 4.16 neu gestaltet).
 *
 *  - Runde Scheibe mit weichem Verlauf, Rahmen in "Frame Color",
 *    Entfernungsringe bei 1/3 und 2/3, feines Fadenkreuz.
 *  - Kompass: N/O/S/W am Rand, drehen sich mit der Blickrichtung.
 *  - Sweep (abschaltbar): ein Lichtstrahl, der langsam kreist.
 *  - Entities als Punkte mit dunklem Rand (Spieler rot, Freunde tuerkis,
 *    Monster orange-rot, Tiere gruen, Items gelb) bzw. als Spawn-Ei; kleine
 *    Pfeile zeigen, ob etwas deutlich ueber oder unter dir ist.
 *  - Fluessig: Positionen und Blickrichtung werden zwischen den Ticks
 *    interpoliert, neu auftauchende Entities blenden ein.
 *
 * Gezeichnet wird in halben Pixeln (Pose 1/2): Kreise und Linien sind damit
 * auf normalen GUI-Skalen deutlich glatter als vorher (Pixel-Treppen). Das
 * ist auch schneller: der alte Rahmen bestand aus Hunderten Einzelpixeln.
 */
public final class RadarRenderer {

    private static final int COL_PLAYER  = 0xFFFF5252;
    private static final int COL_FRIEND  = 0xFF3FE0D0;
    private static final int COL_HOSTILE = 0xFFFF8A3D;
    private static final int COL_ANIMAL  = 0xFF6EE07A;
    private static final int COL_ITEM    = 0xFFFFD34E;

    /** Zeichen-Einheiten pro GUI-Pixel. */
    private static final int Q = 2;

    /**
     * One ItemStack per spawn-egg item, built on first use and kept
     * (render thread only).
     */
    private static final java.util.Map<net.minecraft.world.item.Item, net.minecraft.world.item.ItemStack>
            EGG_STACKS = new java.util.HashMap<>();

    /** Seit wann eine Entity im Radar ist (Einblenden). */
    private static final it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap SEIT = new it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap();
    private static final it.unimi.dsi.fastutil.ints.IntOpenHashSet JETZT = new it.unimi.dsi.fastutil.ints.IntOpenHashSet();

    public static void render(GuiGraphicsExtractor context, Minecraft client) {
        long pvpT0 = System.nanoTime();
        try {
            renderInner(context, client);
        } finally {
            com.vortex.client.core.Profiler.record("Radar",
                    System.nanoTime() - pvpT0);
        }
    }

    private static void renderInner(GuiGraphicsExtractor context, Minecraft client) {
        if (client.player == null || client.level == null) return;

        RadarModule mod = (RadarModule) module();
        if (mod == null || !mod.isEnabled()) { SEIT.clear(); return; }

        double scale = mod.scale.get();
        int diameter = (int) Math.round(mod.baseDiameter() * scale);
        int left = mod.x.getInt();
        int top = mod.y.getInt();
        int frame = mod.color.get() | 0xFF000000;
        float pt = client.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        long ms = System.currentTimeMillis();

        var m = context.pose();
        m.pushMatrix();
        try {
            m.translate(left, top);
            m.scale(1f / Q, 1f / Q);
            int D = diameter * Q;
            int R = D / 2;

            // 1) Scheibe mit leichtem Verlauf nach innen
            disc(context, R, R, R, 0xB80B0D12);
            disc(context, R, R, Math.round(R * 0.72f), 0x14FFFFFF);
            disc(context, R, R, Math.round(R * 0.38f), 0x0AFFFFFF);
            // Fadenkreuz und Entfernungsringe
            int kreuz = 0x16FFFFFF;
            for (int iy = 0; iy < D; iy++) {
                double yy = iy + 0.5 - R;
                if (Math.abs(yy) >= R - Q) continue;
                if (Math.abs(yy) < 0.6) {
                    int w = (int) Math.floor(Math.sqrt(R * (double) R - yy * yy)) - Q;
                    context.fill(R - w, iy, R + w, iy + 1, kreuz);
                }
            }
            context.fill(R, Q, R + 1, D - Q, kreuz);
            ring(context, R, R, Math.round(R / 3f), 1, 0x1EFFFFFF);
            ring(context, R, R, Math.round(R * 2f / 3f), 1, 0x1EFFFFFF);

            // 2) Blickrichtung (interpoliert) -> Basisvektoren "Blick = oben"
            float yaw = client.player.getViewYRot(pt);
            double yawRad = Math.toRadians(yaw);
            double rightX = -Math.cos(yawRad), rightZ = -Math.sin(yawRad);
            double fwdX = -Math.sin(yawRad), fwdZ = Math.cos(yawRad);

            // 3) Sweep
            if (mod.sweep.get()) {
                double winkel = (ms % 4000L) / 4000.0 * Math.PI * 2.0;
                for (int i = 0; i < 22; i++) {
                    double a = winkel - i * 0.024;
                    int alpha = Math.max(0, 80 - i * 4);
                    strahl(context, R, R, R - Q, a, (alpha << 24) | (frame & 0xFFFFFF));
                }
            }

            // 4) Rahmen (aussen dunkel, innen in Rahmenfarbe)
            ring(context, R, R, R, Q, 0xC0000000);
            ring(context, R, R, R - Q, Q, frame);

            // 5) Kompass
            if (mod.compass.get()) {
                String[] namen = {"N", "E", "S", "W"};
                double[][] richt = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
                float ts = (float) (Q * 0.5 * Math.max(1.0, scale));
                for (int i = 0; i < 4; i++) {
                    double sx = richt[i][0] * rightX + richt[i][1] * rightZ;
                    double sy = richt[i][0] * fwdX + richt[i][1] * fwdZ;
                    double rr = R - 5.5 * Q * Math.max(1.0, scale);
                    float tx = (float) (R + sx * rr), ty = (float) (R - sy * rr);
                    int w = client.font.width(namen[i]);
                    m.pushMatrix();
                    m.translate(tx, ty);
                    m.scale(ts, ts);
                    context.text(client.font, Component.literal(namen[i]), -w / 2, -4,
                            i == 0 ? 0xFFFF6B6B : 0xB0D8DCE6, true);
                    m.popMatrix();
                }
            }

            // 6) Entities
            double maxRange = mod.range.get();
            net.minecraft.world.phys.Vec3 ich = client.player.getPosition(pt);
            double f = (R - 2.0 * Q) / maxRange;
            JETZT.clear();
            for (Entity e : com.vortex.client.core.EntityCache.all()) {
                if (e == client.player || !e.isAlive()) continue;
                int farbe;
                boolean isPlayer = e instanceof Player;
                boolean useEgg = false;
                if (isPlayer) {
                    if (!mod.showPlayers.get()) continue;
                    farbe = com.vortex.client.core.Friends.istFreund(e) ? COL_FRIEND : COL_PLAYER;
                } else if (isHostile(e)) {
                    if (!mod.showHostiles.get()) continue;
                    farbe = COL_HOSTILE;
                    useEgg = mod.mobIcons.get();
                } else if (isAnimal(e)) {
                    if (!mod.showAnimals.get()) continue;
                    farbe = COL_ANIMAL;
                    useEgg = mod.mobIcons.get();
                } else if (e instanceof ItemEntity) {
                    if (!mod.showItems.get()) continue;
                    farbe = COL_ITEM;
                } else {
                    continue;
                }
                net.minecraft.world.phys.Vec3 pos = e.getPosition(pt);
                double dx = pos.x - ich.x, dz = pos.z - ich.z;
                double dist = Math.sqrt(dx * dx + dz * dz);
                if (dist > maxRange) continue;
                if (isPlayer) melde(client, mod, e, dist);

                JETZT.add(e.getId());
                if (!SEIT.containsKey(e.getId())) SEIT.put(e.getId(), ms);
                float ein = Math.min(1f, (ms - SEIT.get(e.getId())) / 250f);
                ein = ein * ein * (3f - 2f * ein);

                double sRight = dx * rightX + dz * rightZ;
                double sUp = dx * fwdX + dz * fwdZ;
                float ex = (float) (R + sRight * f), ey = (float) (R - sUp * f);
                int ix = Math.round(ex), iy = Math.round(ey);

                boolean playerWithHead = isPlayer && mod.playerDetails.get() && e instanceof AbstractClientPlayer;
                boolean gezeichnet = false;
                if (useEgg && e instanceof net.minecraft.world.entity.LivingEntity living) {
                    disc(context, ix, iy, (int) Math.round(3.5 * Q * scale), mitAlpha((farbe & 0xFFFFFF) | 0x50000000, ein));
                    gezeichnet = drawMobEgg(context, living, ix, iy, scale);
                }
                if (!gezeichnet && !playerWithHead) {
                    int r = (int) Math.round(1.6 * Q * Math.max(1.0, scale));
                    disc(context, ix, iy, r + 1, mitAlpha(0xD0000000, ein));
                    disc(context, ix, iy, r, mitAlpha(farbe, ein));
                }
                // Hoehe: deutlich ueber/unter dir?
                double dy = pos.y - ich.y;
                if (Math.abs(dy) > 3.0) {
                    int s = (int) Math.round(1.2 * Q * Math.max(1.0, scale));
                    int off = (int) Math.round(4.0 * Q * Math.max(1.0, scale));
                    dreieck(context, ix + off, iy, s, dy > 0, mitAlpha(0xE6FFFFFF, ein));
                }
                if (playerWithHead) {
                    drawPlayerInfo(context, client, (AbstractClientPlayer) e, ix, iy, dist, farbe, ein, scale);
                }
            }
            SEIT.keySet().retainAll(JETZT);

            // 7) Du in der Mitte: Pfeil mit dunklem Rand
            int s = (int) Math.round(3.0 * Q * Math.max(1.0, scale));
            pfeil(context, R, R, s + Q, 0xE0000000);
            pfeil(context, R, R, s, 0xFFFFFFFF);
        } finally {
            m.popMatrix();
        }
        merkeReichweite();
    }

    // ------------------------------------------------------------------
    // Formen (alles aus waagerechten Streifen -- wenige, billige fills)

    /** Gefuellter Kreis. */
    private static void disc(GuiGraphicsExtractor c, int cx, int cy, int r, int farbe) {
        if (r <= 0 || (farbe >>> 24) == 0) return;
        for (int dy = -r; dy < r; dy++) {
            double yy = dy + 0.5;
            int w = (int) Math.round(Math.sqrt(r * (double) r - yy * yy));
            if (w > 0) c.fill(cx - w, cy + dy, cx + w, cy + dy + 1, farbe);
        }
    }

    /** Kreisring: aussen r, Dicke d. */
    private static void ring(GuiGraphicsExtractor c, int cx, int cy, int r, int d, int farbe) {
        if (r <= 0) return;
        int ri = Math.max(0, r - d);
        for (int dy = -r; dy < r; dy++) {
            double yy = dy + 0.5;
            int wo = (int) Math.round(Math.sqrt(r * (double) r - yy * yy));
            if (wo <= 0) continue;
            if (Math.abs(yy) >= ri) {
                c.fill(cx - wo, cy + dy, cx + wo, cy + dy + 1, farbe);
            } else {
                int wi = (int) Math.round(Math.sqrt(ri * (double) ri - yy * yy));
                c.fill(cx - wo, cy + dy, cx - wi, cy + dy + 1, farbe);
                c.fill(cx + wi, cy + dy, cx + wo, cy + dy + 1, farbe);
            }
        }
    }

    /** Strahl vom Mittelpunkt (fuer den Sweep), Winkel 0 = oben, im Uhrzeigersinn. */
    private static void strahl(GuiGraphicsExtractor c, int cx, int cy, int laenge, double winkel, int farbe) {
        double sx = Math.sin(winkel), sy = -Math.cos(winkel);
        for (int t = 3; t < laenge; t += 3) {
            int x = (int) Math.round(cx + sx * t), y = (int) Math.round(cy + sy * t);
            c.fill(x - 1, y - 1, x + 2, y + 2, farbe);
        }
    }

    /** Pfeil nach oben (Spitze oben, hinten eingekerbt). */
    private static void pfeil(GuiGraphicsExtractor c, int cx, int cy, int s, int farbe) {
        int oben = cy - s, unten = cy + s;
        for (int y = oben; y <= unten; y++) {
            float t = (float) (y - oben) / (2 * s);
            int halb = Math.round(s * 0.85f * t);
            int kerbe = y > cy + s / 3 ? Math.round((y - (cy + s / 3f)) * 1.1f) : 0;
            if (kerbe <= 0) {
                c.fill(cx - halb, y, cx + halb + 1, y + 1, farbe);
            } else if (kerbe < halb) {
                c.fill(cx - halb, y, cx - kerbe + 1, y + 1, farbe);
                c.fill(cx + kerbe, y, cx + halb + 1, y + 1, farbe);
            }
        }
    }

    /** Kleines Dreieck (Hoehen-Hinweis). */
    private static void dreieck(GuiGraphicsExtractor c, int cx, int cy, int s, boolean hoch, int farbe) {
        for (int i = 0; i <= s; i++) {
            int y = hoch ? cy - s / 2 + i : cy + s / 2 - i;
            c.fill(cx - i, y, cx + i + 1, y + 1, farbe);
        }
    }

    private static int mitAlpha(int argb, float faktor) {
        int a = Math.round(((argb >>> 24) & 0xFF) * Math.max(0f, Math.min(1f, faktor)));
        return (a << 24) | (argb & 0xFFFFFF);
    }

    /**
     * Zeichnet das Spawn-Ei-Icon eines Mobs/Tiers an die Radar-Position.
     * Gibt true zurueck, wenn ein Ei gezeichnet wurde, sonst false (dann
     * faellt der Aufrufer auf einen Punkt zurueck).
     */
    private static boolean drawMobEgg(GuiGraphicsExtractor context,
                                      net.minecraft.world.entity.LivingEntity living,
                                      int dotX, int dotY, double scale) {
        try {
            var type = living.getType();
            //#if 26
            var eggHolder = net.minecraft.world.item.SpawnEggItem.byId(type);
            if (eggHolder.isEmpty()) return false;
            var egg = eggHolder.get().value();
            //#else
            //$ var egg = net.minecraft.world.item.SpawnEggItem.byId(type);
            //$ if (egg == null) return false;
            //#endif
            // Icon-Groesse klein halten (5px Basis), damit der Radar
            // uebersichtlich bleibt und sich die Icons nicht gegenseitig
            // (und den Mittel-Pfeil) verdecken.
            int iconSize = Math.max(5 * Q, (int) Math.round(5 * Q * scale));
            int ix = dotX - iconSize / 2;
            int iy = dotY - iconSize / 2;

            // Item-Icons sind immer 16px; per Matrix auf iconSize skalieren.
            float factor = iconSize / 16.0f;
            var m = context.pose();
            m.pushMatrix();
            m.translate(ix, iy);
            m.scale(factor, factor);
            context.item(EGG_STACKS.computeIfAbsent(egg,
                    net.minecraft.world.item.ItemStack::new), 0, 0);
            m.popMatrix();
            return true;
        } catch (Throwable ignored) {
            return false; // bei Problemen lieber den Punkt zeichnen
        }
    }

    /** Kopf (mit Rand in Spieler-/Freundesfarbe) + Name und Entfernung in einer kleinen Plakette. */
    private static void drawPlayerInfo(GuiGraphicsExtractor context, Minecraft client,
                                       AbstractClientPlayer player,
                                       int dotX, int dotY, double dist, int farbe, float ein, double scale) {
        try {
            int headSize = (int) Math.round(6 * Q * Math.max(1.0, scale));
            int hx = dotX - headSize / 2;
            int hy = dotY - headSize / 2;
            context.fill(hx - 2, hy - 2, hx + headSize + 2, hy + headSize + 2, mitAlpha(farbe, ein));
            var handler = client.getConnection();
            String playerName = player.getName().getString();
            if (handler != null) {
                var entry = handler.getPlayerInfo(playerName);
                if (entry != null) {
                    var skin = entry.getSkin();
                    context.blit(skin.body().texturePath(), hx, hy, hx + headSize, hy + headSize,
                            0.125F, 0.125F, 0.25F, 0.25F);
                    context.blit(skin.body().texturePath(), hx, hy, hx + headSize, hy + headSize,
                            0.625F, 0.125F, 0.75F, 0.25F);
                }
            }
            if (ein < 0.3f) return;
            String info = playerName + "  " + (int) Math.round(dist) + "m";
            float ts = (float) (Q * 0.5);
            int tw = Math.round(client.font.width(info) * ts);
            int tx = hx + headSize + 3 * Q, ty = dotY - Math.round(4.5f * ts);
            context.fill(tx - Q, ty - Q, tx + tw + Q, ty + Math.round(9 * ts) + Q / 2, mitAlpha(0xA0000000, ein));
            var m = context.pose();
            m.pushMatrix();
            m.translate(tx, ty);
            m.scale(ts, ts);
            context.text(client.font, Component.literal(playerName), 0, 0, mitAlpha(0xFFFFFFFF, ein), false);
            context.text(client.font, Component.literal("  " + (int) Math.round(dist) + "m"),
                    client.font.width(playerName), 0, mitAlpha(0xFFB4B8C4, ein), false);
            m.popMatrix();
        } catch (Throwable ignored) {
        }
    }

    private static boolean isHostile(Entity e) {
        // Monster-Interface faengt auch Slimes/Magma-Wuerfel.
        return e instanceof net.minecraft.world.entity.monster.Enemy;
    }

    private static boolean isAnimal(Entity e) {
        return e instanceof Animal || e instanceof AgeableMob;
    }

    private static com.vortex.client.module.Module module() {
        for (var m : ModuleManager.INSTANCE.getModules()) {
            if (m instanceof RadarModule) return m;
        }
        return null;
    }

    // --- Warnung bei neuen Spielern ---------------------------------------
    //
    // Gemeldet wird nur, wer NEU auftaucht. Wer dauerhaft in Reichweite
    // bleibt, loest genau einmal aus -- sonst waere es im Kampf ein Dauerton.

    private static final java.util.Map<String, Long> GEMELDET = new java.util.HashMap<>();
    private static final java.util.Set<String> IN_REICHWEITE = new java.util.HashSet<>();
    private static final java.util.Set<String> DIESEN_FRAME = new java.util.HashSet<>();

    private static void melde(Minecraft client, RadarModule mod, Entity e, double dist) {
        try {
            if (!mod.alertChat.get() && !mod.alertSound.get()) return;
            if (client.player == null) return;

            String name = e.getName().getString();
            if (name == null || name.isEmpty()) return;
            if (name.equals(client.player.getName().getString())) return;

            DIESEN_FRAME.add(name);
            if (IN_REICHWEITE.contains(name)) return;      // war schon da

            long jetzt = System.currentTimeMillis();
            long sperre = (long) (mod.alertCooldown.get() * 1000);
            Long zuletzt = GEMELDET.get(name);
            if (zuletzt != null && jetzt - zuletzt < sperre) return;
            GEMELDET.put(name, jetzt);

            if (mod.alertChat.get()) {
                Component text = Component.literal("[Radar] ")
                        .withStyle(net.minecraft.ChatFormatting.AQUA)
                        .append(Component.literal(name)
                                .withStyle(net.minecraft.ChatFormatting.WHITE))
                        .append(Component.literal(" — " + (int) dist + "m")
                                .withStyle(net.minecraft.ChatFormatting.GRAY));
                // sendSystemMessage: der im Projekt bereits benutzte Weg.
                client.player.sendSystemMessage(text);
            }

            if (mod.alertSound.get()) {
                // Drei Toene mit steigender Hoehe -- ein einzelner geht im
                // Spielgeraeusch unter.
                pling(client, 0, 1.2f);
                pling(client, 120, 1.5f);
                pling(client, 240, 1.9f);
            }
        } catch (Throwable pvpErr) {
            com.vortex.client.core.Errors.report("RadarRenderer.melde", pvpErr);
        }
    }

    private static void pling(Minecraft client, long verzoegerungMs, float hoehe) {
        if (verzoegerungMs == 0) { spiele(client, hoehe); return; }
        Thread t = new Thread(() -> {
            try {
                Thread.sleep(verzoegerungMs);
                client.execute(() -> spiele(client, hoehe));
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }, "vortex-radar-ping");
        t.setDaemon(true);
        t.start();
    }

    private static void spiele(Minecraft client, float hoehe) {
        try {
            if (client.player == null) return;
            client.player.playSound(
                    net.minecraft.sounds.SoundEvents.NOTE_BLOCK_PLING.value(), 1.0f, hoehe);
        } catch (Throwable pvpErr) {
            com.vortex.client.core.Errors.report("RadarRenderer.sound", pvpErr);
        }
    }

    /** Nach jedem Durchgang: wer diesmal fehlte, gilt beim naechsten Mal als neu. */
    private static void merkeReichweite() {
        IN_REICHWEITE.clear();
        IN_REICHWEITE.addAll(DIESEN_FRAME);
        DIESEN_FRAME.clear();
        if (GEMELDET.size() > 200) {
            long jetzt = System.currentTimeMillis();
            GEMELDET.entrySet().removeIf(x -> jetzt - x.getValue() > 600000);
        }
    }

}
