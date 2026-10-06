package com.vortex.legacy.mixin;

import com.vortex.legacy.gui.MenuStil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.widget.ButtonWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ButtonWidget.class)
public abstract class MenuStilMixin {
    @Shadow protected int height;
    @Shadow protected boolean hovered;
    @Shadow public int width;
    @Shadow protected abstract void mouseDragged(MinecraftClient client, int mouseX, int mouseY);

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void vortex$knopf(MinecraftClient client, int mx, int my, CallbackInfo ci) {
        ButtonWidget b = (ButtonWidget) (Object) this;
        if (!b.visible || !MenuStil.knopf()) return;
        // Schieberegler (Optionen) behalten ihr Verhalten -- nur Hintergrund und Text neu
        hovered = mx >= b.x && my >= b.y && mx < b.x + width && my < b.y + height;
        try {
            MenuStil.zeichneKnopf(b, mx, my, width, height);
            mouseDragged(client, mx, my);
            ci.cancel();
        } catch (Throwable t) {
            com.vortex.legacy.core.Errors.report("MenuStil", t);
        }
    }
}
