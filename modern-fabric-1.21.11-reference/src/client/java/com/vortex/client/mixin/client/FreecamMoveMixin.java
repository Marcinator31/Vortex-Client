package com.vortex.client.mixin.client;

import com.vortex.client.freecam.Freecam;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Haelt den echten Spieler waehrend der Freecam an Ort und Stelle.
 *
 * WARUM AN DIESER STELLE:
 * Das Neutralisieren der Tasten-Eingabe allein reicht nicht. Der Spieler kann
 * sich auch ohne Eingabe weiterbewegen -- durch Restschwung nach dem Loslaufen,
 * durch Sprint-Impuls, durch Wasserstroemung oder durch Rutschen auf Eis. Genau
 * das faellt auf, wenn mehrere Tasten gleichzeitig gedrueckt waren: es ist mehr
 * Schwung vorhanden, der danach noch abgebaut wird.
 *
 * Nachtraeglich die Position zurueckzusetzen ist der falsche Weg -- die Bewegung
 * hat dann schon stattgefunden (und wurde unter Umstaenden bereits an den Server
 * gemeldet), und beim Zeichnen entsteht ein sichtbares Gleiten zwischen alter und
 * zurueckgesetzter Position.
 *
 * Deshalb wird hier direkt der Bewegungsvektor abgefangen, BEVOR er angewendet
 * wird: die waagerechten Anteile werden auf null gesetzt, der senkrechte bleibt
 * erhalten. Dadurch wirkt die Schwerkraft normal weiter (wer im Sprung die
 * Freecam oeffnet, landet sauber), aber seitlich bewegt sich nichts mehr.
 */
@Mixin(Entity.class)
public abstract class FreecamMoveMixin {

    @ModifyVariable(method = "method_5784", at = @At("HEAD"), argsOnly = true)
    private Vec3d pvpclient$freezeHorizontalMovement(Vec3d movement) {
        if (!Freecam.isActive() || movement == null) return movement;

        // Nur den echten Spieler betreffen -- alle anderen Wesen (und die
        // Freecam-Kamera selbst) bewegen sich normal weiter.
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null) return movement;
        if ((Object) this != client.player) return movement;

        if (movement.x == 0.0 && movement.z == 0.0) return movement;
        return new Vec3d(0.0, movement.y, 0.0);
    }
}
