package com.vortex.client.module.modules;

import com.vortex.client.core.setting.BooleanSetting;
import com.vortex.client.core.setting.ColorSetting;
import com.vortex.client.core.setting.NumberSetting;
import com.vortex.client.hud.HudElement;
import com.vortex.client.module.Module;

/** HUD: was gerade laeuft -- Cover, Titel, Kuenstler, Fortschritt (Kategorie Music). */
public class NowPlayingModule extends Module implements HudElement {

    public final NumberSetting x = new NumberSetting("X", 6, 0, 1920, 1);
    public final NumberSetting y = new NumberSetting("Y", 6, 0, 1080, 1);
    public final NumberSetting scale = new NumberSetting("Scale", 1.0, 0.5, 2.5, 0.1);
    public final ColorSetting color = new ColorSetting("Accent Color", 0xFF1ED760);
    public final BooleanSetting cover = new BooleanSetting("Show Cover", true);
    public final BooleanSetting progress = new BooleanSetting("Show Progress", true);
    public final BooleanSetting hidePaused = new BooleanSetting("Hide When Paused", false);
    /** Auch den Song zeigen, bei dem man gerade mithoert ("Listening with ..."). */
    public final BooleanSetting showListening = new BooleanSetting("Show Listen Along", true);

    public NowPlayingModule() {
        super("Now Playing", Category.MUSIC);
        addSetting(x);
        addSetting(y);
        addSetting(scale);
        addSetting(color);
        addSetting(cover);
        addSetting(progress);
        addSetting(hidePaused);
        addSetting(showListening);
    }

    public static final int BREITE = 168, HOEHE = 40;

    @Override public String hudName() { return "Now Playing"; }
    @Override public NumberSetting hudX() { return x; }
    @Override public NumberSetting hudY() { return y; }
    @Override public NumberSetting hudScale() { return scale; }
    @Override public ColorSetting hudColor() { return color; }
    @Override public int hudWidth() { return (int) Math.round(BREITE * scale.get()); }
    @Override public int hudHeight() { return (int) Math.round(HOEHE * scale.get()); }
}
