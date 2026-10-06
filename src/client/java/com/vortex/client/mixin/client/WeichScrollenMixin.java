package com.vortex.client.mixin.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractScrollArea;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Weiches Scrollen (Modern Menus): Server-, Welt- und Options-Listen springen
 * beim Mausrad nicht mehr um ganze Stuecke, sondern gleiten in ~0,15 s hin --
 * bildratenunabhaengig. Schnelles Drehen addiert sich (Ziel wandert weiter).
 *
 * Ziehen am Balken, Pfeiltasten und alles, was die Liste selbst setzt,
 * springt wie bisher sofort (setScrollAmount mit anderem Wert = Animation aus).
 */
@Mixin(AbstractScrollArea.class)
public abstract class WeichScrollenMixin {

    @Shadow private double scrollAmount;
    @Shadow public abstract int maxScrollAmount();
    @Shadow protected abstract double scrollRate();
    @Shadow public abstract void setScrollAmount(double amount);

    @Unique private double vortex$ziel = Double.NaN;
    @Unique private long vortex$zeit;
    @Unique private boolean vortex$selbst;

    @Unique
    private static boolean vortex$an() {
        try { return com.vortex.client.core.ClientSettings.INSTANCE.modernMenus.get(); } catch (Throwable t) { return false; }
    }

    @Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true, require = 0)
    private void vortex$weich(double mx, double my, double h, double v, CallbackInfoReturnable<Boolean> cir) {
        if (!vortex$an() || !((net.minecraft.client.gui.components.AbstractWidget) (Object) this).visible) return;
        double basis = Double.isNaN(vortex$ziel) ? scrollAmount : vortex$ziel;
        double max = maxScrollAmount();
        vortex$ziel = Math.max(0, Math.min(max, basis - v * scrollRate()));
        vortex$zeit = System.nanoTime();
        cir.setReturnValue(true);
    }

    /** Jedes Bild ein Stueck Richtung Ziel (wird beim Zeichnen des Balkens aufgerufen). */
    @Inject(method = "extractScrollbar", at = @At("HEAD"), require = 0)
    private void vortex$schritt(GuiGraphicsExtractor ctx, int mx, int my, CallbackInfo ci) {
        if (Double.isNaN(vortex$ziel)) return;
        long t = System.nanoTime();
        double dt = Math.min(0.1, (t - vortex$zeit) / 1e9);
        vortex$zeit = t;
        double ziel = Math.max(0, Math.min(maxScrollAmount(), vortex$ziel));
        double neu = scrollAmount + (ziel - scrollAmount) * (1 - Math.exp(-dt * 16));
        if (Math.abs(ziel - neu) < 0.25) { neu = ziel; vortex$ziel = Double.NaN; }
        vortex$selbst = true;
        try { setScrollAmount(neu); } finally { vortex$selbst = false; }
    }

    @Inject(method = "setScrollAmount", at = @At("HEAD"), require = 0)
    private void vortex$fremd(double amount, CallbackInfo ci) {
        // Von aussen auf einen anderen Wert gesetzt (Ziehen, Tasten, neue Liste): Animation beenden
        if (!vortex$selbst && !Double.isNaN(vortex$ziel) && Math.abs(amount - scrollAmount) > 0.5) vortex$ziel = Double.NaN;
    }
}
