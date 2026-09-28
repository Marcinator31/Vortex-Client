package com.vortex.client.hud;

import com.vortex.client.module.ModuleManager;
import com.vortex.client.module.modules.ExplosionTimerModule;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.vehicle.minecart.MinecartTNT;

/**
 * TNT & Explosion Timer (siehe ExplosionTimerModule). Wird im HUD gezeichnet
 * (Weltpunkt -> Bildschirm), damit die Zahl immer lesbar ist.
 */
public final class ExplosionTimer {

    private ExplosionTimer() {}

    /** Standard-Zuendzeit von TNT in Ticks -- fuer den Balken. */
    private static final float TNT_ZEIT = 80f;

    public static void render(GuiGraphicsExtractor ctx, Minecraft mc) {
        ExplosionTimerModule m = ModuleManager.INSTANCE.get(ExplosionTimerModule.class);
        if (m == null || !m.isEnabled() || mc.player == null || mc.level == null || mc.font == null) return;
        float td = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        double r2 = m.range.get() * m.range.get();
        boolean begonnen = false;
        for (Entity e : mc.level.entitiesForRendering()) {
            float rest;          // Sekunden
            float voll;          // Gesamtzeit in Sekunden (fuer den Balken)
            if (e instanceof PrimedTnt t && m.tnt.get()) {
                rest = Math.max(0f, (t.getFuse() - td) / 20f);
                voll = Math.max(rest, TNT_ZEIT / 20f);
            } else if (e instanceof MinecartTNT c && m.minecarts.get() && c.isPrimed()) {
                rest = Math.max(0f, (c.getFuse() - td) / 20f);
                voll = Math.max(rest, 4f);
            } else if (e instanceof Creeper c && m.creepers.get()) {
                float s = c.getSwelling(td);
                if (s <= 0.001f) continue;
                // swell zaehlt bis maxSwell (Standard 30); getSwelling = swell / (maxSwell - 2)
                rest = Math.max(0f, ((1f - s) * 28f + 2f) / 20f);
                voll = 1.5f;
            } else {
                continue;
            }
            if (e.distanceToSqr(mc.player) > r2) continue;
            if (!begonnen) {
                ScreenProject.begin(mc, ctx.guiWidth(), ctx.guiHeight());
                begonnen = true;
            }
            double x = e.xOld + (e.getX() - e.xOld) * td;
            double y = e.yOld + (e.getY() - e.yOld) * td + e.getBbHeight() + 0.5;
            double z = e.zOld + (e.getZ() - e.zOld) * td;
            double[] p = ScreenProject.project(x, y, z);
            if (p == null) continue;
            float anteil = voll > 0 ? Math.min(1f, rest / voll) : 0f;
            int farbe = mischen(anteil);
            String text = String.format(Locale.ROOT, "%.1fs", rest);
            float sk = (float) (m.size.get() * Math.max(0.6, Math.min(1.8, 8.0 / Math.max(1.0, p[2]))));
            var pose = ctx.pose();
            pose.pushMatrix();
            pose.translate((float) p[0], (float) p[1]);
            pose.scale(sk, sk);
            int w = mc.font.width(text);
            ctx.fill(-w / 2 - 3, -6, w / 2 + 3, m.bar.get() ? 8 : 6, 0x90000000);
            ctx.text(mc.font, Component.literal(text), -w / 2, -4, 0xFF000000 | farbe, true);
            if (m.bar.get()) {
                int bw = w + 4;
                ctx.fill(-bw / 2, 5, bw / 2, 7, 0xFF303030);
                ctx.fill(-bw / 2, 5, -bw / 2 + Math.round(bw * anteil), 7, 0xFF000000 | farbe);
            }
            pose.popMatrix();
        }
    }

    /** Gruen (viel Zeit) ueber Gelb nach Rot (gleich). */
    private static int mischen(float a) {
        int r, g;
        if (a > 0.5f) { r = Math.round(255 * (1f - a) * 2f); g = 220; }
        else { r = 255; g = Math.round(220 * a * 2f); }
        return (Math.max(0, Math.min(255, r)) << 16) | (Math.max(0, Math.min(255, g)) << 8) | 0x40;
    }
}
