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
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * Zeichnet die HUD-Overlays ueber die HudElementRegistry-API.
 */
public final class HudRenderer {

    private static final String MOD_ID = "vortexclient";

    public static void register() {
        HudElementRegistry.attachElementAfter(
            VanillaHudElements.MISC_OVERLAYS,
            Identifier.of(MOD_ID, "hud"),
            (context, tickCounter) -> onHudRender(context)
        );

        // Vanilla-Statuseffekt-Overlay (oben rechts) entfernen, damit unsere
        // eigene Effekt-Anzeige links nicht doppelt ist. removeElement tut
        // nichts, falls der Identifier nicht existiert -> kein Crash-Risiko.
        try {
            HudElementRegistry.removeElement(Identifier.ofVanilla("status_effects"));
        } catch (Throwable ignored) {
            // Falls der Name in dieser Version abweicht: ignorieren.
        }
    }

    private static Module find(Class<? extends Module> type) {
        // Konstante Laufzeit statt die ganze Liste zu durchlaufen.
        return ModuleManager.INSTANCE.get(type);
    }

    private static void onHudRender(DrawContext context) {
        long pvpT0 = System.nanoTime();
        try {
            onHudRenderInner(context);
        } finally {
            com.vortex.client.core.Profiler.record("HUD", System.nanoTime() - pvpT0);
        }
    }

    private static void onHudRenderInner(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();

        // KEINE frühen return-Checks mehr. Vorher brach hier
        // 'if (client.player == null) return;' oder der Debug-Check die
        // Methode ab, BEVOR die HUDs gezeichnet wurden -- deshalb erschien
        // nur das (frühere) Diagnose-Rechteck, aber nie CPS/FPS.
        if (client.textRenderer == null) return;

        com.vortex.client.hud.WaypointHud.draw(context, client);
        drawKeystrokes(context, client);
        drawTotemPopper(context, client);
        drawRecordingHint(context, client);
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
            context.drawTextWithShadow(client.textRenderer, Text.literal(text),
                    cps.x.getInt(), cps.y.getInt(), cps.color.get());
            popScale(context);
        }

        // --- FPS ---
        FpsModule fps = (FpsModule) find(FpsModule.class);
        if (fps != null && fps.isEnabled()) {
            String text = client.getCurrentFps() + " FPS";
            pushScale(context, fps.x.getInt(), fps.y.getInt(), fps.scale.getFloat());
            context.drawTextWithShadow(client.textRenderer, Text.literal(text),
                    fps.x.getInt(), fps.y.getInt(), fps.color.get());
            popScale(context);
        }

        // --- Ping (aktuelle Latenz zum Server) ---
        PingModule ping = (PingModule) find(PingModule.class);
        if (ping != null && ping.isEnabled() && client.player != null
                && client.getNetworkHandler() != null) {
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
                    net.minecraft.client.network.PlayerListEntry entry =
                            client.getNetworkHandler()
                            .getPlayerListEntry(client.player.getUuid());
                    if (entry != null) latency = entry.getLatency();
                } catch (Throwable ignored) {
                }
            }
            String text = latency + " ms";
            pushScale(context, ping.x.getInt(), ping.y.getInt(), ping.scale.getFloat());
            context.drawTextWithShadow(client.textRenderer, Text.literal(text),
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
            switch (client.player.getHorizontalFacing()) {
                case NORTH -> dir = "N";
                case SOUTH -> dir = "S";
                case EAST  -> dir = "O";
                case WEST  -> dir = "W";
                default    -> dir = "";
            }

            String text = "XYZ: " + px + " " + py + " " + pz + "  [" + dir + "]";
            pushScale(context, coords.x.getInt(), coords.y.getInt(), coords.scale.getFloat());
            context.drawTextWithShadow(client.textRenderer, Text.literal(text),
                    coords.x.getInt(), coords.y.getInt(), coords.color.get());
            popScale(context);
        }

        // --- Potion-Effekte (Box + Icon + Name + Restzeit, wie AppleSkin-Stil) ---
        PotionEffectsModule potions = (PotionEffectsModule) find(PotionEffectsModule.class);
        if (potions != null && potions.isEnabled() && client.player != null) {
            int lineY = potions.y.getInt();
            int lineX = potions.x.getInt();

            pushScale(context, lineX, lineY, potions.scale.getFloat());
            for (var effect : client.player.getStatusEffects()) {
                // Namen + Stufe vorbereiten (fuer Box-Breite).
                String key = effect.getTranslationKey();
                String raw = key.substring(key.lastIndexOf('.') + 1);
                String name = capitalize(raw.replace('_', ' '));
                int amp = effect.getAmplifier();
                if (amp > 0) {
                    name = name + " " + toRoman(amp + 1);
                }
                String time = net.minecraft.entity.effect.StatusEffectUtil
                        .getDurationText(effect, 1.0f, 20.0f).getString();

                // Box-Breite: Icon (22) + breiterer der beiden Texte + Rand.
                int textW = Math.max(
                        client.textRenderer.getWidth(name),
                        client.textRenderer.getWidth(time));
                int boxW = 24 + textW + 6;
                int boxH = 22;

                // 1) Dunkler, halbtransparenter Hintergrund-Kasten.
                context.fill(lineX, lineY, lineX + boxW, lineY + boxH, 0xC0000000);

                // 2) Vanilla-Icon links ueber den GUI-Sprite-Pfad
                //    "mob_effect/<name>" (drawGuiTexture nimmt einen Identifier).
                String effId = net.minecraft.registry.Registries.STATUS_EFFECT
                        .getId(effect.getEffectType().value()).getPath();
                var spriteId = Identifier.ofVanilla("mob_effect/" + effId);
                try {
                    context.drawGuiTexture(
                        net.minecraft.client.gl.RenderPipelines.GUI_TEXTURED,
                        spriteId, lineX + 2, lineY + 2, 18, 18);
                } catch (Throwable ignored) {
                    // Icon nicht ladbar -> nur Text/Box.
                }

                // 3) Name oben, Restzeit darunter -- rechts neben dem Icon.
                context.drawTextWithShadow(client.textRenderer, Text.literal(name),
                        lineX + 24, lineY + 2, potions.color.get());
                context.drawTextWithShadow(client.textRenderer, Text.literal(time),
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
            var totemItem = TotemCountModule.totem();
            if (totemItem != null) {
                context.drawItem(new net.minecraft.item.ItemStack(totemItem), tx, ty);
            }

            // Anzahl rechts neben dem Icon, vertikal mittig zum 16px-Icon.
            String text = "x" + count;
            context.drawTextWithShadow(client.textRenderer, Text.literal(text),
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
                && client.world != null) {
            try {
                // Spieler sammeln (ohne den eigenen) mit Distanz.
                java.util.List<net.minecraft.client.network.AbstractClientPlayerEntity> players =
                        client.world.getPlayers();
                java.util.List<String> lines = new java.util.ArrayList<>();
                for (net.minecraft.client.network.AbstractClientPlayerEntity p : players) {
                    if (p == client.player) continue;
                    int dist = (int) client.player.distanceTo(p);
                    lines.add(p.getName().getString() + "  " + dist + "m");
                }
                // Oben rechts anzeigen.
                int screenW = client.getWindow().getScaledWidth();
                int y = 2;
                String header = "Spieler: " + lines.size();
                int hw = client.textRenderer.getWidth(header);
                context.drawTextWithShadow(client.textRenderer, Text.literal(header),
                        screenW - hw - 2, y, plist.getColor());
                y += 11;
                for (String line : lines) {
                    int w = client.textRenderer.getWidth(line);
                    context.drawTextWithShadow(client.textRenderer, Text.literal(line),
                            screenW - w - 2, y, plist.getColor());
                    y += 10;
                    if (y > client.getWindow().getScaledHeight() - 10) break; // Schutz
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
     * Nutzt den 2D-Matrixstack (Matrix3x2fStack) von DrawContext.getMatrices(),
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
    private static void drawRecordingHint(DrawContext ctx, MinecraftClient client) {
        if (!com.vortex.client.macro.MacroManager.isRecording()) return;
        var macro = com.vortex.client.macro.MacroManager.recordingMacro();
        if (macro == null) return;

        String text = "\u25CF REC  " + macro.name + "  \u00B7  "
                + macro.steps.size() + " steps  \u00B7  Right Shift \u2192 Macros \u2192 Stop";
        int w = client.textRenderer.getWidth(text);
        int x = (client.getWindow().getScaledWidth() - w) / 2;
        int y = 6;

        // Slow pulse, so the dot reads as "running" rather than as a stuck
        // pixel, without flashing hard enough to distract during a fight.
        float pulse = 0.65f + 0.35f * (float) Math.sin(System.currentTimeMillis() / 400.0);
        int alpha = (int) (255 * pulse) << 24;

        ctx.fill(x - 6, y - 3, x + w + 6, y + 11, 0x90000000);
        ctx.drawTextWithShadow(client.textRenderer, Text.literal(text),
                x, y, alpha | 0xFF5555);
    }

    private static void drawTotemPopper(DrawContext ctx, MinecraftClient client) {
        TotemPopperModule mod = (TotemPopperModule) find(TotemPopperModule.class);
        if (mod == null || !mod.isEnabled()) return;
        // The overhead count and this list switch independently.
        if (!mod.showList.get()) return;
        if (client.textRenderer == null) return;

        var list = com.vortex.client.hud.TotemPops.top(mod.maxEntries.getInt());
        if (list.isEmpty()) return;

        int bx = mod.x.getInt();
        int by = mod.y.getInt();
        pushScale(ctx, bx, by, mod.scale.getFloat());

        ctx.drawTextWithShadow(client.textRenderer, Text.literal("Totems"),
                bx, by, mod.color.get());
        int ly = by + 10;
        for (var e : list) {
            // Frisch verbrauchte Totems fuer zwei Sekunden hervorheben.
            boolean fresh = mod.highlight.get() && e.since < 2000;
            int col = fresh ? mod.highlightColor.get() : mod.color.get();
            ctx.drawTextWithShadow(client.textRenderer,
                    Text.literal(e.name + ": " + e.count), bx, ly, col);
            ly += 10;
        }
        popScale(ctx);
    }

    /** Spielzeit, Tode, eigene Totems, hoechste Klickrate. */
    private static void drawSessionStats(DrawContext ctx, MinecraftClient client) {
        SessionStatsModule mod = (SessionStatsModule) find(SessionStatsModule.class);
        if (mod == null || !mod.isEnabled()) return;
        if (client.textRenderer == null) return;

        int bx = mod.x.getInt();
        int by = mod.y.getInt();
        pushScale(ctx, bx, by, mod.scale.getFloat());

        int col = mod.color.get();
        int ly = by;
        if (mod.showTime.get()) {
            ctx.drawTextWithShadow(client.textRenderer,
                    Text.literal("Time: " + com.vortex.client.hud.SessionStats.playtime()),
                    bx, ly, col);
            ly += 10;
        }
        if (mod.showDeaths.get()) {
            ctx.drawTextWithShadow(client.textRenderer,
                    Text.literal("Deaths: " + com.vortex.client.hud.SessionStats.getDeaths()),
                    bx, ly, col);
            ly += 10;
        }
        if (mod.showTotems.get()) {
            ctx.drawTextWithShadow(client.textRenderer,
                    Text.literal("Totems: " + com.vortex.client.hud.SessionStats.getOwnTotems()),
                    bx, ly, col);
            ly += 10;
        }
        if (mod.showMaxCps.get()) {
            ctx.drawTextWithShadow(client.textRenderer,
                    Text.literal("Max CPS: " + com.vortex.client.hud.SessionStats.getMaxCps()),
                    bx, ly, col);
        }
        popScale(ctx);
    }

    private static void drawKeystrokes(DrawContext ctx, MinecraftClient client) {
        KeystrokesModule mod = (KeystrokesModule) find(KeystrokesModule.class);
        if (mod == null || !mod.isEnabled()) return;
        if (client.options == null || client.textRenderer == null) return;

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
                "W", client.options.forwardKey.isPressed(), idle, press, textCol);

        // Reihe 2: A S D
        int row2 = baseY + KEY + GAP;
        drawKey(ctx, client, baseX, row2, KEY, KEY,
                "A", client.options.leftKey.isPressed(), idle, press, textCol);
        drawKey(ctx, client, baseX + KEY + GAP, row2, KEY, KEY,
                "S", client.options.backKey.isPressed(), idle, press, textCol);
        drawKey(ctx, client, baseX + (KEY + GAP) * 2, row2, KEY, KEY,
                "D", client.options.rightKey.isPressed(), idle, press, textCol);

        int nextY = row2 + KEY + GAP;
        int fullW = KEY * 3 + GAP * 2;

        if (mod.showMouse.get()) {
            int half = (fullW - GAP) / 2;
            drawKey(ctx, client, baseX, nextY, half, KEY,
                    "L " + CpsCounter.LEFT.getCps(),
                    client.options.attackKey.isPressed(), idle, press, textCol);
            drawKey(ctx, client, baseX + half + GAP, nextY, half, KEY,
                    "R " + CpsCounter.RIGHT.getCps(),
                    client.options.useKey.isPressed(), idle, press, textCol);
            nextY += KEY + GAP;
        }

        if (mod.showSpace.get()) {
            drawKey(ctx, client, baseX, nextY, fullW, 10,
                    "", client.options.jumpKey.isPressed(), idle, press, textCol);
        }

        popScale(ctx);
    }

    /** Eine einzelne Taste: Flaeche plus mittige Beschriftung. */
    private static void drawKey(DrawContext ctx, MinecraftClient client,
                                int x, int y, int w, int h, String label,
                                boolean pressed, int idle, int press, int textCol) {
        ctx.fill(x, y, x + w, y + h, pressed ? press : idle);
        if (label == null || label.isEmpty()) return;
        // Gedrueckte Taste ist hell -> dunkle Schrift, sonst die eingestellte Farbe.
        int col = pressed ? 0xFF101014 : textCol;
        int tw = client.textRenderer.getWidth(label);
        ctx.drawTextWithShadow(client.textRenderer, Text.literal(label),
                x + (w - tw) / 2, y + (h - 8) / 2, col);
    }

    private static void pushScale(DrawContext context, float anchorX, float anchorY, float scale) {
        var m = context.getMatrices();
        m.pushMatrix();
        m.translate(anchorX, anchorY);
        m.scale(scale, scale);
        m.translate(-anchorX, -anchorY);
    }

    private static void popScale(DrawContext context) {
        context.getMatrices().popMatrix();
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
