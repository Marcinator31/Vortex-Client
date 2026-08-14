package com.vortex.client.macro;

import java.util.ArrayList;
import java.util.List;

/**
 * A recorded sequence of actions.
 *
 * A macro is a list of steps, each with a delay in front of it. Recording fills
 * that list from what you actually did; afterwards every delay can be typed in
 * by hand, which is usually the point — you record the sequence roughly and
 * then tighten the timing until it does what you want.
 *
 * Stored as text so a macro can be read, edited outside the game, and shared.
 */
public final class Macro {

    /** What a single step does. */
    public enum Action {
        LEFT_CLICK("Left click"),
        RIGHT_CLICK("Right click"),
        KEY("Hold key"),
        SLOT("Hotbar slot"),
        WAIT("Wait");

        public final String label;
        Action(String label) { this.label = label; }
    }

    /** One step: wait, then do something. */
    public static final class Step {
        public Action action;

        /**
         * Meaning depends on the action: the key code for KEY, the hotbar slot
         * (1-9) for SLOT, and how long to hold in milliseconds for KEY.
         */
        public int value;

        /** Milliseconds to wait before this step runs. */
        public int delay;

        /** How long a key is held down, in milliseconds. */
        public int hold;

        public Step(Action action, int value, int delay, int hold) {
            this.action = action;
            this.value = value;
            this.delay = delay;
            this.hold = hold;
        }

        public String describe() {
            switch (action) {
                case KEY:
                    return "Hold " + keyName(value) + " for " + hold + " ms";
                case SLOT:
                    return "Hotbar slot " + value;
                case WAIT:
                    return "Wait " + delay + " ms";
                default:
                    return action.label;
            }
        }

        private static String keyName(int code) {
            try {
                return net.minecraft.client.util.InputUtil.Type.KEYSYM
                        .createFromCode(code).getLocalizedText().getString().toUpperCase();
            } catch (Throwable pvpErr) {
                return "key " + code;
            }
        }
    }

    public String name;
    public final List<Step> steps = new ArrayList<>();

    /**
     * How often the macro runs.
     *
     * 0 means "keep going until stopped"; any other number is a count. Keyboard
     * software usually offers exactly this, and it is the difference between
     * "do this combo three times" and "hold this down".
     */
    public int repeat = 1;

    /**
     * How the key behaves.
     *
     * TOGGLE  press once to start, again to stop
     * HOLD    runs while the key is held, stops when released
     * ONCE    one pass per press, ignoring repeat
     */
    public Trigger trigger = Trigger.TOGGLE;

    /** Key that starts and stops this macro. */
    public int key = org.lwjgl.glfw.GLFW.GLFW_KEY_UNKNOWN;

    /**
     * Random variation added to every delay, in percent.
     *
     * A macro with identical delays every time is the easiest thing in the
     * world to spot -- no human produces the same gap twice. This spreads each
     * delay a little, which also makes a macro feel less mechanical to use.
     *
     * It does NOT make a macro undetectable, and nothing here should be relied
     * on for that.
     */
    public int jitter = 15;

    /** How the assigned key starts the macro. */
    public enum Trigger {
        TOGGLE("Toggle"), HOLD("While held"), ONCE("Once per press");
        public final String label;
        Trigger(String label) { this.label = label; }
    }

    /**
     * Speed as a percentage. 100 is as recorded, 50 is half as fast,
     * 200 twice as fast. Applies to every delay at once, which is easier than
     * editing thirty numbers when the whole thing is a touch too slow.
     */
    public int speed = 100;

    /** Milliseconds to wait after the key before the first step runs. */
    public int startDelay = 0;

    public Macro(String name) {
        this.name = name;
    }

    public int totalMs() {
        int t = 0;
        for (Step s : steps) t += s.delay + s.hold;
        return t;
    }

    // ---- text form: name;loop;key;jitter;action,value,delay,hold|... ----

    public String serialize() {
        StringBuilder sb = new StringBuilder();
        // Fields 5 onwards were added later. Older files simply lack them and
        // fall back to the defaults, so nothing is lost on an upgrade.
        sb.append(esc(name)).append(';')
          .append(repeat == 0 ? '1' : '0').append(';')
          .append(key).append(';')
          .append(jitter).append(';')
          .append(repeat).append(';')
          .append(speed).append(';')
          .append(startDelay).append(';')
          .append(trigger.name()).append(';');
        for (int i = 0; i < steps.size(); i++) {
            Step s = steps.get(i);
            if (i > 0) sb.append('|');
            sb.append(s.action.name()).append(',')
              .append(s.value).append(',')
              .append(s.delay).append(',')
              .append(s.hold);
        }
        return sb.toString();
    }

    public static Macro deserialize(String data) {
        if (data == null || data.isEmpty()) return null;
        String[] head = data.split(";", 9);
        if (head.length < 4) return null;
        try {
            Macro m = new Macro(unesc(head[0]));
            boolean oldLoop = "1".equals(head[1].trim());
            m.key = Integer.parseInt(head[2].trim());
            m.jitter = Integer.parseInt(head[3].trim());

            // Newer fields, absent in files written before they existed.
            m.repeat = oldLoop ? 0 : 1;
            if (head.length >= 8) {
                m.repeat = Integer.parseInt(head[4].trim());
                m.speed = Integer.parseInt(head[5].trim());
                m.startDelay = Integer.parseInt(head[6].trim());
                try {
                    m.trigger = Trigger.valueOf(head[7].trim());
                } catch (Throwable ignored) {
                    m.trigger = Trigger.TOGGLE;
                }
            }

            String stepData = (head.length >= 9) ? head[8]
                            : (head.length == 5 ? head[4] : "");
            if (!stepData.isEmpty()) {
                for (String part : stepData.split("\\|")) {
                    String[] p = part.split(",");
                    if (p.length < 4) continue;
                    m.steps.add(new Step(Action.valueOf(p[0].trim()),
                            Integer.parseInt(p[1].trim()),
                            Integer.parseInt(p[2].trim()),
                            Integer.parseInt(p[3].trim())));
                }
            }
            return m;
        } catch (Throwable pvpErr) {
            com.vortex.client.core.Errors.report("Macro.deserialize", pvpErr);
            return null;
        }
    }

    /** Separators are escaped so a name may contain any character. */
    private static String esc(String v) {
        if (v == null) return "";
        return v.replace("%", "%25").replace(";", "%3B")
                .replace("|", "%7C").replace(",", "%2C");
    }

    private static String unesc(String v) {
        if (v == null) return "";
        return v.replace("%7C", "|").replace("%2C", ",")
                .replace("%3B", ";").replace("%25", "%");
    }
}
