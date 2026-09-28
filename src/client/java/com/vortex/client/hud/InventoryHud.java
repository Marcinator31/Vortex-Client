package com.vortex.client.hud;

import com.vortex.client.module.ModuleManager;
import com.vortex.client.module.modules.InventoryHudModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;

/** Zeichnet das Inventory HUD: Reihen 9-35 (oben nach unten), optional die Hotbar darunter. */
public final class InventoryHud {

    private InventoryHud() {}

    public static void render(GuiGraphicsExtractor ctx, Minecraft mc) {
        InventoryHudModule m = ModuleManager.INSTANCE.get(InventoryHudModule.class);
        if (m == null || !m.isEnabled() || mc.player == null) return;
        var inv = mc.player.getInventory();
        boolean hotbar = m.showHotbar.get();
        boolean editor = mc.gui.screen() instanceof com.vortex.client.gui.HudEditorScreen;
        if (m.hideEmpty.get() && !editor) {
            boolean any = false;
            for (int i = 9; i < 36 && !any; i++) any = !inv.getItem(i).isEmpty();
            if (hotbar) for (int i = 0; i < 9 && !any; i++) any = !inv.getItem(i).isEmpty();
            if (!any) return;
        }
        int x0 = m.x.getInt(), y0 = m.y.getInt();
        int rows = hotbar ? 4 : 3;
        int w = 9 * 18 + 4, h = rows * 18 + 4 + (hotbar ? 3 : 0);
        HudRenderer.pushScale(ctx, x0, y0, m.scale.getFloat());
        try {
            if (m.background.get()) ctx.fill(x0, y0, x0 + w, y0 + h, m.backgroundColor.get());
            for (int r = 0; r < rows; r++) {
                for (int c = 0; c < 9; c++) {
                    int slot = r < 3 ? 9 + r * 9 + c : c;
                    int sx = x0 + 2 + c * 18, sy = y0 + 2 + r * 18 + (r == 3 ? 3 : 0);
                    if (m.slotFrames.get()) ctx.fill(sx, sy, sx + 17, sy + 17, 0x30FFFFFF);
                    ItemStack st = inv.getItem(slot);
                    if (st.isEmpty()) continue;
                    ctx.item(st, sx, sy);
                    ctx.itemDecorations(mc.font, st, sx, sy);
                }
            }
        } finally {
            HudRenderer.popScale(ctx);
        }
    }
}
