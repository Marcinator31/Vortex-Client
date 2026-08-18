package com.vortex.client.mixin.client;

import com.vortex.client.freecam.Freecam;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Laesst in der Freecam Abbau/Angriff/Benutzen vom ECHTEN SPIELER ausgehen statt
 * von der Kamera.
 *
 * Hintergrund: Vanilla berechnet das Crosshair-Ziel (worauf Abbau/Angriff
 * zielt) ueber die aktuelle Kamera-Entity. In der Freecam ist das unsere
 * Kamera-Entity -- das Ziel laege also dort, wo die Freecam hinschaut. Das ist
 * nicht gewuenscht (und auf Servern ein Anticheat-Risiko).
 *
 * Loesung: Nach dem normalen updateCrosshairTarget berechnen wir bei aktiver
 * Freecam das Ziel NEU -- per Raycast vom echten Spieler mit dessen
 * (eingefrorener) Blickrichtung -- und ueberschreiben damit client.crosshairTarget.
 * Der echte Spieler steht still, schaut in seine fixe Richtung und alle
 * Interaktionen kommen von ihm: fuer den Server ganz normales Verhalten,
 * waehrend die Kamera frei bleibt.
 *
 * So gilt: aus der Freecam selbst geht NICHTS aus, alles kommt vom Spieler.
 *
 *   updateCrosshairTarget = method_3190 (float tickDelta)
 *   player.raycast        = method_5745 (double, float, boolean) -> HitResult
 */
@Mixin(GameRenderer.class)
public abstract class CrosshairTargetMixin {

    @Inject(method = "pick", at = @At("TAIL"))
    private void pvpclient$retargetFromPlayer(float tickDelta, CallbackInfo ci) {
        if (!Freecam.isActive()) return;

        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        if (player == null) return;

        try {
            // Reichweite des Spielers (Survival ~4.5). Block-Raycast vom echten
            // Spieler mit dessen aktueller (in der Freecam eingefrorener)
            // Blickrichtung.
            //
            // WICHTIG: festes tickDelta = 1.0F statt des frame-abhaengigen Werts.
            // Der Spieler steht in der Freecam still (Position + Blick
            // eingefroren), daher braucht es keine Interpolation. Mit einem
            // konstanten tickDelta trifft der Raycast in JEDEM Frame exakt
            // denselben Block -- sonst verschiebt der schwankende Wert die
            // Trefferposition minimal, das Spiel denkt "anderer Block" und setzt
            // den Abbau-Fortschritt zurueck (man muss den Block mehrfach
            // anfangen).
            double reach = player.getAttributeValue(net.minecraftforge.common.ForgeMod.BLOCK_REACH.get());
            HitResult target = player.pick(reach, 1.0F, false);
            // IMMER setzen (auch bei MISS), damit nie das Kamera-Ziel der
            // Freecam durchrutscht und den Block wechselt.
            if (target != null) {
                ((MinecraftClientAccessor) client).pvpclient$setCrosshairTarget(target);
            }
        } catch (Throwable pvpErr) {
                com.vortex.client.core.Errors.report("CrosshairTargetMixin", pvpErr);
            }
    }
}
