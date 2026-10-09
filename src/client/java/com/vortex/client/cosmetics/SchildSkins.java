package com.vortex.client.cosmetics;

import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
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

    private static final String MARKE = "vortex_shield_skin", FARBE = "vortex_shield_tint";
    private static final ThreadLocal<LivingEntity> AKTUELL = new ThreadLocal<>();

    /** Vor/nach dem Aufbereiten eines Items: wem gehoert es? */
    public static void besitzer(ItemOwner owner) {
        if (owner == null) { AKTUELL.remove(); return; }
        try {
            LivingEntity e = owner.asLivingEntity();
            if (e != null) { AKTUELL.set(e); return; }
        } catch (Throwable ignored) {
        }
        AKTUELL.remove();
    }

    private static String skinVon(LivingEntity e) {
        if (!(e instanceof Player p)) return null;
        String id = Cosmetics.fuer(p.getUUID()).schild();
        return id != null && !id.isEmpty() && Zubehoer.get(Zubehoer.Kategorie.SHIELD, id) != null ? id : null;
    }

    /** Daten des Schilds mit Skin- und Status-Marke (oder unveraendert). */
    public static DataComponentMap markieren(DataComponentMap daten, ItemStack stack) {
        LivingEntity e = AKTUELL.get();
        if (e == null || daten == null) return daten;
        String id = skinVon(e);
        int farbe = SchildStatus.farbe(e, stack);
        if (id == null && farbe == 0) return daten;
        CompoundTag tag = new CompoundTag();
        if (id != null) tag.putString(MARKE, id);
        if (farbe != 0) tag.putInt(FARBE, farbe);
        return DataComponentMap.builder().addAll(daten).set(DataComponents.CUSTOM_DATA, CustomData.of(tag)).build();
    }

    /** Bilder je animiertem Skin (Datei id_0.png ... id_{n-1}.png); sonst eine Datei id.png. */
    private static final java.util.Map<String, Integer> BILDER = new java.util.HashMap<>();

    private static int bilder(Zubehoer.Design d) {
        return BILDER.computeIfAbsent(d.id(), k -> {
            try { return Math.max(1, Integer.parseInt(d.werte().getOrDefault("frames", "1"))); } catch (Exception x) { return 1; }
        });
    }

    /** Textur fuer markierte Daten, sonst null (dann zeichnet Minecraft selbst). Animierte Skins wechseln das Bild. */
    public static Identifier textur(DataComponentMap daten) {
        if (daten == null) return null;
        CustomData cd = daten.get(DataComponents.CUSTOM_DATA);
        if (cd == null) return null;
        String id = cd.copyTag().getStringOr(MARKE, "");
        if (id.isEmpty() || !id.matches("[a-z0-9_]{1,40}")) return null;
        return texturFuer(id);
    }

    /** Textur eines Skins (animierte wechseln mit der Zeit das Bild); null = unbekannt. Auch fuer die Menue-Vorschau. */
    public static Identifier texturFuer(String id) {
        Zubehoer.Design d = Zubehoer.get(Zubehoer.Kategorie.SHIELD, id);
        if (d == null) return null;
        int n = bilder(d);
        if (n > 1) {
            long ms = net.minecraft.util.Util.getMillis();
            int ms_bild = 1000 / Math.max(1, (int) zahl(d.werte().get("fps"), 8));
            id = id + "_" + (int) ((ms / ms_bild) % n);
        }
        return Identifier.fromNamespaceAndPath("vortexclient", "textures/cosmetics/shield/" + id + ".png");
    }

    private static float zahl(String s, float d) {
        try { return s == null ? d : Float.parseFloat(s); } catch (NumberFormatException e) { return d; }
    }

    /** Shield-Status-Farbe (ARGB) oder -1 = keine Faerbung. */
    public static int farbe(DataComponentMap daten) {
        if (daten == null) return -1;
        CustomData cd = daten.get(DataComponents.CUSTOM_DATA);
        if (cd == null) return -1;
        int f = cd.copyTag().getIntOr(FARBE, 0);
        return f == 0 ? -1 : f;
    }
}
