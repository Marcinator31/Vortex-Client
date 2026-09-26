package com.vortex.client.mixin.client;

import com.vortex.client.hud.CpsCounter;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Faengt Mausklicks ab und meldet sie an den CpsCounter.
 *
 * WICHTIG -- Signatur fuer 1.21.11 (verifiziert gegen Yarn-Javadocs):
 *   onMouseButton(long window, MouseButtonInfo input, int action)
 *
 * In aelteren Versionen waren das vier ints (long, int, int, int).
 * Jetzt ist Button + Modifier im MouseButtonInfo-Record zusammengefasst:
 *   - input.button() -> welche Taste (0 = links, 1 = rechts)
 *   - action         -> 1 = gedrueckt, 0 = losgelassen
 *
 * Die Mixin-Signatur MUSS exakt zur Zielmethode passen, sonst stuerzt
 * Minecraft beim Start ab (genau der vorherige Crash).
 */
@Mixin(MouseHandler.class)
public class MouseMixin {

    private static final int LEFT_BUTTON = 0;
    private static final int RIGHT_BUTTON = 1;
    private static final int ACTION_PRESS = 1; // GLFW: 1 = gedrueckt

    @org.spongepowered.asm.mixin.Unique
    private static void vortex$freund() {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player == null || mc.gui.screen() != null) return;
        var m = com.vortex.client.module.ModuleManager.INSTANCE.get(
                com.vortex.client.module.modules.FriendsModule.class);
        if (m == null || !m.isEnabled() || !m.middleClick.get()) return;
        if (!(mc.hitResult instanceof net.minecraft.world.phys.EntityHitResult ehr)) return;
        if (!(ehr.getEntity() instanceof net.minecraft.world.entity.player.Player p)) return;
        String name = p.getName().getString();
        boolean jetzt = com.vortex.client.core.Friends.umschalten(name);
        com.vortex.client.core.ConfigManager.save();
        mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                jetzt ? "\u00a7b" + name + " \u00a77is now your friend."
                      : "\u00a77" + name + " removed from friends."));
    }

    @Inject(method = "onButton", at = @At("HEAD"))
    private void pvpclient$onMouseButton(long window, MouseButtonInfo input, int action, CallbackInfo ci) {
        if (action != ACTION_PRESS) {
            return; // nur das Druecken zaehlen, nicht das Loslassen
        }
        int button = input.button();
        // Mittelklick auf einen Spieler: Freund hinzufuegen oder entfernen.
        if (button == 2) {
            try {
                vortex$freund();
            } catch (Throwable pvpErr) {
                com.vortex.client.core.Errors.report("MouseMixin.freund", pvpErr);
            }
        }
        if (button == LEFT_BUTTON) {
            CpsCounter.LEFT.onClick();
            com.vortex.client.macro.MacroManager.record(
                    com.vortex.client.macro.Macro.Action.LEFT_CLICK, 0, 0);
        } else if (button == RIGHT_BUTTON) {
            CpsCounter.RIGHT.onClick();
            com.vortex.client.macro.MacroManager.record(
                    com.vortex.client.macro.Macro.Action.RIGHT_CLICK, 0, 0);
        }
    }
}
