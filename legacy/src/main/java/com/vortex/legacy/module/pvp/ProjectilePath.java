package com.vortex.legacy.module.pvp;

import com.vortex.legacy.core.ColorSetting;
import com.vortex.legacy.core.Module;
import com.vortex.legacy.gui.Render3D;
import com.vortex.legacy.module.WorldRenderers;
import net.minecraft.item.BowItem;
import net.minecraft.item.EggItem;
import net.minecraft.item.EnderPearlItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.PotionItem;
import net.minecraft.item.SnowballItem;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/** Flugbahn von Bogen, Perle, Schneeball, Ei und Wurftrank -- mit Landepunkt. */
public class ProjectilePath extends Module implements WorldRenderers.InWorld {
    public final ColorSetting color = add(new ColorSetting("Color", 0xE08B5CF6));
    public ProjectilePath() { super("Projectile Path", Category.PVP, "Shows where your arrow or pearl will land."); }

    @Override
    public void renderWorld(float tickDelta) {
        if (mc.player == null || mc.world == null) return;
        ItemStack s = mc.player.inventory.getMainHandStack();
        if (s == null) return;
        Item it = s.getItem();
        double speed, gravity, drag = 0.99, pitchOff = 0;
        if (it instanceof BowItem) {
            if (!mc.player.isUsingItem()) return;
            float f = mc.player.getItemUseTicks() / 20f;
            f = (f * f + f * 2) / 3f;
            if (f < 0.1f) return;
            speed = Math.min(1f, f) * 3.0; gravity = 0.05;
        } else if (it instanceof EnderPearlItem || it instanceof SnowballItem || it instanceof EggItem) {
            speed = 1.5; gravity = 0.03;
        } else if (it instanceof PotionItem && PotionItem.isThrowable(s.getData())) {
            speed = 0.5; gravity = 0.05; pitchOff = -20;
        } else return;

        float yaw = mc.player.prevYaw + (mc.player.yaw - mc.player.prevYaw) * tickDelta;
        float pitch = mc.player.prevPitch + (mc.player.pitch - mc.player.prevPitch) * tickDelta;
        double x = Render3D.camX - Math.cos(Math.toRadians(yaw)) * 0.16;
        double y = Render3D.camY + mc.player.getEyeHeight() - 0.1;
        double z = Render3D.camZ - Math.sin(Math.toRadians(yaw)) * 0.16;
        double p = Math.toRadians(pitch + pitchOff), yw = Math.toRadians(yaw);
        double vx = -Math.sin(yw) * Math.cos(p), vy = -Math.sin(p), vz = Math.cos(yw) * Math.cos(p);
        double len = Math.sqrt(vx * vx + vy * vy + vz * vz);
        vx = vx / len * speed; vy = vy / len * speed; vz = vz / len * speed;

        Render3D.begin(2f, false);
        int c = color.get();
        BlockHitResult hit = null;
        for (int i = 0; i < 300; i++) {
            Vec3d a = new Vec3d(x, y, z), b = new Vec3d(x + vx, y + vy, z + vz);
            hit = mc.world.rayTrace(a, b);
            if (hit != null) { Render3D.line(x, y, z, hit.pos.x, hit.pos.y, hit.pos.z, c); break; }
            Render3D.line(x, y, z, b.x, b.y, b.z, i == 0 ? (c & 0xFFFFFF) | 0x40000000 : c);
            x = b.x; y = b.y; z = b.z;
            vx *= drag; vy = vy * drag - gravity; vz *= drag;
            if (y < 0) break;
        }
        if (hit != null) {
            double h = 0.25;
            Render3D.box(new Box(hit.pos.x - h, hit.pos.y - h, hit.pos.z - h, hit.pos.x + h, hit.pos.y + h, hit.pos.z + h), (c & 0xFFFFFF) | 0x30000000, c);
        }
        Render3D.end();
    }
}
