package com.vortex.client.mixin.client;

import com.vortex.client.freecam.Freecam;
import net.minecraft.world.entity.Entity;
import net.minecraft.client.DeltaTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lenkt die Kamera auf Position UND Blickrichtung der Freecam um, wenn diese
 * aktiv ist.
 *
 * Wir haengen uns ans Ende von Camera.update(...) (method_19321) und ueberschreiben
 * danach Position und Rotation. Die Rotation MUSS ueber setRotation gesetzt werden
 * (nicht nur das yaw/pitch-Feld), weil setRotation auch die internen Richtungs-
 * vektoren und die Quaternion neu berechnet -- sonst wuerde die Kamera zwar an
 * der richtigen Stelle sein, aber falsch blicken (und Hitboxen/Entities saehen
 * verschoben aus).
 *
 * pos (field_18712) setzen wir direkt per @Shadow. setPos/setRotation sind in
 * Camera protected -> wir rufen sie ueber @Shadow-Methoden auf.
 */
@Mixin(net.minecraft.client.Camera.class)
public abstract class CameraMixin {

    @Shadow
    protected abstract void setRotation(float yaw, float pitch);

    @Shadow
    protected abstract void setPosition(double x, double y, double z);

    /**
     * "Abgeloeste" Kamera wie in F5. In der Freecam auf true gesetzt -- dann
     * zeichnet Minecraft den eigenen Spieler von selbst, OHNE dass eine
     * eigene Kamera-Entity noetig ist (dieselbe Stelle wie Meteor in 26.2).
     */
    @Shadow
    private boolean detached;

    @Shadow
    protected abstract void move(float vorwaerts, float hoch, float seitlich);

    @Shadow
    private float getMaxZoom(float abstand) { return abstand; }

    /**
     * Freelook: die Kamera bekommt die Freelook-Drehung statt der des Spielers.
     *
     * FEHLER BIS 4.6.2: Eingehakt war der ERSTE setRotation-Aufruf in
     * alignWithEntity. In 26.x ist das aber der Sonderfall "sitzt in einer
     * Lore" -- im normalen Spiel lief der Eingriff nie, und die Selbstpruefung
     * hat Freelook nach 1,5 s abgeschaltet.
     *
     * JETZT: direkt dort, wo die Kamera die Blickrichtung des Spielers liest
     * (getViewYRot/getViewXRot). Das deckt alle Faelle ab. F5 von vorne leitet
     * Minecraft danach selbst aus diesen Werten ab (yRot + 180, -xRot) -- also
     * funktioniert Freelook in jeder F5-Ansicht.
     */
    @com.llamalad7.mixinextras.injector.ModifyExpressionValue(method = "alignWithEntity",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getViewYRot(F)F"),
            require = 0)
    private float vortex$freelookYaw(float original) {
        if (!com.vortex.client.hud.Freelook.aktiv()) return original;
        com.vortex.client.hud.Freelook.kameraGreift();
        return com.vortex.client.hud.Freelook.yaw();
    }

    @com.llamalad7.mixinextras.injector.ModifyExpressionValue(method = "alignWithEntity",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getViewXRot(F)F"),
            require = 0)
    private float vortex$freelookPitch(float original) {
        return com.vortex.client.hud.Freelook.aktiv() ? com.vortex.client.hud.Freelook.pitch() : original;
    }

    //#if 26
    @Inject(method = "update", at = @At("TAIL"))
    private void pvpclient$freecamUpdate(DeltaTracker deltaTracker, CallbackInfo ci) {
    //#else
    //$ @Inject(method = "setup", at = @At("TAIL"))
    //$ private void pvpclient$freecamUpdate(net.minecraft.world.level.Level level, net.minecraft.world.entity.Entity entity,
    //$                                      boolean detached, boolean mirrored, float partialTick, CallbackInfo ci) {
    //#endif
        if (!Freecam.isActive()) return;
        // Bewegung pro Frame berechnen (fluessig, framerate-unabhaengig).
        Freecam.updateFrame();
        // Erst Rotation (berechnet Richtungsvektoren neu), dann Position.
        setRotation(Freecam.getYaw(), Freecam.getPitch());
        setPosition(Freecam.getPos().x, Freecam.getPos().y, Freecam.getPos().z);
        // F5 BLEIBT F5 (seit 4.14): in der Dritt-Person-Ansicht steht die Kamera
        // hinter (bzw. bei F5 von vorne: vor) dem Freecam-Punkt -- genau wie
        // Minecraft es fuer den Spieler macht. Vorher zeigte die Freecam in F5
        // trotzdem die Ich-Sicht, F5 schien abgeschaltet. getMaxZoom zieht die
        // Kamera an Waenden heran (Ghost View "Camera Clip" schaltet das ab).
        var typ = net.minecraft.client.Minecraft.getInstance().options.getCameraType();
        if (typ != null && !typ.isFirstPerson()) {
            if (typ.isMirrored()) setRotation(Freecam.getYaw() + 180f, -Freecam.getPitch());
            move(-getMaxZoom(4.0f), 0f, 0f);
        }
        // SPIELER SICHTBAR, OHNE ANTI-CHEAT-RISIKO.
        //
        // Vorher brauchte "Show Player" eine eigene Kamera-Entity. Dann galt
        // aber der echte Spieler nicht mehr als Kamera -- und Minecraft
        // schickt Bewegungspakete nur fuer die Kamera. Der Spieler verstummte
        // auf dem Server, was Anti-Cheats auffaellt.
        //
        // Jetzt bleibt der Spieler die Kamera-Entity (er meldet sich weiter
        // ganz normal), und die Kamera gilt nur als "abgeloest" -- wie in F5.
        // Damit zeichnet Minecraft den Koerper, und die eigene Hand
        // verschwindet in der Freecam.
        detached = true;
    }
}
