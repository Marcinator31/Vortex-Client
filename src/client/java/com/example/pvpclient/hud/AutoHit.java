package com.example.pvpclient.hud;

import com.example.pvpclient.module.Module;
import com.example.pvpclient.module.ModuleManager;
import com.example.pvpclient.module.modules.AutoHitModule;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;

/**
 * Logik fuer Auto Hit: schlaegt zu, wenn das Fadenkreuz auf einem (Spieler-)
 * Ziel liegt und der Angriff voll aufgeladen ist.
 *
 * Wir nutzen client.crosshairTarget -- das ist genau der Treffer unter dem
 * Fadenkreuz (denselben Wert benutzt auch Vanilla beim manuellen Klick). Liegt
 * dort eine gueltige Entity und ist der Cooldown voll, rufen wir client.doAttack()
 * auf. doAttack erledigt die komplette Vanilla-Logik (Reichweite, Schaden,
 * Swing-Animation, Server-Pakete) -- damit ist der Schlag identisch zu einem
 * echten Klick, nur eben automatisch getimt.
 */
public final class AutoHit {

    private AutoHit() {}

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            long pvpT0 = System.nanoTime();
            try {
            AutoHitModule mod = (AutoHitModule) find(AutoHitModule.class);
            if (mod == null || !mod.isEnabled()) return;
            if (client.player == null || client.world == null) return;
            if (client.interactionManager == null) return;
            // In einem Menue/Screen nicht zuschlagen.
            if (client.currentScreen != null) return;

            ClientPlayerEntity self = client.player;

            // Angriff muss voll (bzw. ueber der Schwelle) aufgeladen sein.
            float charge = self.getAttackCooldownProgress(0.0f);
            if (charge < mod.getMinCharge()) return;

            // Fadenkreuz-Ziel pruefen.
            HitResult hit = client.crosshairTarget;
            if (hit == null || hit.getType() != HitResult.Type.ENTITY) return;
            if (!(hit instanceof EntityHitResult ehr)) return;

            Entity targetEntity = ehr.getEntity();
            if (targetEntity == null || targetEntity == self) return;
            if (!targetEntity.isAlive()) return;

            // Optional nur Spieler.
            if (mod.playersOnly() && !(targetEntity instanceof PlayerEntity)) return;

            // Zuschlagen: doAttack nutzt das crosshairTarget (= dieses Ziel) und
            // macht Reichweiten-Check, Schaden, Swing + Pakete selbst. doAttack
            // ist privat -> ueber den Invoker im MinecraftClientAccessor aufrufen.
            ((com.example.pvpclient.mixin.client.MinecraftClientAccessor) client)
                    .pvpclient$invokeDoAttack();
                    } finally {
                com.example.pvpclient.core.Profiler.record("AutoHit",
                        System.nanoTime() - pvpT0);
            }
        });
    }

    private static Module find(Class<? extends Module> type) {
        // Konstante Laufzeit statt die ganze Liste zu durchlaufen.
        return ModuleManager.INSTANCE.get(type);
    }
}
