package com.vortex.client.hud;

import com.vortex.client.module.Module;
import com.vortex.client.module.ModuleManager;
import com.vortex.client.module.modules.AutoTotemModule;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;

/**
 * Logik fuer Auto Totem: sobald die Off-Hand leer ist und ein Totem im Inventar
 * liegt, wird es per Slot-Klick in die Off-Hand gelegt.
 *
 * Slot-Mechanik im Spieler-Inventar (PlayerScreenHandler):
 *   - Off-Hand-Slot = 45
 *   - Inventar-Klicks laufen ueber interactionManager.clickSlot mit syncId 0.
 *
 * Wir nutzen PICKUP (Links-Klick) in zwei Schritten: Totem-Slot anklicken
 * (Totem auf den Cursor), Off-Hand-Slot anklicken (Totem ablegen), und -- falls
 * danach noch etwas am Cursor haengt -- zurueck auf den Ursprungsslot legen. Das
 * funktioniert auch bei geschlossenem Inventar.
 *
 * Ein kleiner Cooldown verhindert, dass bei einem kurzzeitig leeren Slot mehrere
 * Aktionen pro Tick abgefeuert werden.
 */
public final class AutoTotem {

    private static final int OFFHAND_SLOT = 45;
    private static long lastActionTick = 0;
    private static long tickCounter = 0;

    private AutoTotem() {}

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            long pvpT0 = System.nanoTime();
            try {
            tickCounter++;
            AutoTotemModule mod = (AutoTotemModule) find(AutoTotemModule.class);
            if (mod == null || !mod.isEnabled()) return;
            if (client.player == null || client.interactionManager == null) return;

            ClientPlayerEntity player = client.player;

            // Nur handeln, wenn die Off-Hand wirklich leer ist.
            if (!player.getOffHandStack().isEmpty()) return;

            // Kleiner Cooldown (ein paar Ticks), damit nicht mehrfach geklickt
            // wird, waehrend der Wechsel noch synchronisiert.
            if (tickCounter - lastActionTick < 3) return;

            // Ein Totem im Inventar suchen (Hauptinventar + Hotbar).
            int totemSlot = findTotemInventorySlot(player);
            if (totemSlot < 0) return; // kein Totem vorhanden -> nichts tun

            try {
                int syncId = player.playerScreenHandler.syncId;
                // Inventar-Index -> Screen-Slot-Nummer umrechnen.
                int screenSlot = inventoryIndexToScreenSlot(totemSlot);
                if (screenSlot < 0) return;

                // 1. Totem aufnehmen (auf den Cursor).
                client.interactionManager.clickSlot(
                        syncId, screenSlot, 0, SlotActionType.PICKUP, player);
                // 2. In die Off-Hand legen.
                client.interactionManager.clickSlot(
                        syncId, OFFHAND_SLOT, 0, SlotActionType.PICKUP, player);
                // 3. Falls noch etwas am Cursor ist (Off-Hand war doch belegt),
                //    zurueck auf den Ursprungsslot legen.
                if (!player.playerScreenHandler.getCursorStack().isEmpty()) {
                    client.interactionManager.clickSlot(
                            syncId, screenSlot, 0, SlotActionType.PICKUP, player);
                }

                lastActionTick = tickCounter;
            } catch (Throwable pvpErr) {
                com.vortex.client.core.Errors.report("AutoTotem", pvpErr);
            }
                    } finally {
                com.vortex.client.core.Profiler.record("AutoTotem",
                        System.nanoTime() - pvpT0);
            }
        });
    }

    /**
     * Sucht einen Totem-Stack im Spieler-Inventar und gibt den Inventar-Index
     * (0..35) zurueck, oder -1 wenn keiner da ist.
     */
    private static int findTotemInventorySlot(ClientPlayerEntity player) {
        // getInventory().size() deckt Hauptinventar + Hotbar + Ruestung + Off-Hand
        // ab; wir interessieren uns fuer die normalen Slots 0..35.
        int size = Math.min(player.getInventory().size(), 36);
        for (int i = 0; i < size; i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (!stack.isEmpty() && stack.isOf(Items.TOTEM_OF_UNDYING)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Rechnet einen PlayerInventory-Index (0..35) in die Slot-Nummer des
     * PlayerScreenHandler um.
     *
     * PlayerScreenHandler-Layout:
     *   Slot 9..35  = Hauptinventar (Inventar-Index 9..35)
     *   Slot 36..44 = Hotbar        (Inventar-Index 0..8)
     */
    private static int inventoryIndexToScreenSlot(int invIndex) {
        if (invIndex >= 0 && invIndex <= 8) {
            return 36 + invIndex;      // Hotbar
        } else if (invIndex >= 9 && invIndex <= 35) {
            return invIndex;           // Hauptinventar (gleiche Nummer)
        }
        return -1;
    }

    private static Module find(Class<? extends Module> type) {
        // Konstante Laufzeit statt die ganze Liste zu durchlaufen.
        return ModuleManager.INSTANCE.get(type);
    }
}
