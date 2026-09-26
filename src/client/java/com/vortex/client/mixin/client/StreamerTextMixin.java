package com.vortex.client.mixin.client;

import net.minecraft.util.StringDecomposer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Streamer-Modus: jeder gezeichnete Text laeuft hier durch. Ein Eingriff an
 * dieser einen Stelle erfasst Chat, Tabliste, Scoreboard, Namensschilder und
 * Titel zugleich -- ohne jede Anzeige einzeln anzufassen.
 *
 * Signatur gegen 26.2 geprueft.
 */
@Mixin(StringDecomposer.class)
public abstract class StreamerTextMixin {

    @ModifyArg(method = "iterateFormatted(Ljava/lang/String;ILnet/minecraft/network/chat/Style;Lnet/minecraft/util/FormattedCharSink;)Z",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/util/StringDecomposer;iterateFormatted(Ljava/lang/String;ILnet/minecraft/network/chat/Style;Lnet/minecraft/network/chat/Style;Lnet/minecraft/util/FormattedCharSink;)Z",
                    ordinal = 0),
            index = 0, require = 0)
    private static String vortex$verstecken(String text) {
        try {
            return com.vortex.client.hud.StreamerMode.ersetze(text);
        } catch (Throwable t) {
            return text;
        }
    }
}
