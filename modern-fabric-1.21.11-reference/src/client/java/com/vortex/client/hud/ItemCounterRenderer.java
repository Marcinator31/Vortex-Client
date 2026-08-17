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

    /**
     * Counts, refreshed a few times a second.
     *
     * Counting walks the whole inventory, and it was doing that for every
     * counter on every frame. At four hundred frames a second with three
     * counters that is over forty thousand slot reads a second to produce a
     * number that changes when you pick something up.
     */
    private static final java.util.Map<ItemCounter, Cached> COUNTS =
            new java.util.HashMap<>();

    /**
     * Everything the draw loop needs, built once per 200 ms window instead of
     * per frame. Before this, EVERY FRAME did per counter: an Identifier.of
     * string parse plus a registry lookup plus a new ItemStack (firstIcon),
     * and one or two Text.literal allocations -- for values that only change
     * when the recount runs anyway. Plain class rather than a record so the
     * javalang session check keeps parsing this file.
     */
    private static final class Cached {
        final int n;
        final ItemStack icon;      // may be null
        final Text numText;        // "42"
        final Text nameText;       // "Pearls: 42"
        Cached(int n, ItemStack icon, String name) {
            this.n = n;
            this.icon = icon;
            this.numText = Text.literal(String.valueOf(n));
            this.nameText = Text.literal(name + ": " + n);
        }
    }

    /** When the counts were last worked out. */
    private static long counted = 0L;

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
        long pvpT0 = System.nanoTime();
        try {
            ItemCounterModule mod = ModuleManager.INSTANCE.get(ItemCounterModule.class);
            if (mod == null || !mod.isEnabled()) return;
            if (client.player == null) return;

            // Recount a few times a second, then draw from that. The icon and
            // the Text objects are rebuilt here too -- the 200 ms staleness
            // after editing a counter in the GUI is imperceptible.
            long now = System.currentTimeMillis();
            if (now - counted > 200) {
                counted = now;
                COUNTS.clear();
                for (ItemCounter c : mod.getCounters()) {
                    COUNTS.put(c, new Cached(count(client, c), firstIcon(c), c.name));
                }
            }

            for (ItemCounter c : mod.getCounters()) {
                Cached cd = COUNTS.get(c);
                if (cd == null) continue;
                int n = cd.n;
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
                                cd.numText, x, y, color);
                        break;

                    case 2: {
                        ctx.drawTextWithShadow(client.textRenderer,
                                cd.nameText, x, y, color);
                        break;
                    }

                    default: {
                        // Icon of the first chosen item, then the number. The
                        // first is enough: a counter that adds several items
                        // together is about the total, and one icon says which
                        // group it is without a row of pictures.
                        // Icon and texts come prebuilt from the cache.
                        if (cd.icon != null && !cd.icon.isEmpty()) {
                            ctx.drawItem(cd.icon, x, y - 4);
                            ctx.drawTextWithShadow(client.textRenderer,
                                    cd.numText, x + 20, y, color);
                        } else {
                            ctx.drawTextWithShadow(client.textRenderer,
                                    cd.numText, x, y, color);
                        }
                        break;
                    }
                }
                HudRenderer.popScale(ctx);
            }
        } catch (Throwable pvpErr) {
            Errors.report("ItemCounterRenderer", pvpErr);
        } finally {
            // Note for /lag readers: this is a SUBSET of the "HUD" section --
            // HudRenderer wraps all HUD parts, this line attributes ours.
            com.vortex.client.core.Profiler.record("ItemCounter",
                    System.nanoTime() - pvpT0);
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
