package com.vortex.legacy.module.hud;

import com.vortex.legacy.core.NumberSetting;
import com.vortex.legacy.gui.Render2D;
import com.vortex.legacy.hud.HudModule;
import net.minecraft.item.ItemStack;

/** Warnt, wenn ein Ruestungsteil fast kaputt ist. */
public class ArmorWarningModule extends HudModule {
    private final NumberSetting percent = add(new NumberSetting("Warn Below %", 15, 1, 50, 1));
    private float w = 120, h = 16;
    public ArmorWarningModule() { super("Armor Warning", "Warns when a piece of armor is about to break.", 330, 30); }
    @Override public float width() { return w; }
    @Override public float height() { return h; }

    @Override
    public void render(boolean editor) {
        if (mc.player == null) return;
        String low = null;
        String[] names = { "Boots", "Leggings", "Chestplate", "Helmet" };
        for (int i = 0; i < 4; i++) {
            ItemStack s = mc.player.inventory.getArmor(i);
            if (s == null || !s.isDamageable()) continue;
            int left = s.getMaxDamage() - s.getDamage();
            if (left * 100 < percent.get() * s.getMaxDamage()) { low = names[i] + " almost broken (" + left + ")"; break; }
        }
        if (low == null && editor) low = "Helmet almost broken (12)";
        if (low == null) return;
        float a = 0.6f + 0.4f * (float) Math.abs(Math.sin(System.currentTimeMillis() / 300.0));
        w = Render2D.width(low) + 10;
        h = 15;
        Render2D.round(0, 0, w, h, 3, Render2D.alpha(0xC0B91C1C, a));
        Render2D.text(low, 5, 4, 0xFFFFFFFF, true);
    }
}
