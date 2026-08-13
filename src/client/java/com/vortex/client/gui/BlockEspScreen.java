package com.vortex.client.gui;

import com.vortex.client.module.ModuleManager;
import com.vortex.client.module.modules.BlockEspModule;
import net.minecraft.block.Block;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

/**
 * Auswahl der Bloecke fuers Block-ESP.
 *
 * Bloecke ohne zugehoeriges Item (rein technische Bloecke) werden ausgelassen --
 * sie haetten kein Symbol. Darstellung und Bedienung kommen aus
 * {@link SelectionScreen}.
 */
public class BlockEspScreen extends SelectionScreen {

    public BlockEspScreen(Screen parent) {
        super(parent, "Bloecke auswaehlen");
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

    private BlockEspModule mod() {
        return ModuleManager.INSTANCE.get(BlockEspModule.class);
    }

    @Override
    protected boolean isOn(String id) {
        BlockEspModule m = mod();
        return m != null && m.isBlockEnabled(id);
    }

    @Override
    protected void toggle(String id) {
        BlockEspModule m = mod();
        if (m != null) m.toggleBlock(id);
    }

    @Override
    protected void clearAll() {
        BlockEspModule m = mod();
        if (m != null) m.getEnabledBlocks().clear();
    }

    @Override
    protected String hint() {
        return "werden durch Waende gezeigt";
    }
}
