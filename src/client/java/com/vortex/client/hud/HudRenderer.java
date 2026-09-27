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
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * Zeichnet die HUD-Overlays ueber die HudElementRegistry-API.
 */
public final class HudRenderer {

    private static final String MOD_ID = "vortexclient";

    public static void register() {
        HudElementRegistry.attachElementAfter(
            VanillaHudElements.MISC_OVERLAYS,
            Identifier.fromNamespaceAndPath(MOD_ID, "hud"),
            (context, tickCounter) -> onHudRender(context)
        );

        // Vanilla-Statuseffekt-Overlay (oben rechts) entfernen, damit unsere
        // eigene Effekt-Anzeige links nicht doppelt ist. removeElement tut
        // nichts, falls der Identifier nicht existiert -> kein Crash-Risiko.
        try {
            HudElementRegistry.removeElement(Identifier.withDefaultNamespace("status_effects"));
        } catch (Throwable ignored) {
            // Falls der Name in dieser Version abweicht: ignorieren.
        }
    }

    private static Module find(Class<? extends Module> type) {
        // Konstante Laufzeit statt die ganze Liste zu durchlaufen.
        return ModuleManager.INSTANCE.get(type);
    }

    private static void onHudRender(GuiGraphicsExtractor context) {
        long pvpT0 = System.nanoTime();
        try {
            onHudRenderInner(context);
        } finally {
            com.vortex.client.core.Profiler.record("HUD", System.nanoTime() - pvpT0);
        }
    }

    private static void onHudRenderInner(GuiGraphicsExtractor context) {
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
        ExtraHud.render(context, client);
        BossBars.render(context, client);
        SlotLock.render(context, client);

        // --- CPS ---
        CpsModule cps = (CpsModule) find(CpsModule.class);
        if (cps != null && cps.isEnabled()) {
            // Left, right, or both side by side -- see the module for why the
            // two are not added together.
            String wert;
            switch (cps.mode.getIndex()) {
                case 1:
                    wert = String.valueOf(CpsCounter.RIGHT.getCps());
                    break;
                case 2:
                    wert = CpsCounter.LEFT.getCps() + " | " + CpsCounter.RIGHT.getCps();
                    break;
                default:
                    wert = String.valueOf(CpsCounter.LEFT.getCps());
                    break;
            }
            HudText.zeile(context, client.font, cps.x.getInt(), cps.y.getInt(), cps.scale.getFloat(),
                    cps.style, cps.color, "CPS", wert, HudStyle.FORM_LABEL_FIRST);
        }

        // --- FPS ---
        FpsModule fps = (FpsModule) find(FpsModule.class);
        if (fps != null && fps.isEnabled()) {
            HudText.zeile(context, client.font, fps.x.getInt(), fps.y.getInt(), fps.scale.getFloat(),
                    fps.style, fps.color, "FPS", String.valueOf(client.getFps()), HudStyle.FORM_LABEL_LAST);
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
            HudText.zeile(context, client.font, ping.x.getInt(), ping.y.getInt(), ping.scale.getFloat(),
                    ping.style, ping.color, "Ping", text, HudStyle.FORM_VALUE_ONLY);
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

            String text = px + " " + py + " " + pz + "  [" + dir + "]";
            // Streamer-Modus: Richtung ja, Position nein.
            if (com.vortex.client.module.modules.StreamerModeModule.koordinatenVerstecken()) {
                text = "hidden  [" + dir + "]";
            }
            HudText.zeile(context, client.font, coords.x.getInt(), coords.y.getInt(), coords.scale.getFloat(),
                    coords.style, coords.color, "XYZ", text, HudStyle.FORM_LABEL_FIRST);
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
                        .formatDuration(effect, 1.0f, 20.0f).getString();

                // Box-Breite: Icon (22) + breiterer der beiden Texte + Rand.
                int textW = Math.max(
                        client.font.width(name),
                        client.font.width(time));
                int boxW = 24 + textW + 6;
                int boxH = 22;

                // 1) Dunkler, halbtransparenter Hintergrund-Kasten.
                context.fill(lineX, lineY, lineX + boxW, lineY + boxH, 0xC0000000);

                // 2) Vanilla-Icon links ueber den GUI-Sprite-Pfad
                //    "mob_effect/<name>" (drawGuiTexture nimmt einen Identifier).
                String effId = net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT
                        .getKey(effect.getEffect().value()).getPath();
                var spriteId = Identifier.withDefaultNamespace("mob_effect/" + effId);
                try {
                    context.blitSprite(
                        net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,
                        spriteId, lineX + 2, lineY + 2, 18, 18);
                } catch (Throwable ignored) {
                    // Icon nicht ladbar -> nur Component/Box.
                }

                // 3) Name oben, Restzeit darunter -- rechts neben dem Icon.
                context.text(client.font, Component.literal(name),
                        lineX + 24, lineY + 2, potions.color.get());
                context.text(client.font, Component.literal(time),
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
            }

            // Anzahl rechts neben dem Icon, vertikal mittig zum 16px-Icon --
            // Kasten, Rahmen und Verlauf ueber HudText.
            if (count != lastTotemCount || totemCountText == null) {
                lastTotemCount = count;
                totemCountText = Component.literal("x" + count);
            }
            HudText.mitIcon(context, client.font, tx, ty, totem.scale.getFloat(),
                    totem.style, totem.color, totemCountText.getString(), totemIcon);
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
                context.text(client.font, Component.literal(header),
                        screenW - hw - 2, y, plist.getColor());
                y += 11;
                for (String line : lines) {
                    int w = client.font.width(line);
                    context.text(client.font, Component.literal(line),
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
     * Nutzt den 2D-Matrixstack (Matrix3x2fStack) von GuiGraphicsExtractor.getMatrices(),
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
    private static void drawRecordingHint(GuiGraphicsExtractor ctx, Minecraft client) {
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
        ctx.text(client.font, Component.literal(text),
                x, y, alpha | 0xFF5555);
    }

    /** Header of the totem popper list -- never changes, built once. */
    private static final Component TOTEM_POPPER_TITLE = Component.literal("Totems");

    private static void drawTotemPopper(GuiGraphicsExtractor ctx, Minecraft client) {
        TotemPopperModule mod = (TotemPopperModule) find(TotemPopperModule.class);
        if (mod == null || !mod.isEnabled()) return;
        // The overhead count and this list switch independently.
        if (!mod.showList.get()) return;
        if (client.font == null) return;

        var list = com.vortex.client.hud.TotemPops.top(mod.maxEntries.getInt());
        if (list.isEmpty()) return;

        java.util.List<HudText.Zeile> zeilen = new java.util.ArrayList<>(list.size() + 1);
        zeilen.add(new HudText.Zeile(null, TOTEM_POPPER_TITLE.getString()));
        for (var e : list) {
            // Frisch verbrauchte Totems fuer zwei Sekunden hervorheben.
            boolean fresh = mod.highlight.get() && e.since < 2000;
            zeilen.add(new HudText.Zeile(e.name, String.valueOf(e.count),
                    fresh ? mod.highlightColor.get() : 0));
        }
        HudText.block(ctx, client.font, mod.x.getInt(), mod.y.getInt(), mod.scale.getFloat(),
                mod.style, mod.color, zeilen, HudStyle.FORM_LABEL_FIRST, 0, 1f, null);
    }

    /** Spielzeit, Tode, eigene Totems, hoechste Klickrate. */
    private static void drawSessionStats(GuiGraphicsExtractor ctx, Minecraft client) {
        SessionStatsModule mod = (SessionStatsModule) find(SessionStatsModule.class);
        if (mod == null || !mod.isEnabled()) return;
        if (client.font == null) return;


        // Rebuilt twice a second: playtime is the fastest-moving of the four
        // and only changes once a second -- four string concats plus four
        // Component.literal per frame for that was pure allocation churn.
        long nowMs = System.currentTimeMillis();
        if (nowMs - sessionTextsBuilt > 500 || sessionTime == null) {
            sessionTextsBuilt = nowMs;
            sessionTime   = com.vortex.client.hud.SessionStats.playtime();
            sessionDeaths = String.valueOf(com.vortex.client.hud.SessionStats.getDeaths());
            sessionTotems = String.valueOf(com.vortex.client.hud.SessionStats.getOwnTotems());
            sessionCps    = String.valueOf(com.vortex.client.hud.SessionStats.getMaxCps());
        }

        java.util.List<HudText.Zeile> zeilen = new java.util.ArrayList<>(4);
        if (mod.showTime.get())   zeilen.add(new HudText.Zeile("Time", sessionTime));
        if (mod.showDeaths.get()) zeilen.add(new HudText.Zeile("Deaths", sessionDeaths));
        if (mod.showTotems.get()) zeilen.add(new HudText.Zeile("Totems", sessionTotems));
        if (mod.showMaxCps.get()) zeilen.add(new HudText.Zeile("Max CPS", sessionCps));
        HudText.block(ctx, client.font, mod.x.getInt(), mod.y.getInt(), mod.scale.getFloat(),
                mod.style, mod.color, zeilen, HudStyle.FORM_LABEL_FIRST, 0, 1f, null);
    }

    private static long sessionTextsBuilt = 0L;
    private static String sessionTime, sessionDeaths, sessionTotems, sessionCps;

    private static void drawKeystrokes(GuiGraphicsExtractor ctx, Minecraft client) {
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
    private static void drawKey(GuiGraphicsExtractor ctx, Minecraft client,
                                int x, int y, int w, int h, String label,
                                boolean pressed, int idle, int press, int textCol) {
        ctx.fill(x, y, x + w, y + h, pressed ? press : idle);
        if (label == null || label.isEmpty()) return;
        // Gedrueckte Taste ist hell -> dunkle Schrift, sonst die eingestellte Farbe.
        int col = pressed ? 0xFF101014 : textCol;
        int tw = client.font.width(label);
        ctx.text(client.font, Component.literal(label),
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

    static void pushScale(GuiGraphicsExtractor context, float anchorX, float anchorY, float scale) {
        var m = context.pose();
        m.pushMatrix();
        m.translate(anchorX, anchorY);
        m.scale(scale, scale);
        m.translate(-anchorX, -anchorY);
    }

    static void popScale(GuiGraphicsExtractor context) {
        context.pose().popMatrix();
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
