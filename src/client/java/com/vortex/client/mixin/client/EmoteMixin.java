package com.vortex.client.mixin.client;

import com.vortex.client.cosmetics.Emotes;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Emotes: nach Minecrafts eigener Animation die Emote-Pose setzen
 * (Arme, Beine, Kopf, Koerper). Am Ende der Methode, nichts wird ersetzt --
 * so bleibt Minecrafts Animation erhalten, wenn kein Emote laeuft.
 */
@Mixin(PlayerModel.class)
public abstract class EmoteMixin {

    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V", at = @At("TAIL"), require = 0)
    private void vortex$emote(AvatarRenderState state, CallbackInfo ci) {
        try {
            Emotes.anwenden((PlayerModel) (Object) this, state);
        } catch (Throwable t) {
            com.vortex.client.core.Errors.report("EmoteMixin", t);
        }
    }
}
