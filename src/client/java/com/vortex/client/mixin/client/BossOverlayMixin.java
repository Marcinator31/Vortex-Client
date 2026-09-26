package com.vortex.client.mixin.client;

import com.vortex.client.hud.BossBars;
import com.vortex.client.module.modules.BossBarModule;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.BossHealthOverlay;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Bossleisten: verschieben, skalieren, umfaerben, eigene Leisten.
 *
 *  - Die Liste der Leisten wird abgefangen. Im Stil "Custom" (oder bei
 *    "Hide") bekommt Minecraft eine leere Liste und zeichnet nichts; wir
 *    zeichnen aus der echten Liste selbst (hud/BossBars).
 *  - Im Stil "Vanilla" wird die Zeichenmatrix verschoben und skaliert, und
 *    der Name bekommt auf Wunsch eine eigene Farbe.
 */
@Mixin(BossHealthOverlay.class)
public abstract class BossOverlayMixin {

    @Unique private boolean vortex$verschoben = false;

    @Redirect(method = "extractRenderState",
            at = @At(value = "INVOKE", target = "Ljava/util/Collection;iterator()Ljava/util/Iterator;"),
            require = 0)
    private Iterator<?> vortex$leisten(Collection<?> werte) {
        try {
            BossBars.merke(werte);
            BossBarModule m = BossBars.modul();
            if (m != null && m.isEnabled() && (m.hide.get() || m.style.getIndex() == 1)) {
                return Collections.emptyIterator();
            }
        } catch (Throwable pvpErr) {
            com.vortex.client.core.Errors.report("BossOverlayMixin.leisten", pvpErr);
        }
        return werte.iterator();
    }

    @Redirect(method = "extractRenderState",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/LerpingBossEvent;getName()Lnet/minecraft/network/chat/Component;"),
            require = 0)
    private Component vortex$name(LerpingBossEvent e) {
        return BossBars.name(e.getName());
    }

    @Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;)V",
            at = @At("HEAD"), require = 0)
    private void vortex$anfang(GuiGraphicsExtractor g, CallbackInfo ci) {
        vortex$verschoben = false;
        BossBarModule m = BossBars.modul();
        if (m == null || !m.isEnabled() || m.style.getIndex() != 0) return;
        float ax = Minecraft.getInstance().getWindow().getGuiScaledWidth() / 2f;
        var p = g.pose();
        p.pushMatrix();
        p.translate(ax + m.offsetX.getFloat(), m.offsetY.getFloat());
        p.scale(m.scale.getFloat(), m.scale.getFloat());
        p.translate(-ax, 0f);
        vortex$verschoben = true;
    }

    @Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;)V",
            at = @At("RETURN"), require = 0)
    private void vortex$ende(GuiGraphicsExtractor g, CallbackInfo ci) {
        if (vortex$verschoben) {
            g.pose().popMatrix();
            vortex$verschoben = false;
        }
    }
}
