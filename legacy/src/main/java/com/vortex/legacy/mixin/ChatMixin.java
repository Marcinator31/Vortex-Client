package com.vortex.legacy.mixin;

import com.vortex.legacy.core.ModuleManager;
import com.vortex.legacy.module.misc.AutoGG;
import com.vortex.legacy.module.misc.Chat;
import com.vortex.legacy.module.hud.SessionStatsModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatHud.class)
public abstract class ChatMixin {
    @Inject(method = "addMessage(Lnet/minecraft/text/Text;IIZ)V", at = @At("HEAD"), cancellable = true)
    private void vortex$filter(Text message, int id, int time, boolean ignoreLimit, CallbackInfo ci) {
        if (ignoreLimit) return; // Neu-Aufbau des Verlaufs (z. B. Fenstergroesse) -- schon verarbeitet
        try {
            String plain = message.asUnformattedString();
            Chat c = ModuleManager.INSTANCE.get(Chat.class);
            if (c != null && c.isEnabled() && c.hide(plain)) { ci.cancel(); return; }
            AutoGG gg = ModuleManager.INSTANCE.get(AutoGG.class);
            if (gg != null && gg.isEnabled()) gg.onChat(plain);
            String me = MinecraftClient.getInstance().getSession().getUsername();
            if (plain.contains(" by " + me) || plain.contains("killed by " + me) || plain.contains("slain by " + me)) SessionStatsModule.kills++;
            else if (plain.startsWith(me + " was ") || plain.startsWith(me + " died")) SessionStatsModule.deaths++;
        } catch (Throwable t) {
            com.vortex.legacy.core.Errors.report("Chat", t);
        }
    }

    @ModifyVariable(method = "addMessage(Lnet/minecraft/text/Text;IIZ)V", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private Text vortex$zeit(Text message, Text m2, int id, int time, boolean ignoreLimit) {
        if (ignoreLimit) return message;
        Chat c = ModuleManager.INSTANCE.get(Chat.class);
        if (c == null || !c.isEnabled() || !c.timestamps.get()) return message;
        return new LiteralText(c.stamp()).append(message);
    }

    @ModifyConstant(method = "addMessage(Lnet/minecraft/text/Text;IIZ)V", constant = @Constant(intValue = 100))
    private int vortex$verlauf(int v) {
        Chat c = ModuleManager.INSTANCE.get(Chat.class);
        return c != null && c.isEnabled() && c.longHistory.get() ? 1000 : v;
    }
}
