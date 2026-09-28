package com.vortex.client.module.modules;

import com.vortex.client.core.setting.BooleanSetting;
import com.vortex.client.core.setting.ColorSetting;
import com.vortex.client.core.setting.NumberSetting;
import com.vortex.client.hud.HudElement;
import com.vortex.client.module.Module;

/**
 * Inventory HUD: dein Inventar klein auf dem Bildschirm -- Totems, Perlen und
 * Traenke im Blick, ohne E zu druecken.
 */
public class InventoryHudModule extends Module implements HudElement {

    public final NumberSetting x = new NumberSetting("X", 4, 0, 1920, 1);
    public final NumberSetting y = new NumberSetting("Y", 220, 0, 1080, 1);
    public final NumberSetting scale = new NumberSetting("Scale", 0.8, 0.4, 2.0, 0.1);
    public final BooleanSetting background = new BooleanSetting("Background", true);
    public final ColorSetting backgroundColor = new ColorSetting("Background Color", 0x90101018);
    public final BooleanSetting slotFrames = new BooleanSetting("Slot Frames", true);
    public final BooleanSetting showHotbar = new BooleanSetting("Show Hotbar", false);
    public final BooleanSetting hideEmpty = new BooleanSetting("Hide When Empty", true);

    public InventoryHudModule() {
        super("Inventory HUD", Category.HUD);
        addSetting(x); addSetting(y); addSetting(scale); addSetting(background); addSetting(backgroundColor);
        addSetting(slotFrames); addSetting(showHotbar); addSetting(hideEmpty);
    }

    @Override public String hudName() { return "Inventory HUD"; }
    @Override public NumberSetting hudX() { return x; }
    @Override public NumberSetting hudY() { return y; }
    @Override public NumberSetting hudScale() { return scale; }
    @Override public ColorSetting hudColor() { return backgroundColor; }
    @Override public int hudWidth() { return Math.round((9 * 18 + 4) * scale.getFloat()); }
    @Override public int hudHeight() { return Math.round(((showHotbar.get() ? 4 : 3) * 18 + 4 + (showHotbar.get() ? 3 : 0)) * scale.getFloat()); }
}
