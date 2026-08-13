package com.example.pvpclient.module.modules;

import com.example.pvpclient.core.setting.BooleanSetting;
import com.example.pvpclient.core.setting.ColorSetting;
import com.example.pvpclient.module.Module;

/**
 * Container ESP: markiert einzelne Container (Truhen, Shulker, Faesser,
 * Trichter, Spender, ...) durch Waende mit einer Box-Outline. Ergaenzt den
 * Stash Finder: waehrend der Stash Finder ganze Cluster meldet, zeigt das hier
 * jeden einzelnen Container in Reichweite.
 */
public class ContainerEspModule extends Module {

    public final ColorSetting color = new ColorSetting("Farbe", 0xFFFFA500);
    public final BooleanSetting tracer = new BooleanSetting("Tracer", false);

    public ContainerEspModule() {
        super("Container ESP", Category.PVP);
        addSetting(color);
        addSetting(tracer);
    }

    public int getColor() { return color.get(); }
    public boolean tracerEnabled() { return tracer.get(); }
}
