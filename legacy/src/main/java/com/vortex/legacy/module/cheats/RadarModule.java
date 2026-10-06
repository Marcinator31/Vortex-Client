package com.vortex.legacy.module.cheats;

import com.mojang.blaze3d.platform.GlStateManager;
import com.vortex.legacy.core.BoolSetting;
import com.vortex.legacy.core.Module;
import com.vortex.legacy.core.NumberSetting;
import com.vortex.legacy.gui.Render2D;
import com.vortex.legacy.gui.Theme;
import com.vortex.legacy.hud.HudModule;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.player.PlayerEntity;

/** Kreis-Radar: Spieler (und Monster) in der Naehe als Punkte, deine Blickrichtung oben. Achtung: auf vielen Servern verboten. */
public class RadarModule extends HudModule {
    public final NumberSetting range = add(new NumberSetting("Range", 48, 16, 128, 4));
    public final BoolSetting mobs = add(new BoolSetting("Show Monsters", false));
    public final BoolSetting names = add(new BoolSetting("Names", true));
    private static final float R = 46;

    public RadarModule() { super("Radar", "Shows nearby players on a small radar.", 10000, 380); }
    @Override public Category getCategory() { return Category.CHEATS; }
    @Override public float width() { return R * 2 + 2; }
    @Override public float height() { return R * 2 + 2; }

    @Override
    public void render(boolean editor) {
        if (mc.player == null) return;
        float c = R + 1;
        Render2D.circle(c, c, R, 0xB0100D18);
        Render2D.roundOutline(1, 1, R * 2, R * 2, R, 1, Render2D.alpha(Theme.accent(), 0.7f));
        Render2D.rect(c - 0.5f, 6, 1, R * 2 - 10, 0x22FFFFFF);
        Render2D.rect(6, c - 0.5f, R * 2 - 10, 1, 0x22FFFFFF);
        float yaw = (float) Math.toRadians(mc.player.yaw);
        double scale = R / range.get();
        for (Entity e : mc.world.loadedEntities) {
            if (e == mc.player) continue;
            boolean pl = e instanceof PlayerEntity;
            if (!pl && !(mobs.get() && e instanceof Monster)) continue;
            double dx = e.x - mc.player.x, dz = e.z - mc.player.z;
            // so drehen, dass "vorne" oben ist
            double rx = -(dx * Math.cos(yaw) + dz * Math.sin(yaw));
            double ry = -(dz * Math.cos(yaw) - dx * Math.sin(yaw));
            double d = Math.sqrt(rx * rx + ry * ry) * scale;
            if (d > R - 3) { rx *= (R - 3) / d; ry *= (R - 3) / d; d = R - 3; } else { rx *= scale; ry *= scale; }
            if (d == R - 3 && Math.abs(rx) < 1e-9) continue;
            float px = (float) (c + rx), py = (float) (c + ry);
            Render2D.circle(px, py, 2.2f, pl ? 0xFFFF5555 : 0xFFF5B942);
            if (pl && names.get()) {
                GlStateManager.pushMatrix();
                GlStateManager.translate(px, py - 7, 0);
                GlStateManager.scale(0.5f, 0.5f, 1);
                String n = ((PlayerEntity) e).getGameProfile().getName();
                Render2D.text(n, -Render2D.width(n) / 2f, 0, 0xFFFFFFFF, true);
                GlStateManager.popMatrix();
            }
        }
        // du
        Render2D.circle(c, c, 2.4f, 0xFFFFFFFF);
    }
}
