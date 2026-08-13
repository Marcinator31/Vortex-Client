package com.example.pvpclient.gui;

import com.example.pvpclient.module.ModuleManager;
import com.example.pvpclient.module.modules.AntiRenderModule;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.entity.EntityType;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

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
        entries.add(new Entry(Items.PLAYER_HEAD, "minecraft:player", "Spieler"));
        for (EntityType<?> type : Registries.ENTITY_TYPE) {
            Identifier id = Registries.ENTITY_TYPE.getId(type);
            if (id == null) continue;
            if ("minecraft:player".equals(id.toString())) continue;
            SpawnEggItem egg = SpawnEggItem.forEntity(type);
            Item icon = (egg != null) ? egg : Items.BARRIER;
            entries.add(new Entry(icon, id.toString(), type.getName().getString()));
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
        return "werden nicht gezeichnet";
    }
}
