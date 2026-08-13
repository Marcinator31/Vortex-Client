package com.example.pvpclient.module.modules;

import com.example.pvpclient.core.setting.BooleanSetting;
import com.example.pvpclient.core.setting.ColorSetting;
import com.example.pvpclient.core.setting.NumberSetting;
import com.example.pvpclient.hud.HudElement;
import com.example.pvpclient.module.Module;

/**
 * Session-Statistik: Spielzeit, eigene Tode, verbrauchte Totems und die hoechste
 * erreichte Klickrate seit dem Start.
 *
 * Bewusst nur Werte, die der Client zuverlaessig selbst kennt. Auf eine
 * Kill-Zaehlung wird verzichtet: ob ein Gegner wirklich durch dich gestorben ist,
 * laesst sich clientseitig nicht sicher feststellen -- eine geratene Zahl waere
 * schlechter als gar keine.
 */
public class SessionStatsModule extends Module implements HudElement {

    public final NumberSetting x = new NumberSetting("X", 4, 0, 1920, 1);
    public final NumberSetting y = new NumberSetting("Y", 200, 0, 1080, 1);
    public final ColorSetting color = new ColorSetting("Textfarbe", 0xFFFFFFFF);
    public final NumberSetting scale = new NumberSetting("Skalierung", 1.0, 0.5, 3.0, 0.1);

    public final BooleanSetting showTime = new BooleanSetting("Spielzeit", true);
    public final BooleanSetting showDeaths = new BooleanSetting("Tode", true);
    public final BooleanSetting showTotems = new BooleanSetting("Eigene Totems", true);
    public final BooleanSetting showMaxCps = new BooleanSetting("Hoechste CPS", true);

    public SessionStatsModule() {
        super("Session Stats", Category.HUD);
        addSetting(x);
        addSetting(y);
        addSetting(color);
        addSetting(scale);
        addSetting(showTime);
        addSetting(showDeaths);
        addSetting(showTotems);
        addSetting(showMaxCps);
    }

    /** Anzahl der eingeschalteten Zeilen. */
    public int lineCount() {
        int n = 0;
        if (showTime.get()) n++;
        if (showDeaths.get()) n++;
        if (showTotems.get()) n++;
        if (showMaxCps.get()) n++;
        return n;
    }

    @Override public String hudName() { return "Session Stats"; }
    @Override public NumberSetting hudX() { return x; }
    @Override public NumberSetting hudY() { return y; }
    @Override public NumberSetting hudScale() { return scale; }
    @Override public ColorSetting hudColor() { return color; }
    @Override public int hudWidth() { return (int) (95 * scale.get()); }
    @Override public int hudHeight() { return (int) (Math.max(1, lineCount()) * 10 * scale.get()); }
}
