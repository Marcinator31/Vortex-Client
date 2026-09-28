package com.vortex.client.mixin.client;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Better Tooltips: welcher Platz liegt unter dem Mauszeiger? */
@Mixin(AbstractContainerScreen.class)
public interface TooltipSlotAccessor {
    @Accessor("hoveredSlot")
    Slot vortex$hoveredSlot();
}
