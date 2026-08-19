package com.vortex.client.mixin.client;

import com.vortex.client.cosmetics.ActiveCape;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Setzt das im Launcher gewaehlte Cape beim eigenen Spieler ein.
 *
 * Gleicher Ansatzpunkt wie SkinOverrideMixin: PlayerInfo.getSkin liefert das
 * PlayerSkin-Objekt, und dessen Patch traegt neben Koerper- auch Cape- und
 * Elytra-Textur. Die zweite Stelle in Patch.create ist das Cape -- ablesbar
 * daran, dass SkinOverrideMixin dort Optional.empty() uebergibt, um das
 * vorhandene Cape unangetastet zu lassen.
 *
 * DAS VANILLA-CAPE: Es wird durch das Ersetzen automatisch verdeckt -- die
 * Cape-Textur des Kontos wird ja gerade ueberschrieben. Ein zusaetzliches
 * Abschalten ist nicht noetig und waere sogar schaedlich, weil ohne
 * gewaehltes Cape wieder das eigene erscheinen soll.
 *
 * Beide Mixins greifen an derselben Methode an; Mixin fuehrt sie
 * nacheinander aus, und jeder liest den Rueckgabewert des vorherigen. Die
 * Reihenfolge spielt deshalb keine Rolle: einer setzt den Koerper, der
 * andere das Cape, beide behalten den jeweils anderen Teil.
 */
@Mixin(PlayerInfo.class)
public abstract class CapeOverrideMixin {

    @Inject(method = "getSkin", at = @At("RETURN"), cancellable = true, require = 0)
    private void vortex$applyCustomCape(CallbackInfoReturnable<PlayerSkin> cir) {
        try {
            Identifier capeTexture = ActiveCape.textureId();
            if (capeTexture == null) return;

            Minecraft client = Minecraft.getInstance();
            if (client == null || client.player == null || client.getConnection() == null) return;

            // Nur der eigene Eintrag. Ohne diese Pruefung traegen alle Spieler
            // dasselbe Cape -- und zwar nur bei dir auf dem Bildschirm, was
            // besonders verwirrend waere.
            PlayerInfo localInfo = client.getConnection()
                    .getPlayerInfo(client.player.getName().getString());
            if (localInfo == null || localInfo != (Object) this) return;

            PlayerSkin original = cir.getReturnValue();
            if (original == null) return;

            // Wie in ActiveSkin: Asset-Kennung und Texturpfad muessen beide
            // exakt die registrierte Kennung sein, sonst wird ein
            // Ressourcenpack-Pfad daraus abgeleitet.
            ClientAsset.ResourceTexture cape =
                    new ClientAsset.ResourceTexture(capeTexture, capeTexture);

            // Reihenfolge: Koerper, Cape, Elytra, Modell.
            //
            // Die Elytra bekommt DIESELBE Textur. Grund: Minecraft zeichnet
            // die Elytra mit der Elytra-Textur des Skins, nicht mit der des
            // Gegenstands. Blieb sie leer, hatte die Elytra gar keine Textur
            // und wurde unsichtbar -- samt Cape darunter.
            //
            // Eine 64x32-Datei traegt beides: links die Cape-Flaechen, im
            // Bereich ab x 22 die Fluegel. Deshalb genuegt eine Textur.
            //
            // Koerper und Modell bleiben leer, damit ein gleichzeitig
            // gewaehlter eigener Skin erhalten bleibt.
            PlayerSkin.Patch patch = PlayerSkin.Patch.create(
                    Optional.empty(),
                    Optional.of(cape),
                    Optional.of(cape),
                    Optional.empty());
            cir.setReturnValue(original.with(patch));
        } catch (Throwable error) {
            // Ein Cape darf niemals den Renderpfad anderer Spieler stoeren.
            com.vortex.client.core.Errors.report("CapeOverrideMixin", error);
        }
    }
}
