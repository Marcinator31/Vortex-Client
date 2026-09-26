package com.vortex.client.hud;

import com.vortex.client.module.ModuleManager;
import com.vortex.client.module.modules.ClockModule;
import com.vortex.client.module.modules.ComboModule;
import com.vortex.client.module.modules.CompassModule;
import com.vortex.client.module.modules.ReachModule;
import com.vortex.client.module.modules.SpeedometerModule;
import com.vortex.client.module.modules.TpsModule;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * Die HUD-Anzeigen aus 4.4.0: Tacho, Kompass, Reichweite, Combo, TPS, Uhr.
 *
 * Eigene Klasse statt noch mehr Zeilen im HudRenderer -- der ist schon lang
 * genug. Gezeichnet wird ueber dieselbe Skalierungshilfe wie alle anderen
 * Anzeigen, damit sich die neuen im HUD-Editor genauso verhalten.
 *
 * Texte werden nur neu gebaut, wenn sich der Inhalt aendert. Eine Anzeige,
 * die 150-mal pro Sekunde denselben String neu zusammensetzt, kostet Speicher
 * fuer nichts.
 */
public final class ExtraHud {

    private ExtraHud() {}

    private static final DateTimeFormatter H24 = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter H12 = DateTimeFormatter.ofPattern("h:mm a", Locale.ROOT);

    // --- Tacho: pro Tick gemessen, weich gemittelt ------------------------
    private static double vorX = Double.NaN, vorY, vorZ;
    private static double tempo = 0;

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (mc.player == null) {
                vorX = Double.NaN;
                tempo = 0;
                return;
            }
            SpeedometerModule sm = ModuleManager.INSTANCE.get(SpeedometerModule.class);
            double px = mc.player.getX(), py = mc.player.getY(), pz = mc.player.getZ();
            if (!Double.isNaN(vorX)) {
                double dx = px - vorX, dz = pz - vorZ;
                double dy = (sm != null && sm.vertical.get()) ? py - vorY : 0;
                double proSek = Math.sqrt(dx * dx + dy * dy + dz * dz) * 20.0;
                // Teleport (Weltwechsel, /tp) nicht als Rekordtempo zeigen
                if (proSek > 400) proSek = tempo;
                tempo += (proSek - tempo) * 0.5;
            }
            vorX = px;
            vorY = py;
            vorZ = pz;
        });
    }

    public static void render(GuiGraphicsExtractor ctx, Minecraft mc) {
        if (mc.player == null || mc.font == null) return;
        tacho(ctx, mc);
        kompass(ctx, mc);
        reichweite(ctx, mc);
        combo(ctx, mc);
        tps(ctx, mc);
        uhr(ctx, mc);
    }

    // ----------------------------------------------------------------------

    private static String tachoText = null;
    private static int tachoWert = Integer.MIN_VALUE;

    private static void tacho(GuiGraphicsExtractor ctx, Minecraft mc) {
        SpeedometerModule m = ModuleManager.INSTANCE.get(SpeedometerModule.class);
        if (m == null || !m.isEnabled()) return;
        boolean kmh = m.unit.getIndex() == 1;
        double wert = kmh ? tempo * 3.6 : tempo;
        int schluessel = (int) Math.round(wert * 10) * 2 + (kmh ? 1 : 0);
        if (schluessel != tachoWert || tachoText == null) {
            tachoWert = schluessel;
            tachoText = String.format(Locale.ROOT, "%.1f %s", wert, kmh ? "km/h" : "b/s");
        }
        zeile(ctx, mc, m.x.getInt(), m.y.getInt(), m.scale.getFloat(), tachoText, m.color.get());
    }

    // ----------------------------------------------------------------------

    private static final String[] RICHTUNG = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};

    private static void kompass(GuiGraphicsExtractor ctx, Minecraft mc) {
        CompassModule m = ModuleManager.INSTANCE.get(CompassModule.class);
        if (m == null || !m.isEnabled()) return;
        int w = m.width.getInt();
        float sc = m.scale.getFloat();
        int sw = mc.getWindow().getGuiScaledWidth();

        // Mittig halten, bis der Spieler die Leiste selbst verschiebt.
        if (m.centered.get()) {
            double mitte = Math.max(0, Math.round((sw - w * sc) / 2.0));
            if (!Double.isNaN(m.lastX) && Math.abs(m.x.get() - m.lastX) > 0.5) {
                m.centered.set(false);
            } else {
                m.x.set(mitte);
                m.lastX = mitte;
            }
        }

        int x = m.x.getInt(), y = m.y.getInt();
        HudRenderer.pushScale(ctx, x, y, sc);
        int h = m.degrees.get() ? 22 : 14;
        if (m.background.get()) {
            ctx.fill(x, y, x + w, y + h, 0x70000000);
        }

        // Kompasskurs: Minecraft zaehlt 0 = Sueden, 90 = Westen. Plus 180
        // ergibt die gewohnte Zaehlung mit 0 = Norden, 90 = Osten.
        float kurs = ((mc.player.getYRot() + 180f) % 360f + 360f) % 360f;
        float proGrad = w / 150f;   // 150 Grad sichtbar
        int mitteX = x + w / 2;
        int farbe = m.color.get();
        for (int grad = 0; grad < 360; grad += 15) {
            float diff = ((grad - kurs) % 360f + 540f) % 360f - 180f;
            float px = mitteX + diff * proGrad;
            if (px < x + 4 || px > x + w - 4) continue;
            // Zu den Raendern hin ausblenden
            float rand = 1f - Math.abs(diff) / 75f;
            int alpha = (int) (255 * Math.max(0.15f, Math.min(1f, rand * 1.6f)));
            int col = (alpha << 24) | (farbe & 0xFFFFFF);
            int ix = (int) px;
            if (grad % 45 == 0) {
                String t = RICHTUNG[grad / 45];
                int tw = mc.font.width(t);
                ctx.text(mc.font, Component.literal(t), ix - tw / 2, y + 3, col);
            } else {
                ctx.fill(ix, y + 4, ix + 1, y + 9, (alpha / 2 << 24) | (farbe & 0xFFFFFF));
            }
        }
        // Markierung in der Mitte
        int akz = m.accent.get() | 0xFF000000;
        ctx.fill(mitteX - 1, y, mitteX + 1, y + 2, akz);
        ctx.fill(mitteX - 2, y, mitteX + 2, y + 1, akz);
        if (m.degrees.get()) {
            String g = String.valueOf(Math.round(kurs) % 360);
            int gw = mc.font.width(g);
            ctx.text(mc.font, Component.literal(g), mitteX - gw / 2, y + 12, akz);
        }
        HudRenderer.popScale(ctx);
    }

    // ----------------------------------------------------------------------

    private static void reichweite(GuiGraphicsExtractor ctx, Minecraft mc) {
        ReachModule m = ModuleManager.INSTANCE.get(ReachModule.class);
        if (m == null || !m.isEnabled()) return;
        double r = CombatTracker.reichweite();
        String text;
        int alpha = 255;
        if (r < 0) {
            text = "-- blocks";
        } else {
            long alter = CombatTracker.reichweiteAlter();
            long grenze = (long) (m.hideAfter.get() * 1000);
            if (grenze > 0 && alter > grenze + 400) return;
            if (grenze > 0 && alter > grenze) alpha = (int) (255 * (1f - (alter - grenze) / 400f));
            text = String.format(Locale.ROOT, m.decimals.getIndex() == 1 ? "%.2f blocks" : "%.1f blocks", r);
        }
        int col = (Math.max(8, alpha) << 24) | (m.color.get() & 0xFFFFFF);
        zeile(ctx, mc, m.x.getInt(), m.y.getInt(), m.scale.getFloat(), text, col);
    }

    private static void combo(GuiGraphicsExtractor ctx, Minecraft mc) {
        ComboModule m = ModuleManager.INSTANCE.get(ComboModule.class);
        if (m == null || !m.isEnabled()) return;
        int c = CombatTracker.combo((long) (m.resetAfter.get() * 1000));
        if (c == 0 && m.hideAtZero.get()) return;
        // Kurzes Aufleuchten bei jedem neuen Treffer
        long alter = CombatTracker.comboAlter();
        int col = m.color.get();
        if (c > 0 && alter < 250) {
            col = com.vortex.client.gui.VortexStyle.mix(0xFFFFFFFF, col, alter / 250f);
        }
        zeile(ctx, mc, m.x.getInt(), m.y.getInt(), m.scale.getFloat(),
                c == 1 ? "1 hit" : c + " hits", col);
    }

    private static void tps(GuiGraphicsExtractor ctx, Minecraft mc) {
        TpsModule m = ModuleManager.INSTANCE.get(TpsModule.class);
        if (m == null || !m.isEnabled()) return;
        float t = TickRate.tps();
        String text = String.format(Locale.ROOT, "TPS %.1f", t);
        float seit = TickRate.seitLetztem();
        if (m.lagTimer.get() && seit >= 2f) {
            text += String.format(Locale.ROOT, "  (%.0fs)", seit);
        }
        int col = m.color.get();
        if (m.colorByValue.get()) {
            if (seit >= 3f || t < 15f) col = 0xFFFF5555;
            else if (t < 18f) col = 0xFFFFD050;
            else col = 0xFF55FF7A;
        }
        zeile(ctx, mc, m.x.getInt(), m.y.getInt(), m.scale.getFloat(), text, col);
    }

    private static String uhrText = null;
    private static long uhrGebaut = 0;

    private static void uhr(GuiGraphicsExtractor ctx, Minecraft mc) {
        ClockModule m = ModuleManager.INSTANCE.get(ClockModule.class);
        if (m == null || !m.isEnabled()) return;
        long jetzt = System.currentTimeMillis();
        if (uhrText == null || jetzt - uhrGebaut > 500) {
            uhrGebaut = jetzt;
            StringBuilder sb = new StringBuilder();
            if (m.showTime.get()) {
                sb.append(LocalTime.now().format(m.format.getIndex() == 1 ? H12 : H24));
            }
            if (m.showDay.get() && mc.level != null) {
                if (sb.length() > 0) sb.append("  ");
                sb.append("Day ").append(mc.level.getLevelData().getGameTime() / 24000L + 1);
            }
            if (m.showMemory.get()) {
                Runtime rt = Runtime.getRuntime();
                long benutzt = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);
                long max = rt.maxMemory() / (1024 * 1024);
                if (sb.length() > 0) sb.append("  ");
                sb.append(benutzt).append('/').append(max).append(" MB");
            }
            uhrText = sb.toString();
        }
        if (uhrText.isEmpty()) return;
        zeile(ctx, mc, m.x.getInt(), m.y.getInt(), m.scale.getFloat(), uhrText, m.color.get());
    }

    // ----------------------------------------------------------------------

    private static void zeile(GuiGraphicsExtractor ctx, Minecraft mc, int x, int y, float sc,
                              String text, int col) {
        HudRenderer.pushScale(ctx, x, y, sc);
        ctx.text(mc.font, Component.literal(text), x, y, col);
        HudRenderer.popScale(ctx);
    }
}
