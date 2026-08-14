package com.vortex.client.macro;

import com.vortex.client.core.Errors;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Records and plays back macros.
 *
 * RECORDING captures what you actually do: clicks, keys held down, hotbar
 * switches, and the pauses between them. Timing is taken from the wall clock,
 * not from ticks, so a recording keeps its rhythm even if the frame rate moves
 * around.
 *
 * PLAYBACK walks the list step by step. Each step waits out its delay, then
 * runs, using the same calls the game itself uses for a click — so from the
 * server's point of view it looks like an ordinary action.
 *
 * ON DETECTION, PLAINLY: playback adds a little random spread to each delay,
 * because identical gaps are the single most obvious sign of automation. That
 * makes a macro look less mechanical. It does not make it invisible, and
 * nothing here is built to defeat any particular anti-cheat. A macro that
 * fights for you is automation, servers treat it as such, and the risk of a
 * ban is real.
 */
public final class MacroManager {

    private static final List<Macro> MACROS = new ArrayList<>();

    // ---- recording ----
    private static Macro recording = null;
    private static long lastEventTime = 0L;

    // ---- playback ----
    private static Macro playing = null;
    private static int stepIndex = 0;
    /** How many passes are done, for the repeat count. */
    private static int passesDone = 0;
    private static long nextStepAt = 0L;
    private static int heldKey = GLFW.GLFW_KEY_UNKNOWN;
    private static long releaseHeldAt = 0L;

    /** Which macro keys were down last tick, for edge detection. */
    private static final java.util.Map<String, Boolean> keyDown = new java.util.HashMap<>();

    private static final java.util.Random RANDOM = new java.util.Random();

    private MacroManager() {}

    public static synchronized List<Macro> all() {
        return MACROS;
    }

    public static synchronized Macro create(String name) {
        Macro m = new Macro((name == null || name.isBlank()) ? "Macro " + (MACROS.size() + 1) : name.trim());
        MACROS.add(m);
        return m;
    }

    public static synchronized void remove(Macro m) {
        if (playing == m) stop();
        if (recording == m) recording = null;
        MACROS.remove(m);
    }

    // ---------------------------------------------------------------- record

    public static boolean isRecording() {
        return recording != null;
    }

    public static Macro recordingMacro() {
        return recording;
    }

    /** Starts recording into a macro, replacing whatever it held. */
    public static synchronized void startRecording(Macro m) {
        stop();
        recording = m;
        m.steps.clear();
        lastEventTime = System.currentTimeMillis();
    }

    public static synchronized void stopRecording() {
        recording = null;
    }

    /**
     * Records one action.
     *
     * The delay stored is the time since the previous action, which is what
     * makes a recording play back at the speed it was performed.
     */
    public static synchronized void record(Macro.Action action, int value, int hold) {
        if (recording == null) return;
        long now = System.currentTimeMillis();
        int delay = (int) Math.min(now - lastEventTime, 60_000L);
        lastEventTime = now;
        // The first action needs no lead-in.
        if (recording.steps.isEmpty()) delay = 0;
        recording.steps.add(new Macro.Step(action, value, delay, hold));
    }

    // -------------------------------------------------------------- playback

    public static boolean isPlaying() {
        return playing != null;
    }

    public static Macro playingMacro() {
        return playing;
    }

    public static synchronized void play(Macro m) {
        if (m == null || m.steps.isEmpty()) return;
        stopRecording();
        playing = m;
        stepIndex = 0;
        passesDone = 0;
        // The start delay is the pause between pressing the key and the first
        // action -- useful when the macro should not fire the instant you press.
        nextStepAt = System.currentTimeMillis() + Math.max(0, m.startDelay);
    }

    public static synchronized void stop() {
        releaseHeldKey();
        playing = null;
        stepIndex = 0;
    }

    public static synchronized void toggle(Macro m) {
        if (playing == m) {
            stop();
        } else {
            play(m);
        }
    }

    // ------------------------------------------------------------------ tick

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            long t0 = System.nanoTime();
            try {
                tickRecording(client);
                tickKeys(client);
                tickPlayback(client);
            } catch (Throwable pvpErr) {
                Errors.report("MacroManager", pvpErr);
            } finally {
                com.vortex.client.core.Profiler.record("Macros", System.nanoTime() - t0);
            }
        });
    }

    // Which keys were held during recording, and since when.
    private static final java.util.Map<Integer, Long> recHeld = new java.util.HashMap<>();
    private static int recLastSlot = -1;

    /**
     * Watches keys and the hotbar while recording.
     *
     * Keys are stored with how long they were held, because "walk forward for
     * 400 ms" is a very different step from "tap forward". The hotbar is
     * recorded whenever the selected slot changes.
     */
    private static void tickRecording(MinecraftClient client) {
        if (recording == null || client.player == null) {
            recHeld.clear();
            recLastSlot = -1;
            return;
        }
        if (client.currentScreen != null) return;

        // Movement and action keys worth recording.
        var o = client.options;
        int[] codes = {
            keyCode(o.forwardKey), keyCode(o.backKey), keyCode(o.leftKey), keyCode(o.rightKey),
            keyCode(o.jumpKey), keyCode(o.sneakKey), keyCode(o.sprintKey), keyCode(o.dropKey)
        };
        long now = System.currentTimeMillis();
        for (int code : codes) {
            if (code == GLFW.GLFW_KEY_UNKNOWN) continue;
            boolean down = InputUtil.isKeyPressed(client.getWindow(), code);
            Long since = recHeld.get(code);
            if (down && since == null) {
                recHeld.put(code, now);
            } else if (!down && since != null) {
                recHeld.remove(code);
                record(Macro.Action.KEY, code, (int) Math.min(now - since, 30_000L));
            }
        }

        // Hotbar changes.
        int slot = client.player.getInventory().getSelectedSlot();
        if (recLastSlot < 0) {
            recLastSlot = slot;
        } else if (slot != recLastSlot) {
            recLastSlot = slot;
            record(Macro.Action.SLOT, slot + 1, 0);
        }
    }

    /** Key code behind a binding, or unknown. */
    private static int keyCode(net.minecraft.client.option.KeyBinding binding) {
        try {
            return net.minecraft.client.util.InputUtil.fromTranslationKey(
                    binding.getBoundKeyTranslationKey()).getCode();
        } catch (Throwable pvpErr) {
            return GLFW.GLFW_KEY_UNKNOWN;
        }
    }

    /** Starts and stops macros by their assigned keys. */
    private static void tickKeys(MinecraftClient client) {
        if (client.currentScreen != null) {
            keyDown.clear();
            return;
        }
        for (Macro m : all()) {
            if (m.key == GLFW.GLFW_KEY_UNKNOWN) continue;
            boolean down = InputUtil.isKeyPressed(client.getWindow(), m.key);
            boolean was = Boolean.TRUE.equals(keyDown.get(m.name));

            switch (m.trigger) {
                case HOLD:
                    // Runs while the key is down and stops the moment it is let go.
                    if (down && !was) play(m);
                    if (!down && was && playing == m) stop();
                    break;
                case ONCE:
                    if (down && !was) play(m);
                    break;
                case TOGGLE:
                default:
                    if (down && !was) toggle(m);
                    break;
            }
            keyDown.put(m.name, down);
        }
    }

    /** Runs the steps of the macro currently playing. */
    private static void tickPlayback(MinecraftClient client) {
        if (playing == null) return;
        if (client.player == null || client.world == null) {
            stop();
            return;
        }

        long now = System.currentTimeMillis();

        // Release a key whose hold time is up before anything else runs.
        if (heldKey != GLFW.GLFW_KEY_UNKNOWN && now >= releaseHeldAt) {
            releaseHeldKey();
        }

        // A screen interrupts playback: the macro would otherwise type into it.
        if (client.currentScreen != null) return;

        while (playing != null && now >= nextStepAt) {
            if (stepIndex >= playing.steps.size()) {
                passesDone++;
                // repeat 0 means "until stopped"; anything else is a count.
                boolean again = (playing.repeat == 0) || (passesDone < playing.repeat);
                if (playing.trigger == Macro.Trigger.ONCE) again = false;
                if (again) {
                    stepIndex = 0;
                } else {
                    stop();
                    return;
                }
            }
            Macro.Step step = playing.steps.get(stepIndex);
            stepIndex++;

            runStep(client, step);

            // Schedule the next one, with a little spread on the delay.
            // Speed scales every delay at once: 200 % halves them, 50 % doubles.
            int speed = Math.max(10, Math.min(400, playing.speed));
            int base = (int) Math.round(step.delay * 100.0 / speed);
            int spread = Math.max(0, playing.jitter);
            int varied = base;
            if (spread > 0 && base > 0) {
                int range = Math.max(1, base * spread / 100);
                varied = base + RANDOM.nextInt(range * 2 + 1) - range;
                if (varied < 0) varied = 0;
            }
            nextStepAt = now + varied;
            now = System.currentTimeMillis();
        }
    }

    private static void runStep(MinecraftClient client, Macro.Step step) {
        try {
            switch (step.action) {
                case LEFT_CLICK:
                    // The same call the game makes on a left click, so it
                    // attacks or breaks depending on what is in front of you.
                    ((com.vortex.client.mixin.client.MinecraftClientAccessor) client)
                            .pvpclient$invokeDoAttack();
                    break;

                case RIGHT_CLICK:
                    ((com.vortex.client.mixin.client.MinecraftClientAccessor) client)
                            .vortex$invokeDoItemUse();
                    break;

                case SLOT: {
                    int slot = Math.max(1, Math.min(9, step.value)) - 1;
                    client.player.getInventory().setSelectedSlot(slot);
                    break;
                }

                case KEY:
                    holdKey(client, step.value, step.hold);
                    break;

                case WAIT:
                default:
                    break;
            }
        } catch (Throwable pvpErr) {
            Errors.report("MacroManager.step", pvpErr);
        }
    }

    /**
     * Holds a key down for a while.
     *
     * Done through the game's own key bindings rather than by faking input at
     * the window level: that way movement, sneaking and jumping behave exactly
     * as if the key were really down, including everything the game does about
     * it.
     */
    private static void holdKey(MinecraftClient client, int code, int ms) {
        releaseHeldKey();
        try {
            net.minecraft.client.option.KeyBinding.setKeyPressed(
                    InputUtil.Type.KEYSYM.createFromCode(code), true);
            heldKey = code;
            releaseHeldAt = System.currentTimeMillis() + Math.max(20, ms);
        } catch (Throwable pvpErr) {
            Errors.report("MacroManager.holdKey", pvpErr);
        }
    }

    private static void releaseHeldKey() {
        if (heldKey == GLFW.GLFW_KEY_UNKNOWN) return;
        try {
            net.minecraft.client.option.KeyBinding.setKeyPressed(
                    InputUtil.Type.KEYSYM.createFromCode(heldKey), false);
        } catch (Throwable pvpErr) {
            Errors.report("MacroManager.releaseKey", pvpErr);
        }
        heldKey = GLFW.GLFW_KEY_UNKNOWN;
    }

    // --------------------------------------------------------- save and load

    public static synchronized String serialize() {
        StringBuilder sb = new StringBuilder();
        for (Macro m : MACROS) {
            if (sb.length() > 0) sb.append('\n');
            sb.append(m.serialize());
        }
        return sb.toString().replace("\n", "%0A");
    }

    public static synchronized void deserialize(String data) {
        MACROS.clear();
        if (data == null || data.isEmpty()) return;
        for (String line : data.split("%0A")) {
            Macro m = Macro.deserialize(line);
            if (m != null) MACROS.add(m);
        }
    }
}
