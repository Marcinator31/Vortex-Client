package com.vortex.legacy.module.hud;

import com.mojang.blaze3d.platform.GlStateManager;
import com.vortex.legacy.core.BoolSetting;
import com.vortex.legacy.core.ModeSetting;
import com.vortex.legacy.gui.Render2D;
import com.vortex.legacy.hud.HudModule;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.item.ItemStack;

/** Ruestung (und Item in der Hand) mit Haltbarkeit. */
public class ArmorHudModule extends HudModule {
    private final ModeSetting layout = add(new ModeSetting("Layout", 0, "Vertical", "Horizontal"));
    private final ModeSetting durability = add(new ModeSetting("Durability", 1, "Off", "Number", "Percent"));
    private final BoolSetting hand = add(new BoolSetting("Held Item", true));
    private float w = 60, h = 70;

    public ArmorHudModule() { super("ArmorHUD", "Your armor and its durability.", 10000, 170); }
    @Override public boolean defaultEnabled() { return true; }
    @Override public float width() { return w; }
    @Override public float height() { return h; }

    @Override
    public void render(boolean editor) {
        if (mc.player == null) return;
        java.util.List<ItemStack> items = new java.util.ArrayList<ItemStack>();
        for (int i = 3; i >= 0; i--) { ItemStack s = mc.player.inventory.getArmor(i); if (s != null) items.add(s); }
        if (hand.get() && mc.player.inventory.getMainHandStack() != null) items.add(mc.player.inventory.getMainHandStack());
        if (items.isEmpty() && editor) {
            items.add(new ItemStack(net.minecraft.item.Items.DIAMOND_HELMET));
            items.add(new ItemStack(net.minecraft.item.Items.DIAMOND_CHESTPLATE));
            items.add(new ItemStack(net.minecraft.item.Items.DIAMOND_LEGGINGS));
            items.add(new ItemStack(net.minecraft.item.Items.DIAMOND_BOOTS));
        }
        boolean vert = layout.is("Vertical");
        float x = 0, y = 0, maxW = 0;
        for (ItemStack s : items) {
            String t = text(s);
            float iw = 16 + (t.isEmpty() ? 0 : 3 + Render2D.width(t));
            maxW = Math.max(maxW, iw);
            GlStateManager.pushMatrix();
            DiffuseLighting.enable();
            GlStateManager.enableRescaleNormal();
            mc.getItemRenderer().renderInGuiWithOverrides(s, (int) x, (int) y);
            mc.getItemRenderer().renderGuiItemOverlay(mc.textRenderer, s, (int) x, (int) y, "");
            DiffuseLighting.disable();
            GlStateManager.disableRescaleNormal();
            GlStateManager.disableLighting();
            GlStateManager.popMatrix();
            if (!t.isEmpty()) Render2D.text(t, x + 19, y + 4, color(s), textShadow.get());
            if (vert) y += 17; else x += iw + 4;
        }
        w = vert ? Math.max(16, maxW) : Math.max(16, x - 4);
        h = vert ? Math.max(16, y - 1) : 16;
    }

    private String text(ItemStack s) {
        if (durability.is("Off")) return s.count > 1 ? String.valueOf(s.count) : "";
        if (!s.isDamageable()) return s.count > 1 ? String.valueOf(s.count) : "";
        int left = s.getMaxDamage() - s.getDamage();
        return durability.is("Percent") ? (left * 100 / Math.max(1, s.getMaxDamage())) + "%" : String.valueOf(left);
    }
    private int color(ItemStack s) {
        if (!s.isDamageable()) return textColor.get();
        float f = 1f - (float) s.getDamage() / Math.max(1, s.getMaxDamage());
        return f > 0.5f ? textColor.get() : f > 0.2f ? 0xFFFFC857 : 0xFFFF5555;
    }
}
