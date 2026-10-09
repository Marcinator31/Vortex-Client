package com.vortex.client.cosmetics;

import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.component.CustomData;

/**
 * Shield Skins (seit 4.29): ein Schild in der Hand eines Vortex-Spielers
 * bekommt dessen gewaehlte Textur (assets/vortexclient/textures/cosmetics/shield).
 *
 * Ablauf: Beim Aufbereiten eines Items (ItemModelResolver) merken wir uns,
 * wem es gehoert. Der Schild-Renderer haengt dann eine Marke an seine Daten,
 * und beim Zeichnen wird statt der Vanilla-Textur unsere genommen. Muster von
 * Bannern auf dem Schild bleiben dann unsichtbar -- der Skin ersetzt das Ganze.
 */
public final class SchildSkins {
    private SchildSkins() {}

    private static final String MARKE = "vortex_shield_skin";
    private static final ThreadLocal<String> AKTUELL = new ThreadLocal<>();

    /** Vor/nach dem Aufbereiten eines Items: wem gehoert es? */
    public static void besitzer(ItemOwner owner) {
        if (owner == null) { AKTUELL.remove(); return; }
        try {
            LivingEntity e = owner.asLivingEntity();
            if (e instanceof Player p) {
                String id = Cosmetics.fuer(p.getUUID()).schild();
                if (id != null && !id.isEmpty() && Zubehoer.get(Zubehoer.Kategorie.SHIELD, id) != null) { AKTUELL.set(id); return; }
            }
        } catch (Throwable ignored) {
        }
        AKTUELL.remove();
    }

    /** Daten des Schilds mit Skin-Marke (oder unveraendert). */
    public static DataComponentMap markieren(DataComponentMap daten) {
        String id = AKTUELL.get();
        if (id == null || daten == null) return daten;
        CompoundTag tag = new CompoundTag();
        tag.putString(MARKE, id);
        return DataComponentMap.builder().addAll(daten).set(DataComponents.CUSTOM_DATA, CustomData.of(tag)).build();
    }

    /** Textur fuer markierte Daten, sonst null (dann zeichnet Minecraft selbst). */
    public static Identifier textur(DataComponentMap daten) {
        if (daten == null) return null;
        CustomData cd = daten.get(DataComponents.CUSTOM_DATA);
        if (cd == null) return null;
        String id = cd.copyTag().getStringOr(MARKE, "");
        if (id.isEmpty() || !id.matches("[a-z0-9_]{1,40}")) return null;
        return Identifier.fromNamespaceAndPath("vortexclient", "textures/cosmetics/shield/" + id + ".png");
    }
}
