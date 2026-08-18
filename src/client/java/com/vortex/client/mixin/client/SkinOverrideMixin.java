package com.vortex.client.mixin.client;

import com.vortex.client.skin.ActiveSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerSkin;

/**
 * Ersetzt den dargestellten Skin des eigenen Spielers durch den in der
 * Garderobe ausgewaehlten.
 *
 * WO ES ANSETZT: Der Skin eines Spielers kommt aus seinem Eintrag in der
 * Spielerliste. Genau dort wird abgefangen -- und nur beim eigenen Eintrag,
 * erkennbar an der Konto-Kennung. Alle anderen Spieler bleiben unveraendert.
 *
 * WIE ES ERSETZT: Statt ein komplett neues Skin-Objekt zu bauen, wird die dafuer
 * vorgesehene Ueberschreibung benutzt. Der Vorteil: Umhang und Elytra bleiben
 * erhalten, es wird ausschliesslich die Haut-Textur und das Modell ersetzt.
 *
 * Nur zur Darstellung bei dir: an den Server geht nichts, andere Spieler sehen
 * weiterhin den Skin deines Kontos.
 */
@Mixin(PlayerInfo.class)
public abstract class SkinOverrideMixin {

    @Inject(method = "getSkin", at = @At("RETURN"), cancellable = true, require = 0)
    private void pvpclient$applyCustomSkin(CallbackInfoReturnable<PlayerSkin> cir) {
        try {
            Identifier tex = ActiveSkin.textureId();
            if (tex == null) return;   // kein eigener Skin gewaehlt

            // Nur der eigene Eintrag -- fremde Spieler nicht anfassen.
            Minecraft client = Minecraft.getInstance();
            if (client == null || client.player == null) return;

            // Eigenen Listeneintrag ueber den Netzwerk-Handler holen und mit
            // diesem Eintrag vergleichen.
            //
            // Bewusst dieser Weg (siehe RadarRenderer, dort erprobt):
            //  - getPlayerListEntry() auf dem Spieler ist NICHT oeffentlich
            //  - GameProfile-Zugriffe unterscheiden sich je nach Bibliotheks-
            //    Version (mal getId(), mal id())
            // Die Namens-Variante nutzt nur oeffentliche, im Projekt bereits
            // kompilierende Aufrufe.
            var handler = client.getConnection();
            if (handler == null) return;
            PlayerInfo mine =
                    handler.getPlayerInfo(client.player.getName().getString());
            if (mine == null || mine != (Object) this) return;

            PlayerSkin original = cir.getReturnValue();
            if (original == null) return;

            PlayerSkin replaced = pvpclient$buildOverride(original, tex);
            if (replaced != null) cir.setReturnValue(replaced);
        } catch (Throwable pvpErr) {
            // Niemals das Rendern wegen eines Skins abbrechen.
            com.vortex.client.core.Errors.report("SkinOverrideMixin", pvpErr);
        }
    }


    /**
     * Baut die Skin-Ueberschreibung.
     *
     * BESONDERHEIT: Die Ueberschreibung erwartet vier Optional-Werte, aber welche
     * Typen darin stecken muessen, geht aus den Mappings nicht hervor. Deshalb
     * werden hier bewusst ROHE Optional-Typen benutzt -- der Compiler laesst sie
     * in jeden erwarteten Typ zu. Welche Variante zur Laufzeit passt, probieren
     * wir einmal aus und merken uns das Ergebnis:
     *   Variante 1: eine Textur-Referenz (ClientAsset.TextureAssetInfo)
     *   Variante 2: die Kennung (Identifier) direkt
     * Schlaegt beides fehl, wird nichts ersetzt und der eigene Skin bleibt.
     */
    @org.spongepowered.asm.mixin.Unique
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static PlayerSkin pvpclient$buildOverride(PlayerSkin original, Identifier tex) {
        // Vom Nutzer gewaehlte Variante (in der Garderobe umschaltbar).
        int wanted = ActiveSkin.getVariant();

        Object model = ActiveSkin.isSlim()
                ? net.minecraft.world.entity.player.PlayerModelType.byLegacyServicesName("slim")
                : net.minecraft.world.entity.player.PlayerModelType.byLegacyServicesName("default");

        // Variante 1: Textur-Referenz
        if (wanted == 1) {
            try {
                Optional texOpt = Optional.of(new ClientAsset.ResourceTexture(tex));
                Optional modelOpt = Optional.of(model);
                PlayerSkin.Patch ov = PlayerSkin.Patch.create(
                        texOpt, Optional.empty(), Optional.empty(), modelOpt);
                return original.with(ov);
            } catch (Throwable pvpErr) {
                com.vortex.client.core.Errors.report("SkinOverride.variante1", pvpErr);
                return null;
            }
        }

        // Variante 2: Kennung direkt
        try {
            Optional texOpt = Optional.of(tex);
            Optional modelOpt = Optional.of(model);
            PlayerSkin.Patch ov = PlayerSkin.Patch.create(
                    texOpt, Optional.empty(), Optional.empty(), modelOpt);
            return original.with(ov);
        } catch (Throwable pvpErr) {
            com.vortex.client.core.Errors.report("SkinOverride.variante2", pvpErr);
            return null;
        }
    }
}
