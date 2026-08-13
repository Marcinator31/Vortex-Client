package com.example.pvpclient.module.modules;

import com.example.pvpclient.core.setting.BooleanSetting;
import com.example.pvpclient.core.setting.ColorSetting;
import com.example.pvpclient.module.Module;

/**
 * Item ESP: markiert auf dem Boden liegende Items (gedroppte ItemEntities) mit
 * einer Box und optional einem Tracer. Nuetzlich, um Loot nach Kaempfen oder
 * ausgelaufene Stashes zu finden.
 */
public class ItemEspModule extends Module {

    public final ColorSetting color = new ColorSetting("Farbe", 0xFF00FF00);
    public final BooleanSetting tracer = new BooleanSetting("Tracer", false);

    public ItemEspModule() {
        super("Item ESP", Category.PVP);
        addSetting(color);
        addSetting(tracer);
    }

    public int getColor() { return color.get(); }
    public boolean tracerEnabled() { return tracer.get(); }
}
