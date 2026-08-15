package com.vortex.client.hud;

import com.vortex.client.core.Errors;
import com.vortex.client.module.ModuleManager;
import com.vortex.client.module.modules.ArmorWarningModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

/**
 * Watches armour durability and says something before a piece goes.
 *
 * Only pieces that actually wear out are considered. Elytra, a pumpkin on your
 * head, anything without durability -- none of that can break, and warning
 * about it would be noise.
 */
public final class ArmorWarning {

    /** Which pieces have already had their sound, so it plays once each. */
    private static final java.util.Set<String> announced = new java.util.HashSet<>();

    private ArmorWarning() {}

    /** Percentage of durability left, or -1 for things that do not wear. */
    private static int percent(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return -1;
        if (!stack.isDamageable()) return -1;
        int max = stack.getMaxDamage();
        if (max <= 0) return -1;
        int left = max - stack.getDamage();
        return (int) Math.round(left * 100.0 / max);
    }

    public static void render(DrawContext ctx, MinecraftClient client) {
        try {
            ArmorWarningModule mod = ModuleManager.INSTANCE.get(ArmorWarningModule.class);
            if (mod == null || !mod.isEnabled()) return;
            if (client.player == null) return;

            int warnAt = mod.threshold.getInt();
            int critAt = mod.critical.getInt();

            record Piece(String name, int pct) {}
            java.util.List<Piece> low = new java.util.ArrayList<>();

            EquipmentSlot[] slots = {
                EquipmentSlot.HEAD, EquipmentSlot.CHEST,
                EquipmentSlot.LEGS, EquipmentSlot.FEET
            };
            String[] names = { "Helmet", "Chestplate", "Leggings", "Boots" };

            for (int i = 0; i < slots.length; i++) {
                int p = percent(client.player.getEquippedStack(slots[i]));
                if (p >= 0 && p <= warnAt) low.add(new Piece(names[i], p));
            }
            if (mod.includeHand.get()) {
                int p = percent(client.player.getMainHandStack());
                if (p >= 0 && p <= warnAt) low.add(new Piece("Held item", p));
            }

            // Nothing low: forget what was announced, so the sound plays again
            // once a fresh piece wears down.
            if (low.isEmpty()) {
                announced.clear();
                return;
            }

            int x = mod.x.getInt();
            int y = mod.y.getInt();
            float scale = mod.scale.getFloat();

            // Same scaling helper the other HUD elements use, so this behaves
            // identically in the editor and needs no second mechanism.
            HudRenderer.pushScale(ctx, x, y, scale);

            int line = 0;
            for (Piece p : low) {
                boolean crit = p.pct() <= critAt;
                int color = crit ? mod.critColor.get() : mod.warnColor.get();

                // Flashing is kept to the critical step. Something blinking all
                // the time stops registering as a warning.
                if (crit && mod.flash.get()) {
                    boolean on = (System.currentTimeMillis() / 350) % 2 == 0;
                    if (!on) color = (color & 0x00FFFFFF) | 0x60000000;
                }

                String text = p.name() + ": " + p.pct() + "%";
                ctx.drawTextWithShadow(client.textRenderer, Text.literal(text),
                        x, y + line * 10, color);
                line++;

                if (mod.sound.get() && announced.add(p.name())) {
                    ping(client, crit);
                }
            }
            HudRenderer.popScale(ctx);
        } catch (Throwable pvpErr) {
            Errors.report("ArmorWarning", pvpErr);
        }
    }

    /** A short note, once per piece. */
    private static void ping(MinecraftClient client, boolean critical) {
        try {
            if (client.player == null) return;
            client.player.playSound(
                    critical ? net.minecraft.sound.SoundEvents.BLOCK_ANVIL_LAND
                             : net.minecraft.sound.SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(),
                    0.5f, critical ? 0.8f : 1.6f);
        } catch (Throwable pvpErr) {
            Errors.report("ArmorWarning.sound", pvpErr);
        }
    }
}
