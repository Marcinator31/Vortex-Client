package com.example.pvpclient.module.modules;

import com.example.pvpclient.core.setting.BooleanSetting;
import com.example.pvpclient.core.setting.ColorSetting;
import com.example.pvpclient.module.Module;

/**
 * Spawner ESP: hebt Mob-Spawner durch Waende hervor. Nuetzlich, um XP-Farmen,
 * Dungeons oder von Spielern gebaute Spawner-Farmen zu finden.
 */
public class SpawnerEspModule extends Module {

    public final ColorSetting color = new ColorSetting("Farbe", 0xFFFF0000);
    public final BooleanSetting tracer = new BooleanSetting("Tracer", false);

    public SpawnerEspModule() {
        super("Spawner ESP", Category.PVP);
        addSetting(color);
        addSetting(tracer);
    }

    public int getColor() { return color.get(); }
    public boolean tracerEnabled() { return tracer.get(); }
}
