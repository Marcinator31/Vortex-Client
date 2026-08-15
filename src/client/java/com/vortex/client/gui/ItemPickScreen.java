package com.vortex.client.gui;

import com.vortex.client.hud.ItemCounter;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

/**
 * Picks the items one counter adds up.
 *
 * Every item in the game is offered, searchable by name. Several can be ticked:
 * a counter for building blocks wants cobblestone and deepslate together, and
 * one for healing wants both kinds of golden apple.
 */
public class ItemPickScreen extends SelectionScreen {

    private final ItemCounter counter;

    public ItemPickScreen(Screen parent, ItemCounter counter) {
        super(parent, "Select items");
        this.counter = counter;
    }

    @Override
    protected void buildEntries() {
        for (Item item : Registries.ITEM) {
            Identifier id = Registries.ITEM.getId(item);
            if (id == null) continue;
            // Air is in the registry but is not an item anyone carries.
            if ("minecraft:air".equals(id.toString())) continue;
            entries.add(new Entry(item, id.toString(), item.getName().getString()));
        }
    }

    @Override
    protected boolean isOn(String id) {
        return counter.items.contains(id);
    }

    @Override
    protected void toggle(String id) {
        if (!counter.items.remove(id)) {
            counter.items.add(id);
        }
        com.vortex.client.core.ConfigManager.save();
    }

    @Override
    protected void clearAll() {
        counter.items.clear();
        com.vortex.client.core.ConfigManager.save();
    }

    @Override
    protected String hint() {
        return "counted together";
    }
}
