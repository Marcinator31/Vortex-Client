package com.vortex.client.gui;

import com.vortex.client.macro.Macro;
import com.vortex.client.macro.MacroManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * Manages macros: record, edit, bind a key, play.
 *
 * Two panes. On the left the macros; on the right the steps of whichever is
 * selected, each with its delay as a number you can type over. That is where
 * the real work happens — recording gets the sequence roughly right, and the
 * numbers make it exact.
 */
public class MacroScreen extends Screen {
    /** Base height of the header; grows when the buttons wrap. */
    private static final int HEADER_BASE_H = 62;

    /** Actual header height for this frame. */
    private int headerH = HEADER_BASE_H;
    private static final int FOOTER_H = 24;
    private static final int ROW_H = 22;
    /** Width of the macro list. Worked out from the window width. */
    private int listW = 210;

    private static final int C_DIM    = 0xB4000000;
    private static final int C_WINDOW = 0xF21B1B21;
    private static final int C_BAR    = 0xFF16161B;
    private static final int C_CARD   = 0xFF24242B;
    private static final int C_HOV    = 0xFF2E2E38;
    private static final int C_INNER  = 0xFF1C1C22;
    private static final int C_LINE   = 0xFF31313A;

    private final Screen parent;

    private Macro selected = null;
    private Macro renaming = null;
    private Macro.Step editing = null;
    private Macro bindingKey = null;
    private Macro pendingDelete = null;

    private TextFieldWidget nameField;
    private TextFieldWidget valueField;

    private String status = "";
    private float openAnim = 0f;
    private long lastNano = 0L;
    private int mx = 0, my = 0;
    private float scroll = 0f, scrollTarget = 0f;

    private int winX, winY, winW, winH, listH;

    private enum Act { BACK, NEW, PICK, RECORD, PLAY, TRIGGER, BIND, RENAME, DELETE,
                       STEP_DELAY, STEP_HOLD, STEP_DEL, STEP_UP, STEP_DOWN,
                       ADD_WAIT, JITTER, REPEAT, SPEED, START_DELAY, APPLY,
                       SHARE, PASTE }

    private static final class Hit {
        final int x, y, w, h; final Act act; final Object data;
        Hit(int x, int y, int w, int h, Act act, Object data) {
            this.x = x; this.y = y; this.w = w; this.h = h; this.act = act; this.data = data;
        }
        boolean has(int px, int py) { return px >= x && px < x + w && py >= y && py < y + h; }
    }
    private final List<Hit> hits = new ArrayList<>();

    public MacroScreen(Screen parent) {
        super(Text.literal("Macros"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        // NEVER wider than the screen.
        //
        // This used to force a minimum of 620 pixels. On a small display -- or
        // simply at a large GUI scale -- the window then reached past both
        // edges, and the buttons at the sides could not be clicked at all.
        // A window that does not fit is worse than a cramped one.
        winW = Math.min(this.width - 20, 720);
        winH = Math.min(this.height - 20, 420);

        // The list of macros takes about a third, within sensible bounds, so
        // the step list keeps usable space on a narrow window.
        // Never wider than the window leaves room for.
        listW = Math.min(Math.max(110, Math.min(210, winW / 3)),
                         Math.max(60, winW - 120));
        winX = (this.width - winW) / 2;
        winY = (this.height - winH) / 2;
        listH = winH - headerH - FOOTER_H;

        nameField = new TextFieldWidget(this.textRenderer,
                winX + 90, winY + winH - 18, 180, 14, Text.literal(""));
        nameField.setDrawsBackground(false);
        nameField.setMaxLength(32);
        nameField.setVisible(false);
        this.addDrawableChild(nameField);

        valueField = new TextFieldWidget(this.textRenderer,
                winX + 90, winY + winH - 18, 90, 14, Text.literal(""));
        valueField.setDrawsBackground(false);
        valueField.setMaxLength(6);
        valueField.setVisible(false);
        this.addDrawableChild(valueField);

        if (selected == null && !MacroManager.all().isEmpty()) {
            selected = MacroManager.all().get(0);
        }
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        this.mx = mouseX;
        this.my = mouseY;
        hits.clear();

        long now = System.nanoTime();
        float dt = (lastNano == 0L) ? 0.016f : (now - lastNano) / 1_000_000_000.0f;
        lastNano = now;
        if (dt > 0.1f) dt = 0.1f;
        openAnim += (1f - openAnim) * Math.min(1f, 14f * dt);
        scroll += (scrollTarget - scroll) * Math.min(1f, 18f * dt);

        // Waiting for a key to bind: take the next one pressed.
        captureBindKey();

        ctx.fill(0, 0, this.width, this.height, fade(C_DIM, openAnim));
        int accent = Theme.INSTANCE.accent.get() | 0xFF000000;

        roundRect(ctx, winX, winY, winW, winH, fade(C_WINDOW, openAnim));
        ctx.fill(winX, winY, winX + winW, winY + 1, fade(accent, openAnim));

        drawHeader(ctx, accent);
        // The header works out its own height (the buttons may have wrapped),
        // so the space left for the lists is only known now.
        listH = Math.max(40, winH - headerH - FOOTER_H);
        drawList(ctx, accent);
        drawSteps(ctx, accent);
        drawFooter(ctx, accent);

        if (nameField != null) { nameField.setX(winX + 90); nameField.setY(winY + winH - 18); }
        if (valueField != null) { valueField.setX(winX + 144); valueField.setY(winY + winH - 18); }
        super.render(ctx, mouseX, mouseY, delta);
    }

    private void drawHeader(DrawContext ctx, int accent) {
        ctx.fill(winX, winY, winX + winW, winY + headerH, fade(C_BAR, openAnim));
        ctx.fill(winX, winY + headerH - 1, winX + winW, winY + headerH, fade(C_LINE, openAnim));

        boolean backHov = in(winX + 8, winY + 8, 16, 16);
        ctx.drawTextWithShadow(this.textRenderer, Text.literal("<"),
                winX + 12, winY + 12, backHov ? accent : 0xFF9A9AA6);
        hits.add(new Hit(winX + 8, winY + 8, 16, 16, Act.BACK, null));

        ctx.drawTextWithShadow(this.textRenderer, Text.literal("Macros"),
                winX + 30, winY + 11, 0xFFFFFFFF);

        String c = MacroManager.all().size() + " saved";
        int cw = this.textRenderer.getWidth(c);
        ctx.drawText(this.textRenderer, Text.literal(c),
                winX + winW - cw - 12, winY + 12, 0xFF74747F, false);

        // The buttons wrap onto a second row when they do not fit.
        //
        // They used to be laid out strictly left to right, so on a narrow
        // window the last ones ran off the edge and could not be reached --
        // including the key binding.
        int bx = winX + 12;
        int by = winY + 32;
        int right = winX + winW - 10;

        int[] pos = { bx, by };
        wrapButton(ctx, pos, right, "New macro", Act.NEW, null, accent, false);

        if (selected != null) {
            boolean rec = MacroManager.isRecording()
                    && MacroManager.recordingMacro() == selected;
            wrapButton(ctx, pos, right, rec ? "Stop recording" : "Record",
                    Act.RECORD, selected, accent, rec);

            boolean play = MacroManager.isPlaying() && MacroManager.playingMacro() == selected;
            wrapButton(ctx, pos, right, play ? "Stop" : "Play",
                    Act.PLAY, selected, accent, play);

            wrapButton(ctx, pos, right, "Trigger: " + selected.trigger.label,
                    Act.TRIGGER, selected, accent, false);

            String keyLabel = "Key: " + (bindingKey == selected ? "press a key" : keyName(selected.key));
            wrapButton(ctx, pos, right, keyLabel, Act.BIND, selected, accent,
                    bindingKey == selected);

            wrapButton(ctx, pos, right, "Copy", Act.SHARE, selected, accent, false);
        }
        wrapButton(ctx, pos, right, "Paste", Act.PASTE, null, accent, false);

        // How tall the header actually turned out.
        headerH = (pos[1] + 20 + 8) - winY;
    }

    /**
     * Draws a button and moves on, starting a new row when the width runs out.
     *
     * pos carries the current x and y between calls, which keeps the caller
     * free of layout arithmetic.
     */
    private void wrapButton(DrawContext ctx, int[] pos, int right, String label,
                            Act act, Object data, int accent, boolean active) {
        int w = this.textRenderer.getWidth(label) + 16;
        if (pos[0] + w > right && pos[0] > winX + 12) {
            pos[0] = winX + 12;
            pos[1] += 24;
        }
        button(ctx, pos[0], pos[1], label, act, data, accent, active);
        pos[0] += w + 6;
    }

    private int button(DrawContext ctx, int x, int y, String label, Act act,
                       Object data, int accent, boolean active) {
        int w = this.textRenderer.getWidth(label) + 16;
        boolean hov = in(x, y, w, 20);
        int bg = active ? mix(C_INNER, accent, 0.5f) : (hov ? mix(C_INNER, accent, 0.3f) : C_INNER);
        roundRect(ctx, x, y, w, 20, bg);
        ctx.drawText(this.textRenderer, Text.literal(label), x + 8, y + 6, 0xFFE6E6EC, false);
        hits.add(new Hit(x, y, w, 20, act, data));
        return x + w + 6;
    }

    private void drawList(DrawContext ctx, int accent) {
        int x = winX + 8;
        int y = winY + headerH + 6;
        ctx.fill(winX + listW, winY + headerH, winX + listW + 1,
                winY + headerH + listH, C_LINE);

        if (MacroManager.all().isEmpty()) {
            ctx.drawText(this.textRenderer, Text.literal("No macros yet"),
                    x + 6, y + 6, 0xFF6A6A76, false);
            return;
        }

        for (Macro m : MacroManager.all()) {
            boolean sel = m == selected;
            boolean hov = in(x, y, listW - 16, ROW_H);
            roundRect(ctx, x, y, listW - 16, ROW_H,
                    sel ? mix(C_CARD, accent, 0.28f) : (hov ? C_HOV : C_CARD));

            String label = shorten(m.name, listW - 70);
            ctx.drawText(this.textRenderer, Text.literal(label), x + 8, y + 3, 0xFFFFFFFF, false);
            ctx.drawText(this.textRenderer, Text.literal(m.steps.size() + " steps"),
                    x + 8, y + 12, 0xFF74747F, false);

            ctx.drawText(this.textRenderer, Text.literal("R"),
                    x + listW - 44, y + 7, 0xFF9AD8FF, false);
            ctx.drawText(this.textRenderer, Text.literal("x"),
                    x + listW - 28, y + 7,
                    pendingDelete == m ? 0xFFFF3030 : 0xFFFF7A7A, false);

            hits.add(new Hit(x + listW - 46, y + 5, 14, 12, Act.RENAME, m));
            hits.add(new Hit(x + listW - 30, y + 5, 14, 12, Act.DELETE, m));
            hits.add(new Hit(x, y, listW - 50, ROW_H, Act.PICK, m));
            y += ROW_H + 4;
        }
    }

    private void drawSteps(DrawContext ctx, int accent) {
        int x = winX + listW + 12;
        int w = winW - listW - 24;
        int top = winY + headerH;

        if (selected == null) {
            ctx.drawText(this.textRenderer, Text.literal("Pick a macro on the left"),
                    x, top + 10, 0xFF6A6A76, false);
            return;
        }

        // Everything that applies to the whole macro, as typeable numbers --
        // the same way keyboard software presents it.
        int bx = button(ctx, x, top + 6,
                "Repeat: " + (selected.repeat == 0 ? "until stopped" : selected.repeat + "x"),
                Act.REPEAT, selected, accent, false);
        bx = button(ctx, bx, top + 6, "Speed: " + selected.speed + "%",
                Act.SPEED, selected, accent, false);
        bx = button(ctx, bx, top + 6, "Spread: " + selected.jitter + "%",
                Act.JITTER, selected, accent, false);
        bx = button(ctx, bx, top + 6, "Start: " + selected.startDelay + " ms",
                Act.START_DELAY, selected, accent, false);
        button(ctx, bx, top + 6, "+ wait", Act.ADD_WAIT, selected, accent, false);

        int y = top + 32 - (int) scroll;
        ctx.enableScissor(x, top + 30, x + w, top + listH);

        if (selected.steps.isEmpty()) {
            ctx.drawText(this.textRenderer,
                    Text.literal("Empty. Press Record, do the sequence, press Stop."),
                    x, y + 6, 0xFF6A6A76, false);
        }

        int i = 1;
        for (Macro.Step step : new ArrayList<>(selected.steps)) {
            if (y + ROW_H >= top + 30 && y <= top + listH) {
                boolean hov = in(x, y, w, ROW_H);
                roundRect(ctx, x, y, w, ROW_H, hov ? C_HOV : C_CARD);

                ctx.drawText(this.textRenderer, Text.literal(String.valueOf(i)),
                        x + 7, y + 7, 0xFF5A5A66, false);
                ctx.drawText(this.textRenderer, Text.literal(step.describe()),
                        x + 26, y + 7, 0xFFE6E6EC, false);

                // Delay as an editable number.
                String d = step.delay + " ms";
                int dw = this.textRenderer.getWidth(d);
                int dx = x + w - dw - 118;
                roundRect(ctx, dx - 5, y + 4, dw + 10, 14,
                        editing == step ? mix(C_INNER, accent, 0.45f) : C_INNER);
                ctx.drawText(this.textRenderer, Text.literal(d), dx, y + 7, 0xFFD0D0DA, false);
                hits.add(new Hit(dx - 5, y + 4, dw + 10, 14, Act.STEP_DELAY, step));

                if (step.action == Macro.Action.KEY) {
                    String h = step.hold + " ms";
                    int hw = this.textRenderer.getWidth(h);
                    int hx = x + w - hw - 62;
                    roundRect(ctx, hx - 5, y + 4, hw + 10, 14, C_INNER);
                    ctx.drawText(this.textRenderer, Text.literal(h), hx, y + 7, 0xFFD0D0DA, false);
                    hits.add(new Hit(hx - 5, y + 4, hw + 10, 14, Act.STEP_HOLD, step));
                }

                ctx.drawText(this.textRenderer, Text.literal("^"), x + w - 44, y + 7, 0xFF9A9AA6, false);
                ctx.drawText(this.textRenderer, Text.literal("v"), x + w - 30, y + 7, 0xFF9A9AA6, false);
                ctx.drawText(this.textRenderer, Text.literal("x"), x + w - 16, y + 7, 0xFFFF7A7A, false);
                hits.add(new Hit(x + w - 46, y + 5, 12, 12, Act.STEP_UP, step));
                hits.add(new Hit(x + w - 32, y + 5, 12, 12, Act.STEP_DOWN, step));
                hits.add(new Hit(x + w - 18, y + 5, 12, 12, Act.STEP_DEL, step));
            }
            y += ROW_H + 3;
            i++;
        }
        ctx.disableScissor();
    }

    private void drawFooter(DrawContext ctx, int accent) {
        int fy = winY + winH - FOOTER_H;
        ctx.fill(winX, fy, winX + winW, winY + winH, fade(C_BAR, openAnim));
        ctx.fill(winX, fy, winX + winW, fy + 1, fade(C_LINE, openAnim));

        boolean editingSomething = renaming != null || editing != null
                || editingMacroField != null;
        if (editingSomething) {
            String label = (renaming != null) ? "Name:"
                    : (editingMacroField == Act.REPEAT) ? "Repeats (0 = endless):"
                    : (editingMacroField == Act.SPEED) ? "Speed in %:"
                    : (editingMacroField == Act.JITTER) ? "Spread in %:"
                    : (editingMacroField == Act.START_DELAY) ? "Start delay in ms:"
                    : "Milliseconds:";
            ctx.drawText(this.textRenderer, Text.literal(label),
                    winX + 12, fy + 8, 0xFFD0D0DA, false);
            // The labels differ in length, so the box starts where the longest
            // one ends -- otherwise "Start delay in ms:" would run into it.
            int fieldX = winX + (renaming != null ? 86 : 140);
            roundRect(ctx, fieldX, fy + 4, renaming != null ? 188 : 90, 16, C_INNER);
            String ok = "Apply";
            int okw = this.textRenderer.getWidth(ok) + 14;
            int oxx = fieldX + (renaming != null ? 196 : 98);
            boolean hov = in(oxx, fy + 4, okw, 16);
            roundRect(ctx, oxx, fy + 4, okw, 16, hov ? mix(C_INNER, accent, 0.45f) : C_INNER);
            ctx.drawText(this.textRenderer, Text.literal(ok), oxx + 7, fy + 8, 0xFFFFFFFF, false);
            hits.add(new Hit(oxx, fy + 4, okw, 16, Act.APPLY, null));
            return;
        }

        String hint = status.isEmpty()
                ? "Record captures clicks, keys and hotbar switches with their timing"
                : status;
        ctx.drawText(this.textRenderer, Text.literal(shorten(hint, winW - 24)),
                winX + 12, fy + 8, status.isEmpty() ? 0xFF74747F : 0xFFD0D0DA, false);
    }

    // ---------------------------------------------------------------- input

    /** While waiting for a binding, the next key pressed is taken. */
    private void captureBindKey() {
        if (bindingKey == null) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (net.minecraft.client.util.InputUtil.isKeyPressed(
                mc.getWindow(), org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE)) {
            bindingKey.key = org.lwjgl.glfw.GLFW.GLFW_KEY_UNKNOWN;
            bindingKey = null;
            save();
            status = "Key cleared.";
            return;
        }
        for (int code = org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE;
             code <= org.lwjgl.glfw.GLFW.GLFW_KEY_LAST; code++) {
            if (net.minecraft.client.util.InputUtil.isKeyPressed(mc.getWindow(), code)) {
                bindingKey.key = code;
                bindingKey = null;
                save();
                return;
            }
        }
        // Mouse buttons too, from the third onwards. Left and right are left
        // alone: they are needed to press the button that starts the binding.
        for (int b = 2; b <= 7; b++) {
            if (MacroManager.isDown(mc, MacroManager.MOUSE_BASE + b)) {
                bindingKey.key = MacroManager.MOUSE_BASE + b;
                bindingKey = null;
                save();
                return;
            }
        }
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.gui.Click click, boolean doubled) {
        if (super.mouseClicked(click, doubled)) return true;

        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit h = hits.get(i);
            if (!h.has(mx, my)) continue;
            switch (h.act) {
                case BACK:
                    this.close();
                    return true;
                case NEW:
                    selected = MacroManager.create(null);
                    save();
                    status = "Created. Press Record and do the sequence.";
                    return true;
                case PICK:
                    selected = (Macro) h.data;
                    scroll = 0f; scrollTarget = 0f;
                    return true;
                case RECORD: {
                    Macro m = (Macro) h.data;
                    if (MacroManager.isRecording() && MacroManager.recordingMacro() == m) {
                        MacroManager.stopRecording();
                        save();
                        status = m.steps.size() + " steps recorded.";
                    } else {
                        MacroManager.startRecording(m);
                        // Straight back into the game.
                        //
                        // Recording only happens in game -- a menu swallows
                        // every key, otherwise typing a name would end up in
                        // the macro. Leaving the menu open after pressing
                        // Record therefore looked exactly like a broken
                        // feature: you act, and nothing is captured. Closing
                        // it here removes the trap entirely.
                        MinecraftClient.getInstance().setScreen(null);
                    }
                    return true;
                }
                case PLAY:
                    MacroManager.toggle((Macro) h.data);
                    return true;
                case TRIGGER: {
                    Macro m = (Macro) h.data;
                    Macro.Trigger[] all = Macro.Trigger.values();
                    m.trigger = all[(m.trigger.ordinal() + 1) % all.length];
                    save();
                    return true;
                }
                case REPEAT:
                case SPEED:
                case START_DELAY: {
                    // All three are plain numbers, so they share one input box.
                    clearInputs();
                    editingMacroField = h.act;
                    editingMacro = (Macro) h.data;
                    if (valueField != null) {
                        int cur = (h.act == Act.REPEAT) ? editingMacro.repeat
                                : (h.act == Act.SPEED) ? editingMacro.speed
                                : editingMacro.startDelay;
                        valueField.setText(String.valueOf(cur));
                        valueField.setVisible(true);
                        this.setFocused(valueField);
                    }
                    return true;
                }
                case BIND:
                    bindingKey = (Macro) h.data;
                    return true;
                case RENAME: {
                    Macro m = (Macro) h.data;
                    clearInputs();
                    renaming = m;
                    if (nameField != null) {
                        nameField.setText(m.name);
                        nameField.setVisible(true);
                        this.setFocused(nameField);
                    }
                    return true;
                }
                case DELETE: {
                    Macro m = (Macro) h.data;
                    if (pendingDelete != m) {
                        pendingDelete = m;
                        status = "Click x again to delete \"" + m.name + "\".";
                        return true;
                    }
                    MacroManager.remove(m);
                    if (selected == m) selected = null;
                    pendingDelete = null;
                    save();
                    status = "Deleted.";
                    return true;
                }
                case STEP_DELAY:
                case STEP_HOLD: {
                    Macro.Step s = (Macro.Step) h.data;
                    clearInputs();
                    editing = s;
                    editingHold = (h.act == Act.STEP_HOLD);
                    if (valueField != null) {
                        valueField.setText(String.valueOf(editingHold ? s.hold : s.delay));
                        valueField.setVisible(true);
                        this.setFocused(valueField);
                    }
                    return true;
                }
                case STEP_DEL:
                    if (selected != null) {
                        selected.steps.remove((Macro.Step) h.data);
                        save();
                    }
                    return true;
                case STEP_UP:
                case STEP_DOWN: {
                    if (selected == null) return true;
                    int idx = selected.steps.indexOf((Macro.Step) h.data);
                    int to = idx + (h.act == Act.STEP_UP ? -1 : 1);
                    if (idx >= 0 && to >= 0 && to < selected.steps.size()) {
                        Macro.Step s = selected.steps.remove(idx);
                        selected.steps.add(to, s);
                        save();
                    }
                    return true;
                }
                case ADD_WAIT:
                    if (selected != null) {
                        selected.steps.add(new Macro.Step(Macro.Action.WAIT, 0, 200, 0));
                        save();
                    }
                    return true;
                case JITTER:
                    if (selected != null) {
                        clearInputs();
                        editingMacroField = Act.JITTER;
                        editingMacro = selected;
                        if (valueField != null) {
                            valueField.setText(String.valueOf(selected.jitter));
                            valueField.setVisible(true);
                            this.setFocused(valueField);
                        }
                    }
                    return true;
                case SHARE: {
                    Macro m = (Macro) h.data;
                    MinecraftClient.getInstance().keyboard.setClipboard(
                            MacroManager.export(m));
                    status = "Copied. Send it to a friend, they press Paste.";
                    return true;
                }
                case PASTE: {
                    String text = MinecraftClient.getInstance().keyboard.getClipboard();
                    Macro m = MacroManager.importFrom(text);
                    if (m == null) {
                        status = "Clipboard holds no Vortex macro.";
                    } else {
                        selected = m;
                        save();
                        status = "Imported: " + m.name;
                    }
                    return true;
                }
                case APPLY:
                    applyInput();
                    return true;
                default:
                    break;
            }
        }
        return false;
    }

    private boolean editingHold = false;

    /** Which macro-wide number is being typed, and for which macro. */
    private Act editingMacroField = null;
    private Macro editingMacro = null;

    private void clearInputs() {
        renaming = null;
        editing = null;
        editingMacroField = null;
        editingMacro = null;
        if (nameField != null) nameField.setVisible(false);
        if (valueField != null) valueField.setVisible(false);
    }

    private void applyInput() {
        // A macro-wide number: repeat, speed, spread or start delay.
        if (editingMacroField != null && editingMacro != null && valueField != null) {
            try {
                int v = Integer.parseInt(valueField.getText().trim());
                switch (editingMacroField) {
                    case REPEAT:
                        editingMacro.repeat = Math.max(0, Math.min(9999, v));
                        status = v == 0 ? "Repeats until stopped." : "Repeats " + v + " times.";
                        break;
                    case SPEED:
                        editingMacro.speed = Math.max(10, Math.min(400, v));
                        status = "Speed " + editingMacro.speed + "%.";
                        break;
                    case JITTER:
                        editingMacro.jitter = Math.max(0, Math.min(50, v));
                        status = "Spread " + editingMacro.jitter + "%.";
                        break;
                    case START_DELAY:
                        editingMacro.startDelay = Math.max(0, Math.min(10_000, v));
                        status = "Start delay " + editingMacro.startDelay + " ms.";
                        break;
                    default:
                        break;
                }
            } catch (NumberFormatException e) {
                status = "Enter a whole number.";
                return;
            }
            clearInputs();
            save();
            return;
        }

        if (renaming != null && nameField != null) {
            String n = nameField.getText().trim();
            if (n.isEmpty()) {
                status = "Name cannot be empty.";
                return;
            }
            renaming.name = n;
            status = "Renamed.";
        } else if (editing != null && valueField != null) {
            try {
                int v = Integer.parseInt(valueField.getText().trim());
                v = Math.max(0, Math.min(60_000, v));
                if (editingHold) editing.hold = v; else editing.delay = v;
                status = "Updated.";
            } catch (NumberFormatException e) {
                status = "Enter a whole number of milliseconds.";
                return;
            }
        }
        clearInputs();
        save();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY,
                                 double horizontal, double vertical) {
        if (selected == null) return true;
        int content = selected.steps.size() * (ROW_H + 3) + 40;
        scrollTarget -= (float) vertical * 30f;
        float max = Math.max(0f, content - listH);
        if (scrollTarget < 0f) scrollTarget = 0f;
        if (scrollTarget > max) scrollTarget = max;
        return true;
    }

    private void save() {
        com.vortex.client.core.ConfigManager.save();
    }

    // ----------------------------------------------------------- helpers

    private static String keyName(int code) {
        if (code == org.lwjgl.glfw.GLFW.GLFW_KEY_UNKNOWN) return "none";
        // Mouse buttons are counted the way people name them: button 3 is
        // "Mouse 4", because the first two are left and right.
        if (MacroManager.isMouse(code)) {
            return "Mouse " + (code - MacroManager.MOUSE_BASE + 1);
        }
        try {
            return net.minecraft.client.util.InputUtil.Type.KEYSYM
                    .createFromCode(code).getLocalizedText().getString().toUpperCase();
        } catch (Throwable pvpErr) {
            return "none";
        }
    }

    private String shorten(String s, int maxW) {
        if (this.textRenderer.getWidth(s) <= maxW) return s;
        String cur = s;
        while (cur.length() > 1 && this.textRenderer.getWidth(cur + "..") > maxW) {
            cur = cur.substring(0, cur.length() - 1);
        }
        return cur + "..";
    }

    private boolean in(int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private void roundRect(DrawContext ctx, int x, int y, int w, int h, int color) {
        if (w <= 0 || h <= 0) return;
        ctx.fill(x + 1, y, x + w - 1, y + h, color);
        ctx.fill(x, y + 1, x + 1, y + h - 1, color);
        ctx.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
    }

    private static int fade(int argb, float f) {
        if (f >= 1f) return argb;
        if (f <= 0f) return argb & 0x00FFFFFF;
        return ((int) (((argb >>> 24) & 0xFF) * f) << 24) | (argb & 0x00FFFFFF);
    }

    private static int mix(int a, int b, float t) {
        if (t < 0f) t = 0f;
        if (t > 1f) t = 1f;
        int aa = (a >>> 24) & 0xFF, ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int ba = (b >>> 24) & 0xFF, br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        return (((int) (aa + (ba - aa) * t)) << 24)
                | (((int) (ar + (br - ar) * t)) << 16)
                | (((int) (ag + (bg - ag) * t)) << 8)
                | ((int) (ab + (bb - ab) * t));
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void close() {
        save();
        this.client.setScreen(parent);
    }
}
