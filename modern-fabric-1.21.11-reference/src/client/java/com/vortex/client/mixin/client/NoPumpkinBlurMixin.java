package com.vortex.client.mixin.client;

import com.vortex.client.module.ModuleManager;
import com.vortex.client.module.modules.NoPumpkinBlurModule;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * No Pumpkin Blur -- nach dem Vorbild von BactroMod.
 *
 * Mechanismus: Im InGameHud wird beim Rendern des Kuerbis-Overlays
 * getEquippedStack(HEAD) abgefragt. Wir leiten genau diesen Aufruf um:
 * Ist das No-Pumpkin-Blur-Modul an UND der Spieler traegt einen
 * geschnitzten Kuerbis, geben wir stattdessen ItemStack.EMPTY zurueck.
 * Dann denkt der Renderer "kein Kuerbis auf dem Kopf" und zeichnet das
 * verschwommene Overlay nicht.
 *
 * Ziel verifiziert aus BactroMod-Bytecode:
 *   class_329 (InGameHud) -> method_55798, Redirect auf
 *   class_746.method_6118 (getEquippedStack).
 */
@Mixin(net.minecraft.client.gui.hud.InGameHud.class)
public class NoPumpkinBlurMixin {

    @Redirect(
        method = "method_55798",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/class_746;method_6118(Lnet/minecraft/class_1304;)Lnet/minecraft/class_1799;"
        )
    )
    private ItemStack pvpclient$noPumpkinBlur(ClientPlayerEntity player, EquipmentSlot slot) {
        ItemStack real = player.getEquippedStack(slot);

        NoPumpkinBlurModule mod = find();
        if (mod != null && mod.isEnabled() && real.isOf(Items.CARVED_PUMPKIN)) {
            return ItemStack.EMPTY;
        }
        return real;
    }

    private static NoPumpkinBlurModule find() {
        // Konstante Laufzeit statt die ganze Modul-Liste zu durchlaufen --
        // diese Methode wird in Render-Pfaden sehr haeufig aufgerufen.
        return ModuleManager.INSTANCE.get(NoPumpkinBlurModule.class);
    }
}
