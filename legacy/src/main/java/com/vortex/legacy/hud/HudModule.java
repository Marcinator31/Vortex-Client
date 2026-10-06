package com.vortex.legacy.hud;

import com.vortex.legacy.core.BoolSetting;
import com.vortex.legacy.core.ColorSetting;
import com.vortex.legacy.core.Module;
import com.vortex.legacy.core.NumberSetting;
import com.vortex.legacy.gui.Render2D;
import com.vortex.legacy.gui.Theme;

/**
 * HUD-Element: Position (x, y in GUI-Einheiten), Groesse, optional Hintergrund.
 * Verschiebbar im HUD-Editor.
 */
public abstract class HudModule extends Module {
    public final NumberSetting x, y;
    public final NumberSetting scale = add(new NumberSetting("Scale", 1.0, 0.5, 2.5, 0.05));
    public final BoolSetting background = add(new BoolSetting("Background", true));
    public final ColorSetting textColor = add(new ColorSetting("Text Color", 0xFFFFFFFF));
    public final BoolSetting textShadow = add(new BoolSetting("Text Shadow", true));

    protected HudModule(String name, String description, int defX, int defY) {
        super(name, Category.HUD, description);
        x = add(new NumberSetting("X", defX, -10000, 10000, 0));
        y = add(new NumberSetting("Y", defY, -10000, 10000, 0));
    }

    /** Breite/Hoehe in unskalierten Einheiten (nach dem letzten render). */
    public abstract float width();
    public abstract float height();

    /** Zeichnen bei (0,0). editor = im HUD-Editor (Beispielwerte zeigen). */
    public abstract void render(boolean editor);

    // --- Helfer fuer typische "Kasten mit Text"-Elemente ---
    protected static final float PAD = 4;

    protected float textBoxWidth(String s) { return Render2D.width(s) + PAD * 2; }
    protected float textBoxHeight() { return Render2D.height() + PAD * 2 - 1; }

    protected void drawTextBox(String s) {
        float w = textBoxWidth(s), h = textBoxHeight();
        if (background.get()) Render2D.round(0, 0, w, h, 3, 0x90000000);
        Render2D.text(s, PAD, PAD, textColor.get(), textShadow.get());
    }

    protected int accent() { return Theme.accent(); }
}
