package com.vortex.legacy.test;

import com.vortex.legacy.VortexLegacy;
import com.vortex.legacy.core.Combat;
import com.vortex.legacy.core.Errors;
import com.vortex.legacy.core.Module;
import com.vortex.legacy.core.ModuleManager;
import com.vortex.legacy.gui.ClickGui;
import com.vortex.legacy.gui.HudEditorScreen;
import com.vortex.legacy.hud.HudModule;
import com.vortex.legacy.module.pvp.Crosshair;
import com.vortex.legacy.module.pvp.HitColor;
import com.vortex.legacy.module.pvp.ToggleSprint;
import com.vortex.legacy.module.visual.Fullbright;
import com.vortex.legacy.module.visual.TimeChanger;
import com.vortex.legacy.module.visual.Zoom;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.legacyfabric.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.ScreenshotUtils;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.world.level.LevelGeneratorType;
import net.minecraft.world.level.LevelInfo;

/**
 * Test im echten Spiel (CI, -Dvortex.legacy.test=true): Hauptmenue, Welt
 * erzeugen, Module einschalten, Menues oeffnen, Bildschirmfotos, Funktionen
 * pruefen. Ergebnis: run/vortex-test-results.txt, Bilder: run/screenshots.
 * Ablauf als Liste von Schritten, je Schritt eine Wartezeit in Ticks.
 */
public final class LegacyTest {
    private LegacyTest() {}

    private interface Step { void run(MinecraftClient mc) throws Exception; }
    private static final List<Object[]> STEPS = new ArrayList<Object[]>();
    private static final List<String> OUT = new ArrayList<String>();
    private static int idx, wait, fails;
    private static long started;
    private static String only = System.getProperty("vortex.legacy.test.only", "");

    private static void step(int ticksAfter, String name, Step s) { STEPS.add(new Object[]{ ticksAfter, name, s }); }
    private static void ok(boolean cond, String what) {
        OUT.add((cond ? "OK    " : "FAIL  ") + what);
        if (!cond) fails++;
        VortexLegacy.LOG.info("[vortex-test] " + (cond ? "OK " : "FAIL ") + what);
    }
    private static void note(String s) { OUT.add("      " + s); VortexLegacy.LOG.info("[vortex-test] " + s); }
    private static void shot(MinecraftClient mc, String name) {
        ScreenshotUtils.saveScreenshot(mc.runDirectory, name + ".png", mc.width, mc.height, mc.getFramebuffer());
    }
    private static <T extends Module> T mod(Class<T> c) { return ModuleManager.INSTANCE.get(c); }

    public static void install() {
        build();
        ClientTickEvents.END_CLIENT_TICK.register(new ClientTickEvents.EndTick() {
            public void onEndTick(MinecraftClient mc) { tick(mc); }
        });
    }

    private static void tick(MinecraftClient mc) {
        if (started == 0) started = System.currentTimeMillis();
        if (System.currentTimeMillis() - started > 15 * 60 * 1000L) { ok(false, "finished within 15 minutes"); finish(mc); return; }
        if (wait > 0) { wait--; return; }
        if (idx >= STEPS.size()) { finish(mc); return; }
        Object[] s = STEPS.get(idx++);
        try {
            ((Step) s[2]).run(mc);
        } catch (Throwable t) {
            ok(false, s[1] + " threw " + t);
            VortexLegacy.LOG.error("[vortex-test] step failed", t);
        }
        wait = (Integer) s[0];
    }

    private static boolean done;
    private static void finish(MinecraftClient mc) {
        if (done) return;
        done = true;
        Map<String, Integer> errs = Errors.all();
        ok(errs.isEmpty(), "no module errors " + (errs.isEmpty() ? "" : errs.toString()));
        OUT.add(0, "=== Vortex Client 1.8.9 in-game test ===  " + (fails == 0 ? "ALL PASSED" : fails + " FAILED"));
        try {
            Files.write(new File(mc.runDirectory, "vortex-test-results.txt").toPath(), OUT, StandardCharsets.UTF_8);
        } catch (IOException e) {
            VortexLegacy.LOG.error("[vortex-test] could not write results", e);
        }
        mc.scheduleStop();
    }

    private static int waitForWorld;

    private static void build() {
        step(40, "title screen", new Step() { public void run(MinecraftClient mc) {
            mc.options.pauseOnLostFocus = false; // Xvfb hat keinen Fokus -- sonst pausiert das Spiel
            ok(mc.currentScreen instanceof TitleScreen, "title screen is open (" + (mc.currentScreen == null ? "none" : mc.currentScreen.getClass().getSimpleName()) + ")");
            ok(ModuleManager.INSTANCE.all().size() >= 30, ModuleManager.INSTANCE.all().size() + " modules registered");
        }});
        step(5, "title shot", new Step() { public void run(MinecraftClient mc) { shot(mc, "01-title"); }});
        step(1, "create world", new Step() { public void run(MinecraftClient mc) {
            LevelInfo info = new LevelInfo(4242L, LevelInfo.GameMode.CREATIVE, true, false, LevelGeneratorType.DEFAULT).enableCommands();
            mc.startIntegratedServer("vortextest" + System.currentTimeMillis() % 100000, "Vortex Test", info);
        }});
        // Auf die Welt warten (Schritt wiederholt sich, bis sie da ist)
        step(20, "wait world", new Step() { public void run(MinecraftClient mc) {
            if ((mc.world == null || mc.player == null) && waitForWorld++ < 60) { idx--; return; }
            ok(mc.world != null && mc.player != null, "world loaded");
        }});
        step(10, "setup", new Step() { public void run(MinecraftClient mc) {
            mc.player.sendChatMessage("/time set 1000");
            mc.player.sendChatMessage("/weather clear");
            mc.player.sendChatMessage("/gamerule doDaylightCycle false");
            mc.options.fov = 80f;
        }});
        step(100, "chunks", new Step() { public void run(MinecraftClient mc) { }});
        step(5, "hud on", new Step() { public void run(MinecraftClient mc) {
            int n = 0;
            for (Module m : ModuleManager.INSTANCE.all()) if (m instanceof HudModule) { m.setEnabled(true); n++; }
            mod(Crosshair.class).setEnabled(true);
            note(n + " HUD modules enabled");
        }});
        step(5, "hud shot", new Step() { public void run(MinecraftClient mc) { shot(mc, "02-hud-all"); }});

        // --- Funktionen ---
        step(30, "sprint", new Step() { public void run(MinecraftClient mc) {
            ToggleSprint ts = mod(ToggleSprint.class);
            ts.setEnabled(true);
            ts.mode.set("Always");
            KeyBinding.setKeyPressed(mc.options.forwardKey.getCode(), true);
        }});
        step(5, "sprint check", new Step() { public void run(MinecraftClient mc) {
            ok(mc.player.isSprinting(), "Toggle Sprint: player sprints while walking forward");
            shot(mc, "03-sprinting");
            KeyBinding.setKeyPressed(mc.options.forwardKey.getCode(), false);
        }});
        step(5, "fullbright", new Step() { public void run(MinecraftClient mc) { mod(Fullbright.class).setEnabled(true); }});
        step(5, "fullbright check", new Step() { public void run(MinecraftClient mc) {
            ok(mc.options.gamma >= 9f, "Fullbright raises gamma (" + mc.options.gamma + ")");
            mod(Fullbright.class).setEnabled(false);
            ok(mc.options.gamma <= 1f, "Fullbright restores gamma (" + mc.options.gamma + ")");
        }});
        step(40, "summon", new Step() { public void run(MinecraftClient mc) {
            mc.player.sendChatMessage("/summon Villager ~2 ~ ~ {NoAI:1,Invulnerable:1}");
        }});
        step(10, "attack", new Step() { public void run(MinecraftClient mc) {
            LivingEntity v = null;
            double best = 1e9;
            for (Entity e : mc.world.loadedEntities)
                if (e instanceof net.minecraft.entity.passive.VillagerEntity && mc.player.distanceTo(e) < best) { v = (LivingEntity) e; best = mc.player.distanceTo(e); }
            ok(v != null, "villager spawned");
            if (v == null) return;
            mod(HitColor.class).setEnabled(true);
            mc.player.yaw = (float) (Math.toDegrees(Math.atan2(v.z - mc.player.z, v.x - mc.player.x)) - 90);
            mc.player.pitch = 10;
            mc.interactionManager.attackEntity(mc.player, v);
            ok(Combat.reachFresh(2000) && Combat.lastReach > 0.5 && Combat.lastReach < 3.5, String.format("Reach Display measured %.2f blocks", Combat.lastReach));
            ok(Combat.combo >= 1, "Combo counter counts the hit (" + Combat.combo + ")");
        }});
        step(3, "hit shot", new Step() { public void run(MinecraftClient mc) { shot(mc, "04-hit-color"); }});
        step(5, "zoom", new Step() { public void run(MinecraftClient mc) {
            Zoom z = mod(Zoom.class);
            ok(z.isEnabled(), "Zoom is on by default");
            ok(Math.abs(z.divisor() - 1f) < 0.01f, "no zoom without key");
        }});
        step(20, "night", new Step() { public void run(MinecraftClient mc) {
            TimeChanger t = mod(TimeChanger.class);
            t.time.set("Night");
            t.setEnabled(true);
        }});
        step(3, "night check", new Step() { public void run(MinecraftClient mc) {
            ok(mc.world.getTimeOfDay() % 24000 == 18000, "Time Changer sets night (" + mc.world.getTimeOfDay() % 24000 + ")");
            shot(mc, "05-time-night");
            mod(TimeChanger.class).setEnabled(false);
        }});

        // --- Menues ---
        step(10, "clickgui", new Step() { public void run(MinecraftClient mc) { mc.setScreen(new ClickGui()); }});
        step(2, "clickgui shot", new Step() { public void run(MinecraftClient mc) { shot(mc, "06-menu-hud"); }});
        for (int i = 1; i < Module.Category.values().length; i++) {
            final int tab = i;
            step(10, "tab " + tab, new Step() { public void run(MinecraftClient mc) { mc.setScreen(new ClickGui().showTab(tab)); }});
            step(2, "tab shot " + tab, new Step() { public void run(MinecraftClient mc) { shot(mc, "07-menu-" + Module.Category.values()[tab].label.toLowerCase()); }});
        }
        step(10, "settings", new Step() { public void run(MinecraftClient mc) { mc.setScreen(new ClickGui().openModule(mod(Crosshair.class))); }});
        step(2, "settings shot", new Step() { public void run(MinecraftClient mc) { shot(mc, "08-menu-crosshair-settings"); }});
        step(10, "client settings", new Step() { public void run(MinecraftClient mc) { mc.setScreen(new ClickGui().showTab(-1)); }});
        step(2, "client settings shot", new Step() { public void run(MinecraftClient mc) { shot(mc, "09-menu-client-settings"); }});
        step(10, "hud editor", new Step() { public void run(MinecraftClient mc) { mc.setScreen(new HudEditorScreen(null)); }});
        step(2, "hud editor shot", new Step() { public void run(MinecraftClient mc) { shot(mc, "10-hud-editor"); }});
        step(10, "close", new Step() { public void run(MinecraftClient mc) { mc.setScreen(null); }});
        step(10, "freelook view", new Step() { public void run(MinecraftClient mc) {
            mc.options.perspective = 1;
        }});
        step(3, "third person shot", new Step() { public void run(MinecraftClient mc) { shot(mc, "11-third-person"); mc.options.perspective = 0; }});
    }
}
