package com.vortex.client.mixin.client;

import net.minecraft.client.gui.screen.DisconnectedScreen;
import net.minecraft.network.DisconnectionInfo;
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

    @Accessor("field_52131")
    DisconnectionInfo vortex$getInfo();
}
