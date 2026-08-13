package com.example.pvpclient.module.modules;

import com.example.pvpclient.core.setting.NumberSetting;
import com.example.pvpclient.module.Module;

/**
 * Item-Groesse (Hand): macht das gehaltene Item in der ersten Person kleiner
 * (Haupt- und Nebenhand). Praktisch, wenn das Item zu viel vom Bildschirm
 * verdeckt.
 *
 * Die "Groesse" ist ein Faktor: 1.0 = normal, 0.5 = halb so gross.
 *
 * Wirkung im HeldItemRendererMixin (skaliert den MatrixStack vor dem Rendern).
 */
public class HandItemScaleModule extends Module {

    public final NumberSetting size = new NumberSetting("Groesse", 0.7, 0.3, 1.0, 0.05);

    public HandItemScaleModule() {
        super("Item-Groesse Hand", Category.MISC);
        addSetting(size);
    }
}
