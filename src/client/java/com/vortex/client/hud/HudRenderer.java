package com.vortex.client.hud;

import com.vortex.client.module.Module;
import com.vortex.client.module.ModuleManager;
import com.vortex.client.module.modules.ArmorHudModule;
import com.vortex.client.module.modules.CpsModule;
import com.vortex.client.module.modules.KeystrokesModule;
import com.vortex.client.module.modules.TotemPopperModule;
import com.vortex.client.module.modules.SessionStatsModule;
import com.vortex.client.module.modules.CoordinatesModule;
import com.vortex.client.module.modules.PotionEffectsModule;
import com.vortex.client.module.modules.FpsModule;
import com.vortex.client.module.modules.PingModule;
import com.vortex.client.module.modules.TotemCountModule;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * Zeichnet die HUD-Overlays ueber die HudElementRegistry-API.
 */
public final class HudRenderer {

    private static final String MOD_ID = "vortexclient";

    public static void register() {
        HudElementRegistry.attachElementAfter(
            VanillaHudElements.MISC_OVERLAYS,
            new ResourceLocation(MOD_ID, "hud"),
            (context, tickCounter) -> onHudRender(context)
        );

        // Vanilla-Statuseffekt-Overlay (oben rechts) entfernen, damit unsere
        // eigene Effekt-Anzeige links nicht doppelt ist. removeElement tut
        // nichts, falls der ResourceLocation nicht existiert -> kein Crash-Risiko.
        try {
            HudElementRegistry.removeElement(new ResourceLocation("minecraft", "status_effects"));
        } catch (Throwable ignored) {
            // Falls der Name in dieser Version abweicht: ignorieren.
        }
    }

    private static Module find(Class<? extends Module> type) {
        // Konstante Laufzeit statt die ganze Liste zu durchlaufen.
        return ModuleManager.INSTANCE.get(type);
    }

    private static void onHudRender(GuiGraphics context) {
        long pvpT0 = System.nanoTime();
        try {
            onHudRenderInner(context);
        } finally {
            com.vortex.client.core.Profiler.record("HUD", System.nanoTime() - pvpT0);
        }
    }

    private static void onHudRenderInner(GuiGraphics context) {
        Minecraft client = Minecraft.getInstance();

        // KEINE frühen return-Checks mehr. Vorher brach hier
        // 'if (client.player == null) return;' oder der Debug-Check die
        // Methode ab, BEVOR die HUDs gezeichnet wurden -- deshalb erschien
        // nur das (frühere) Diagnose-Rechteck, aber nie CPS/FPS.
        if (client.font == null) return;

        com.vortex.client.hud.WaypointHud.draw(context, client);
        drawKeystrokes(context, client);
        drawTotemPopper(context, client);
        drawRecordingHint(context, client);
        ArmorWarning.render(context, client);
        ItemCounterRenderer.render(context, client);
        DebugOverlay.render(context, client);
        drawSessionStats(context, client);

        // --- CPS ---
        CpsModule cps = (CpsModule) find(CpsModule.class);
        if (cps != null && cps.isEnabled()) {
            // Left, right, or both side by side -- see the module for why the
            // two are not added together.
            String text;
            switch (cps.mode.getIndex()) {
                case 1:
                    text = "CPS: " + CpsCounter.RIGHT.getCps();
                    break;
                case 2:
                    text = "CPS: " + CpsCounter.LEFT.getCps()
                            + " | " + CpsCounter.RIGHT.getCps();
                    break;
                default:
                    text = "CPS: " + CpsCounter.LEFT.getCps();
                    break;
            }
            pushScale(context, cps.x.getInt(), cps.y.getInt(), cps.scale.getFloat());
            context.drawString(client.font, Component.literal(text),
                    cps.x.getInt(), cps.y.getInt(), cps.color.get());
            popScale(context);
        }

        // --- FPS ---
        FpsModule fps = (FpsModule) find(FpsModule.class);
        if (fps != null && fps.isEnabled()) {
            String text = client.getFps() + " FPS";
            pushScale(context, fps.x.getInt(), fps.y.getInt(), fps.scale.getFloat());
            context.drawString(client.font, Component.literal(text),
                    fps.x.getInt(), fps.y.getInt(), fps.color.get());
            popScale(context);
        }

        // --- Ping (aktuelle Latenz zum Server) ---
        PingModule ping = (PingModule) find(PingModule.class);
        if (ping != null && ping.isEnabled() && client.player != null
                && client.getConnection() != null) {
            int latency = 0;
            boolean own = false;
            if (ping.measure.get()) {
                // Our own measurement, refreshed every second. Only used while
                // it is actually recent -- a stale reading is no better than
                // the server's, so fall back rather than show something old.
                PingMeter.setInterval((long) (ping.interval.get() * 1000));
                int measured = PingMeter.get();
                if (measured >= 0 && PingMeter.age() < 15_000L) {
                    latency = measured;
                    own = true;
                }
            }
            if (!own) {
                try {
                    net.minecraft.client.multiplayer.PlayerInfo entry =
                            client.getConnection()
                            .getPlayerInfo(client.player.getUUID());
                    if (entry != null) latency = entry.getLatency();
                } catch (Throwable ignored) {
                }
            }
            // A star marks the server's own figure.
            //
            // That number is only refreshed about every thirty seconds, so it
            // lags behind by design. Without the mark you cannot tell a stale
            // reading from a live one -- which is exactly how a wrong-looking
            // ping goes unexplained for weeks.
            String text = own ? (latency + " ms") : (latency + " ms*");
            pushScale(context, ping.x.getInt(), ping.y.getInt(), ping.scale.getFloat());
            context.drawString(client.font, Component.literal(text),
                    ping.x.getInt(), ping.y.getInt(), ping.color.get());
            popScale(context);
        }

        // --- Koordinaten (nur wenn ein Spieler da ist) ---
        CoordinatesModule coords = (CoordinatesModule) find(CoordinatesModule.class);
        if (coords != null && coords.isEnabled() && client.player != null) {
            // Mit einer Nachkommastelle, wie im F3-Bildschirm.
            double px = Math.round(client.player.getX() * 10.0) / 10.0;
            double py = Math.round(client.player.getY() * 10.0) / 10.0;
            double pz = Math.round(client.player.getZ() * 10.0) / 10.0;

            // Blickrichtung als Himmelsrichtung.
            String dir;
            switch (client.player.getDirection()) {
                case NORTH -> dir = "N";
                case SOUTH -> dir = "S";
                case EAST  -> dir = "O";
                case WEST  -> dir = "W";
                default    -> dir = "";
            }

            String text = "XYZ: " + px + " " + py + " " + pz + "  [" + dir + "]";
            pushScale(context, coords.x.getInt(), coords.y.getInt(), coords.scale.getFloat());
            context.drawString(client.font, Component.literal(text),
                    coords.x.getInt(), coords.y.getInt(), coords.color.get());
            popScale(context);
        }

        // --- Potion-Effekte (Box + Icon + Name + Restzeit, wie AppleSkin-Stil) ---
        PotionEffectsModule potions = (PotionEffectsModule) find(PotionEffectsModule.class);
        if (potions != null && potions.isEnabled() && client.player != null) {
            int lineY = potions.y.getInt();
            int lineX = potions.x.getInt();

            pushScale(context, lineX, lineY, potions.scale.getFloat());
            for (var effect : client.player.getActiveEffects()) {
                // Namen + Stufe vorbereiten (fuer Box-Breite).
                String key = effect.getDescriptionId();
                String raw = key.substring(key.lastIndexOf('.') + 1);
                String name = capitalize(raw.replace('_', ' '));
                int amp = effect.getAmplifier();
                if (amp > 0) {
                    name = name + " " + toRoman(amp + 1);
                }
                String time = net.minecraft.world.effect.MobEffectUtil
                        .formatDuration(effect, 1.0f).getString();

                // Box-Breite: Icon (22) + breiterer der beiden Texte + Rand.
                int textW = Math.max(
                        client.font.width(name),
                        client.font.width(time));
                int boxW = 24 + textW + 6;
                int boxH = 22;

                // 1) Dunkler, halbtransparenter Hintergrund-Kasten.
                context.fill(lineX, lineY, lineX + boxW, lineY + boxH, 0xC0000000);

                // 2) Vanilla-Icon links ueber den GUI-Sprite-Pfad
                //    "mob_effect/<name>" (drawGuiTexture nimmt einen ResourceLocation).
                String effId = net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT
                        .getKey(effect.getEffect()).getPath();
                var spriteTexture = new ResourceLocation("minecraft", "textures/mob_effect/" + effId + ".png");
                try {
                    context.blit(spriteTexture, lineX + 2, lineY + 2,
                            0.0F, 0.0F, 18, 18, 18, 18);
                } catch (Throwable ignored) {
                    // Icon nicht ladbar -> nur Component/Box.
                }

                // 3) Name oben, Restzeit darunter -- rechts neben dem Icon.
                context.drawString(client.font, Component.literal(name),
                        lineX + 24, lineY + 2, potions.color.get());
                context.drawString(client.font, Component.literal(time),
                        lineX + 24, lineY + 12, 0xFFAAAAAA);

                lineY += boxH + 3; // naechste Box mit kleinem Abstand
            }
            popScale(context);
        }

        // --- Totem-Counter (Icon + Anzahl im Inventar) ---
        TotemCountModule totem = (TotemCountModule) find(TotemCountModule.class);
        if (totem != null && totem.isEnabled() && client.player != null) {
            int count = TotemCountModule.countTotems();
            int tx = totem.x.getInt();
            int ty = totem.y.getInt();

            pushScale(context, tx, ty, totem.scale.getFloat());

            // Totem-Icon links zeichnen (16x16). new ItemStack(Item) ist ok,
            // weil Item das ItemConvertible-Interface erfuellt.
            // The stack is built once and kept.
            //
            // A new one per frame is a small object, but a small object a
            // hundred and fifty times a second is still work for a picture
            // that never changes.
            var totemItem = TotemCountModule.totem();
            if (totemItem != null) {
                if (totemIcon == null
                        || !totemIcon.is(totemItem)) {
                    totemIcon = new net.minecraft.world.item.ItemStack(totemItem);
                }
                context.renderItem(totemIcon, tx, ty);
            }

            // Anzahl rechts neben dem Icon, vertikal mittig zum 16px-Icon.
            // Component object rebuilt only when the count changes -- same idea as
            // the icon above it.
            if (count != lastTotemCount || totemCountText == null) {
                lastTotemCount = count;
                totemCountText = Component.literal("x" + count);
            }
            context.drawString(client.font, totemCountText,
                    tx + 20, ty + 4, totem.color.get());

            popScale(context);
        }

        // --- ArmorHUD (nur wenn ein Spieler da ist) ---
        ArmorHudModule armor = (ArmorHudModule) find(ArmorHudModule.class);
        if (armor != null && armor.isEnabled() && client.player != null) {
            ArmorHud.render(context, client);
        }

        // --- Radar (zeichnet sich selbst, prueft intern auf aktiv/Spieler) ---
        RadarRenderer.render(context, client);

        // --- Player List ESP (Spieler in Reichweite mit Distanz) ---
        com.vortex.client.module.modules.PlayerListEspModule plist =
                (com.vortex.client.module.modules.PlayerListEspModule)
                        find(com.vortex.client.module.modules.PlayerListEspModule.class);
        if (plist != null && plist.isEnabled() && client.player != null
                && client.level != null) {
            try {
                // Spieler sammeln (ohne den eigenen) mit Distanz.
                // Rebuilt a few times a second, not on every frame.
                //
                // This used to build a fresh list and a fresh string for every
                // player on every single frame. At 150 frames a second with
                // twenty players nearby that is three thousand strings a
                // second for a list nobody can read that fast -- distances in
                // whole metres do not change meaningfully between frames.
                long nowMs = System.currentTimeMillis();
                if (nowMs - playerListBuilt > 200) {
                    playerListBuilt = nowMs;
                    playerLines.clear();
                    for (net.minecraft.client.player.AbstractClientPlayer p
                            : client.level.players()) {
                        if (p == client.player) continue;
                        int dist = (int) client.player.distanceTo(p);
                        playerLines.add(p.getName().getString() + "  " + dist + "m");
                    }
                }
                java.util.List<String> lines = playerLines;
                // Oben rechts anzeigen.
                int screenW = client.getWindow().getGuiScaledWidth();
                int y = 2;
                String header = "Spieler: " + lines.size();
                int hw = client.font.width(header);
                context.drawString(client.font, Component.literal(header),
                        screenW - hw - 2, y, plist.getColor());
                y += 11;
                for (String line : lines) {
                    int w = client.font.width(line);
                    context.drawString(client.font, Component.literal(line),
                            screenW - w - 2, y, plist.getColor());
                    y += 10;
                    if (y > client.getWindow().getGuiScaledHeight() - 10) break; // Schutz
                }
            } catch (Throwable ignored) {
            }
        }
    }

    /**
     * Beginnt eine Skalierung um den Punkt (anchorX, anchorY). Alles bis zum
     * passenden popScale wird mit dem Faktor scale gezeichnet, wobei der
     * Ankerpunkt fix bleibt (das Element waechst/schrumpft also an seiner
     * Position statt zum Bildschirmrand zu wandern).
     *
     * Nutzt den 2D-Matrixstack (Matrix3x2fStack) von GuiGraphics.getMatrices(),
     * der in 1.21.11 fuer GUI-Transforms zustaendig ist.
     */
    /**
     * Zeichnet die Keystrokes-Anzeige (WASD, Leertaste, Maustasten).
     *
     * Gedrueckte Tasten werden hervorgehoben. Die Maustasten zeigen zusaetzlich
     * die Klicks pro Sekunde, damit man sein Klickverhalten im Blick hat.
     */
    /** Liste der Spieler mit verbrauchten Totems. */
    /**
     * Shows that a macro is being recorded.
     *
     * Without this the feature is a guessing game: you press Record, the menu
     * closes, and nothing on screen tells you whether anything is being
     * captured -- or how to stop. The line says both.
     */
    private static void drawRecordingHint(GuiGraphics ctx, Minecraft client) {
        if (!com.vortex.client.macro.MacroManager.isRecording()) return;
        var macro = com.vortex.client.macro.MacroManager.recordingMacro();
        if (macro == null) return;

        String text = "\u25CF REC  " + macro.name + "  \u00B7  "
                + macro.steps.size() + " steps  \u00B7  Right Shift \u2192 Macros \u2192 Stop";
        int w = client.font.width(text);
        int x = (client.getWindow().getGuiScaledWidth() - w) / 2;
        int y = 6;

        // Slow pulse, so the dot reads as "running" rather than as a stuck
        // pixel, without flashing hard enough to distract during a fight.
        float pulse = 0.65f + 0.35f * (float) Math.sin(System.currentTimeMillis() / 400.0);
        int alpha = (int) (255 * pulse) << 24;

        ctx.fill(x - 6, y - 3, x + w + 6, y + 11, 0x90000000);
        ctx.drawString(client.font, Component.literal(text),
                x, y, alpha | 0xFF5555);
    }

    /** Header of the totem popper list -- never changes, built once. */
    private static final Component TOTEM_POPPER_TITLE = Component.literal("Totems");

    private static void drawTotemPopper(GuiGraphics ctx, Minecraft client) {
        TotemPopperModule mod = (TotemPopperModule) find(TotemPopperModule.class);
        if (mod == null || !mod.isEnabled()) return;
        // The overhead count and this list switch independently.
        if (!mod.showList.get()) return;
        if (client.font == null) return;

        var list = com.vortex.client.hud.TotemPops.top(mod.maxEntries.getInt());
        if (list.isEmpty()) return;

        int bx = mod.x.getInt();
        int by = mod.y.getInt();
        pushScale(ctx, bx, by, mod.scale.getFloat());

        ctx.drawString(client.font, TOTEM_POPPER_TITLE,
                bx, by, mod.color.get());
        int ly = by + 10;
        for (var e : list) {
            // Frisch verbrauchte Totems fuer zwei Sekunden hervorheben.
            boolean fresh = mod.highlight.get() && e.since < 2000;
            int col = fresh ? mod.highlightColor.get() : mod.color.get();
            ctx.drawString(client.font,
                    Component.literal(e.name + ": " + e.count), bx, ly, col);
            ly += 10;
        }
        popScale(ctx);
    }

    /** Spielzeit, Tode, eigene Totems, hoechste Klickrate. */
    private static void drawSessionStats(GuiGraphics ctx, Minecraft client) {
        SessionStatsModule mod = (SessionStatsModule) find(SessionStatsModule.class);
        if (mod == null || !mod.isEnabled()) return;
        if (client.font == null) return;

        int bx = mod.x.getInt();
        int by = mod.y.getInt();
        pushScale(ctx, bx, by, mod.scale.getFloat());

        int col = mod.color.get();
        int ly = by;

        // Rebuilt twice a second: playtime is the fastest-moving of the four
        // and only changes once a second -- four string concats plus four
        // Component.literal per frame for that was pure allocation churn.
        long nowMs = System.currentTimeMillis();
        if (nowMs - sessionTextsBuilt > 500 || sessionTime == null) {
            sessionTextsBuilt = nowMs;
            sessionTime   = Component.literal("Time: " + com.vortex.client.hud.SessionStats.playtime());
            sessionDeaths = Component.literal("Deaths: " + com.vortex.client.hud.SessionStats.getDeaths());
            sessionTotems = Component.literal("Totems: " + com.vortex.client.hud.SessionStats.getOwnTotems());
            sessionCps    = Component.literal("Max CPS: " + com.vortex.client.hud.SessionStats.getMaxCps());
        }

        if (mod.showTime.get()) {
            ctx.drawString(client.font, sessionTime, bx, ly, col);
            ly += 10;
        }
        if (mod.showDeaths.get()) {
            ctx.drawString(client.font, sessionDeaths, bx, ly, col);
            ly += 10;
        }
        if (mod.showTotems.get()) {
            ctx.drawString(client.font, sessionTotems, bx, ly, col);
            ly += 10;
        }
        if (mod.showMaxCps.get()) {
            ctx.drawString(client.font, sessionCps, bx, ly, col);
        }
        popScale(ctx);
    }

    private static long sessionTextsBuilt = 0L;
    private static Component sessionTime, sessionDeaths, sessionTotems, sessionCps;

    private static void drawKeystrokes(GuiGraphics ctx, Minecraft client) {
        KeystrokesModule mod = (KeystrokesModule) find(KeystrokesModule.class);
        if (mod == null || !mod.isEnabled()) return;
        if (client.options == null || client.font == null) return;

        int baseX = mod.x.getInt();
        int baseY = mod.y.getInt();
        pushScale(ctx, baseX, baseY, mod.scale.getFloat());

        int idle = mod.idleColor.get();
        int press = mod.pressColor.get();
        int textCol = mod.color.get();

        final int KEY = 20;
        final int GAP = 2;

        // Reihe 1: W mittig ueber ASD.
        drawKey(ctx, client, baseX + KEY + GAP, baseY, KEY, KEY,
                "W", client.options.keyUp.isDown(), idle, press, textCol);

        // Reihe 2: A S D
        int row2 = baseY + KEY + GAP;
        drawKey(ctx, client, baseX, row2, KEY, KEY,
                "A", client.options.keyLeft.isDown(), idle, press, textCol);
        drawKey(ctx, client, baseX + KEY + GAP, row2, KEY, KEY,
                "S", client.options.keyDown.isDown(), idle, press, textCol);
        drawKey(ctx, client, baseX + (KEY + GAP) * 2, row2, KEY, KEY,
                "D", client.options.keyRight.isDown(), idle, press, textCol);

        int nextY = row2 + KEY + GAP;
        int fullW = KEY * 3 + GAP * 2;

        if (mod.showMouse.get()) {
            int half = (fullW - GAP) / 2;
            drawKey(ctx, client, baseX, nextY, half, KEY,
                    "L " + CpsCounter.LEFT.getCps(),
                    client.options.keyAttack.isDown(), idle, press, textCol);
            drawKey(ctx, client, baseX + half + GAP, nextY, half, KEY,
                    "R " + CpsCounter.RIGHT.getCps(),
                    client.options.keyUse.isDown(), idle, press, textCol);
            nextY += KEY + GAP;
        }

        if (mod.showSpace.get()) {
            drawKey(ctx, client, baseX, nextY, fullW, 10,
                    "", client.options.keyJump.isDown(), idle, press, textCol);
        }

        popScale(ctx);
    }

    /** Eine einzelne Taste: Flaeche plus mittige Beschriftung. */
    private static void drawKey(GuiGraphics ctx, Minecraft client,
                                int x, int y, int w, int h, String label,
                                boolean pressed, int idle, int press, int textCol) {
        ctx.fill(x, y, x + w, y + h, pressed ? press : idle);
        if (label == null || label.isEmpty()) return;
        // Gedrueckte Taste ist hell -> dunkle Schrift, sonst die eingestellte Farbe.
        int col = pressed ? 0xFF101014 : textCol;
        int tw = client.font.width(label);
        ctx.drawString(client.font, Component.literal(label),
                x + (w - tw) / 2, y + (h - 8) / 2, col);
    }

    /**
     * Scaling helper, shared with the other HUD parts.
     *
     * Package visible rather than private so ArmorWarning uses the very same
     * one -- a second copy of this would drift, and elements would then behave
     * differently in the editor for no reason anyone could see.
     */
    /** The totem icon, built once rather than every frame. */
    private static net.minecraft.world.item.ItemStack totemIcon = null;
    private static int lastTotemCount = Integer.MIN_VALUE;
    private static Component totemCountText = null;

    /** Cached player list, so it is not rebuilt on every frame. */
    private static final java.util.List<String> playerLines = new java.util.ArrayList<>();

    /** When that list was last rebuilt. */
    private static long playerListBuilt = 0L;

    static void pushScale(GuiGraphics context, float anchorX, float anchorY, float scale) {
        var m = context.pose();
        m.pushPose();
        m.translate(anchorX, anchorY, 0.0D);
        m.scale(scale, scale, 1.0F);
        m.translate(-anchorX, -anchorY, 0.0D);
    }

    static void popScale(GuiGraphics context) {
        context.pose().popPose();
    }

    /** Macht den ersten Buchstaben jedes Wortes gross ("strength" -> "Strength"). */
    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        StringBuilder sb = new StringBuilder();
        boolean cap = true;
        for (char c : s.toCharArray()) {
            if (cap && Character.isLetter(c)) {
                sb.append(Character.toUpperCase(c));
                cap = false;
            } else {
                sb.append(c);
                if (c == ' ') cap = true;
            }
        }
        return sb.toString();
    }

    /** Wandelt 1..n in roemische Zahlen (I, II, III, IV, ...). */
    private static String toRoman(int n) {
        if (n <= 0) return "";
        int[] values = {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};
        String[] symbols = {"M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"};
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            while (n >= values[i]) {
                n -= values[i];
                sb.append(symbols[i]);
            }
        }
        return sb.toString();
    }
}
