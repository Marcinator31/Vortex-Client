package com.example.pvpclient.module.modules;

import com.example.pvpclient.core.setting.BooleanSetting;
import com.example.pvpclient.core.setting.ColorSetting;
import com.example.pvpclient.core.setting.NumberSetting;
import com.example.pvpclient.hud.HudElement;
import com.example.pvpclient.module.Module;

/**
 * Totem-Zaehler: zeigt an, wie viele Totems die Spieler in der Umgebung
 * verbraucht haben.
 *
 * Im Kampf die vielleicht nuetzlichste Information ueberhaupt -- wer gerade sein
 * letztes Totem gezogen hat, ist angreifbar. Gezaehlt werden nur Ereignisse, die
 * der Server ohnehin an alle Umstehenden schickt (fuer die Totem-Animation).
 */
public class TotemPopperModule extends Module implements HudElement {

    public final NumberSetting x = new NumberSetting("X", 4, 0, 1920, 1);
    public final NumberSetting y = new NumberSetting("Y", 60, 0, 1080, 1);
    public final ColorSetting color = new ColorSetting("Textfarbe", 0xFFFFFFFF);
    public final NumberSetting scale = new NumberSetting("Skalierung", 1.0, 0.5, 3.0, 0.1);

    /** Wie viele Spieler hoechstens aufgelistet werden. */
    public final NumberSetting maxEntries = new NumberSetting("Max. Eintraege", 5, 1, 15, 1);

    /** Frisch verbrauchte Totems kurz hervorheben. */
    public final BooleanSetting highlight = new BooleanSetting("Neue hervorheben", true);
    public final ColorSetting highlightColor = new ColorSetting("Hervorhebung", 0xFFFF5555);

    public TotemPopperModule() {
        super("Totem Popper", Category.HUD);
        addSetting(x);
        addSetting(y);
        addSetting(color);
        addSetting(scale);
        addSetting(maxEntries);
        addSetting(highlight);
        addSetting(highlightColor);
    }

    @Override public String hudName() { return "Totem Popper"; }
    @Override public NumberSetting hudX() { return x; }
    @Override public NumberSetting hudY() { return y; }
    @Override public NumberSetting hudScale() { return scale; }
    @Override public ColorSetting hudColor() { return color; }
    @Override public int hudWidth() { return (int) (110 * scale.get()); }
    @Override public int hudHeight() { return (int) ((maxEntries.getInt() * 10 + 10) * scale.get()); }
}
