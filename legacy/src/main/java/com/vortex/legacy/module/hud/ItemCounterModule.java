package com.vortex.legacy.module.hud;

import com.mojang.blaze3d.platform.GlStateManager;
import com.vortex.legacy.core.BoolSetting;
import com.vortex.legacy.gui.Render2D;
import com.vortex.legacy.hud.HudModule;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

/** Wichtige Items zaehlen: Pfeile, Goldaepfel, Perlen, Traenke, Bloecke ... */
public class ItemCounterModule extends HudModule {
    private final BoolSetting arrows = add(new BoolSetting("Arrows", true));
    private final BoolSetting gapples = add(new BoolSetting("Golden Apples", true));
    private final BoolSetting pearls = add(new BoolSetting("Ender Pearls", true));
    private final BoolSetting potions = add(new BoolSetting("Potions", true));
    private final BoolSetting blocks = add(new BoolSetting("Blocks", false));
    private final BoolSetting hideZero = add(new BoolSetting("Hide Empty", true));
    private float w = 40, h = 20;

    public ItemCounterModule() { super("Item Counter", "Counts arrows, golden apples, pearls and potions.", 10000, 260); }
    @Override public float width() { return w; }
    @Override public float height() { return h; }

    private int count(Item item) {
        int n = 0;
        for (int i = 0; i < 36; i++) { ItemStack s = mc.player.inventory.getInvStack(i); if (s != null && s.getItem() == item) n += s.count; }
        return n;
    }
    private int countBlocks() {
        int n = 0;
        for (int i = 0; i < 36; i++) { ItemStack s = mc.player.inventory.getInvStack(i); if (s != null && s.getItem() instanceof net.minecraft.item.BlockItem) n += s.count; }
        return n;
    }

    @Override
    public void render(boolean editor) {
        if (mc.player == null) return;
        List<Object[]> rows = new ArrayList<Object[]>();
        if (arrows.get()) rows.add(new Object[]{ new ItemStack(Items.ARROW), count(Items.ARROW) });
        if (gapples.get()) rows.add(new Object[]{ new ItemStack(Items.GOLDEN_APPLE), count(Items.GOLDEN_APPLE) });
        if (pearls.get()) rows.add(new Object[]{ new ItemStack(Items.ENDER_PEARL), count(Items.ENDER_PEARL) });
        if (potions.get()) rows.add(new Object[]{ new ItemStack(Items.POTION, 1, 16421), count(Items.POTION) });
        if (blocks.get()) rows.add(new Object[]{ new ItemStack(net.minecraft.block.Blocks.WOOL), countBlocks() });
        float y = 0, maxW = 0;
        for (Object[] r : rows) {
            int n = (Integer) r[1];
            if (n == 0 && hideZero.get() && !editor) continue;
            ItemStack s = (ItemStack) r[0];
            DiffuseLighting.enable();
            GlStateManager.enableRescaleNormal();
            mc.getItemRenderer().renderInGuiWithOverrides(s, 0, (int) y);
            DiffuseLighting.disable();
            GlStateManager.disableRescaleNormal();
            GlStateManager.disableLighting();
            String t = String.valueOf(editor && n == 0 ? 16 : n);
            Render2D.text(t, 19, y + 4, textColor.get(), textShadow.get());
            maxW = Math.max(maxW, 19 + Render2D.width(t));
            y += 17;
        }
        w = Math.max(16, maxW);
        h = Math.max(16, y - 1);
    }
}
