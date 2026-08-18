package com.vortex.client.mixin.client;

import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Reads the reason shown on the disconnect screen.
 *
 * Used to tell a kick from a lost connection, so the client does not walk
 * straight back into a server that just threw you out. Read only.
 */
@Mixin(DisconnectedScreen.class)
public interface DisconnectInfoAccessor {

    @Accessor("reason")
    Component vortex$getInfo();
}
