package com.vortex.client.mixin.client;

import com.vortex.client.gui.glatt.MenuStil;
import net.minecraft.client.renderer.state.gui.GuiTextRenderState;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Im Haupt-, Einzelspieler- und Mehrspielermenue wird JEDER Text in der
 * glatten Schrift "Inter" gezeichnet (siehe MenuStil). Hier laeuft aller
 * Oberflaechentext durch; nur Text in Minecrafts Standardschrift wird
 * umgestellt, Sonderschriften bleiben.
 */
@Mixin(GuiTextRenderState.class)
public abstract class MenuSchriftMixin {

    @Shadow @Mutable @Final private FormattedCharSequence text;
    @Shadow @Mutable @Final private boolean dropShadow;

    @Inject(method = "<init>", at = @At("TAIL"), require = 0)
    private void vortex$schrift(CallbackInfo ci) {
        try {
            if (MenuStil.textAktiv()) {
                this.text = MenuStil.umschreiben(this.text);
                // Der harte Pixelschatten (1 GUI-Pixel versetzt) passt nicht zu
                // glatter Schrift -- der Hintergrund ist dort ohnehin abgedunkelt.
                this.dropShadow = false;
            }
        } catch (Throwable t) {
            com.vortex.client.core.Errors.report("MenuSchrift", t);
        }
    }
}
