package com.vortex.client.gui;

import com.vortex.client.module.modules.ItemSizeModule;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;

/** Auswahl der Gegenstaende, die Item Size vergroessert (Modus "Selected Items"). */
public class ItemSizeScreen extends SelectionScreen {

    private final ItemSizeModule modul;

    public ItemSizeScreen(Screen parent, ItemSizeModule modul) {
        super(parent, "Bigger items");
        this.modul = modul;
    }

    @Override
    protected void buildEntries() {
        for (Item item : BuiltInRegistries.ITEM) {
            Identifier id = BuiltInRegistries.ITEM.getKey(item);
            if (id == null || "minecraft:air".equals(id.toString())) continue;
            entries.add(new Entry(item, id.toString(),
                    item.getName(new net.minecraft.world.item.ItemStack(item)).getString()));
        }
    }

    @Override
    protected boolean isOn(String id) {
        return modul.ausgewaehlt().contains(id);
    }

    @Override
    protected void toggle(String id) {
        if (!modul.ausgewaehlt().remove(id)) modul.ausgewaehlt().add(id);
        com.vortex.client.core.ConfigManager.save();
    }

    @Override
    protected void clearAll() {
        modul.ausgewaehlt().clear();
        com.vortex.client.core.ConfigManager.save();
    }

    @Override
    protected String hint() {
        return "shown bigger on the ground";
    }
}
