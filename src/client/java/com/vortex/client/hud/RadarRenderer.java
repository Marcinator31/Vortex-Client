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
 * Zeichnet den runden Entity-Radar.
 *
 *  - Kreis-Rahmen + halbtransparenter Hintergrund.
 *  - Spieler-Pfeil in der Mitte (zeigt nach oben = Blickrichtung).
 *  - Entities als farbige Punkte, nach ihrer relativen Lage projiziert.
 *  - Bei Spielern: Kopf, Name und Entfernung in Metern.
 *
 * Die Projektion dreht die Welt so, dass die Blickrichtung des Spielers
 * immer nach oben zeigt (verifizierte Mathematik, siehe unten).
 */
public final class RadarRenderer {

    // Punktfarben je Typ.
    private static final int COL_PLAYER  = 0xFFFF5555; // rot
    private static final int COL_HOSTILE = 0xFFFFAA00; // orange
    private static final int COL_ANIMAL  = 0xFF55FF55; // gruen
    private static final int COL_ITEM    = 0xFF55FFFF; // cyan

    /**
     * One ItemStack per spawn-egg item, built on first use and kept.
     * Same reasoning as HudRenderer's totemIcon: a new stack per icon per
     * frame is a small object, but at 150+ fps with a dozen mobs on the
     * radar that is thousands of allocations a second for pictures that
     * never change. Bounded by the number of mob types (~80).
     * Render thread only, so a plain HashMap is fine.
     */
    private static final java.util.Map<net.minecraft.world.item.Item, net.minecraft.world.item.ItemStack>
            EGG_STACKS = new java.util.HashMap<>();

    public static void render(GuiGraphicsExtractor context, Minecraft client) {
        // Same wrapper pattern as HudRenderer.onHudRender: time everything,
        // record in finally so early returns and errors are counted too.
        // Subset of the "HUD" section in /lag.
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
        if (mod == null || !mod.isEnabled()) return;

        double scale = mod.scale.get();
        int diameter = (int) Math.round(mod.baseDiameter() * scale);
        int radius = diameter / 2;

        int left = mod.x.getInt();
        int top = mod.y.getInt();
        int cx = left + radius; // Mittelpunkt X
        int cy = top + radius;  // Mittelpunkt Y

        int frame = mod.color.get();

        // 1) Hintergrund (halbtransparentes Quadrat hinter dem Kreis).
        context.fill(left, top, left + diameter, top + diameter, 0x80000000);

        // 2) Kreis-Rahmen aus vielen kurzen Segmenten zeichnen.
        drawCircle(context, cx, cy, radius, frame);
        // Innenkreis (halber Radius) als Hilfslinie.
        drawCircle(context, cx, cy, radius / 2, 0x60FFFFFF);

        // 3) Reichweite + Blickrichtung des Spielers.
        double maxRange = mod.range.get();        // Bloecke, die der Rand bedeutet
        float yaw = client.player.getYRot();
        double yawRad = Math.toRadians(yaw);

        // Basisvektoren fuer "Blickrichtung = oben" (verifizierte Formel V2):
        //   rechts im Radar = -cos/-sin, oben = Blickrichtung.
        double rightX = -Math.cos(yawRad), rightZ = -Math.sin(yawRad);
        double fwdX   = -Math.sin(yawRad), fwdZ   =  Math.cos(yawRad);

        double px = client.player.getX();
        double pz = client.player.getZ();

        // 4) Entities durchgehen und zeichnen.
        // From the shared list: the radar draws every frame, but entities
        // only move on ticks.
        for (Entity e : com.vortex.client.core.EntityCache.all()) {
            if (e == client.player) continue;

            // Typ bestimmen + Filter. dotColor fuer Punkt-Darstellung,
            // useEgg=true bedeutet: als Spawn-Ei-Icon zeichnen (Tiere/Mobs).
            int dotColor;
            boolean isPlayer = e instanceof Player;
            boolean useEgg = false;
            if (isPlayer) {
                if (!mod.showPlayers.get()) continue;
                dotColor = COL_PLAYER;
            } else if (isHostile(e)) {
                if (!mod.showHostiles.get()) continue;
                dotColor = COL_HOSTILE;
                useEgg = true;
            } else if (isAnimal(e)) {
                if (!mod.showAnimals.get()) continue;
                dotColor = COL_ANIMAL;
                useEgg = true;
            } else if (e instanceof ItemEntity) {
                if (!mod.showItems.get()) continue;
                dotColor = COL_ITEM;
            } else {
                continue; // andere Typen ignorieren
            }

            double dx = e.getX() - px;
            double dz = e.getZ() - pz;
            double dist = Math.sqrt(dx * dx + dz * dz);
            if (dist > maxRange) continue; // ausserhalb der Reichweite

            // Projektion auf den Radar.
            double sRight = dx * rightX + dz * rightZ;
            double sUp    = dx * fwdX   + dz * fwdZ;

            // Auf Radarradius skalieren (Distanz/maxRange * radius).
            double f = (radius) / maxRange;
            int dotX = (int) Math.round(cx + sRight * f);
            int dotY = (int) Math.round(cy - sUp * f); // minus: +screenY = unten

            // 5) Darstellung waehlen.
            //    - Tiere/Mobs: Spawn-Ei-Icon.
            //    - Spieler mit Detail-Anzeige: NUR der Kopf (kein Punkt), damit
            //      der Kopf selbst die exakte Position markiert.
            //    - sonst: kleiner Punkt.
            boolean playerWithHead = isPlayer && mod.playerDetails.get()
                    && e instanceof AbstractClientPlayer;

            boolean drewEgg = false;
            if (useEgg && e instanceof net.minecraft.world.entity.LivingEntity living) {
                drewEgg = drawMobEgg(context, living, dotX, dotY, scale);
            }
            if (!drewEgg && !playerWithHead) {
                // Fallback-Punkt (Items, Mob ohne Spawn-Ei, Spieler ohne Kopf) -- klein.
                int s = Math.max(1, (int) Math.round(1.5 * scale));
                context.fill(dotX - s, dotY - s, dotX + s, dotY + s, dotColor);
            }

            // 6) Bei Spielern: Kopf (mittig auf der Position), Name, Entfernung.
            if (playerWithHead) {
                drawPlayerInfo(context, client, (AbstractClientPlayer) e, dotX, dotY, dist);
            }
        }

        // 7) Spieler-Marker in der Mitte: kleines Dreieck (zeigt nach oben).
        //    Wird als LETZTES gezeichnet, liegt also ueber allen Icons.
        drawPlayerArrow(context, cx, cy, Math.max(2, (int) Math.round(1.5 * scale)));
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
            var eggHolder = net.minecraft.world.item.SpawnEggItem.byId(type);
            if (eggHolder.isEmpty()) return false;
            var egg = eggHolder.get().value();
            // Icon-Groesse klein halten (5px Basis), damit der Radar
            // uebersichtlich bleibt und sich die Icons nicht gegenseitig
            // (und den Mittel-Pfeil) verdecken.
            int iconSize = Math.max(5, (int) Math.round(5 * scale));
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

    /** Zeichnet Kopf + Name + Entfernung neben einem Spieler-Punkt (klein). */
    private static void drawPlayerInfo(GuiGraphicsExtractor context, Minecraft client,
                                       AbstractClientPlayer player,
                                       int dotX, int dotY, double dist) {
        try {
            // Sehr kleiner Kopf, ZENTRIERT auf der exakten Position (dotX,dotY).
            // So markiert der Kopf selbst die genaue Stelle des Spielers.
            int headSize = 5;
            int hx = dotX - headSize / 2;
            int hy = dotY - headSize / 2;

            // In 1.21.11 liegt getSkinTextures() nicht mehr direkt auf dem
            // Spieler, sondern auf dem PlayerListEntry. Den holen wir oeffentlich
            // ueber den NetworkHandler. Wir nehmen die NAMEN-Variante von
            // getPlayerListEntry, weil player.getName().getString() bereits
            // nachweislich kompiliert (gleiche Datei, Namensanzeige unten) --
            // so umgehen wir die authlib-GameProfile-Accessor-Unterschiede.
            var handler = client.getConnection();
            String playerName = player.getName().getString();
            if (handler != null) {
                var entry = handler.getPlayerInfo(playerName);
                if (entry != null) {
                    var skin = entry.getSkin();
                    // Kopf zeichnen (inkl. Hut-Overlay).
                    // GuiGraphicsExtractor erwartet in 26.x x1/y1 und x2/y2,
                    // nicht x/y plus Breite/Höhe. headSize als Endpunkt hatte
                    // deshalb riesige Rechtecke bis zur oberen linken Ecke erzeugt.
                    context.blit(skin.body().texturePath(), hx, hy, hx + headSize, hy + headSize,
                            0.125F, 0.125F, 0.25F, 0.25F);
                    context.blit(skin.body().texturePath(), hx, hy, hx + headSize, hy + headSize,
                            0.625F, 0.125F, 0.75F, 0.25F);
                }
            }

            // Name + Entfernung kleiner darstellen (Component auf 0.7 skaliert).
            // Die Schriftgroesse ist fix, daher skalieren wir per Matrix um den
            // Textanker herum.
            String info = playerName + " " + (int) Math.round(dist) + "m";
            float textScale = 0.5f;
            int textX = dotX + headSize / 2 + 2; // rechts neben dem Kopf
            int textY = dotY - 2;

            var m = context.pose();
            m.pushMatrix();
            m.translate(textX, textY);
            m.scale(textScale, textScale);
            context.text(client.font, Component.literal(info),
                    0, 0, 0xFFFFFFFF);
            m.popMatrix();
        } catch (Throwable ignored) {
            // Skin/Name nicht verfuegbar -> nur der Punkt bleibt.
        }
    }

    /** Zeichnet einen Kreis-Umriss aus kurzen Liniensegmenten. */
    private static void drawCircle(GuiGraphicsExtractor context, int cx, int cy, int r, int color) {
        if (r <= 0) return;
        int segments = Math.max(24, r * 2);
        double step = (Math.PI * 2) / segments;
        int prevX = cx + r, prevY = cy;
        for (int i = 1; i <= segments; i++) {
            double a = step * i;
            int x = (int) Math.round(cx + Math.cos(a) * r);
            int y = (int) Math.round(cy + Math.sin(a) * r);
            drawLine(context, prevX, prevY, x, y, color);
            prevX = x;
            prevY = y;
        }
    }

    /** Zeichnet eine duenne Linie zwischen zwei Punkten (Bresenham-artig). */
    private static void drawLine(GuiGraphicsExtractor context, int x0, int y0, int x1, int y1, int color) {
        int dx = Math.abs(x1 - x0), dy = Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1, sy = y0 < y1 ? 1 : -1;
        int err = dx - dy;
        int guard = 0;
        while (true) {
            context.fill(x0, y0, x0 + 1, y0 + 1, color);
            if (x0 == x1 && y0 == y1) break;
            if (++guard > 5000) break; // Sicherheitsbremse
            int e2 = 2 * err;
            if (e2 > -dy) { err -= dy; x0 += sx; }
            if (e2 < dx) { err += dx; y0 += sy; }
        }
    }

    /**
     * Zeichnet den Spieler-Marker als gut sichtbares, nach oben zeigendes
     * gefuelltes Dreieck. Ein dunkler Rand (etwas groesseres schwarzes Dreieck
     * darunter) hebt es klar von den Mob-Icons ab. Cyan-Spitze faellt zudem
     * farblich auf.
     */
    private static void drawPlayerArrow(GuiGraphicsExtractor context, int cx, int cy, int size) {
        // Erst dunkler Rand, dann helle Fuellung darueber.
        fillTriangleUp(context, cx, cy, size + 1, 0xFF000000);
        fillTriangleUp(context, cx, cy, size, 0xFF66E0FF);
    }

    /** Fuellt ein nach oben zeigendes Dreieck (Spitze oben) um (cx,cy). */
    private static void fillTriangleUp(GuiGraphicsExtractor context, int cx, int cy, int size, int color) {
        int apexY = cy - size;
        int baseY = cy + size;
        int height = baseY - apexY;
        if (height <= 0) return;
        for (int y = apexY; y <= baseY; y++) {
            // t = 0 an der Spitze (oben), 1 an der Basis (unten).
            float t = (float) (y - apexY) / height;
            int halfW = Math.round(size * t);
            context.fill(cx - halfW, y, cx + halfW + 1, y + 1, color);
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
}
