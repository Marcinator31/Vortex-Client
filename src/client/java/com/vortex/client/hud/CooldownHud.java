package com.vortex.client.hud;

import com.vortex.client.module.ModuleManager;
import com.vortex.client.module.modules.CooldownHudModule;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemCooldowns;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Zeichnet das Cooldown HUD (siehe CooldownHudModule, ItemCooldownsHookMixin). */
public final class CooldownHud {

    private CooldownHud() {}

    private record Cd(long start, long end) {}

    private static final Map<Identifier, Cd> ACTIVE = new LinkedHashMap<>();
    private static int lastW = 110, lastH = 20;

    public static int lastW(float s) { return Math.round(lastW * s); }
    public static int lastH(float s) { return Math.round(lastH * s); }

    /** Nur die Abklingzeiten des eigenen Spielers (nicht die anderer Spieler im Einzelspieler-Server). */
    private static boolean mine(ItemCooldowns c) {
        var p = Minecraft.getInstance().player;
        return p != null && p.getCooldowns() == c;
    }

    public static synchronized void started(ItemCooldowns c, Identifier group, int ticks) {
        if (!mine(c) || group == null) return;
        long now = System.currentTimeMillis();
        if (ticks <= 0) { ACTIVE.remove(group); return; }
        ACTIVE.put(group, new Cd(now, now + ticks * 50L));
    }

    public static synchronized void removed(ItemCooldowns c, Identifier group) {
        if (mine(c)) ACTIVE.remove(group);
    }

    public static synchronized void render(GuiGraphicsExtractor ctx, Minecraft mc) {
        CooldownHudModule m = ModuleManager.INSTANCE.get(CooldownHudModule.class);
        if (m == null || !m.isEnabled() || mc.player == null) return;
        long now = System.currentTimeMillis();
        ACTIVE.values().removeIf(cd -> cd.end <= now);
        List<Map.Entry<Identifier, Cd>> list = new ArrayList<>(ACTIVE.entrySet());
        boolean editor = mc.gui.screen() instanceof com.vortex.client.gui.HudEditorScreen;
        if (list.isEmpty() && editor) {
            list.add(Map.entry(Identifier.withDefaultNamespace("ender_pearl"), new Cd(now - 400, now + 600)));
            list.add(Map.entry(Identifier.withDefaultNamespace("wind_charge"), new Cd(now - 100, now + 400)));
        }
        if (list.isEmpty()) return;

        int x0 = m.x.getInt(), y0 = m.y.getInt();
        boolean horiz = m.horizontal.get();
        int rowW = m.showName.get() ? 130 : 80, rowH = 20;
        int w = horiz ? list.size() * (44 + 4) - 4 : rowW;
        int h = horiz ? 30 : list.size() * (rowH + 2) - 2;
        lastW = w + 6; lastH = h + 6;
        HudRenderer.pushScale(ctx, x0, y0, m.scale.getFloat());
        try {
            if (m.background.get()) ctx.fill(x0, y0, x0 + w + 6, y0 + h + 6, 0x90101018);
            int i = 0;
            for (var e : list) {
                Cd cd = e.getValue();
                float left = Math.max(0f, Math.min(1f, (cd.end - now) / (float) Math.max(1, cd.end - cd.start)));
                String secs = String.format(Locale.ROOT, "%.1fs", (cd.end - now) / 1000f);
                ItemStack icon = iconOf(e.getKey());
                if (horiz) {
                    int bx = x0 + 3 + i * 48, by = y0 + 3;
                    if (!icon.isEmpty()) ctx.item(icon, bx + 14, by);
                    ctx.fill(bx, by + 19, bx + 44, by + 22, 0x60FFFFFF);
                    ctx.fill(bx, by + 19, bx + Math.round(44 * left), by + 22, m.color.at(left) | 0xFF000000);
                    if (m.showSeconds.get()) ctx.text(mc.font, Component.literal(secs), bx + 22 - mc.font.width(secs) / 2, by + 24, 0xFFFFFFFF, true);
                } else {
                    int bx = x0 + 3, by = y0 + 3 + i * (rowH + 2);
                    if (!icon.isEmpty()) ctx.item(icon, bx, by + 1);
                    int tx = bx + 20;
                    int barW = rowW - 22;
                    String label = m.showName.get() ? nameOf(e.getKey(), icon) : "";
                    String right = m.showSeconds.get() ? secs : "";
                    if (!label.isEmpty()) ctx.text(mc.font, Component.literal(label), tx, by + 2, 0xFFE6E6EC, true);
                    if (!right.isEmpty()) ctx.text(mc.font, Component.literal(right), bx + rowW - mc.font.width(right), by + 2, 0xFFFFFFFF, true);
                    int barY = by + (label.isEmpty() && right.isEmpty() ? 7 : 13);
                    ctx.fill(tx, barY, tx + barW, barY + 3, 0x60FFFFFF);
                    ctx.fill(tx, barY, tx + Math.round(barW * left), barY + 3, m.color.at(1f - left) | 0xFF000000);
                }
                i++;
            }
        } finally {
            HudRenderer.popScale(ctx);
        }
    }

    private static ItemStack iconOf(Identifier group) {
        try {
            var item = BuiltInRegistries.ITEM.getValue(group);
            if (item != null && item != Items.AIR) return new ItemStack(item);
        } catch (Throwable ignored) { }
        return ItemStack.EMPTY;
    }

    private static String nameOf(Identifier group, ItemStack icon) {
        if (!icon.isEmpty()) return icon.getHoverName().getString();
        String p = group.getPath().replace('_', ' ');
        return p.isEmpty() ? "?" : Character.toUpperCase(p.charAt(0)) + p.substring(1);
    }
}
