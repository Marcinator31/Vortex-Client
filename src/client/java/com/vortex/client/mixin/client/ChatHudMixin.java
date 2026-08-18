package com.vortex.client.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

/**
 * Puts the time in front of arriving chat messages.
 *
 * The text is changed on the way in rather than while drawing, so the stamp is
 * part of the line: it wraps with it, it is there when the line is copied, and
 * it survives scrolling back.
 */
@Mixin(ChatComponent.class)
public abstract class ChatHudMixin {

    private static final DateTimeFormatter SHORT = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter LONG = DateTimeFormatter.ofPattern("HH:mm:ss");

    @ModifyVariable(method = "method_1812", at = @At("HEAD"), argsOnly = true, require = 0)
    private Component vortex$addTimestamp(Component message) {
        try {
            var mod = com.vortex.client.module.ModuleManager.INSTANCE.get(
                    com.vortex.client.module.modules.ChatModule.class);
            if (message == null) return message;
            if (mod == null || !mod.isEnabled()) return message;

            if (!mod.timestamps.get()) {
                // Still collected, so the copy key works either way.
                com.vortex.client.hud.ChatCopy.add(message.getString());
                return message;
            }

            DateTimeFormatter fmt = (mod.format.getIndex() == 1) ? LONG : SHORT;
            String stamp = LocalTime.now().format(fmt);

            MutableComponent prefix = Component.literal(stamp + " ")
                    .setStyle(Style.EMPTY.withColor(mod.timeColor.get() & 0xFFFFFF));

            // Keep a plain copy for the clipboard, with the stamp included --
            // a pasted log without times is much harder to make sense of.
            com.vortex.client.hud.ChatCopy.add(stamp + " " + message.getString());

            return prefix.append(message);
        } catch (Throwable pvpErr) {
            com.vortex.client.core.Errors.report("ChatHudMixin", pvpErr);
            return message;
        }
    }

    /**
     * Keeps more lines than the usual hundred.
     *
     * A hundred lines is a couple of minutes on a busy server. The limit is a
     * constant compiled into the trimming step, so it is changed there rather
     * than through a field -- there is no field left to change.
     */
    @ModifyConstant(method = "method_1812", constant = @Constant(intValue = 100), require = 0)
    private int vortex$moreHistory(int original) {
        try {
            var mod = com.vortex.client.module.ModuleManager.INSTANCE.get(
                    com.vortex.client.module.modules.ChatModule.class);
            if (mod == null || !mod.isEnabled() || !mod.longHistory.get()) return original;
            return 1000;
        } catch (Throwable pvpErr) {
            return original;
        }
    }
}
