package com.vortex.client.mixin.client;

import com.vortex.client.freecam.Freecam;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
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
    private Vec3 field_18712; // pos

    @Shadow
    protected abstract void method_19325(float yaw, float pitch); // setRotation

    @Shadow
    protected abstract void method_19327(double x, double y, double z); // setPos

    @Inject(method = "method_19321", at = @At("TAIL"))
    private void pvpclient$freecamUpdate(Level area, Entity focusedEntity,
                                         boolean thirdPerson, boolean inverseView,
                                         float tickProgress, CallbackInfo ci) {
        if (!Freecam.isActive()) return;
        // Bewegung pro Frame berechnen (fluessig, framerate-unabhaengig).
        Freecam.updateFrame();
        // Erst Rotation (berechnet Richtungsvektoren neu), dann Position.
        method_19325(Freecam.getYaw(), Freecam.getPitch());
        method_19327(Freecam.getPos().x, Freecam.getPos().y, Freecam.getPos().z);
    }
}
