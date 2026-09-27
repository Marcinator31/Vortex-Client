package com.vortex.client.module.modules;

import com.vortex.client.core.setting.BooleanSetting;
import com.vortex.client.core.setting.ColorSetting;
import com.vortex.client.core.setting.NumberSetting;
import com.vortex.client.hud.HudElement;
import com.vortex.client.module.Module;

/**
 * Mace HUD: deine Fallhoehe und wie viel ein Streitkolben-Schlag JETZT am
 * Gegner anrichten wuerde -- plus die Hoehe, ab der er toedlich ist.
 *
 * Gerechnet wie der Server (geprueft an MaceItem, Player.attack, CombatRules
 * in 26.x): Smash-Bonus nach Fallhoehe (4/Block bis 3, 2/Block bis 8, dann
 * 1/Block), Density +0,5 pro Stufe und Block, Krit x1,5, Ruestung und Haerte
 * mit Breach (-15 % Ruestungswirkung pro Stufe), Resistenz, Schutz.
 */
public class MaceHudModule extends Module implements HudElement {

    public final NumberSetting x = new NumberSetting("X", 4, 0, 1920, 1);
    public final NumberSetting y = new NumberSetting("Y", 120, 0, 1080, 1);
    public final ColorSetting color = new ColorSetting("Text Color", 0xFFFFFFFF);
    public final NumberSetting scale = new NumberSetting("Scale", 1.0, 0.5, 3.0, 0.1);
    public final BooleanSetting onlyWithMace = new BooleanSetting("Only With Mace", true);
    public final BooleanSetting showTarget = new BooleanSetting("Show Target", true);
    public final BooleanSetting showKillHeight = new BooleanSetting("Show Kill Height", true);
    public final NumberSetting targetRange = new NumberSetting("Target Range", 8, 3, 32, 1);

    /** Aussehen: Schatten, Kasten, Rahmen, Beschriftung ... */
    public final com.vortex.client.hud.HudStyle style = new com.vortex.client.hud.HudStyle(true);

    public MaceHudModule() {
        super("Mace HUD", Category.HUD);
        addSetting(x);
        addSetting(y);
        addSetting(color);
        addSetting(scale);
        addSetting(onlyWithMace);
        addSetting(showTarget);
        addSetting(showKillHeight);
        addSetting(targetRange);
        style.addTo(this::addSetting);
    }

    @Override public String hudName() { return "Mace HUD"; }
    @Override public NumberSetting hudX() { return x; }
    @Override public NumberSetting hudY() { return y; }
    @Override public NumberSetting hudScale() { return scale; }
    @Override public ColorSetting hudColor() { return color; }
    @Override public int hudWidth() { return style.breite(120); }
    @Override public int hudHeight() { return style.hoehe(40); }
}
