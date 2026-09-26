package com.vortex.client.mixin.client;

import net.minecraft.client.renderer.item.ItemStackRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Zugriff auf die Modell-Ebenen eines Gegenstands -- Item Physics misst
 * daran, ob ein Gegenstand flach ist. Feldnamen gegen 26.2 geprueft.
 */
@Mixin(ItemStackRenderState.class)
public interface ItemStackRenderStateAccessor {

    @Accessor("activeLayerCount")
    int vortex$getActiveLayerCount();

    @Accessor("layers")
    ItemStackRenderState.LayerRenderState[] vortex$getLayers();
}
