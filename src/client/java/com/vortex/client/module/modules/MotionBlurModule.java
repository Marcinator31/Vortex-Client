package com.vortex.client.module.modules;

import com.vortex.client.core.setting.ModeSetting;
import com.vortex.client.module.Module;
import com.vortex.client.module.ModuleManager;
import net.minecraft.resources.Identifier;

/**
 * Motion Blur: Bewegungsunschaerfe wie bei Lunar -- das neue Bild wird mit
 * dem vorigen gemischt. Nur die Welt, HUD und Menues bleiben scharf.
 *
 * Kostet etwas Leistung (drei zusaetzliche Durchgaenge pro Bild) und wirkt bei
 * hohen FPS schwaecher als bei niedrigen -- so funktioniert diese Art Blur.
 */
public class MotionBlurModule extends Module {

    public final ModeSetting strength = new ModeSetting("Strength", 1, "Low", "Medium", "High", "Extreme");

    private static final Identifier[] IDS = {
            Identifier.fromNamespaceAndPath("vortexclient", "motion_blur_1"),
            Identifier.fromNamespaceAndPath("vortexclient", "motion_blur_2"),
            Identifier.fromNamespaceAndPath("vortexclient", "motion_blur_3"),
            Identifier.fromNamespaceAndPath("vortexclient", "motion_blur_4")
    };

    public MotionBlurModule() {
        super("Motion Blur", Category.MISC);
        addSetting(strength);
    }

    /** Welcher Effekt gerade gilt, oder null (aus / keine Welt). */
    public static Identifier activeEffect() {
        MotionBlurModule m = ModuleManager.INSTANCE.get(MotionBlurModule.class);
        if (m == null || !m.isEnabled() || net.minecraft.client.Minecraft.getInstance().level == null) return null;
        return IDS[Math.max(0, Math.min(3, m.strength.getIndex()))];
    }
}
