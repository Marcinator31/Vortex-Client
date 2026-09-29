package com.vortex.client.hud;

import com.vortex.client.core.Errors;
import com.vortex.client.module.ModuleManager;
import com.vortex.client.module.modules.DebugOverlayModule;
import com.vortex.client.module.modules.StreamerModeModule;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Zeichnet das Debug Overlay (neu gebaut in 4.7.2, siehe DebugOverlayModule).
 *
 * WAS VORHER SCHLECHT WAR
 *  - "low" war das Minimum aus client.getFps() -- das ist schon ein
 *    Sekundenmittel, Ruckler sah man darin gar nicht. Jetzt wird JEDER Frame
 *    gemessen (Abstand zwischen zwei Aufrufen = das, was man spuert): Frametime,
 *    1%-Low und ein Graph der letzten Frames.
 *  - Die Uhrzeit kam aus der Spielzeit (getGameTime). Die laeuft stur weiter,
 *    auch nach /time set oder beim Schlafen -- die Anzeige war also meist falsch.
 *    Jetzt: die Tageszeit der Welt.
 *  - Keine Abschnitte, keine Farben nach Zustand, kein Licht, kein Tempo, keine
 *    Nether-Koordinaten, kein TPS; der Kasten im HUD-Editor hatte eine feste
 *    (falsche) Groesse.
 *
 * KOSTEN
 * Texte werden 5x pro Sekunde neu gebaut (Biom, Speicher, Sortieren fuer das
 * 1%-Low), gezeichnet wird jeden Frame nur das Fertige plus der Graph.
 */
public final class DebugOverlay {

    private DebugOverlay() {}

    // ---- Zeilen -------------------------------------------------------------

    private static final int KOPF = 1, ZEILE = 0, GRAPH = 2, BALKEN = 3;

    private static final class Line {
        final int art;
        final Component label, value;
        final int labelW, valueW;
        final int farbe;          // 0 = Wertfarbe
        final float anteil;       // fuer BALKEN
        Line(Font f, int art, String label, String value, int farbe, float anteil) {
            this.art = art;
            this.label = Component.literal(label == null ? "" : label);
            this.value = Component.literal(value == null ? "" : value);
            this.labelW = label == null ? 0 : f.width(label);
            this.valueW = value == null ? 0 : f.width(value);
            this.farbe = farbe;
            this.anteil = anteil;
        }
    }

    private static List<Line> lines = new ArrayList<>();
    private static int labelSpalte = 0, breite = 0, hoehe = 0;
    private static long gebaut = 0L;

    // Zuletzt gezeichnete Groesse (Bildschirmpixel ohne Skalierung) fuer den Editor.
    private static int letzteB = -1, letzteH = -1;

    public static int lastWidth(int ersatz, float scale) { return Math.round((letzteB > 0 ? letzteB : ersatz) * scale); }
    public static int lastHeight(int ersatz, float scale) { return Math.round((letzteH > 0 ? letzteH : ersatz) * scale); }

    // ---- Frames -------------------------------------------------------------

    private static final int FRAMES = 240;
    private static final long[] FRAME_NS = new long[FRAMES];
    private static int frameIdx = 0, frameAnzahl = 0;
    private static long letzterFrame = 0L;

    // ---- Tempo --------------------------------------------------------------

    private static double tempoX, tempoZ;
    private static long tempoZeit = 0L;
    private static double tempo = 0;

    // ---- Farben -------------------------------------------------------------

    private static final int GRUEN = 0xFF55E07A, GELB = 0xFFFFD25A, ORANGE = 0xFFFF9F43, ROT = 0xFFFF5A5A;

    public static void render(GuiGraphicsExtractor ctx, Minecraft mc) {
        long t0 = System.nanoTime();
        try {
            DebugOverlayModule mod = ModuleManager.INSTANCE.get(DebugOverlayModule.class);
            if (mod == null || !mod.isEnabled()) { letzterFrame = 0; return; }

            // Frame messen: Abstand zum letzten Aufruf. Laenger als 1 s = Pause,
            // Menue, Ladebildschirm -- nicht mitzaehlen.
            long jetztNs = System.nanoTime();
            if (letzterFrame != 0L) {
                long d = jetztNs - letzterFrame;
                if (d > 0 && d < 1_000_000_000L) {
                    FRAME_NS[frameIdx] = d;
                    frameIdx = (frameIdx + 1) % FRAMES;
                    if (frameAnzahl < FRAMES) frameAnzahl++;
                }
            }
            letzterFrame = jetztNs;

            if (mc.player == null || mc.level == null || mc.font == null) return;
            if (mod.hideWithF3.get() && mc.getDebugOverlay() != null && mc.getDebugOverlay().showDebugScreen()) return;

            long jetzt = System.currentTimeMillis();
            if (jetzt - gebaut > 200 || lines.isEmpty()) {
                long alt = gebaut;
                gebaut = jetzt;
                bauen(mc, mod, alt == 0 ? 0 : jetzt - alt);
            }
            if (lines.isEmpty()) return;
            zeichnen(ctx, mc, mod);
        } catch (Throwable e) {
            Errors.report("DebugOverlay", e);
        } finally {
            com.vortex.client.core.Profiler.record("DebugOverlay", System.nanoTime() - t0);
        }
    }

    // =========================================================================
    // Zeichnen
    // =========================================================================

    private static void zeichnen(GuiGraphicsExtractor ctx, Minecraft mc, DebugOverlayModule mod) {
        Font font = mc.font;
        boolean kompakt = mod.layout.getIndex() == 1;
        int pad = kompakt ? 3 : 5;
        int px = mod.x.getInt(), py = mod.y.getInt();
        int w = breite + pad * 2 + 2, h = hoehe + pad * 2;
        letzteB = w;
        letzteH = h;
        boolean schatten = mod.shadow.get();

        HudRenderer.pushScale(ctx, px, py, mod.scale.getFloat());
        try {
            if (mod.background.get()) {
                int a = (int) Math.round(mod.opacity.get() * 2.55);
                if (a > 0) ctx.fill(px, py, px + w, py + h, (Math.min(255, a) << 24) | 0x0C0C12);
                // Akzentleiste links (Verlauf von oben nach unten)
                for (int i = 0; i < h; i += 2) {
                    ctx.fill(px, py + i, px + 2, py + Math.min(i + 2, h), mod.accent.at((i + 1f) / h));
                }
            }
            int x0 = px + pad + 2;
            int vx = x0 + labelSpalte + 8;
            int cy = py + pad;
            int labelFarbe = mod.labelColor.get(), wertFarbe = mod.valueColor.get();
            boolean ersterKopf = true;

            for (Line l : lines) {
                switch (l.art) {
                    case KOPF -> {
                        if (!ersterKopf) cy += 3;
                        ersterKopf = false;
                        ctx.text(font, l.label, x0, cy, mod.accent.at(0f), schatten);
                        int lx = x0 + l.labelW + 4;
                        int ende = x0 + breite;
                        if (ende > lx) ctx.fill(lx, cy + 4, ende, cy + 5, (mod.accent.at(1f) & 0x00FFFFFF) | 0x50000000);
                        cy += 11;
                    }
                    case GRAPH -> {
                        graph(ctx, x0, cy, breite, 18, mod);
                        cy += 21;
                    }
                    case BALKEN -> {
                        ctx.text(font, l.label, x0, cy, labelFarbe, schatten);
                        ctx.text(font, l.value, vx, cy, farbe(mod, l.farbe, wertFarbe), schatten);
                        int bx = vx + l.valueW + 6, bw = Math.max(20, x0 + breite - bx);
                        ctx.fill(bx, cy + 2, bx + bw, cy + 6, 0x40FFFFFF);
                        ctx.fill(bx, cy + 2, bx + Math.round(bw * Math.max(0f, Math.min(1f, l.anteil))), cy + 6,
                                farbe(mod, l.farbe, mod.accent.at(0.5f)));
                        cy += 10;
                    }
                    default -> {
                        ctx.text(font, l.label, x0, cy, labelFarbe, schatten);
                        ctx.text(font, l.value, vx, cy, farbe(mod, l.farbe, wertFarbe), schatten);
                        cy += 10;
                    }
                }
            }
        } finally {
            HudRenderer.popScale(ctx);
        }
    }

    private static int farbe(DebugOverlayModule mod, int zustand, int sonst) {
        return zustand != 0 && mod.coloredValues.get() ? zustand : sonst;
    }

    /** Die letzten Frames als Balken. Linie = 60 FPS, oben = 30 FPS (33 ms). */
    private static void graph(GuiGraphicsExtractor ctx, int x, int y, int w, int h, DebugOverlayModule mod) {
        ctx.fill(x, y, x + w, y + h, 0x30FFFFFF);
        int n = Math.min(frameAnzahl, w);
        final double maxMs = 33.3;
        for (int i = 0; i < n; i++) {
            int idx = Math.floorMod(frameIdx - 1 - i, FRAMES);
            double ms = FRAME_NS[idx] / 1e6;
            int bh = (int) Math.max(1, Math.min(h, Math.round(ms / maxMs * h)));
            int c = !mod.coloredValues.get() ? mod.accent.at(0.5f)
                    : ms <= 17.5 ? GRUEN : ms <= 34 ? GELB : ROT;
            int bx = x + w - 1 - i;
            ctx.fill(bx, y + h - bh, bx + 1, y + h, (c & 0x00FFFFFF) | 0xC0000000);
        }
        int y60 = y + h - (int) Math.round(16.7 / maxMs * h);
        ctx.fill(x, y60, x + w, y60 + 1, 0x60FFFFFF);
    }

    // =========================================================================
    // Inhalt
    // =========================================================================

    private static void bauen(Minecraft mc, DebugOverlayModule mod, long dtMs) {
        Font f = mc.font;
        List<Line> neu = new ArrayList<>(28);
        boolean kompakt = mod.layout.getIndex() == 1;
        LocalPlayer p = mc.player;
        boolean versteckt = StreamerModeModule.koordinatenVerstecken();

        // ---- Performance ------------------------------------------------------
        if (mod.showFps.get()) {
            kopf(neu, f, kompakt, "PERFORMANCE");
            int fps = mc.getFps();
            double[] st = frameStats();
            String fpsText = fps + " fps";
            if (st != null) fpsText += String.format(Locale.ROOT, "   %.1f ms", st[0]);
            neu.add(new Line(f, ZEILE, "FPS", fpsText, fps >= 60 ? GRUEN : fps >= 30 ? GELB : ROT, 0));
            if (st != null) {
                int low = (int) Math.round(1000.0 / st[1]);
                neu.add(new Line(f, ZEILE, "1% low", String.format(Locale.ROOT, "%d fps   max %.0f ms", low, st[2]),
                        low >= 60 ? GRUEN : low >= 30 ? GELB : ROT, 0));
            }
            if (mod.frameGraph.get() && !kompakt) neu.add(new Line(f, GRAPH, null, null, 0, 0));
        }

        // ---- Position ---------------------------------------------------------
        if (mod.showPosition.get()) {
            kopf(neu, f, kompakt, "POSITION");
            BlockPos pos = p.blockPosition();
            if (versteckt) {
                neu.add(new Line(f, ZEILE, "XYZ", "hidden (Streamer Mode)", 0, 0));
            } else {
                neu.add(new Line(f, ZEILE, "XYZ", String.format(Locale.ROOT, "%.1f  %.1f  %.1f",
                        p.getX(), p.getY(), p.getZ()), 0, 0));
                if (mod.otherDimension.get()) {
                    var dim = mc.level.dimension();
                    if (dim == Level.OVERWORLD) {
                        neu.add(new Line(f, ZEILE, "Nether", String.format(Locale.ROOT, "%.0f  %.0f",
                                Math.floor(p.getX() / 8), Math.floor(p.getZ() / 8)), 0, 0));
                    } else if (dim == Level.NETHER) {
                        neu.add(new Line(f, ZEILE, "Overworld", String.format(Locale.ROOT, "%.0f  %.0f",
                                Math.floor(p.getX() * 8), Math.floor(p.getZ() * 8)), 0, 0));
                    }
                }
                if (!kompakt) {
                    neu.add(new Line(f, ZEILE, "Chunk", (pos.getX() >> 4) + "  " + (pos.getZ() >> 4)
                            + "   in " + (pos.getX() & 15) + " " + (pos.getY() & 15) + " " + (pos.getZ() & 15), 0, 0));
                }
            }
            float yaw = net.minecraft.util.Mth.wrapDegrees(p.getYRot());
            String richtung = switch (p.getDirection()) {
                case NORTH -> "North  -Z";
                case SOUTH -> "South  +Z";
                case WEST -> "West  -X";
                case EAST -> "East  +X";
                default -> "?";
            };
            neu.add(new Line(f, ZEILE, "Facing", String.format(Locale.ROOT, "%s   %.1f / %.1f",
                    richtung, yaw, p.getXRot()), 0, 0));

            if (mod.speed.get()) {
                // Aus der Positionsaenderung -- stimmt auch mit Elytra, Boot, Pferd.
                if (dtMs > 0 && dtMs < 2000 && tempoZeit != 0) {
                    double dx = p.getX() - tempoX, dz = p.getZ() - tempoZ;
                    double jetzt = Math.sqrt(dx * dx + dz * dz) / (dtMs / 1000.0);
                    tempo = tempo * 0.4 + jetzt * 0.6;   // etwas glaetten
                    if (jetzt < 0.05) tempo = 0;
                }
                tempoX = p.getX();
                tempoZ = p.getZ();
                tempoZeit = System.currentTimeMillis();
                String t = String.format(Locale.ROOT, "%.2f m/s   %.1f km/h", tempo, tempo * 3.6);
                neu.add(new Line(f, ZEILE, "Speed", t, 0, 0));
            }
        }

        // ---- Welt -------------------------------------------------------------
        if (mod.showWorld.get()) {
            kopf(neu, f, kompakt, "WORLD");
            BlockPos pos = p.blockPosition();
            String biom = "?";
            try {
                biom = mc.level.getBiome(pos).unwrapKey().map(k -> schoen(k.identifier().getPath())).orElse("?");
            } catch (Throwable ignored) { }
            String dim = schoen(mc.level.dimension().identifier().getPath());
            neu.add(new Line(f, ZEILE, "Biome", kompakt ? biom + "  (" + dim + ")" : biom, 0, 0));
            if (!kompakt) neu.add(new Line(f, ZEILE, "Dimension", dim, 0, 0));

            try {
                int block = mc.level.getBrightness(LightLayer.BLOCK, pos);
                int himmel = mc.level.getBrightness(LightLayer.SKY, pos);
                // Seit 1.18 spawnen Monster in der Oberwelt nur bei Blocklicht 0.
                boolean spawn = block == 0 && mc.level.dimension() == Level.OVERWORLD;
                neu.add(new Line(f, ZEILE, "Light", "block " + block + "   sky " + himmel
                        + (spawn ? "   mobs can spawn" : ""), spawn ? ORANGE : 0, 0));
            } catch (Throwable ignored) { }

            if (mc.level.dimension() == Level.OVERWORLD) {
                long tag = mc.level.getDefaultClockTime();
                long zeit = Math.floorMod(tag, 24000L);
                String uhr = String.format(Locale.ROOT, "%02d:%02d", (zeit / 1000 + 6) % 24, (zeit % 1000) * 60 / 1000);
                String wetter = mc.level.isThundering() ? "thunder" : mc.level.isRaining() ? "rain" : "clear";
                // Nachts (ca. 19:00 bis 5:00) spawnen Monster auch draussen.
                boolean nacht = zeit >= 13000 && zeit <= 23000;
                neu.add(new Line(f, ZEILE, "Time", uhr + "   day " + (tag / 24000L + 1) + "   " + wetter,
                        nacht ? ORANGE : 0, 0));
            }
        }

        // ---- Blick ------------------------------------------------------------
        if (mod.showTarget.get()) {
            HitResult hit = mc.hitResult;
            if (hit instanceof BlockHitResult b && hit.getType() == HitResult.Type.BLOCK) {
                kopf(neu, f, kompakt, "LOOKING AT");
                BlockState st = mc.level.getBlockState(b.getBlockPos());
                var id = BuiltInRegistries.BLOCK.getKey(st.getBlock());
                neu.add(new Line(f, ZEILE, "Block", id == null ? "?" : schoen(id.getPath()), 0, 0));
                BlockPos bp = b.getBlockPos();
                double abstand = Math.sqrt(p.getEyePosition().distanceToSqr(hit.getLocation()));
                neu.add(new Line(f, ZEILE, "At", (versteckt ? "" : bp.getX() + " " + bp.getY() + " " + bp.getZ() + "   ")
                        + String.format(Locale.ROOT, "%.1f m", abstand) + "   " + b.getDirection().getName(), 0, 0));
                if (mod.blockStates.get()) {
                    //#if 26
                    st.getValues().limit(4).forEach(v ->
                            neu.add(new Line(f, ZEILE, "  " + v.property().getName(), v.valueName(), 0, 0)));
                    //#else
                    //$ st.getValues().entrySet().stream().limit(4).forEach(v ->
                    //$         neu.add(new Line(f, ZEILE, "  " + v.getKey().getName(), String.valueOf(v.getValue()), 0, 0)));
                    //#endif
                }
            } else if (hit instanceof EntityHitResult eh && hit.getType() == HitResult.Type.ENTITY) {
                kopf(neu, f, kompakt, "LOOKING AT");
                Entity e = eh.getEntity();
                String name = e.getName().getString();
                if (name.length() > 24) name = name.substring(0, 24);
                neu.add(new Line(f, ZEILE, "Entity", name, 0, 0));
                double abstand = Math.sqrt(p.getEyePosition().distanceToSqr(hit.getLocation()));
                if (e instanceof LivingEntity le) {
                    float hp = le.getHealth() + le.getAbsorptionAmount();
                    float max = le.getMaxHealth();
                    float anteil = max > 0 ? le.getHealth() / max : 0;
                    neu.add(new Line(f, BALKEN, "Health", String.format(Locale.ROOT, "%.1f / %.0f", hp, max),
                            anteil > 0.6f ? GRUEN : anteil > 0.3f ? GELB : ROT, anteil));
                }
                neu.add(new Line(f, ZEILE, "Distance", String.format(Locale.ROOT, "%.2f m", abstand), 0, 0));
            }
        }

        // ---- System -----------------------------------------------------------
        if (mod.showSystem.get()) {
            kopf(neu, f, kompakt, "SYSTEM");
            Runtime rt = Runtime.getRuntime();
            long used = (rt.totalMemory() - rt.freeMemory()) >> 20;
            long max = rt.maxMemory() >> 20;
            float anteil = max > 0 ? (float) used / max : 0f;
            neu.add(new Line(f, BALKEN, "Memory", used + " / " + max + " MB",
                    anteil > 0.9f ? ROT : anteil > 0.75f ? GELB : GRUEN, anteil));
            neu.add(new Line(f, ZEILE, "Entities", String.valueOf(mc.level.getEntityCount()), 0, 0));
            if (!kompakt) neu.add(new Line(f, ZEILE, "Java", Runtime.version().feature() + "   "
                    + Runtime.getRuntime().availableProcessors() + " threads", 0, 0));
        }

        // ---- Server -----------------------------------------------------------
        if (mod.showServer.get()) {
            kopf(neu, f, kompakt, "SERVER");
            var conn = mc.getConnection();
            var entry = mc.getCurrentServer();
            if (mc.isLocalServer() || entry == null) {
                neu.add(new Line(f, ZEILE, "Server", "singleplayer", 0, 0));
            } else {
                String ip = versteckt ? "hidden (Streamer Mode)" : entry.ip;
                String marke = null;
                try { marke = conn == null ? null : conn.serverBrand(); } catch (Throwable ignored) { }
                if (marke != null && marke.length() > 22) marke = marke.substring(0, 22);
                neu.add(new Line(f, ZEILE, "Server", ip, 0, 0));
                if (marke != null && !kompakt) neu.add(new Line(f, ZEILE, "Software", marke, 0, 0));

                int ping = -1;
                boolean eigen = false;
                if (PingMeter.get() >= 0 && PingMeter.age() < 15_000L) { ping = PingMeter.get(); eigen = true; }
                else if (conn != null) {
                    var info = conn.getPlayerInfo(p.getUUID());
                    if (info != null) ping = info.getLatency();
                }
                if (ping >= 0) {
                    neu.add(new Line(f, ZEILE, "Ping", ping + " ms" + (eigen ? "" : " *"),
                            ping <= 80 ? GRUEN : ping <= 150 ? GELB : ROT, 0));
                }
                float tps = TickRate.tps();
                float still = TickRate.seitLetztem();
                String tpsText = String.format(Locale.ROOT, "%.1f", tps) + (still > 3f ? String.format(Locale.ROOT, "   no data %.0f s", still) : "");
                neu.add(new Line(f, ZEILE, "TPS", tpsText, still > 3f || tps < 15 ? ROT : tps < 19 ? GELB : GRUEN, 0));
            }
            if (conn != null) {
                neu.add(new Line(f, ZEILE, "Players", String.valueOf(conn.getOnlinePlayers().size()), 0, 0));
            }
        }

        // ---- Masse ------------------------------------------------------------
        int lw = 0;
        for (Line l : neu) if (l.art == ZEILE || l.art == BALKEN) lw = Math.max(lw, l.labelW);
        int w = 0, h = 0;
        boolean ersterKopf = true;
        for (Line l : neu) {
            switch (l.art) {
                case KOPF -> { w = Math.max(w, l.labelW + 24); h += (ersterKopf ? 0 : 3) + 11; ersterKopf = false; }
                case GRAPH -> h += 21;
                case BALKEN -> { w = Math.max(w, lw + 8 + l.valueW + 6 + 40); h += 10; }
                default -> { w = Math.max(w, lw + 8 + l.valueW); h += 10; }
            }
        }
        w = Math.max(w, 110);
        lines = neu;
        labelSpalte = lw;
        breite = w;
        hoehe = Math.max(0, h - 1);
    }

    private static void kopf(List<Line> l, Font f, boolean kompakt, String titel) {
        if (!kompakt) l.add(new Line(f, KOPF, titel, null, 0, 0));
    }

    /** "dark_oak_stairs" -> "Dark Oak Stairs" */
    private static String schoen(String s) {
        StringBuilder b = new StringBuilder(s.length());
        boolean gross = true;
        for (char c : s.toCharArray()) {
            if (c == '_' || c == '/') { b.append(' '); gross = true; }
            else { b.append(gross ? Character.toUpperCase(c) : c); gross = false; }
        }
        return b.toString();
    }

    /** {Durchschnitt ms, 1%-Low-Frametime ms, laengster Frame ms} der letzten ~240 Frames. */
    private static double[] frameStats() {
        int n = frameAnzahl;
        if (n < 10) return null;
        long[] kopie = new long[n];
        long summe = 0;
        for (int i = 0; i < n; i++) {
            kopie[i] = FRAME_NS[Math.floorMod(frameIdx - 1 - i, FRAMES)];
            summe += kopie[i];
        }
        Arrays.sort(kopie);
        // 1 %-Low: Mittel der langsamsten 1 % (mindestens 1 Frame)
        int k = Math.max(1, n / 100);
        long schlecht = 0;
        for (int i = n - k; i < n; i++) schlecht += kopie[i];
        return new double[] { summe / (double) n / 1e6, schlecht / (double) k / 1e6, kopie[n - 1] / 1e6 };
    }
}
