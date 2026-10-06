package com.vortex.legacy.module.hud;

import com.vortex.legacy.core.Combat;
import com.vortex.legacy.gui.Render2D;
import com.vortex.legacy.hud.HudModule;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;

/** Name, Leben und Ruestung deines Ziels (zuletzt geschlagen oder angeschaut). */
public class TargetInfoModule extends HudModule {
    private float shown;
    private float hpAnim = -1;
    private long last;
    public TargetInfoModule() { super("Target Info", "Health of the player you are fighting.", 300, 300); }
    private static final float W = 130, H = 38;
    @Override public float width() { return W; }
    @Override public float height() { return H; }

    private LivingEntity target() {
        if (mc.targetedEntity instanceof LivingEntity) return (LivingEntity) mc.targetedEntity;
        if (Combat.lastTarget instanceof LivingEntity && System.currentTimeMillis() - Combat.lastTargetAt < 4000
                && !Combat.lastTarget.removed) return (LivingEntity) Combat.lastTarget;
        return null;
    }

    @Override
    public void render(boolean editor) {
        LivingEntity t = target();
        if (t == null && editor) t = mc.player;
        long now = System.currentTimeMillis();
        float dt = last == 0 ? 0 : Math.min(0.1f, (now - last) / 1000f);
        last = now;
        shown += ((t != null ? 1 : 0) - shown) * (1 - (float) Math.exp(-dt * 12));
        if (t == null || shown < 0.03f) { if (t == null) hpAnim = -1; return; }
        float a = shown;
        float hp = t.getHealth(), max = Math.max(1, t.getMaxHealth());
        if (hpAnim < 0) hpAnim = hp;
        hpAnim += (hp - hpAnim) * (1 - (float) Math.exp(-dt * 8));
        Render2D.shadow(0, 0, W, H, 5, 4, Render2D.alpha(0x60000000, a));
        Render2D.round(0, 0, W, H, 5, Render2D.alpha(0xE0100D18, a));
        String name = t instanceof PlayerEntity ? ((PlayerEntity) t).getGameProfile().getName() : t.getName().asUnformattedString();
        Render2D.text(Render2D.trim(name, (int) W - 12), 6, 5, Render2D.alpha(0xFFFFFFFF, a), true);
        String info = String.format("%.1f ❤", hp / 2f);
        if (mc.player != null && t != mc.player) info += String.format("   %.1fm", mc.player.distanceTo(t));
        Render2D.text(info, 6, 16, Render2D.alpha(0xFFB9B3CC, a), true);
        float bw = W - 12;
        Render2D.round(6, H - 8, bw, 3, 1.5f, Render2D.alpha(0x33FFFFFF, a));
        float f = Math.max(0, Math.min(1, hpAnim / max));
        int c = f > 0.5f ? 0xFF22C55E : f > 0.25f ? 0xFFF5B942 : 0xFFEF4444;
        Render2D.round(6, H - 8, Math.max(3, bw * f), 3, 1.5f, Render2D.alpha(c, a));
    }
}
