package com.vortex.client.hud;

import com.vortex.client.module.ModuleManager;
import com.vortex.client.module.modules.CrystalMacroModule;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;

/**
 * Logik des Crystal Macros: Kristall setzen und sofort sprengen.
 *
 * Laeuft im Client-Tick. Schneller als ein Tick geht ohnehin nicht, weil sowohl
 * das Setzen als auch das Zerschlagen als Pakete an den Server gehen und dieser
 * nur zwanzigmal pro Sekunde arbeitet.
 *
 * Reihenfolge je Durchgang:
 *   - Fadenkreuz auf Obsidian/Grundgestein?
 *   - Kristalle im Hotbar? Falls ja und noetig: Platz wechseln
 *   - Kristall setzen
 *   - vorhandene Kristalle in Reichweite zerschlagen
 *   - auf den alten Platz zurueck
 */
public final class CrystalMacro {

    /** Tick-Zaehler fuer die eingestellte Pause. */
    private static int cooldown = 0;
    /** Platz, auf dem wir vor dem Wechsel standen (-1 = keiner gemerkt). */
    private static int previousSlot = -1;

    private CrystalMacro() {}

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            long pvpT0 = System.nanoTime();
            try {
            try {
                tick(client);
            } catch (Throwable pvpErr) {
                com.vortex.client.core.Errors.report("CrystalMacro", pvpErr);
            }
                    } finally {
                com.vortex.client.core.Profiler.record("CrystalMacro",
                        System.nanoTime() - pvpT0);
            }
        });
    }

    private static void tick(MinecraftClient client) {
        CrystalMacroModule mod = ModuleManager.INSTANCE.get(CrystalMacroModule.class);
        if (mod == null || !mod.isEnabled()) {
            restoreSlot(client);
            return;
        }
        if (client.player == null || client.world == null
                || client.interactionManager == null) {
            return;
        }
        // In Menues nichts tun.
        if (client.currentScreen != null) return;

        ClientPlayerEntity self = client.player;

        // Optional: nur waehrend die Angriffstaste gehalten wird.
        if (mod.onlyWhenHolding.get()
                && client.options != null
                && !client.options.attackKey.isPressed()) {
            restoreSlot(client);
            return;
        }

        if (cooldown > 0) {
            cooldown--;
            return;
        }

        // 1) Zuerst vorhandene Kristalle sprengen -- so entsteht Platz fuer den
        //    naechsten und der Schaden kommt schneller an.
        if (mod.breakThem.get()) {
            breakNearby(client, self, mod.breakRange.get());
        }

        // 2) Zielblock pruefen.
        HitResult hit = client.crosshairTarget;
        if (hit == null || hit.getType() != HitResult.Type.BLOCK) {
            restoreSlot(client);
            return;
        }
        if (!(hit instanceof BlockHitResult bhr)) {
            restoreSlot(client);
            return;
        }
        BlockPos pos = bhr.getBlockPos();
        var state = client.world.getBlockState(pos);
        boolean ok = state.isOf(Blocks.OBSIDIAN)
                || (mod.bedrock.get() && state.isOf(Blocks.BEDROCK));
        if (!ok) {
            restoreSlot(client);
            return;
        }

        // 3) Kristall in der Hand? Sonst passenden Platz suchen und wechseln.
        int slot = findCrystalSlot(self);
        if (slot < 0) {
            restoreSlot(client);
            return;   // keine Kristalle dabei
        }
        var inv = self.getInventory();
        int current = inv.getSelectedSlot();
        if (current != slot) {
            if (previousSlot < 0) previousSlot = current;
            inv.setSelectedSlot(slot);
        }

        // 4) Setzen. Der Server prueft Reichweite und Platz selbst.
        client.interactionManager.interactBlock(self, Hand.MAIN_HAND, bhr);
        self.swingHand(Hand.MAIN_HAND);

        // 5) Direkt danach nochmal sprengen -- der eben gesetzte Kristall ist
        //    im selben Tick schon da.
        if (mod.breakThem.get()) {
            breakNearby(client, self, mod.breakRange.get());
        }

        if (mod.switchBack.get()) restoreSlot(client);
        cooldown = mod.delay.getInt();
    }

    /** Zerschlaegt alle Kristalle in Reichweite. */
    private static void breakNearby(MinecraftClient client, ClientPlayerEntity self,
                                    double range) {
        double rangeSq = range * range;
        for (Entity e : client.world.getEntities()) {
            if (!(e instanceof EndCrystalEntity)) continue;
            if (!e.isAlive()) continue;
            if (self.squaredDistanceTo(e) > rangeSq) continue;
            client.interactionManager.attackEntity(self, e);
            self.swingHand(Hand.MAIN_HAND);
        }
    }

    /** Hotbar-Platz mit Enderkristallen, oder -1. */
    private static int findCrystalSlot(ClientPlayerEntity self) {
        var inv = self.getInventory();
        for (int i = 0; i < 9; i++) {
            ItemStack stack = inv.getStack(i);
            if (stack != null && !stack.isEmpty() && stack.isOf(Items.END_CRYSTAL)) {
                return i;
            }
        }
        return -1;
    }

    /** Zurueck auf den vorherigen Hotbar-Platz, falls gewechselt wurde. */
    private static void restoreSlot(MinecraftClient client) {
        if (previousSlot < 0) return;
        try {
            if (client.player != null) {
                client.player.getInventory().setSelectedSlot(previousSlot);
            }
        } catch (Throwable pvpErr) {
            com.vortex.client.core.Errors.report("CrystalMacro.restoreSlot", pvpErr);
        }
        previousSlot = -1;
    }
}
