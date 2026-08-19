package com.vortex.client.gui;

import com.vortex.client.module.ModuleManager;
import com.vortex.client.module.modules.AntiRenderModule;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;

/**
 * Auswahl der Entity-Typen, die nicht gezeichnet werden sollen (Anti Render).
 *
 * Anders als beim Mob-ESP werden hier ALLE Entity-Typen gezeigt, auch nicht
 * lebendige wie Loren, Ruestungsstaender oder Item-Rahmen -- genau die verursachen
 * die grossen Bildraten-Einbrueche. Typen ohne Spawn-Ei bekommen ein Ersatzsymbol.
 */
public class AntiRenderScreen extends SelectionScreen {

    public AntiRenderScreen(Screen parent) {
        super(parent, "Entities ausblenden");
    }

    @Override
    protected void buildEntries() {
        entries.add(new Entry(Items.PLAYER_HEAD, "minecraft:player", "Players"));
        for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
            Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
            if (id == null) continue;
            if ("minecraft:player".equals(id.toString())) continue;
            var egg = SpawnEggItem.byId(type);
            Item icon = egg.map(holder -> holder.value()).orElse(Items.BARRIER);
            entries.add(new Entry(icon, id.toString(), type.getDescription().getString()));
        }
    }

    private AntiRenderModule mod() {
        return ModuleManager.INSTANCE.get(AntiRenderModule.class);
    }

    @Override
    protected boolean isOn(String id) {
        AntiRenderModule m = mod();
        return m != null && m.isHidden(id);
    }

    @Override
    protected void toggle(String id) {
        AntiRenderModule m = mod();
        if (m != null) m.toggle(id);
    }

    @Override
    protected void clearAll() {
        AntiRenderModule m = mod();
        if (m == null) return;
        // Ueber toggle gehen, damit der Versionszaehler mitlaeuft (der Render-Code
        // erkennt daran, dass sein Zwischenspeicher ungueltig geworden ist).
        for (String id : new java.util.ArrayList<>(m.getHiddenTypes())) {
            m.set(id, false);
        }
    }

    @Override
    protected String hint() {
        return "hidden from view";
    }
}
