package com.vortex.legacy.module.hud;

import com.mojang.blaze3d.platform.GlStateManager;
import com.vortex.legacy.gui.Render2D;
import com.vortex.legacy.hud.HudModule;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.item.ItemStack;

/** Dein Inventar (ohne Hotbar) als kleines Raster. */
public class InventoryHudModule extends HudModule {
    public InventoryHudModule() { super("Inventory HUD", "Shows your inventory on screen.", 300, 260); }
    private static final float W = 9 * 18 + 4, H = 3 * 18 + 4;
    @Override public float width() { return W; }
    @Override public float height() { return H; }

    @Override
    public void render(boolean editor) {
        if (mc.player == null) return;
        if (background.get()) Render2D.round(0, 0, W, H, 4, 0x90000000);
        DiffuseLighting.enable();
        GlStateManager.enableRescaleNormal();
        for (int i = 9; i < 36; i++) {
            ItemStack s = mc.player.inventory.getInvStack(i);
            if (s == null) continue;
            int x = 2 + (i % 9) * 18 + 1, y = 2 + (i / 9 - 1) * 18 + 1;
            mc.getItemRenderer().renderInGuiWithOverrides(s, x, y);
            mc.getItemRenderer().renderGuiItemOverlay(mc.textRenderer, s, x, y);
        }
        DiffuseLighting.disable();
        GlStateManager.disableRescaleNormal();
        GlStateManager.disableLighting();
    }
}
