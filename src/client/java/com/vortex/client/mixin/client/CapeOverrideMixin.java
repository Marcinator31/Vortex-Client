package com.vortex.client.mixin.client;

import com.vortex.client.cosmetics.ActiveCape;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.util.AssetInfo;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

/**
 * Setzt das im Launcher gewaehlte Cape beim eigenen Spieler ein.
 *
 * Gleicher Ansatzpunkt wie SkinOverrideMixin dieser Version: der Skin kommt
 * aus dem Eintrag in der Spielerliste, und dort wird abgefangen. Ersetzt wird
 * ueber SkinTextures.SkinOverride, damit alles andere erhalten bleibt.
 *
 * DIE VIER STELLEN in SkinOverride.create sind: Haut, Cape, Elytra, Modell.
 * Ablesbar an SkinOverrideMixin, der an Stelle 1 die Haut und an 4 das Modell
 * setzt und die beiden mittleren leer laesst, um Umhang und Elytra nicht
 * anzutasten.
 *
 * ELYTRA: bekommt DIESELBE Textur. Minecraft zeichnet die Elytra mit der
 * Elytra-Textur des Skins; bliebe sie leer, waere die Elytra unsichtbar.
 * Eine 64x32-Datei traegt beides -- links das Cape, ab x 22 die Fluegel.
 *
 * TYPFRAGE: Wie beim Skin ist aus den Mappings nicht ersichtlich, ob in den
 * Optional-Werten eine Textur-Referenz oder die Kennung selbst erwartet wird.
 * Deshalb dieselbe Loesung wie dort: beide Varianten probieren und sich die
 * merken, die funktioniert hat. Geraten wird nichts.
 */
@Mixin(PlayerListEntry.class)
public abstract class CapeOverrideMixin {

    /** 0 = noch unbekannt, 1 = Textur-Referenz, 2 = Kennung direkt, -1 = keine. */
    @org.spongepowered.asm.mixin.Unique
    private static int pvpclient$variante = 0;

    @Inject(method = "method_52810", at = @At("RETURN"), cancellable = true, require = 0)
    private void pvpclient$applyCustomCape(CallbackInfoReturnable<SkinTextures> cir) {
        try {
            Identifier cape = ActiveCape.textureId();
            if (cape == null) return;              // kein Cape gewaehlt
            if (pvpclient$variante == -1) return;  // beide Varianten fehlgeschlagen

            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null || client.player == null) return;

            // Nur der eigene Eintrag. Ohne diese Pruefung traegt jeder Spieler
            // dasselbe Cape -- und zwar nur auf deinem Bildschirm.
            var handler = client.getNetworkHandler();
            if (handler == null) return;
            PlayerListEntry mine =
                    handler.getPlayerListEntry(client.player.getName().getString());
            if (mine == null || mine != (Object) this) return;

            SkinTextures original = cir.getReturnValue();
            if (original == null) return;

            SkinTextures ersetzt = pvpclient$buildOverride(original, cape);
            if (ersetzt != null) cir.setReturnValue(ersetzt);
        } catch (Throwable pvpErr) {
            // Ein Cape darf niemals das Rendern abbrechen.
            com.vortex.client.core.Errors.report("CapeOverrideMixin", pvpErr);
        }
    }

    /**
     * Baut die Ueberschreibung mit Cape und Elytra.
     *
     * Haut und Modell bleiben leer, damit ein gleichzeitig gewaehlter eigener
     * Skin erhalten bleibt -- die beiden Mixins arbeiten nacheinander auf
     * demselben Rueckgabewert und ergaenzen sich dadurch.
     */
    @org.spongepowered.asm.mixin.Unique
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static SkinTextures pvpclient$buildOverride(SkinTextures original, Identifier cape) {
        // Variante 1: Textur-Referenz
        if (pvpclient$variante == 0 || pvpclient$variante == 1) {
            try {
                Optional capeOpt = Optional.of(new AssetInfo.TextureAssetInfo(cape));
                SkinTextures.SkinOverride ov = SkinTextures.SkinOverride.create(
                        Optional.empty(), capeOpt, capeOpt, Optional.empty());
                SkinTextures ergebnis = original.withOverride(ov);
                pvpclient$variante = 1;
                return ergebnis;
            } catch (Throwable pvpErr) {
                if (pvpclient$variante == 1) {
                    // Hat frueher funktioniert und jetzt nicht mehr: melden.
                    com.vortex.client.core.Errors.report("CapeOverride.variante1", pvpErr);
                    return null;
                }
                // Beim ersten Versuch: still weiter zu Variante 2.
            }
        }

        // Variante 2: Kennung direkt
        try {
            Optional capeOpt = Optional.of(cape);
            SkinTextures.SkinOverride ov = SkinTextures.SkinOverride.create(
                    Optional.empty(), capeOpt, capeOpt, Optional.empty());
            SkinTextures ergebnis = original.withOverride(ov);
            pvpclient$variante = 2;
            return ergebnis;
        } catch (Throwable pvpErr) {
            com.vortex.client.core.Errors.report("CapeOverride.variante2", pvpErr);
            // Beide Wege gescheitert: nicht bei jedem Bild erneut versuchen.
            pvpclient$variante = -1;
            return null;
        }
    }
}
