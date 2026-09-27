package com.vortex.client.hud;

import com.vortex.client.module.ModuleManager;
import com.vortex.client.module.modules.ClearLavaModule;
import com.vortex.client.module.modules.ClearWaterModule;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.material.FogType;

/**
 * Selbstdiagnose fuer Clear Water / Clear Lava (4.6.2).
 *
 * "Geht bei mir, bei Freunden nicht" hat fast immer einen von zwei Gruenden:
 *
 *  1. Ein Shaderpack (Iris) ist aktiv. Shader zeichnen ihren eigenen Nebel
 *     unter Wasser und in Lava -- Minecrafts Nebelwerte benutzen sie nicht,
 *     also kann auch kein Client-Modul daran etwas aendern.
 *  2. Eine andere Mod ersetzt Minecrafts Nebelrechnung. Dann laeuft keiner
 *     unserer beiden Eingriffe.
 *
 * Statt stumm nichts zu tun, sagt der Client es einmal im Chat -- mit dem
 * Grund. Geprueft wird nur, waehrend das Modul an ist und die Kamera
 * wirklich seit drei Sekunden in Wasser/Lava steckt.
 */
public final class FogCheck {

    private FogCheck() {}

    private static volatile long setupZuletzt = 0, pufferZuletzt = 0;
    private static int imFluid = 0;
    private static boolean gemeldetWasser = false, gemeldetLava = false;

    public static void setupLief() { setupZuletzt = System.currentTimeMillis(); }
    public static void pufferLief() { pufferZuletzt = System.currentTimeMillis(); }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(FogCheck::tick);
    }

    private static void tick(Minecraft mc) {
        try {
            if (mc.player == null || mc.gameRenderer == null) { imFluid = 0; return; }
            FogType typ = mc.gameRenderer.mainCamera().getFluidInCamera();
            boolean wasser = typ == FogType.WATER && an(ClearWaterModule.class) && !gemeldetWasser;
            boolean lava = typ == FogType.LAVA && an(ClearLavaModule.class) && !gemeldetLava;
            if (!wasser && !lava) { imFluid = 0; return; }
            if (++imFluid < 60) return;
            String name = wasser ? "Clear Water" : "Clear Lava";
            String grund = null;
            if (shaderAktiv()) {
                grund = "a shader pack is active. Shaders draw their own " + (wasser ? "underwater" : "lava")
                        + " fog and ignore Minecraft's fog values, so " + name
                        + " cannot change it. Turn shaders off or lower the fog in the shader pack settings.";
            } else {
                long jetzt = System.currentTimeMillis();
                if (jetzt - setupZuletzt > 2000 && jetzt - pufferZuletzt > 2000) {
                    grund = "another mod replaces Minecraft's fog code, so " + name
                            + " never gets the chance to act. Check your other mods (fog / rendering mods).";
                }
            }
            if (wasser) gemeldetWasser = true; else gemeldetLava = true;
            if (grund != null) {
                mc.player.sendSystemMessage(Component.literal("§d[Vortex] §f" + name + ": " + grund));
            }
        } catch (Throwable ignored) {
        }
    }

    private static boolean an(Class<? extends com.vortex.client.module.Module> typ) {
        var m = ModuleManager.INSTANCE.get(typ);
        return m != null && m.isEnabled();
    }

    /** Iris mit aktivem Shaderpack? Per Reflexion -- Iris ist keine Pflicht. */
    private static boolean shaderAktiv() {
        try {
            Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            Object inst = api.getMethod("getInstance").invoke(null);
            return (Boolean) api.getMethod("isShaderPackInUse").invoke(inst);
        } catch (Throwable t) {
            return false;
        }
    }
}
