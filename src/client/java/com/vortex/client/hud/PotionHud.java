package com.vortex.client.hud;

import com.vortex.client.module.modules.PotionEffectsModule;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;

/**
 * Potion Effects HUD (seit Client 4.15 neu gestaltet).
 *
 * Jede Wirkung ist eine Karte: abgerundet, links ein Streifen in der Farbe
 * des Effekts, Icon, Name mit Stufe, Restzeit und darunter ein Balken, der
 * mit der Restzeit kuerzer wird. Alles bewegt sich weich:
 *   - neue Effekte gleiten von links herein und blenden ein
 *   - abgelaufene blenden aus, die anderen ruecken weich nach
 *   - die letzten 10 Sekunden pulsiert die Restzeit rot
 * Schaedliche Effekte stehen oben, danach die guten, jeweils nach Name.
 */
public final class PotionHud {

    private PotionHud() {}

    private static final class Karte {
        Holder<MobEffect> effekt;
        String name, zeit;
        int stufe;
        int farbe;
        Identifier icon;
        float anteil = 1f;
        boolean unendlich, schaedlich;
        int hoechstDauer = 1;
        float ein = 0f;          // 0..1 Einblenden
        float y = Float.NaN;     // aktuelle (weiche) Hoehe
        boolean weg = false;
        boolean warnen;
    }

    private static final Map<Holder<MobEffect>, Karte> KARTEN = new HashMap<>();
    private static long letztesBild = 0L;

    private static final int HOEHE = 24, ABSTAND = 3;

    public static void zeichnen(GuiGraphicsExtractor ctx, Minecraft mc, PotionEffectsModule m) {
        long jetzt = System.nanoTime();
        float dt = letztesBild == 0 ? 0f : Math.min(0.1f, (jetzt - letztesBild) / 1.0e9f);
        letztesBild = jetzt;

        // 1) Aktive Effekte uebernehmen
        for (Karte k : KARTEN.values()) k.weg = true;
        for (MobEffectInstance e : mc.player.getActiveEffects()) {
            Karte k = KARTEN.computeIfAbsent(e.getEffect(), h -> new Karte());
            k.weg = false;
            k.effekt = e.getEffect();
            MobEffect me = e.getEffect().value();
            k.farbe = 0xFF000000 | (me.getColor() & 0xFFFFFF);
            k.schaedlich = !me.isBeneficial();
            k.stufe = e.getAmplifier();
            k.name = Component.translatable(e.getDescriptionId()).getString();
            k.unendlich = e.isInfiniteDuration();
            int dauer = e.getDuration();
            if (dauer > k.hoechstDauer || k.zeit == null) k.hoechstDauer = Math.max(1, dauer);
            k.anteil = k.unendlich ? 1f : Math.max(0f, Math.min(1f, dauer / (float) k.hoechstDauer));
            k.zeit = k.unendlich ? "∞" : net.minecraft.world.effect.MobEffectUtil.formatDuration(e, 1.0f, 20.0f).getString();
            k.warnen = !k.unendlich && e.endsWithin(200);
            if (k.icon == null) {
                Identifier id = BuiltInRegistries.MOB_EFFECT.getKey(me);
                k.icon = id == null ? null : Identifier.withDefaultNamespace("mob_effect/" + id.getPath());
            }
        }

        // 2) Ein-/Ausblenden, Abgelaufene entfernen
        List<Karte> liste = new ArrayList<>();
        for (var it = KARTEN.values().iterator(); it.hasNext(); ) {
            Karte k = it.next();
            k.ein += (k.weg ? -dt : dt) * 6f;
            k.ein = Math.max(0f, Math.min(1f, k.ein));
            if (k.weg && k.ein <= 0f) { it.remove(); continue; }
            liste.add(k);
        }
        if (liste.isEmpty()) return;
        liste.sort((a, b) -> {
            if (a.schaedlich != b.schaedlich) return a.schaedlich ? -1 : 1;
            return a.name.compareToIgnoreCase(b.name);
        });

        // 3) Zeichnen
        int x0 = m.x.getInt(), y0 = m.y.getInt();
        HudRenderer.pushScale(ctx, x0, y0, m.scale.getFloat());
        try {
            float ziel = y0;
            float puls = (float) (0.5 + 0.5 * Math.sin(jetzt / 1.0e9 * Math.PI * 2.0 * 1.6));
            for (Karte k : liste) {
                if (Float.isNaN(k.y)) k.y = ziel;
                k.y += (ziel - k.y) * (1f - (float) Math.exp(-dt * 14f));
                float a = glatt(k.ein);
                int x = x0 + Math.round((1f - a) * -10f);
                karte(ctx, mc, k, x, Math.round(k.y), a, puls, m.color.get());
                if (!k.weg) ziel += (HOEHE + ABSTAND);
                else ziel += (HOEHE + ABSTAND) * a;
            }
        } finally {
            HudRenderer.popScale(ctx);
        }
    }

    private static void karte(GuiGraphicsExtractor ctx, Minecraft mc, Karte k, int x, int y, float a, float puls, int textFarbe) {
        String stufe = k.stufe > 0 ? " " + roemisch(k.stufe + 1) : "";
        int textW = Math.max(mc.font.width(k.name + stufe), mc.font.width(k.zeit) + 2);
        int w = Math.max(112, 28 + textW + 8);
        int h = HOEHE;

        // Hintergrund mit abgerundeten Ecken (Eckpixel ausgespart)
        int bg = mitAlpha(0xC4101016, a);
        ctx.fill(x + 1, y, x + w - 1, y + h, bg);
        ctx.fill(x, y + 1, x + 1, y + h - 1, bg);
        ctx.fill(x + w - 1, y + 1, x + w, y + h - 1, bg);
        // feiner Glanz oben, leichter Schimmer in Effektfarbe von links
        ctx.fill(x + 1, y, x + w - 1, y + 1, mitAlpha(0x1CFFFFFF, a));
        ctx.fillGradient(x + 1, y + 1, x + 40, y + h - 1, mitAlpha((k.farbe & 0xFFFFFF) | 0x26000000, a), mitAlpha((k.farbe & 0xFFFFFF) | 0x08000000, a));
        // Akzentstreifen links
        ctx.fill(x + 1, y + 3, x + 3, y + h - 3, mitAlpha(k.farbe, a));

        // Icon (blinkt in den letzten Sekunden leicht)
        if (k.icon != null) {
            float ia = a * (k.warnen ? 0.55f + 0.45f * puls : 1f);
            try {
                // Farbe statt float-Alpha: so blinkt auch Minecrafts eigene Effektanzeige.
                // (Die float-Variante brachte die Schrift danach durcheinander --
                // farbige Kaesten um den Text, gesehen im Test-Screenshot.)
                int weiss = (Math.round(Math.max(0f, Math.min(1f, ia)) * 255f) << 24) | 0xFFFFFF;
                ctx.blitSprite(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, k.icon, x + 6, y + 3, 18, 18, weiss);
            } catch (Throwable ignored) {
            }
        }

        if (a > 0.12f) {
            // Name + Stufe (Stufe in Effektfarbe, aufgehellt)
            ctx.text(mc.font, Component.literal(k.name), x + 28, y + 3, mitAlpha(textFarbe, a), true);
            if (!stufe.isEmpty()) {
                ctx.text(mc.font, Component.literal(stufe), x + 28 + mc.font.width(k.name), y + 3, mitAlpha(hell(k.farbe), a), true);
            }
            // Restzeit: grau, die letzten 10 s rot pulsierend
            int zeitFarbe = k.warnen ? mische(0xFFFF6464, 0xFFFFFFFF, puls * 0.6f) : 0xFFA8A8B4;
            ctx.text(mc.font, Component.literal(k.zeit), x + 28, y + 12, mitAlpha(zeitFarbe, a), false);
        }

        // Restzeit-Balken unten
        int bx0 = x + 28, bx1 = x + w - 6, by = y + h - 3;
        ctx.fill(bx0, by, bx1, by + 1, mitAlpha(0x26FFFFFF, a));
        int voll = bx0 + Math.round((bx1 - bx0) * k.anteil);
        if (voll > bx0) ctx.fill(bx0, by, voll, by + 1, mitAlpha(hell(k.farbe), a));
    }

    private static float glatt(float t) {
        return t * t * (3f - 2f * t);
    }

    private static int mitAlpha(int argb, float faktor) {
        int alpha = Math.round(((argb >>> 24) & 0xFF) * Math.max(0f, Math.min(1f, faktor)));
        return (alpha << 24) | (argb & 0xFFFFFF);
    }

    /** Effektfarbe aufgehellt -- auf dunklem Grund besser lesbar. */
    private static int hell(int argb) {
        return mische(argb, 0xFFFFFFFF, 0.35f);
    }

    private static int mische(int a, int b, float t) {
        int r = Math.round(((a >> 16) & 0xFF) * (1 - t) + ((b >> 16) & 0xFF) * t);
        int g = Math.round(((a >> 8) & 0xFF) * (1 - t) + ((b >> 8) & 0xFF) * t);
        int bl = Math.round((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    private static String roemisch(int n) {
        String[] z = {"I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
        return n >= 1 && n <= 10 ? z[n - 1] : String.valueOf(n);
    }
}
