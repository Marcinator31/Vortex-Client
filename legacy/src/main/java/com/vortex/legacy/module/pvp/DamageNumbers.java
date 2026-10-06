package com.vortex.legacy.module.pvp;

import com.mojang.blaze3d.platform.GlStateManager;
import com.vortex.legacy.core.Module;
import com.vortex.legacy.gui.Render3D;
import com.vortex.legacy.module.WorldRenderers;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;

/** Schaden als aufsteigende Zahl ueber getroffenen Wesen. */
public class DamageNumbers extends Module implements WorldRenderers.InWorld {
    private final Map<Integer, Float> health = new HashMap<Integer, Float>();
    private final List<double[]> pops = new ArrayList<double[]>(); // x, y, z, amount, startMillis

    public DamageNumbers() { super("Damage Numbers", Category.PVP, "Floating numbers show how much damage was dealt."); }

    @Override
    public void onTick() {
        if (mc.world == null) { health.clear(); return; }
        Map<Integer, Float> now = new HashMap<Integer, Float>();
        for (Entity e : mc.world.loadedEntities) {
            if (!(e instanceof LivingEntity) || e == mc.player) continue;
            float h = ((LivingEntity) e).getHealth();
            Float before = health.get(e.getEntityId());
            if (before != null && h < before - 0.01f && mc.player.distanceTo(e) < 32)
                pops.add(new double[]{ e.x + (Math.random() - 0.5) * 0.6, e.y + e.height + 0.3, e.z + (Math.random() - 0.5) * 0.6, before - h, System.currentTimeMillis() });
            now.put(e.getEntityId(), h);
        }
        health.clear();
        health.putAll(now);
    }

    @Override
    public void renderWorld(float tickDelta) {
        long t = System.currentTimeMillis();
        for (Iterator<double[]> it = pops.iterator(); it.hasNext();) {
            double[] p = it.next();
            float age = (t - (long) p[4]) / 1000f;
            if (age > 1.2f) { it.remove(); continue; }
            String s = String.format("%.1f", p[3] / 2.0) + "❤";
            float a = age < 0.9f ? 1 : 1 - (age - 0.9f) / 0.3f;
            GlStateManager.pushMatrix();
            GlStateManager.translate(p[0] - Render3D.camX, p[1] + age * 0.6 - Render3D.camY, p[2] - Render3D.camZ);
            GlStateManager.rotate(-mc.getEntityRenderManager().yaw, 0, 1, 0);
            GlStateManager.rotate(mc.getEntityRenderManager().pitch, 1, 0, 0);
            float sc = 0.025f * (1 + Math.max(0, 0.25f - age) * 2);
            GlStateManager.scale(-sc, -sc, sc);
            GlStateManager.disableLighting();
            GlStateManager.disableDepthTest();
            GlStateManager.enableBlend();
            GlStateManager.blendFuncSeparate(770, 771, 1, 0);
            int alpha = Math.max(5, (int) (a * 255));
            mc.textRenderer.draw(s, -mc.textRenderer.getStringWidth(s) / 2, 0, (alpha << 24) | 0xFF5555, true);
            GlStateManager.enableDepthTest();
            GlStateManager.popMatrix();
        }
    }
}
