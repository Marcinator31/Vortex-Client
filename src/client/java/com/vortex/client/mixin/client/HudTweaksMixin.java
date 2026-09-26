package com.vortex.client.mixin.client;

import com.vortex.client.module.ModuleManager;
import com.vortex.client.module.modules.ScoreboardModule;
import com.vortex.client.module.modules.TitlesModule;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.network.chat.numbers.BlankFormat;
import net.minecraft.network.chat.numbers.NumberFormat;
import net.minecraft.world.scores.Objective;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Scoreboard und Titel: verschieben, skalieren, ausblenden.
 *
 * Verschoben wird ueber die Zeichenmatrix: vor dem Vanilla-Zeichnen wird sie
 * verschoben und skaliert, danach zurueckgesetzt. Minecraft zeichnet also
 * weiter selbst -- Farben, Teams und Server-Formatierung bleiben erhalten.
 *
 * Die roten Zahlen verschwinden, indem Minecraft fuer dieses Scoreboard das
 * leere Zahlenformat bekommt -- dasselbe, das Server selbst dafuer benutzen.
 *
 * Signaturen gegen 26.2 geprueft. Weicht eine ab, bleibt nur die jeweilige
 * Funktion wirkungslos (require = 0).
 */
@Mixin(Hud.class)
public abstract class HudTweaksMixin {

    @Unique private boolean vortex$scoreVerschoben = false;
    @Unique private boolean vortex$titelVerschoben = false;

    @Inject(method = "displayScoreboardSidebar(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/world/scores/Objective;)V",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void vortex$scoreAnfang(GuiGraphicsExtractor g, Objective objective, CallbackInfo ci) {
        vortex$scoreVerschoben = false;
        ScoreboardModule m = ModuleManager.INSTANCE.get(ScoreboardModule.class);
        if (m == null || !m.isEnabled()) return;
        if (m.hide.get()) {
            ci.cancel();
            return;
        }
        var fenster = Minecraft.getInstance().getWindow();
        float ax = fenster.getGuiScaledWidth();
        float ay = fenster.getGuiScaledHeight() / 2f;
        vortex$schiebe(g, ax, ay, m.offsetX.getFloat(), m.offsetY.getFloat(), m.scale.getFloat());
        vortex$scoreVerschoben = true;
    }

    @Inject(method = "displayScoreboardSidebar(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/world/scores/Objective;)V",
            at = @At("RETURN"), require = 0)
    private void vortex$scoreEnde(GuiGraphicsExtractor g, Objective objective, CallbackInfo ci) {
        if (vortex$scoreVerschoben) {
            g.pose().popMatrix();
            vortex$scoreVerschoben = false;
        }
    }

    @Redirect(method = "displayScoreboardSidebar(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/world/scores/Objective;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/scores/Objective;numberFormatOrDefault(Lnet/minecraft/network/chat/numbers/NumberFormat;)Lnet/minecraft/network/chat/numbers/NumberFormat;"),
            require = 0)
    private NumberFormat vortex$zahlen(Objective objective, NumberFormat standard) {
        ScoreboardModule m = ModuleManager.INSTANCE.get(ScoreboardModule.class);
        if (m != null && m.isEnabled() && m.hideNumbers.get()) return BlankFormat.INSTANCE;
        return objective.numberFormatOrDefault(standard);
    }

    @Inject(method = "extractTitle(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void vortex$titelAnfang(GuiGraphicsExtractor g, DeltaTracker dt, CallbackInfo ci) {
        vortex$titelVerschoben = false;
        TitlesModule m = ModuleManager.INSTANCE.get(TitlesModule.class);
        if (m == null || !m.isEnabled()) return;
        if (m.hide.get()) {
            ci.cancel();
            return;
        }
        var fenster = Minecraft.getInstance().getWindow();
        vortex$schiebe(g, fenster.getGuiScaledWidth() / 2f, fenster.getGuiScaledHeight() / 2f,
                m.offsetX.getFloat(), m.offsetY.getFloat(), m.scale.getFloat());
        vortex$titelVerschoben = true;
    }

    @Inject(method = "extractTitle(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V",
            at = @At("RETURN"), require = 0)
    private void vortex$titelEnde(GuiGraphicsExtractor g, DeltaTracker dt, CallbackInfo ci) {
        if (vortex$titelVerschoben) {
            g.pose().popMatrix();
            vortex$titelVerschoben = false;
        }
    }

    /**
     * Titelfarbe: jedes Lesen von title/subtitle in extractTitle bekommt den
     * Text in der eingestellten Farbe. Feldnamen sind nicht gegen eine
     * Referenz geprueft -- stimmen sie nicht, findet Mixin schlicht keine
     * Stelle (require = 0) und die Titel behalten ihre Farbe. Ein Absturz ist
     * damit ausgeschlossen: der Rueckgabetyp ist in jedem Fall Component.
     */
    @com.llamalad7.mixinextras.injector.ModifyExpressionValue(
            method = "extractTitle(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V",
            at = {
                    @At(value = "FIELD", target = "Lnet/minecraft/client/gui/Hud;title:Lnet/minecraft/network/chat/Component;"),
                    @At(value = "FIELD", target = "Lnet/minecraft/client/gui/Hud;subtitle:Lnet/minecraft/network/chat/Component;")
            },
            require = 0)
    private net.minecraft.network.chat.Component vortex$titelFarbe(net.minecraft.network.chat.Component original) {
        try {
            if (original == null) return null;
            TitlesModule m = ModuleManager.INSTANCE.get(TitlesModule.class);
            if (m == null || !m.isEnabled()) return original;
            int f = m.color.get();
            if ((f >>> 24) == 0) return original;
            return net.minecraft.network.chat.Component.literal(original.getString())
                    .setStyle(net.minecraft.network.chat.Style.EMPTY.withColor(f & 0xFFFFFF));
        } catch (Throwable pvpErr) {
            com.vortex.client.core.Errors.report("HudTweaksMixin.titelFarbe", pvpErr);
            return original;
        }
    }

    /** Verschieben und um den Ankerpunkt skalieren. */
    @Unique
    private static void vortex$schiebe(GuiGraphicsExtractor g, float ax, float ay,
                                       float dx, float dy, float sc) {
        var p = g.pose();
        p.pushMatrix();
        p.translate(ax + dx, ay + dy);
        p.scale(sc, sc);
        p.translate(-ax, -ay);
    }
}
