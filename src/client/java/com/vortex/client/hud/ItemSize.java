package com.vortex.client.hud;

import com.vortex.client.module.ModuleManager;
import com.vortex.client.module.modules.ItemSizeModule;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.item.ItemEntity;

/** Groesse gedroppter Gegenstaende (Modul Item Size). */
public final class ItemSize {

    private ItemSize() {}

    public static boolean aktiv() {
        ItemSizeModule m = ModuleManager.INSTANCE.get(ItemSizeModule.class);
        return m != null && m.isEnabled();
    }

    /** Faktor fuer diesen Gegenstand; 1 = unveraendert. */
    public static float faktor(ItemEntityRenderState state) {
        ItemSizeModule m = ModuleManager.INSTANCE.get(ItemSizeModule.class);
        if (m == null || !m.isEnabled()) return 1f;
        float f = m.scale.getFloat();
        if (Math.abs(f - 1f) < 0.01f) return 1f;
        if (m.items.getIndex() == 1) {
            ItemEntity e = ItemPhysics.wesen(state);
            if (e == null || !wertvoll(e)) return 1f;
        }
        return f;
    }

    /** Wertvolles, das man nach einem Kampf sofort sehen will. */
    private static boolean wertvoll(ItemEntity e) {
        try {
            var id = BuiltInRegistries.ITEM.getKey(e.getItem().getItem());
            if (id == null) return false;
            String p = id.getPath();
            return p.contains("totem") || p.contains("golden_apple") || p.equals("ender_pearl")
                    || p.contains("potion") || p.startsWith("diamond") || p.startsWith("netherite")
                    || p.equals("elytra") || p.contains("shulker_box") || p.equals("end_crystal")
                    || p.equals("experience_bottle") || p.equals("mace") || p.equals("trident");
        } catch (Throwable t) {
            return false;
        }
    }
}
