package com.vortex.legacy.module.visual;

import com.vortex.legacy.core.Module;
import com.vortex.legacy.core.NumberSetting;

/** Grösse und Position des Items in der Hand (Ich-Ansicht). */
public class HandItemSize extends Module {
    public final NumberSetting scale = add(new NumberSetting("Scale", 0.8, 0.3, 1.5, 0.05));
    public final NumberSetting x = add(new NumberSetting("X Offset", 0, -0.5, 0.5, 0.02));
    public final NumberSetting y = add(new NumberSetting("Y Offset", 0, -0.5, 0.5, 0.02));
    public HandItemSize() { super("Hand Item Size", Category.VISUAL, "Make the item in your hand smaller or move it."); }
}
