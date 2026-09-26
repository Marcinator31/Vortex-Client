package com.vortex.client.mixin.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Zeichnet unser Fadenkreuz statt des normalen.
 *
 * HIER LAG DER FEHLER DES ALTEN MODULS: Es zielte auf Gui.renderCrosshair.
 * In 26.2 heisst die Stelle Hud.extractCrosshair (so auch in Meteor fuer
 * 26.2, HudMixin). Weil der Eingriff optional war (require = 0), fand Mixin
 * nichts und schwieg -- das Modul zeichnete nie etwas.
 *
 * Das Original wird abgebrochen statt uebermalt: Vanilla invertiert die
 * Farben darunter, das wuerde durch alles Daruebergelegte durchscheinen.
 * Mit dem Original entfaellt auch seine Angriffsanzeige -- die zeichnet
 * unser Modul selbst (Einstellung "Attack Indicator").
 */
@Mixin(Hud.class)
public abstract class CrosshairMixin {

    @Inject(method = "extractCrosshair(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void vortex$customCrosshair(GuiGraphicsExtractor ctx, DeltaTracker tickCounter, CallbackInfo ci) {
        try {
            var mod = com.vortex.client.module.ModuleManager.INSTANCE.get(
                    com.vortex.client.module.modules.CrosshairModule.class);
            if (mod == null || !mod.isEnabled()) return;

            var client = net.minecraft.client.Minecraft.getInstance();
            if (client == null || client.player == null) return;

            ci.cancel();
            // Zuschauermodus: kein Fadenkreuz (wie Vanilla meistens auch)
            if (client.player.isSpectator()) return;
            boolean ego = client.options.getCameraType().isFirstPerson();
            if (!ego && !mod.thirdPerson.get()) return;

            com.vortex.client.hud.CrosshairRenderer.draw(ctx, client, mod);
        } catch (Throwable pvpErr) {
            com.vortex.client.core.Errors.report("CrosshairMixin", pvpErr);
        }
    }
}
