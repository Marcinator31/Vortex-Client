package com.vortex.client.module.modules;

import com.vortex.client.hud.ItemCounter;
import com.vortex.client.module.Module;

import java.util.ArrayList;
import java.util.List;

/**
 * Counts chosen items in your inventory.
 *
 * Holds any number of counters, each with its own items and its own place on
 * screen -- pearls in one corner, totems in another. The counters themselves
 * live in ItemCounter; this module only keeps them and saves them.
 */
public class ItemCounterModule extends Module {

    private final List<ItemCounter> counters = new ArrayList<>();

    public ItemCounterModule() {
        super("Item Counter", Category.HUD);
    }

    public List<ItemCounter> getCounters() {
        return counters;
    }

    public ItemCounter create(String name) {
        String n = (name == null || name.isBlank())
                ? "Counter " + (counters.size() + 1) : name.trim();
        ItemCounter c = new ItemCounter(n);
        // Stagger new ones a little, so a second counter does not land exactly
        // on top of the first and look like nothing happened.
        c.x.set(4 + counters.size() * 4);
        c.y.set(40 + counters.size() * 18);
        counters.add(c);
        return c;
    }

    public void remove(ItemCounter c) {
        counters.remove(c);
    }

    // ---- persistence ----

    public String serializeCounters() {
        StringBuilder sb = new StringBuilder();
        for (ItemCounter c : counters) {
            if (sb.length() > 0) sb.append(';');
            sb.append(c.serialize());
        }
        return sb.toString();
    }

    public void deserializeCounters(String data) {
        counters.clear();
        if (data == null || data.isEmpty()) return;
        for (String part : data.split(";")) {
            ItemCounter c = ItemCounter.deserialize(part);
            if (c != null) counters.add(c);
        }
    }
}
