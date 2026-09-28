package com.vortex.client.hud;

import com.vortex.client.module.ModuleManager;
import com.vortex.client.module.modules.MaceHudModule;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.EntityHitResult;

/**
 * Zeichnet das Mace HUD (siehe MaceHudModule).
 *
 * DIE FORMEL (26.x, aus MaceItem.getAttackDamageBonus und Player.attack):
 *   bonus = Fall <= 3 : 4 * Fall
 *           Fall <= 8 : 12 + 2 * (Fall - 3)
 *           sonst     : 22 + (Fall - 8)
 *         + Density-Stufe * 0,5 * Fall
 *   (nur ab 1,5 Bloecken Fall und nicht mit Elytra)
 *   schaden = (Grundschaden * Aufladung + bonus) * 1,5 (Krit -- beim Fallen
 *             mit voller Aufladung immer)
 *   dann Ruestung (CombatRules): Anteil = clamp(R - s/(2 + H/4), R/5, 20)/25,
 *        Breach zieht 0,15 pro Stufe vom Anteil ab
 *   dann Resistenz (-20 % pro Stufe) und Schutz (4 % pro Stufe, max. 80 %)
 */
public final class MaceHud {

    private MaceHud() {}

    public static void render(GuiGraphicsExtractor ctx, Minecraft mc) {
        MaceHudModule m = ModuleManager.INSTANCE.get(MaceHudModule.class);
        if (m == null || !m.isEnabled()) return;
        LocalPlayer p = mc.player;
        if (p == null) return;
        ItemStack mace = p.getMainHandItem();
        boolean hatMace = mace.is(Items.MACE);
        if (m.onlyWithMace.get() && !hatMace) return;

        double fall = p.isFallFlying() ? 0 : p.fallDistance;
        List<HudText.Zeile> zeilen = new ArrayList<>(4);
        zeilen.add(new HudText.Zeile("Fall", String.format(Locale.ROOT, "%.1f m", fall)));

        LivingEntity ziel = m.showTarget.get() ? ziel(mc, p, m.targetRange.get()) : null;
        if (ziel == null) {
            float roh = hatMace ? rohSchaden(p, mace, fall) : 0f;
            zeilen.add(new HudText.Zeile("Smash", fall > 1.5 ? String.format(Locale.ROOT, "%.1f", roh) : "from 1.5 m"));
        } else {
            float leben = ziel.getHealth() + ziel.getAbsorptionAmount();
            float s = hatMace ? schaden(p, mace, ziel, fall) : 0f;
            boolean tot = s >= leben;
            String name = ziel.getName().getString();
            if (name.length() > 14) name = name.substring(0, 14);
            zeilen.add(new HudText.Zeile("Smash", fall > 1.5
                    ? String.format(Locale.ROOT, "%.1f / %.1f%s", s, leben, tot ? "  KILL" : "")
                    : "from 1.5 m", tot && fall > 1.5 ? 0xFF55FF55 : 0));
            zeilen.add(new HudText.Zeile("Target", name));
            if (m.showKillHeight.get() && hatMace) {
                double h = killHoehe(p, mace, ziel, leben);
                zeilen.add(new HudText.Zeile("Kill at", h < 0 ? "> 100 m" : String.format(Locale.ROOT, "%.1f m", h)));
            }
        }
        HudText.block(ctx, mc.font, m.x.getInt(), m.y.getInt(), m.scale.getFloat(),
                m.style, m.color, zeilen, HudStyle.FORM_DEFAULT, 0, 1f, null);
    }

    /** Ziel: das Wesen im Fadenkreuz, sonst der naechste Spieler (keine Freunde). */
    private static LivingEntity ziel(Minecraft mc, LocalPlayer p, double weite) {
        if (mc.hitResult instanceof EntityHitResult ehr && ehr.getEntity() instanceof LivingEntity le && le.isAlive()) {
            return le;
        }
        LivingEntity best = null;
        double bestD = weite * weite;
        for (Player o : mc.level.players()) {
            if (o == p || !o.isAlive() || o.isSpectator()) continue;
            if (com.vortex.client.core.Friends.schuetzt(o)) continue;
            double d = o.distanceToSqr(p);
            if (d < bestD) { bestD = d; best = o; }
        }
        return best;
    }

    private static int stufe(ItemStack st, net.minecraft.resources.ResourceKey<net.minecraft.world.item.enchantment.Enchantment> key) {
        for (var e : st.getEnchantments().entrySet()) if (e.getKey().is(key)) return e.getIntValue();
        return 0;
    }

    /** Smash-Bonus nach Fallhoehe (MaceItem.getAttackDamageBonus) inkl. Density. */
    private static double bonus(ItemStack mace, double fall) {
        if (fall <= 1.5) return 0;
        double b = fall <= 3 ? 4 * fall : fall <= 8 ? 12 + 2 * (fall - 3) : 22 + (fall - 8);
        return b + stufe(mace, Enchantments.DENSITY) * 0.5 * fall;
    }

    /** Schaden vor Ruestung. Krit, wenn voll aufgeladen (beim Fallen ist man nicht am Boden). */
    private static float rohSchaden(LocalPlayer p, ItemStack mace, double fall) {
        float lade = p.getAttackStrengthScale(0.5f);
        float grund = (float) p.getAttributeValue(Attributes.ATTACK_DAMAGE) * (0.2f + lade * lade * 0.8f);
        float s = grund + (float) bonus(mace, fall);
        if (lade > 0.9f && fall > 0) s *= 1.5f;
        return s;
    }

    private static float schaden(LocalPlayer p, ItemStack mace, LivingEntity z, double fall) {
        float s = rohSchaden(p, mace, fall);
        // Ruestung + Haerte, mit Breach
        float r = z.getArmorValue();
        float h = (float) z.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
        float anteil = Math.max(r * 0.2f, Math.min(20f, r - s / (2f + h / 4f))) / 25f;
        anteil = Math.max(0f, Math.min(1f, anteil - 0.15f * stufe(mace, Enchantments.BREACH)));
        s *= (1f - anteil);
        // Resistenz
        MobEffectInstance res = z.getEffect(MobEffects.RESISTANCE);
        if (res != null) s = Math.max(0f, s * (25 - (res.getAmplifier() + 1) * 5) / 25f);
        // Schutz (1 Punkt pro Stufe, max. 20)
        int schutz = 0;
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            schutz += stufe(z.getItemBySlot(slot), Enchantments.PROTECTION);
        }
        if (schutz > 0) s = CombatRules.getDamageAfterMagicAbsorb(s, schutz);
        return s;
    }

    /** Ab welcher Fallhoehe reicht ein Schlag fuer den Kill? -1 = nicht bis 100 m. */
    private static double killHoehe(LocalPlayer p, ItemStack mace, LivingEntity z, float leben) {
        // Schaden waechst mit der Fallhoehe -> binaere Suche (~10 Rechnungen
        // statt bis zu 200 in jedem Bild), dabei auf 0,1 m genau.
        double lo = 1.6, hi = 100;
        if (schaden(p, mace, z, hi) < leben) return -1;
        if (schaden(p, mace, z, lo) >= leben) return lo;
        while (hi - lo > 0.1) {
            double mid = (lo + hi) / 2;
            if (schaden(p, mace, z, mid) >= leben) hi = mid; else lo = mid;
        }
        return hi;
    }
}
