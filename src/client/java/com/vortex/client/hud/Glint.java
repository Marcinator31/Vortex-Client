package com.vortex.client.hud;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.vortex.client.module.ModuleManager;
import com.vortex.client.module.modules.GlintModule;
import java.io.InputStream;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.SimpleTexture;
import net.minecraft.resources.Identifier;

/**
 * Glint Customizer (siehe GlintModule).
 *
 * Der Glanz ist eine Textur, die der Glint-Shader ueber das Item legt
 * (glint.fsh: Farbe = Textur * ColorModulator). Wir ersetzen die beiden
 * Glint-Texturen durch eingefaerbte Kopien: Helligkeit des Originals mal
 * gewaehlte Farbe. Wiederholend und weich gefiltert wie das Original.
 */
public final class Glint {

    private Glint() {}

    private static final Identifier[] IDS = {
            Identifier.withDefaultNamespace("textures/misc/enchanted_glint_item.png"),
            Identifier.withDefaultNamespace("textures/misc/enchanted_glint_armor.png")
    };

    private static final class Tinted extends DynamicTexture {
        final int[] original;
        final int w, h;
        Tinted(NativeImage img) {
            super(() -> "vortex_glint", img);
            this.w = img.getWidth();
            this.h = img.getHeight();
            this.original = img.getPixels().clone();
            this.sampler = RenderSystem.getSamplerCache().getRepeat(FilterMode.LINEAR);
        }
        void tint(int rgb) {
            NativeImage img = getPixels();
            if (img == null) return;
            float cr = ((rgb >> 16) & 0xFF) / 255f, cg = ((rgb >> 8) & 0xFF) / 255f, cb = (rgb & 0xFF) / 255f;
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int p = original[y * w + x];
                    int a = (p >>> 24) & 0xFF;
                    float l = Math.max((p >> 16) & 0xFF, Math.max((p >> 8) & 0xFF, p & 0xFF)) / 255f * 1.25f;
                    int r = Math.min(255, Math.round(cr * l * 255)), g = Math.min(255, Math.round(cg * l * 255)), b = Math.min(255, Math.round(cb * l * 255));
                    img.setPixel(x, y, (a << 24) | (r << 16) | (g << 8) | b);
                }
            }
            upload();
        }
    }

    private static Tinted[] active = null;
    private static int lastColor = 0;
    private static long lastUpdate = 0;
    private static Double oldSpeed = null, oldStrength = null;

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(Glint::tick);
    }

    private static void tick(Minecraft mc) {
        GlintModule m = ModuleManager.INSTANCE.get(GlintModule.class);
        if (m == null || !m.isEnabled()) { if (active != null) restore(); return; }
        try {
            if (active == null) install(mc);
            int c = m.color.get() & 0xFFFFFF;
            long now = System.currentTimeMillis();
            if (active != null && c != lastColor && now - lastUpdate >= 80) {
                lastColor = c;
                lastUpdate = now;
                for (Tinted t : active) if (t != null) t.tint(c);
            }
            // Tempo und Staerke (Vanilla-Optionen, 0..1)
            if (oldSpeed == null) { oldSpeed = mc.options.glintSpeed().get(); oldStrength = mc.options.glintStrength().get(); }
            double sp = m.speed.get() / 100.0, st = m.strength.get() / 100.0;
            if (Math.abs(mc.options.glintSpeed().get() - sp) > 1e-3) mc.options.glintSpeed().set(sp);
            if (Math.abs(mc.options.glintStrength().get() - st) > 1e-3) mc.options.glintStrength().set(st);
        } catch (Throwable e) {
            com.vortex.client.core.Errors.report("Glint", e);
        }
    }

    private static void install(Minecraft mc) throws Exception {
        Tinted[] t = new Tinted[IDS.length];
        for (int i = 0; i < IDS.length; i++) {
            var res = mc.getResourceManager().getResource(IDS[i]);
            if (res.isEmpty()) continue;
            try (InputStream in = res.get().open()) {
                t[i] = new Tinted(NativeImage.read(in));
            }
            mc.getTextureManager().register(IDS[i], t[i]);
        }
        active = t;
        lastColor = -1;
    }

    /** Originale zurueck (auch Tempo/Staerke). */
    public static void restore() {
        Minecraft mc = Minecraft.getInstance();
        try {
            if (active != null) {
                for (Identifier id : IDS) mc.getTextureManager().registerAndLoad(id, new SimpleTexture(id));
            }
            if (oldSpeed != null) mc.options.glintSpeed().set(oldSpeed);
            if (oldStrength != null) mc.options.glintStrength().set(oldStrength);
        } catch (Throwable e) {
            com.vortex.client.core.Errors.report("Glint.restore", e);
        }
        active = null;
        oldSpeed = null;
        oldStrength = null;
    }
}
