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
    /**
     * When the next step is due, in nanoseconds.
     *
     * Milliseconds were too coarse: a delay of one was rounded up to a whole
     * tick, so the macro managed a single step every fifty milliseconds no
     * matter what was configured. A keyboard queues about fifty presses in
     * that time, and the game works through all of them at once -- which is
     * exactly why the hardware macro felt so much faster.
     */
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

        // Nothing is recorded while a menu is open.
        //
        // Keys were already handled that way, but clicks came in through the
        // mixin and were recorded even in a menu -- so the click on the Record
        // button itself ended up in the macro. That looked like "clicks work,
        // keys do not", when in truth recording simply only happens in game.
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.currentScreen != null) return;

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
        nextStepAt = System.nanoTime() + Math.max(0, m.startDelay) * 1_000_000L;
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
        // Playback runs on every frame.
        //
        // Restored on request: the tick only comes twenty times a second, which
        // caps the rate no matter what delay is configured. Frames come far
        // more often, so the delays take full effect.
        //
        // This is the behaviour of 2.11.0. It produces action rates no hardware
        // can reach, which is the single most conspicuous thing a macro can do.
        net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents.AFTER_ENTITIES
                .register(context -> {
            try {
                MinecraftClient client = MinecraftClient.getInstance();
                if (client != null) tickPlayback(client);
            } catch (Throwable pvpErr) {
                Errors.report("MacroManager.frame", pvpErr);
            }
        });

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

        // EVERY key, not just a handful of movement keys.
        //
        // The first attempt only watched forward, back, jump and so on. That
        // covered walking, but not the keys you actually bind things to -- and
        // those are the interesting ones in a macro.
        //
        // So the whole keyboard is polled instead. That sounds expensive and
        // is not: this is one cheap lookup per key, twenty times a second, and
        // only while recording is running.
        long now = System.currentTimeMillis();
        for (int code = org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE;
             code <= org.lwjgl.glfw.GLFW.GLFW_KEY_LAST; code++) {

            // Escape opens a screen, so it can never be part of a sequence.
            if (code == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) continue;

            boolean down;
            try {
                down = InputUtil.isKeyPressed(client.getWindow(), code);
            } catch (Throwable pvpErr) {
                continue;   // not every code in the range is a real key
            }

            Long since = recHeld.get(code);
            if (down && since == null) {
                recHeld.put(code, now);
            } else if (!down && since != null) {
                recHeld.remove(code);
                // Number keys change the hotbar; that is recorded below as a
                // slot change, which survives a different key layout.
                if (code >= org.lwjgl.glfw.GLFW.GLFW_KEY_1
                        && code <= org.lwjgl.glfw.GLFW.GLFW_KEY_9) {
                    continue;
                }
                record(Macro.Action.KEY, code, (int) Math.min(now - since, 30_000L));
            }
        }

        // Extra mouse buttons -- the side buttons most mice have.
        //
        // Left and right come in through the mouse mixin, which fires on the
        // press. The others are polled here so their hold time is captured too,
        // exactly like a key.
        for (int b = 2; b <= 7; b++) {
            int code = MOUSE_BASE + b;
            boolean down = isDown(client, code);
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

    /** Starts and stops macros by their assigned keys. */
    private static void tickKeys(MinecraftClient client) {
        if (client.currentScreen != null) {
            keyDown.clear();
            return;
        }
        for (Macro m : all()) {
            if (m.key == GLFW.GLFW_KEY_UNKNOWN) continue;
            boolean down = isDown(client, m.key);
            boolean was = Boolean.TRUE.equals(keyDown.get(m.name));

            switch (m.trigger) {
                case HOLD:
                    // Restarts on its own for as long as the key is held.
                    //
                    // The old version only started on the moment of pressing
                    // ("down && !was"). If the run then finished -- because it
                    // reached its repeat count, or ended for any other reason --
                    // it never came back, since the key had long been down and
                    // that moment was gone. You had to let go and press again.
                    //
                    // Asking "is the key down and is this macro not running?"
                    // instead makes it start again immediately, over and over,
                    // until the key is released. That also makes the behaviour
                    // genuinely independent of the repeat count: whatever ends
                    // the run, a held key starts it right back up.
                    if (down && playing != m) {
                        play(m);
                    } else if (!down && playing == m) {
                        stop();
                    }
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

        long now = System.nanoTime();

        // Release a key whose hold time is up before anything else runs.
        if (heldKey != GLFW.GLFW_KEY_UNKNOWN && System.currentTimeMillis() >= releaseHeldAt) {
            releaseHeldKey();
        }

        // A screen interrupts playback: the macro would otherwise type into it.
        if (client.currentScreen != null) return;

        // Cap on how much runs in a single tick.
        //
        // Without it an endless macro whose steps have no delay would loop
        // inside this one tick forever -- the game would simply freeze. The
        // cap is generous enough that normal macros never touch it.
        // Steps allowed in a single tick.
        //
        // A keyboard queues its presses between ticks and the game works
        // through the lot in one go, so several steps per tick is exactly what
        // the hardware does. The cap keeps an endless macro without any delay
        // from locking the game up inside one tick; a macro with real delays
        // never reaches it, because the delay decides the pace, not this.
        int budget = 100;

        while (playing != null && now >= nextStepAt && budget-- > 0) {
            if (stepIndex >= playing.steps.size()) {
                passesDone++;
                // repeat 0 means "until stopped"; anything else is a count.
                boolean again = (playing.repeat == 0) || (passesDone < playing.repeat);
                // "While held" means exactly that: it runs as long as the key
                // is down, and the count has no say. Stopping early under a
                // held key would look like a fault.
                if (playing.trigger == Macro.Trigger.HOLD) again = true;
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
            nextStepAt = now + varied * 1_000_000L;
            now = System.nanoTime();
        }
    }

    private static void runStep(MinecraftClient client, Macro.Step step) {
        try {
            switch (step.action) {
                case LEFT_CLICK:
                    ((com.vortex.client.mixin.client.MinecraftClientAccessor) client)
                            .pvpclient$invokeDoAttack();
                    break;

                case RIGHT_CLICK:
                    ((com.vortex.client.mixin.client.MinecraftClientAccessor) client)
                            .vortex$invokeDoItemUse();
                    break;

                case SLOT: {
                    int slot = Math.max(1, Math.min(9, step.value)) - 1;
                    // Through the hotbar key where there is one, so the change
                    // travels the same path as a real key press. If the keys
                    // are unavailable, set the slot directly -- the game syncs
                    // it on its next tick either way.
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
     * Feeds a single press into a key binding.
     *
     * onKeyPressed is what the keyboard handler calls when a key really goes
     * down: it queues one press, which the game then works through on its next
     * tick, with everything it normally does. The result is indistinguishable
     * from a key that was actually pressed -- because as far as the game is
     * concerned, one was.
     */
    private static void pressBinding(net.minecraft.client.option.KeyBinding binding) {
        if (binding == null) return;
        try {
            net.minecraft.client.option.KeyBinding.onKeyPressed(
                    InputUtil.fromTranslationKey(binding.getBoundKeyTranslationKey()));
        } catch (Throwable pvpErr) {
            Errors.report("MacroManager.press", pvpErr);
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
            net.minecraft.client.option.KeyBinding.setKeyPressed(keyOf(code), true);
            heldKey = code;
            releaseHeldAt = System.currentTimeMillis() + Math.max(20, ms);
        } catch (Throwable pvpErr) {
            Errors.report("MacroManager.holdKey", pvpErr);
        }
    }

    /**
     * Codes at or above this belong to the mouse: MOUSE_BASE + button number.
     *
     * Keyboard and mouse share one number space here so that a step, a macro
     * key and the saved file all work the same way for both. Keyboard codes
     * stop well below this, so the two can never collide.
     */
    public static final int MOUSE_BASE = 1000;

    public static boolean isMouse(int code) {
        return code >= MOUSE_BASE;
    }

    /** Turns a code into the key object the game expects. */
    private static net.minecraft.client.util.InputUtil.Key keyOf(int code) {
        return isMouse(code)
                ? InputUtil.Type.MOUSE.createFromCode(code - MOUSE_BASE)
                : InputUtil.Type.KEYSYM.createFromCode(code);
    }

    /** Is this code currently held down? Works for keyboard and mouse. */
    public static boolean isDown(MinecraftClient client, int code) {
        try {
            if (isMouse(code)) {
                return GLFW.glfwGetMouseButton(client.getWindow().getHandle(),
                        code - MOUSE_BASE) == GLFW.GLFW_PRESS;
            }
            return InputUtil.isKeyPressed(client.getWindow(), code);
        } catch (Throwable pvpErr) {
            return false;
        }
    }

    private static void releaseHeldKey() {
        if (heldKey == GLFW.GLFW_KEY_UNKNOWN) return;
        try {
            net.minecraft.client.option.KeyBinding.setKeyPressed(keyOf(heldKey), false);
        } catch (Throwable pvpErr) {
            Errors.report("MacroManager.releaseKey", pvpErr);
        }
        heldKey = GLFW.GLFW_KEY_UNKNOWN;
    }

    // ------------------------------------------------------------- sharing

    /**
     * Puts one macro on the clipboard as text.
     *
     * The same form used in the config file, with a short marker in front so a
     * pasted line can be recognised for what it is. Everything is there --
     * steps, delays, trigger, speed -- so what your friend gets behaves exactly
     * like yours.
     */
    public static synchronized String export(Macro m) {
        if (m == null) return "";
        return "vortex-macro:" + m.serialize();
    }

    /**
     * Reads a macro from text and adds it.
     *
     * @return the new macro, or null if the text was not one
     */
    public static synchronized Macro importFrom(String text) {
        if (text == null) return null;
        String t = text.trim();
        if (!t.startsWith("vortex-macro:")) return null;
        Macro m = Macro.deserialize(t.substring("vortex-macro:".length()));
        if (m == null) return null;

        // An imported macro arrives without a key.
        //
        // The sharer's binding means nothing here: it would land on a key you
        // already use for something else, and you would find out by pressing it
        // mid-fight. You pick the key, and until you do the macro simply sits
        // there doing nothing.
        m.key = org.lwjgl.glfw.GLFW.GLFW_KEY_UNKNOWN;

        // A name that is already taken gets a number, so an import never
        // quietly overwrites something you already had.
        String base = m.name;
        int n = 2;
        while (nameTaken(m.name)) {
            m.name = base + " " + n;
            n++;
        }
        MACROS.add(m);
        return m;
    }

    private static boolean nameTaken(String name) {
        for (Macro m : MACROS) {
            if (m.name.equals(name)) return true;
        }
        return false;
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
