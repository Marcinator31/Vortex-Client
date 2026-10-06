package com.vortex.legacy.module.hud;

import com.mojang.blaze3d.platform.GlStateManager;
import com.vortex.legacy.core.BoolSetting;
import com.vortex.legacy.gui.Render2D;
import com.vortex.legacy.hud.HudModule;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.util.Identifier;

/** Aktive Effekte mit Symbol und Restzeit; laeuft ein Effekt aus, blinkt er. */
public class PotionEffectsModule extends HudModule {
    private static final Identifier INV = new Identifier("textures/gui/container/inventory.png");
    private final BoolSetting icons = add(new BoolSetting("Icons", true));
    private final BoolSetting blink = add(new BoolSetting("Blink When Ending", true));
    private float w = 90, h = 40;

    public PotionEffectsModule() { super("Potion Effects", "Active potion effects and how long they last.", 4, 340); }
    @Override public boolean defaultEnabled() { return true; }
    @Override public float width() { return w; }
    @Override public float height() { return h; }

    @Override
    public void render(boolean editor) {
        if (mc.player == null) return;
        Collection<StatusEffectInstance> list = mc.player.getStatusEffectInstances();
        List<StatusEffectInstance> l = new ArrayList<StatusEffectInstance>(list);
        if (l.isEmpty() && editor) {
            l.add(new StatusEffectInstance(StatusEffect.SPEED.id, 20 * 95, 1));
            l.add(new StatusEffectInstance(StatusEffect.FIRE_RESISTANCE.id, 20 * 300, 0));
        }
        float y = 0, maxW = 0;
        for (StatusEffectInstance e : l) {
            StatusEffect fx = StatusEffect.STATUS_EFFECTS[e.getEffectId()];
            if (fx == null) continue;
            String name = I18n.translate(fx.getTranslationKey()) + (e.getAmplifier() > 0 ? " " + roman(e.getAmplifier() + 1) : "");
            String time = StatusEffect.getFormattedDuration(e);
            float tx = icons.get() ? 22 : 0;
            float rowW = tx + Math.max(Render2D.width(name), Render2D.width(time));
            maxW = Math.max(maxW, rowW);
            float a = 1f;
            if (blink.get() && e.getDuration() < 200) a = 0.45f + 0.55f * (float) Math.abs(Math.sin(System.currentTimeMillis() / 250.0));
            if (icons.get() && fx.hasIcon()) {
                GlStateManager.enableBlend();
                GlStateManager.color(1, 1, 1, a);
                mc.getTextureManager().bindTexture(INV);
                int idx = fx.getIconLevel();
                DrawableHelper.drawTexture(0, (int) y + 1, idx % 8 * 18, 198 + idx / 8 * 18, 18, 18, 256, 256);
                GlStateManager.color(1, 1, 1, 1);
            }
            Render2D.text(name, tx, y + 1, Render2D.alpha(Render2D.mix(textColor.get(), 0xFF000000 | fx.getColor(), 0.35f), a), textShadow.get());
            Render2D.text(time, tx, y + 11, Render2D.alpha(0xFFB9B3CC, a), textShadow.get());
            y += 22;
        }
        w = Math.max(20, maxW);
        h = Math.max(20, y);
    }

    private static String roman(int n) {
        String[] r = { "", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X" };
        return n < r.length ? r[n] : String.valueOf(n);
    }
}
