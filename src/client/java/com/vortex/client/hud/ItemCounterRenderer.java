package com.vortex.client.hud;

import com.vortex.client.core.Errors;
import com.vortex.client.module.ModuleManager;
import com.vortex.client.module.modules.ItemCounterModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * Draws the item counters.
 *
 * Counting walks the whole inventory rather than only the hotbar: what matters
 * is how many pearls you have, not how many are within reach right now.
 */
public final class ItemCounterRenderer {

    private ItemCounterRenderer() {}

    /** How many of the chosen items are carried in total. */
    public static int count(MinecraftClient client, ItemCounter counter) {
        if (client.player == null || counter.items.isEmpty()) return 0;
        int total = 0;
        var inv = client.player.getInventory();
        for (int i = 0; i < inv.size(); i++) {
            ItemStack stack = inv.getStack(i);
            if (stack == null || stack.isEmpty()) continue;
            Identifier id = Registries.ITEM.getId(stack.getItem());
            if (id != null && counter.items.contains(id.toString())) {
                total += stack.getCount();
            }
        }
        // The off hand sits outside the main inventory in some versions, so it
        // is added separately rather than assumed to be in there.
        ItemStack off = client.player.getOffHandStack();
        if (off != null && !off.isEmpty()) {
            Identifier id = Registries.ITEM.getId(off.getItem());
            if (id != null && counter.items.contains(id.toString())) {
                total += off.getCount();
            }
        }
        return total;
    }

    public static void render(DrawContext ctx, MinecraftClient client) {
        try {
            ItemCounterModule mod = ModuleManager.INSTANCE.get(ItemCounterModule.class);
            if (mod == null || !mod.isEnabled()) return;
            if (client.player == null) return;

            for (ItemCounter c : mod.getCounters()) {
                int n = count(client, c);
                if (n == 0 && c.hideEmpty.get()) continue;

                int color = c.color.get();
                int low = c.lowAt.getInt();
                if (low > 0 && n < low) color = 0xFFFF5555;

                int x = c.x.getInt();
                int y = c.y.getInt();
                HudRenderer.pushScale(ctx, x, y, c.scale.getFloat());

                switch (c.style.getIndex()) {
                    case 1:
                        ctx.drawTextWithShadow(client.textRenderer,
                                Text.literal(String.valueOf(n)), x, y, color);
                        break;

                    case 2: {
                        String text = c.name + ": " + n;
                        ctx.drawTextWithShadow(client.textRenderer,
                                Text.literal(text), x, y, color);
                        break;
                    }

                    default: {
                        // Icon of the first chosen item, then the number. The
                        // first is enough: a counter that adds several items
                        // together is about the total, and one icon says which
                        // group it is without a row of pictures.
                        ItemStack icon = firstIcon(c);
                        if (icon != null && !icon.isEmpty()) {
                            ctx.drawItem(icon, x, y - 4);
                            ctx.drawTextWithShadow(client.textRenderer,
                                    Text.literal(String.valueOf(n)), x + 20, y, color);
                        } else {
                            ctx.drawTextWithShadow(client.textRenderer,
                                    Text.literal(String.valueOf(n)), x, y, color);
                        }
                        break;
                    }
                }
                HudRenderer.popScale(ctx);
            }
        } catch (Throwable pvpErr) {
            Errors.report("ItemCounterRenderer", pvpErr);
        }
    }

    /** A stack of the first chosen item, for the icon. */
    private static ItemStack firstIcon(ItemCounter c) {
        for (String id : c.items) {
            try {
                var item = Registries.ITEM.get(Identifier.of(id));
                if (item != null) return new ItemStack(item);
            } catch (Throwable ignored) {
                // Unknown id -- try the next one.
            }
        }
        return null;
    }
}
