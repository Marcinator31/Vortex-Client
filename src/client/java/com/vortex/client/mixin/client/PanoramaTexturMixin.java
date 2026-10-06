package com.vortex.client.mixin.client;

import com.vortex.client.gui.Panoramen;
import net.minecraft.client.renderer.texture.CubeMapTexture;
import net.minecraft.client.renderer.texture.ReloadableTexture;
import net.minecraft.client.renderer.texture.TextureContents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Menue-Panorama: Ist ein Vortex-Panorama gewaehlt und geladen, kommen die 6
 * Seiten von dort statt aus den Resource Packs (siehe Panoramen).
 */
@Mixin(CubeMapTexture.class)
public abstract class PanoramaTexturMixin extends ReloadableTexture {

    protected PanoramaTexturMixin(Identifier id) {
        super(id);
    }

    @Inject(method = "loadContents", at = @At("HEAD"), cancellable = true, require = 0)
    private void vortex$panorama(ResourceManager rm, CallbackInfoReturnable<TextureContents> cir) {
        try {
            if (!Panoramen.TEXTUR.equals(this.resourceId())) return;
            TextureContents c = Panoramen.inhalt();
            if (c != null) cir.setReturnValue(c);
        } catch (Throwable t) {
            com.vortex.client.core.Errors.report("PanoramaTextur", t);
        }
    }
}
