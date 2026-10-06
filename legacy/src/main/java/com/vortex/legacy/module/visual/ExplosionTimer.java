package com.vortex.legacy.module.visual;

import com.mojang.blaze3d.platform.GlStateManager;
import com.vortex.legacy.core.Module;
import com.vortex.legacy.gui.Render3D;
import com.vortex.legacy.module.WorldRenderers;
import net.minecraft.entity.Entity;
import net.minecraft.entity.TntEntity;

/** Countdown ueber gezuendetem TNT. */
public class ExplosionTimer extends Module implements WorldRenderers.InWorld {
    public ExplosionTimer() { super("Explosion Timer", Category.VISUAL, "Shows when primed TNT explodes."); }

    @Override
    public void renderWorld(float tickDelta) {
        for (Entity e : mc.world.loadedEntities) {
            if (!(e instanceof TntEntity)) continue;
            TntEntity t = (TntEntity) e;
            float sec = Math.max(0, (t.fuseTimer - tickDelta) / 20f);
            double x = e.prevTickX + (e.x - e.prevTickX) * tickDelta - Render3D.camX;
            double y = e.prevTickY + (e.y - e.prevTickY) * tickDelta - Render3D.camY + 1.4;
            double z = e.prevTickZ + (e.z - e.prevTickZ) * tickDelta - Render3D.camZ;
            String s = String.format("%.1fs", sec);
            int c = sec > 2 ? 0xFF55FF55 : sec > 1 ? 0xFFFFFF55 : 0xFFFF5555;
            GlStateManager.pushMatrix();
            GlStateManager.translate(x, y, z);
            GlStateManager.rotate(-mc.getEntityRenderManager().yaw, 0, 1, 0);
            GlStateManager.rotate(mc.getEntityRenderManager().pitch, 1, 0, 0);
            GlStateManager.scale(-0.03f, -0.03f, 0.03f);
            GlStateManager.disableLighting();
            GlStateManager.disableDepthTest();
            GlStateManager.enableBlend();
            mc.textRenderer.draw(s, -mc.textRenderer.getStringWidth(s) / 2, 0, c, true);
            GlStateManager.enableDepthTest();
            GlStateManager.popMatrix();
        }
    }
}
