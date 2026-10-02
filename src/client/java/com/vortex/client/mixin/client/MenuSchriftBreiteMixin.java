package com.vortex.client.mixin.client;

import com.vortex.client.gui.glatt.MenuStil;
import net.minecraft.client.gui.Font;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Gegenstueck zu MenuSchriftMixin: Breiten werden in denselben Menues mit der
 * Schrift "Inter" gemessen, in der der Text dann auch gezeichnet wird --
 * sonst saesse zentrierter Text schief.
 */
@Mixin(Font.class)
public abstract class MenuSchriftBreiteMixin {

    @Inject(method = "width(Ljava/lang/String;)I", at = @At("HEAD"), cancellable = true, require = 0)
    private void vortex$breiteText(String s, CallbackInfoReturnable<Integer> cir) {
        if (s == null || !MenuStil.textAktiv()) return;
        cir.setReturnValue(((Font) (Object) this).width(MenuStil.umschreiben(FormattedCharSequence.forward(s, Style.EMPTY))));
    }

    @Inject(method = "width(Lnet/minecraft/network/chat/FormattedText;)I", at = @At("HEAD"), cancellable = true, require = 0)
    private void vortex$breiteFormat(FormattedText t, CallbackInfoReturnable<Integer> cir) {
        if (t == null || !MenuStil.textAktiv()) return;
        cir.setReturnValue(((Font) (Object) this).width(MenuStil.umschreiben(Language.getInstance().getVisualOrder(t))));
    }

    @ModifyVariable(method = "width(Lnet/minecraft/util/FormattedCharSequence;)I", at = @At("HEAD"), argsOnly = true, require = 0)
    private FormattedCharSequence vortex$breiteFolge(FormattedCharSequence seq) {
        return seq != null && MenuStil.textAktiv() ? MenuStil.umschreiben(seq) : seq;
    }
}
