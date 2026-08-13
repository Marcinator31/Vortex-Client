package com.example.pvpclient.module.modules;

import com.example.pvpclient.core.setting.ColorSetting;
import com.example.pvpclient.core.setting.NumberSetting;
import com.example.pvpclient.hud.HudElement;
import com.example.pvpclient.module.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

/**
 * Totem-Counter: zeigt an, wie viele Totems der Unsterblichkeit man gerade
 * im Inventar hat -- mit einem Totem-Icon daneben. Praktisch im PvP, um auf
 * einen Blick zu sehen, wie viele "Leben" man noch hat.
 *
 * Das eigentliche Icon + die Zahl werden im HudRenderer gezeichnet; dieses
 * Modul liefert nur die Einstellungen (Position, Farbe, Skalierung) und die
 * Zaehl-Logik.
 *
 * Das Totem-Item wird ueber die Registry geholt (minecraft:totem_of_undying),
 * nicht ueber Items.TOTEM_OF_UNDYING -- das ist robuster, weil der direkte
 * Feldname in manchen Mappings nicht verfuegbar ist.
 */
public class TotemCountModule extends Module implements HudElement {

    public final NumberSetting x = new NumberSetting("X", 4, 0, 1920, 1);
    public final NumberSetting y = new NumberSetting("Y", 60, 0, 1080, 1);
    public final ColorSetting color = new ColorSetting("Textfarbe", 0xFFFFFFFF);
    public final NumberSetting scale = new NumberSetting("Skalierung", 1.0, 0.5, 3.0, 0.1);

    // Das Totem-Item, einmalig per Registry aufgeloest (kann null sein, falls
    // die Registry es nicht kennt -- dann zaehlen wir einfach 0).
    private static Item totemItem;

    public TotemCountModule() {
        super("Totem Counter", Category.HUD);
        enabledByDefault();
        addSetting(x);
        addSetting(y);
        addSetting(color);
        addSetting(scale);
    }

    /** Liefert das Totem-Item (lazy, einmalig aus der Registry). */
    public static Item totem() {
        if (totemItem == null) {
            totemItem = Registries.ITEM.get(Identifier.ofVanilla("totem_of_undying"));
        }
        return totemItem;
    }

    /**
     * Zaehlt alle Totems im Inventar des Spielers (Hotbar, Hauptinventar,
     * Offhand -- das gesamte PlayerInventory). Gibt 0 zurueck, wenn kein
     * Spieler da ist.
     */
    public static int countTotems() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return 0;

        Item totem = totem();
        if (totem == null) return 0;

        int count = 0;
        var inv = client.player.getInventory();
        for (int i = 0; i < inv.size(); i++) {
            ItemStack stack = inv.getStack(i);
            // isEmpty()-Pruefung zuerst: leere Slots nie mitzaehlen (falls die
            // Registry wider Erwarten das Default-Item liefern wuerde).
            if (stack != null && !stack.isEmpty() && stack.isOf(totem)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    @Override public String hudName() { return "Totem"; }
    @Override public NumberSetting hudX() { return x; }
    @Override public NumberSetting hudY() { return y; }
    @Override public NumberSetting hudScale() { return scale; }
    @Override public ColorSetting hudColor() { return color; }
    // Breite etwas groesser: Icon (16) + Abstand + Zahl.
    @Override public int hudWidth() { return 40; }
    @Override public int hudHeight() { return 16; }
}
