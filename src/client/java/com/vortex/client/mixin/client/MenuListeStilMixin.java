package com.vortex.client.mixin.client;

import com.vortex.client.gui.glatt.MenuStil;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.components.AbstractWidget;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Welt- und Serverliste im neuen Menue-Stil: die Auswahl als runde, leicht
 * getoente Karte statt weissem Kasten, Trennlinien oben/unten haarfein.
 */
@Mixin(AbstractSelectionList.class)
public abstract class MenuListeStilMixin {

    /*
     * Die Auswahl zeichnet Minecraft als zwei Rechtecke: aussen der helle
     * Rahmen, innen schwarz. Das erste wird durch die runde Karte ersetzt,
     * das zweite entfaellt.
     */
    @WrapOperation(method = "extractSelection",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;fill(IIIII)V", ordinal = 0),
            require = 0)
    private void vortex$auswahl(GuiGraphicsExtractor ctx, int x0, int y0, int x1, int y1, int farbe, Operation<Void> original) {
        try {
            if (MenuStil.aktiv()) {
                MenuStil.auswahl(ctx, x0, y0, x1 - x0, y1 - y0);
                return;
            }
        } catch (Throwable t) {
            com.vortex.client.core.Errors.report("MenuListeStil", t);
        }
        original.call(ctx, x0, y0, x1, y1, farbe);
    }

    @WrapOperation(method = "extractSelection",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;fill(IIIII)V", ordinal = 1),
            require = 0)
    private void vortex$auswahlInnen(GuiGraphicsExtractor ctx, int x0, int y0, int x1, int y1, int farbe, Operation<Void> original) {
        if (MenuStil.aktiv()) return;
        original.call(ctx, x0, y0, x1, y1, farbe);
    }

    /** Eintraege (Weltname, Servername, MOTD) in Minecrafts Originalschrift lassen. */
    @Inject(method = "extractListItems", at = @At("HEAD"), require = 0)
    private void vortex$listeBeginn(GuiGraphicsExtractor ctx, int mx, int my, float delta, CallbackInfo ci) {
        MenuStil.listeBeginn();
    }

    @Inject(method = "extractListItems", at = @At("RETURN"), require = 0)
    private void vortex$listeEnde(GuiGraphicsExtractor ctx, int mx, int my, float delta, CallbackInfo ci) {
        MenuStil.listeEnde();
    }

    @Inject(method = "extractListSeparators", at = @At("HEAD"), cancellable = true, require = 0)
    private void vortex$trenner(GuiGraphicsExtractor ctx, CallbackInfo ci) {
        try {
            if (!MenuStil.aktiv()) return;
            AbstractWidget w = (AbstractWidget) (Object) this;
            MenuStil.trenner(ctx, w.getX(), w.getY(), w.getWidth(), w.getBottom());
            ci.cancel();
        } catch (Throwable t) {
            com.vortex.client.core.Errors.report("MenuListeStil", t);
        }
    }
}
