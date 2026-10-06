package com.vortex.legacy.module.pvp;

import com.vortex.legacy.core.BoolSetting;
import com.vortex.legacy.core.ColorSetting;
import com.vortex.legacy.core.Module;
import com.vortex.legacy.core.ModeSetting;
import com.vortex.legacy.core.NumberSetting;
import com.vortex.legacy.gui.Render2D;
import net.minecraft.entity.Entity;

/** Eigenes Fadenkreuz: Form, Groesse, Farbe; wird rot, wenn du auf einen Spieler zielst. */
public class Crosshair extends Module {
    public final ModeSetting style = add(new ModeSetting("Style", 0, "Cross", "Dot", "Circle", "Cross + Dot", "T"));
    public final NumberSetting size = add(new NumberSetting("Size", 5, 1, 15, 0.5));
    public final NumberSetting gap = add(new NumberSetting("Gap", 2, 0, 8, 0.5));
    public final NumberSetting thickness = add(new NumberSetting("Thickness", 1, 0.5, 4, 0.5));
    public final ColorSetting color = add(new ColorSetting("Color", 0xFFFFFFFF));
    public final BoolSetting outline = add(new BoolSetting("Outline", true));
    public final BoolSetting targetColor = add(new BoolSetting("Red On Target", true));

    public Crosshair() { super("Crosshair", Category.PVP, "Customize your crosshair."); }

    public void draw(float cx, float cy) {
        int c = color.get();
        Entity t = mc.targetedEntity;
        if (targetColor.get() && t != null && t instanceof net.minecraft.entity.LivingEntity) c = 0xFFFF4444;
        float s = size.getFloat(), g = gap.getFloat(), th = thickness.getFloat();
        String st = style.get();
        if (st.equals("Dot") || st.equals("Cross + Dot")) dot(cx, cy, th + 0.5f, c);
        if (st.equals("Circle")) {
            Render2D.roundOutline(cx - s, cy - s, s * 2, s * 2, s, th, c);
        }
        if (st.equals("Cross") || st.equals("Cross + Dot") || st.equals("T")) {
            if (!st.equals("T")) bar(cx - th / 2, cy - g - s, th, s, c);
            bar(cx - th / 2, cy + g, th, s, c);
            bar(cx - g - s, cy - th / 2, s, th, c);
            bar(cx + g, cy - th / 2, s, th, c);
        }
    }
    private void bar(float x, float y, float w, float h, int c) {
        if (outline.get()) Render2D.rect(x - 0.5f, y - 0.5f, w + 1, h + 1, 0xA0000000);
        Render2D.rect(x, y, w, h, c);
    }
    private void dot(float cx, float cy, float r, int c) {
        if (outline.get()) Render2D.circle(cx, cy, r + 0.5f, 0xA0000000);
        Render2D.circle(cx, cy, r, c);
    }
}
