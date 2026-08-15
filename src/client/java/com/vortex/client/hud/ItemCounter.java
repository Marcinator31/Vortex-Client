package com.vortex.client.hud;

import com.vortex.client.core.setting.BooleanSetting;
import com.vortex.client.core.setting.ColorSetting;
import com.vortex.client.core.setting.ModeSetting;
import com.vortex.client.core.setting.NumberSetting;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * One item counter on the HUD.
 *
 * A counter is its own thing rather than a setting inside the module, because
 * you want several: pearls in one corner, totems in another, blocks somewhere
 * else. Each carries its own position, colour and item list, and each behaves
 * in the HUD editor exactly like the built-in elements -- it is a HudElement,
 * so the editor picks it up without knowing anything about counters.
 */
public final class ItemCounter implements HudElement {

    /** Shown in the editor and in the manager. */
    public String name;

    /**
     * Items counted, by id.
     *
     * A set, and several of them: a counter for "blocks" wants cobblestone and
     * deepslate together, and one for healing wants gapples of both kinds. The
     * total is what matters, not which of them it came from.
     */
    public final Set<String> items = new LinkedHashSet<>();

    public final NumberSetting x = new NumberSetting("X", 4, 0, 1920, 1);
    public final NumberSetting y = new NumberSetting("Y", 4, 0, 1080, 1);
    public final NumberSetting scale = new NumberSetting("Scale", 1.0, 0.5, 3.0, 0.1);
    public final ColorSetting color = new ColorSetting("Colour", 0xFFFFFFFF);

    /** How the count is shown. */
    public final ModeSetting style = new ModeSetting("Style", 0,
            "Icon + count", "Count only", "Name + count");

    /** Hide the counter entirely while the count is zero. */
    public final BooleanSetting hideEmpty = new BooleanSetting("Hide at Zero", false);

    /** Turn the number red below a threshold. */
    public final NumberSetting lowAt = new NumberSetting("Low Below", 0, 0, 999, 1);

    public ItemCounter(String name) {
        this.name = name;
    }

    // ---- HudElement ----

    @Override public String hudName() { return name; }
    @Override public NumberSetting hudX() { return x; }
    @Override public NumberSetting hudY() { return y; }
    @Override public NumberSetting hudScale() { return scale; }
    @Override public ColorSetting hudColor() { return color; }

    @Override
    public int hudWidth() {
        // Roughly: an icon plus a few digits, or the name in front of them.
        return (style.getIndex() == 2) ? 90 : 40;
    }

    @Override
    public int hudHeight() {
        return 16;
    }

    // ---- text form ----

    /**
     * One counter as text.
     *
     * Separators are escaped, so a name may contain anything without tearing
     * the line apart -- the same mistake that once scrambled the waypoints.
     */
    public String serialize() {
        StringBuilder sb = new StringBuilder();
        sb.append(esc(name)).append('|')
          .append(String.join(",", items)).append('|')
          .append(x.getInt()).append('|')
          .append(y.getInt()).append('|')
          .append(scale.get()).append('|')
          .append(color.get()).append('|')
          .append(style.serialize()).append('|')
          .append(hideEmpty.get() ? '1' : '0').append('|')
          .append(lowAt.getInt());
        return sb.toString();
    }

    public static ItemCounter deserialize(String data) {
        if (data == null || data.isEmpty()) return null;
        try {
            String[] p = data.split("\\|");
            if (p.length < 6) return null;
            ItemCounter c = new ItemCounter(unesc(p[0]));
            if (!p[1].isEmpty()) {
                for (String id : p[1].split(",")) {
                    String t = id.trim();
                    if (!t.isEmpty()) c.items.add(t);
                }
            }
            c.x.set(Integer.parseInt(p[2].trim()));
            c.y.set(Integer.parseInt(p[3].trim()));
            c.scale.set(Double.parseDouble(p[4].trim()));
            c.color.set(Integer.parseInt(p[5].trim()));
            if (p.length > 6) {
                // ModeSetting takes the option by name, not by number. Its own
                // deserialize does exactly that job, so it is used here rather
                // than reaching past it.
                c.style.deserialize(p[6].trim());
            }
            if (p.length > 7) c.hideEmpty.set("1".equals(p[7].trim()));
            if (p.length > 8) c.lowAt.set(Integer.parseInt(p[8].trim()));
            return c;
        } catch (Throwable pvpErr) {
            com.vortex.client.core.Errors.report("ItemCounter.deserialize", pvpErr);
            return null;
        }
    }

    private static String esc(String v) {
        if (v == null) return "";
        return v.replace("%", "%25").replace("|", "%7C")
                .replace(";", "%3B").replace(",", "%2C");
    }

    private static String unesc(String v) {
        if (v == null) return "";
        return v.replace("%7C", "|").replace("%2C", ",")
                .replace("%3B", ";").replace("%25", "%");
    }
}
