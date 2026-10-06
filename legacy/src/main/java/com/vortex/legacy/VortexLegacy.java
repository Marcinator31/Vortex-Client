package com.vortex.legacy;

import com.vortex.legacy.core.ClientSettings;
import com.vortex.legacy.core.Combat;
import com.vortex.legacy.core.Config;
import com.vortex.legacy.core.Errors;
import com.vortex.legacy.core.Module;
import com.vortex.legacy.core.ModuleManager;
import com.vortex.legacy.gui.ClickGui;
import com.vortex.legacy.gui.Render2D;
import com.vortex.legacy.hud.HudRenderer;
import com.vortex.legacy.module.Modules;
import com.vortex.legacy.module.pvp.Crosshair;
import com.vortex.legacy.module.pvp.ToggleSprint;
import net.fabricmc.api.ClientModInitializer;
import net.legacyfabric.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.legacyfabric.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.Window;
import net.minecraft.text.LiteralText;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Vortex Client fuer Minecraft 1.8.9. */
public final class VortexLegacy implements ClientModInitializer {
    public static final Logger LOG = LogManager.getLogger("vortexclient");
    private static boolean loaded;

    @Override
    public void onInitializeClient() {
        Modules.registerAll();
        ClientTickEvents.END_CLIENT_TICK.register(new ClientTickEvents.EndTick() {
            public void onEndTick(MinecraftClient mc) { tick(mc); }
        });
        HudRenderCallback.EVENT.register(new HudRenderCallback() {
            public void onHudRender(MinecraftClient mc, float delta) { hud(mc, delta); }
        });
        if (Boolean.getBoolean("vortex.legacy.test")) com.vortex.legacy.test.LegacyTest.install();
        LOG.info("[Vortex] Vortex Client for 1.8.9 loaded with " + ModuleManager.INSTANCE.all().size() + " modules.");
    }

    private static void tick(MinecraftClient mc) {
        try {
            if (!loaded) { loaded = true; Config.load(); }
            Combat.tick();
            ModuleManager.INSTANCE.tick();
            Config.tick();
        } catch (Throwable t) {
            Errors.report("tick", t);
        }
    }

    private static void hud(MinecraftClient mc, float delta) {
        try {
            HudRenderer.render(delta);
            Window w = new Window(mc);
            Crosshair c = ModuleManager.INSTANCE.get(Crosshair.class);
            if (c != null && c.isEnabled() && mc.options.perspective == 0 && mc.currentScreen == null && !mc.options.debugEnabled)
                c.draw(w.getWidth() / 2f, w.getHeight() / 2f);
            ToggleSprint ts = ModuleManager.INSTANCE.get(ToggleSprint.class);
            if (ts != null && ts.isEnabled() && ts.showStatus.get() && !mc.options.debugEnabled) {
                String s = ts.status();
                if (s != null) Render2D.text(s, 4, w.getHeight() - 12, 0xFFFFFFFF, true);
            }
        } catch (Throwable t) {
            Errors.report("hud", t);
        }
    }

    /** Taste gedrueckt (aus MinecraftClientMixin, vor Minecraft). */
    public static void onKey(int key) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.currentScreen != null) return;
        if (key == ClientSettings.INSTANCE.menuKey.get()) { mc.setScreen(new ClickGui()); return; }
        for (Module m : ModuleManager.INSTANCE.all()) {
            if (m.key.get() != key || m.key.get() <= 0) continue;
            m.toggle();
            Config.markDirty();
            toggleMessage(m);
        }
    }

    public static void toggleMessage(Module m) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        String t = m.getName() + (m.isEnabled() ? " §aenabled" : " §cdisabled");
        switch (ClientSettings.INSTANCE.toggleMessage.getIndex()) {
            case 1: mc.inGameHud.setOverlayMessage(t, false); break;
            case 2: mc.player.sendMessage(new LiteralText("§d[Vortex] §f" + t)); break;
            default: break;
        }
    }
}
