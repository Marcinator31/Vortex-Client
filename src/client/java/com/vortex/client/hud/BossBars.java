package com.vortex.client.hud;

import com.vortex.client.module.ModuleManager;
import com.vortex.client.module.modules.BossBarModule;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

/**
 * Eigene Bossleisten (Modul Boss Bar, Stil "Custom").
 *
 * Minecraft zeichnet seine Leisten weiter selbst -- es bekommt nur eine leere
 * Liste untergeschoben (BossOverlayMixin). Die echte Liste wird dabei hier
 * abgelegt, und wir zeichnen daraus schlanke Leisten in unserer Farbe.
 */
public final class BossBars {

    private BossBars() {}

    private static final List<LerpingBossEvent> EREIGNISSE = new ArrayList<>();
    /**
     * Wann Minecraft die Liste zuletzt durchlaufen hat.
     *
     * Minecraft laeuft die Liste nur durch, wenn sie NICHT leer ist. Ist der
     * Boss weg, kommt also kein Aufruf mehr -- ohne diesen Zeitstempel blieben
     * die letzten Leisten fuer immer stehen (auch nach einem Serverwechsel).
     */
    private static long zuletzt = 0L;

    public static BossBarModule modul() {
        return ModuleManager.INSTANCE.get(BossBarModule.class);
    }

    public static boolean eigene() {
        BossBarModule m = modul();
        return m != null && m.isEnabled() && m.style.getIndex() == 1;
    }

    /** Vom Mixin: die Leisten, die Minecraft gerade zeichnen wollte. */
    public static void merke(Collection<?> werte) {
        EREIGNISSE.clear();
        zuletzt = System.currentTimeMillis();
        for (Object o : werte) {
            if (o instanceof LerpingBossEvent e) EREIGNISSE.add(e);
        }
    }

    /** Name ggf. in der eingestellten Farbe. */
    public static Component name(Component original) {
        BossBarModule m = modul();
        if (m == null || !m.isEnabled() || original == null) return original;
        int f = m.nameColor.get();
        if ((f >>> 24) == 0) return original;
        return Component.literal(original.getString()).setStyle(Style.EMPTY.withColor(f & 0xFFFFFF));
    }

    public static void render(GuiGraphicsExtractor ctx, Minecraft mc) {
        if (!eigene()) {
            EREIGNISSE.clear();
            return;
        }
        BossBarModule m = modul();
        // Veraltet (Boss weg) oder keine Welt: nichts mehr zeichnen
        if (mc.level == null || System.currentTimeMillis() - zuletzt > 250L) {
            EREIGNISSE.clear();
            return;
        }
        if (m.hide.get() || EREIGNISSE.isEmpty() || mc.font == null) return;

        int sw = mc.getWindow().getGuiScaledWidth();
        float sc = m.scale.getFloat();
        int breite = 182;
        int ax = sw / 2 + m.offsetX.getInt();
        int ay = 6 + m.offsetY.getInt();
        HudRenderer.pushScale(ctx, ax, ay, sc);
        int x = ax - breite / 2;
        int y = ay;
        for (LerpingBossEvent e : EREIGNISSE) {
            float p = Math.max(0f, Math.min(1f, e.getProgress()));
            int farbe = m.serverColors.get() ? serverFarbe(e) : (m.barColor.get() | 0xFF000000);

            Component n = name(e.getName());
            String prozent = m.percent.get() ? String.format(Locale.ROOT, "  %d%%", Math.round(p * 100)) : "";
            int nw = mc.font.width(n) + mc.font.width(prozent);
            int tx = ax - nw / 2;
            ctx.text(mc.font, n, tx, y, 0xFFFFFFFF);
            if (!prozent.isEmpty()) {
                ctx.text(mc.font, Component.literal(prozent), tx + mc.font.width(n), y, 0xFFB8B2CC);
            }

            int by = y + 11;
            // Schiene, Fuellung, Glanzlinie oben
            ctx.fill(x, by, x + breite, by + 5, 0xC0000000);
            int fw = (int) (breite * p);
            if (fw > 0) {
                ctx.fill(x, by, x + fw, by + 5, farbe);
                ctx.fill(x, by, x + fw, by + 1,
                        com.vortex.client.gui.VortexStyle.mix(farbe, 0xFFFFFFFF, 0.35f));
            }
            y += 20;
            if (y > mc.getWindow().getGuiScaledHeight() / 3 + ay) break;
        }
        HudRenderer.popScale(ctx);
    }

    private static int serverFarbe(LerpingBossEvent e) {
        String n;
        try {
            n = String.valueOf(e.getColor());
        } catch (Throwable t) {
            n = "PURPLE";
        }
        switch (n.toUpperCase(Locale.ROOT)) {
            case "PINK": return 0xFFEC4899;
            case "BLUE": return 0xFF3B82F6;
            case "RED": return 0xFFEF4444;
            case "GREEN": return 0xFF22C55E;
            case "YELLOW": return 0xFFEAB308;
            case "WHITE": return 0xFFE5E7EB;
            default: return 0xFF8B5CF6;
        }
    }
}
