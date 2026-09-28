package com.vortex.client.social;

import com.vortex.client.gui.VortexStyle;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * Kleine Hinweise oben rechts: Freund online, Nachricht, Einladung ...
 *
 * Maximal vier gleichzeitig, der neueste oben. Sie gleiten herein und
 * verschwinden nach ein paar Sekunden (Einladungen und Nachrichten bleiben
 * laenger stehen).
 */
public final class SocialToasts {

    private SocialToasts() {}

    private static final class Toast {
        final String title, body;
        final boolean important;
        final long born = System.currentTimeMillis();
        Toast(String title, String body, boolean important) { this.title = title; this.body = body; this.important = important; }
        long life() { return important ? 9000 : 5000; }
    }

    private static final List<Toast> TOASTS = new ArrayList<>();

    public static void register() {
        HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS,
                Identifier.fromNamespaceAndPath("vortexclient", "friend_toasts"),
                (ctx, tick) -> render(ctx));
    }

    public static synchronized void push(String title, String body, boolean important) {
        TOASTS.add(0, new Toast(title, body == null ? "" : body, important));
        while (TOASTS.size() > 4) TOASTS.remove(TOASTS.size() - 1);
    }

    private static synchronized void render(GuiGraphicsExtractor ctx) {
        if (TOASTS.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        if (font == null) return;
        long now = System.currentTimeMillis();
        TOASTS.removeIf(t -> now - t.born > t.life());
        int sw = ctx.guiWidth();
        int y = 6;
        for (Toast t : TOASTS) {
            long age = now - t.born;
            float in = Math.min(1f, age / 180f);
            float out = Math.min(1f, (t.life() - age) / 400f);
            float a = Math.max(0f, Math.min(in, out));
            String body = cut(font, t.body, 196);
            int w = Math.max(font.width(t.title), font.width(body)) + 20;
            w = Math.max(120, Math.min(w, 220));
            int h = body.isEmpty() ? 20 : 30;
            int x = sw - 6 - (int) (w * (0.2f + 0.8f * ease(in)));
            int bg = VortexStyle.fade(0xF0120E1B, a);
            ctx.fill(x + 1, y, x + w - 1, y + h, bg);
            ctx.fill(x, y + 1, x + 1, y + h - 1, bg);
            ctx.fill(x + w - 1, y + 1, x + w, y + h - 1, bg);
            // Akzentleiste links im Vortex-Verlauf
            for (int i = 0; i < h; i += 2) {
                ctx.fill(x, y + i, x + 2, y + Math.min(i + 2, h), VortexStyle.fade(VortexStyle.akzent((float) i / h), a));
            }
            if (t.important) ctx.fill(x + 2, y, x + w - 1, y + 1, VortexStyle.fade(VortexStyle.VIOLETT, a));
            if (a > 0.05f) {
                ctx.text(font, Component.literal(cut(font, t.title, w - 14)), x + 8, y + 6, VortexStyle.fade(0xFFFFFFFF, a), false);
                if (!body.isEmpty()) ctx.text(font, Component.literal(body), x + 8, y + 17, VortexStyle.fade(0xFFB9B2CC, a), false);
            }
            y += h + 4;
        }
    }

    private static float ease(float t) { return 1f - (1f - t) * (1f - t); }

    static String cut(Font font, String s, int max) {
        if (font.width(s) <= max) return s;
        String c = s;
        while (c.length() > 1 && font.width(c + "..") > max) c = c.substring(0, c.length() - 1);
        return c + "..";
    }
}
