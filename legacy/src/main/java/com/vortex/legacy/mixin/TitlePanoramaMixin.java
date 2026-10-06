package com.vortex.legacy.mixin;

import com.vortex.legacy.gui.Panoramen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Hauptmenue: Panorama-Seiten von Vortex statt Vanilla (siehe Panoramen). */
@Mixin(TitleScreen.class)
public abstract class TitlePanoramaMixin {
    @Redirect(method = "renderPanorama", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/texture/TextureManager;bindTexture(Lnet/minecraft/util/Identifier;)V"), require = 0)
    private void vortex$seite(TextureManager tm, Identifier id) {
        Identifier eigen = null;
        try {
            String p = id.getPath();
            int i = p.lastIndexOf("panorama_");
            if (i >= 0) eigen = Panoramen.seite(p.charAt(i + 9) - '0');
        } catch (Throwable t) {
            com.vortex.legacy.core.Errors.report("TitlePanorama", t);
        }
        tm.bindTexture(eigen != null ? eigen : id);
    }
}
