package com.vortex.client.gui;

import com.vortex.client.module.ModuleManager;
import com.vortex.client.module.modules.NoRenderBlocksModule;
import net.minecraft.block.Block;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

/**
 * Picks which blocks are hidden.
 *
 * Same list as the Block ESP screen, different meaning: here a tick makes the
 * block disappear instead of highlighting it. Blocks without an item form are
 * skipped, since there would be nothing to show as an icon.
 */
public class NoRenderBlocksScreen extends SelectionScreen {

    public NoRenderBlocksScreen(Screen parent) {
        super(parent, "Select blocks to hide");
    }

    @Override
    protected void buildEntries() {
        for (Block block : Registries.BLOCK) {
            Item item = block.asItem();
            if (item == Items.AIR) continue;
            Identifier id = Registries.BLOCK.getId(block);
            if (id == null) continue;
            entries.add(new Entry(item, id.toString(), block.getName().getString()));
        }
    }

    private NoRenderBlocksModule mod() {
        return ModuleManager.INSTANCE.get(NoRenderBlocksModule.class);
    }

    @Override
    protected boolean isOn(String id) {
        NoRenderBlocksModule m = mod();
        return m != null && m.isHidden(id);
    }

    @Override
    protected void toggle(String id) {
        NoRenderBlocksModule m = mod();
        if (m != null) m.set(id, !m.isHidden(id));
    }

    @Override
    protected void clearAll() {
        NoRenderBlocksModule m = mod();
        if (m != null) m.clearAll();
    }

    @Override
    protected String hint() {
        return "hidden from view";
    }
}
