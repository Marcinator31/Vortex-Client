package com.vortex.client.hud;

import com.vortex.client.module.ModuleManager;
import com.vortex.client.module.modules.BetterTooltipsModule;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

/**
 * Better Tooltips (siehe BetterTooltipsModule).
 *
 * Die Zeilen kommen ueber das Fabric-Tooltip-Ereignis, NACHDEM das Spiel sie
 * gebaut hat. Verzauberungszeilen werden daran erkannt, dass ihr Text genau
 * dem Namen entspricht, den das Spiel fuer diese Verzauberung erzeugt -- so
 * bleibt alles andere (Lore, Attribute, fremde Mods) unberuehrt.
 */
public final class BetterTooltips {

    private BetterTooltips() {}

    private static final Map<String, String> KURZ = new HashMap<>();
    static {
        String[] p = {
                "protection", "Prot", "fire_protection", "Fire Prot", "feather_falling", "Feather",
                "blast_protection", "Blast", "projectile_protection", "Proj Prot", "respiration", "Resp",
                "aqua_affinity", "Aqua", "thorns", "Thorns", "depth_strider", "Depth", "frost_walker", "Frost",
                "binding_curse", "Binding", "soul_speed", "Soul", "swift_sneak", "Swift", "sharpness", "Sharp",
                "smite", "Smite", "bane_of_arthropods", "Bane", "knockback", "KB", "fire_aspect", "Fire",
                "looting", "Loot", "sweeping_edge", "Sweep", "efficiency", "Eff", "silk_touch", "Silk",
                "unbreaking", "Unb", "fortune", "Fort", "power", "Power", "punch", "Punch", "flame", "Flame",
                "infinity", "Inf", "luck_of_the_sea", "Luck", "lure", "Lure", "loyalty", "Loyal",
                "impaling", "Impale", "riptide", "Rip", "channeling", "Chan", "multishot", "Multi",
                "quick_charge", "QC", "piercing", "Pierce", "density", "Density", "breach", "Breach",
                "wind_burst", "Wind", "mending", "Mend", "vanishing_curse", "Vanish", "lunge", "Lunge"
        };
        for (int i = 0; i < p.length; i += 2) KURZ.put(p[i], p[i + 1]);
    }

    private static final String[] ROEMISCH = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};

    public static void register() {
        ItemTooltipCallback.EVENT.register((stack, ctx, flag, lines) -> {
            try {
                bearbeiten(stack, flag, lines);
            } catch (Throwable e) {
                com.vortex.client.core.Errors.report("BetterTooltips", e);
            }
        });
        ScreenEvents.AFTER_INIT.register((mc, screen, w, h) -> {
            if (!(screen instanceof AbstractContainerScreen<?>)) return;
            ScreenEvents.afterExtract(screen).register((s, ctx, mx, my, delta) -> {
                try {
                    karte(s, ctx, mx, my);
                } catch (Throwable e) {
                    com.vortex.client.core.Errors.report("BetterTooltips.map", e);
                }
            });
        });
    }

    private static BetterTooltipsModule an() {
        BetterTooltipsModule m = ModuleManager.INSTANCE.get(BetterTooltipsModule.class);
        return m != null && m.isEnabled() ? m : null;
    }

    private static void bearbeiten(ItemStack stack, TooltipFlag flag, List<Component> lines) {
        BetterTooltipsModule m = an();
        if (m == null || stack == null || stack.isEmpty() || lines.isEmpty()) return;

        if (m.compactEnchants.get()) {
            ItemEnchantments ench = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
            if (ench.isEmpty()) ench = stack.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
            if (!ench.isEmpty()) verzauberungen(m, ench, lines);
        }

        if (m.food.get()) {
            FoodProperties fp = stack.get(DataComponents.FOOD);
            if (fp != null) {
                lines.add(Math.min(1, lines.size()), Component.literal("Food +" + fp.nutrition()
                        + "  Saturation +" + String.format(Locale.ROOT, "%.1f", fp.saturation()))
                        .setStyle(Style.EMPTY.withColor(0xE8B04A)));
            }
        }

        if (m.durability.get() && stack.isDamageableItem() && !flag.isAdvanced()) {
            int max = stack.getMaxDamage();
            int rest = max - stack.getDamageValue();
            float anteil = max > 0 ? rest / (float) max : 1f;
            int farbe = anteil > 0.5f ? 0x55FF77 : anteil > 0.2f ? 0xFFD23F : 0xFF5050;
            lines.add(Component.literal("Durability: ").setStyle(Style.EMPTY.withColor(0xA0A0A8))
                    .append(Component.literal(rest + " / " + max).setStyle(Style.EMPTY.withColor(farbe))));
        }
    }

    private static void verzauberungen(BetterTooltipsModule m, ItemEnchantments ench, List<Component> lines) {
        Map<String, Holder<Enchantment>> namen = new HashMap<>();
        for (Holder<Enchantment> h : ench.keySet()) {
            namen.put(Enchantment.getFullname(h, ench.getLevel(h)).getString(), h);
        }
        List<Holder<Enchantment>> reihe = new ArrayList<>();
        int erste = -1;
        for (int i = 0; i < lines.size(); i++) {
            Holder<Enchantment> h = namen.get(lines.get(i).getString());
            if (h == null || reihe.contains(h)) continue;
            reihe.add(h);
            if (erste < 0) erste = i;
            lines.remove(i);
            i--;
        }
        if (reihe.isEmpty()) return;

        List<Component> neu = new ArrayList<>();
        MutableComponent zeile = Component.empty();
        int zeichen = 0, inZeile = 0;
        for (Holder<Enchantment> h : reihe) {
            int stufe = ench.getLevel(h);
            String text;
            String id = h.unwrapKey().map(k -> k.identifier().getPath()).orElse("");
            if (m.shortNames.get() && KURZ.containsKey(id)) {
                text = KURZ.get(id) + (h.value().getMaxLevel() > 1 || stufe > 1 ? " " + roemisch(stufe) : "");
            } else {
                text = Enchantment.getFullname(h, stufe).getString();
            }
            boolean fluch = h.is(EnchantmentTags.CURSE);
            if (inZeile > 0 && zeichen + text.length() > 34) {
                neu.add(zeile);
                zeile = Component.empty();
                zeichen = 0;
                inZeile = 0;
            }
            if (inZeile > 0) zeile.append(Component.literal(", ").setStyle(Style.EMPTY.withColor(0x707078)));
            zeile.append(Component.literal(text).setStyle(Style.EMPTY.withColor(fluch ? 0xFF5555 : 0xB9B9FF)));
            zeichen += text.length() + 2;
            inZeile++;
        }
        if (inZeile > 0) neu.add(zeile);
        lines.addAll(Math.min(erste, lines.size()), neu);
    }

    private static String roemisch(int n) {
        return n >= 0 && n < ROEMISCH.length ? ROEMISCH[n] : String.valueOf(n);
    }

    // ------------------------------------------------------------------
    // Karten-Vorschau

    private static void karte(net.minecraft.client.gui.screens.Screen s, GuiGraphicsExtractor ctx, int mx, int my) {
        BetterTooltipsModule m = an();
        if (m == null || !m.mapPreview.get()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Slot slot = ((com.vortex.client.mixin.client.TooltipSlotAccessor) s).vortex$hoveredSlot();
        if (slot == null || !slot.hasItem()) return;
        ItemStack st = slot.getItem();
        if (!st.is(Items.FILLED_MAP)) return;
        MapId id = st.get(DataComponents.MAP_ID);
        MapItemSavedData data = MapItem.getSavedData(st, mc.level);
        if (id == null || data == null) return;
        Identifier tex = mc.getMapTextureManager().prepareMapTexture(id, data);
        int g = m.mapSize.getInt();
        int x = mx - g - 16;
        if (x < 4) x = mx + 16;
        int y = Math.max(4, Math.min(my - g / 2, ctx.guiHeight() - g - 4));
        ctx.fill(x - 3, y - 3, x + g + 3, y + g + 3, 0xF0201810);
        ctx.fill(x - 2, y - 2, x + g + 2, y + g + 2, 0xFFD9C8A0);
        ctx.blit(RenderPipelines.GUI_TEXTURED, tex, x, y, 0f, 0f, g, g, g, g);
    }
}
