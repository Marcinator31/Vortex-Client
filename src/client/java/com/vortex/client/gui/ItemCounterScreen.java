package com.vortex.client.gui;

import com.vortex.client.hud.ItemCounter;
import com.vortex.client.hud.ItemCounterRenderer;
import com.vortex.client.module.ModuleManager;
import com.vortex.client.module.modules.ItemCounterModule;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Manages the item counters.
 *
 * Each row is one counter: rename it, pick its items, choose how it looks, drop
 * it. Where it sits on screen is set in the HUD editor, where every counter
 * appears alongside the built-in elements and is dragged the same way -- there
 * is no reason to have two different ways of moving something.
 */
public class ItemCounterScreen extends Screen {

    private static final int HEADER_H = 56;
    private static final int FOOTER_H = 24;
    private static final int ROW_H = 26;

    private static final int C_DIM    = 0xB4000000;
    private static final int C_WINDOW = 0xF21B1B21;
    private static final int C_BAR    = 0xFF16161B;
    private static final int C_CARD   = 0xFF24242B;
    private static final int C_HOV    = 0xFF2E2E38;
    private static final int C_INNER  = 0xFF1C1C22;
    private static final int C_LINE   = 0xFF31313A;

    private final Screen parent;

    private EditBox nameField;
    private ItemCounter renaming = null;
    private ItemCounter pendingDelete = null;
    private String status = "";

    private float openAnim = 0f;
    private long lastNano = 0L;
    private int mx = 0, my = 0;
    private float scroll = 0f, scrollTarget = 0f;
    private int winX, winY, winW, winH, listH;

    private enum Act { BACK, NEW, PICK, RENAME, DELETE, STYLE, HIDE, COLOR,
                       COLOR_ALL, APPLY }

    private static final class Hit {
        final int x, y, w, h; final Act act; final ItemCounter data;
        Hit(int x, int y, int w, int h, Act act, ItemCounter data) {
            this.x = x; this.y = y; this.w = w; this.h = h; this.act = act; this.data = data;
        }
        boolean has(int px, int py) { return px >= x && px < x + w && py >= y && py < y + h; }
    }
    private final List<Hit> hits = new ArrayList<>();

    public ItemCounterScreen(Screen parent) {
        super(Component.literal("Item counters"));
        this.parent = parent;
    }

    private ItemCounterModule mod() {
        return ModuleManager.INSTANCE.get(ItemCounterModule.class);
    }

    @Override
    protected void init() {
        winW = Math.min(this.width - 20, 580);
        winH = Math.min(this.height - 20, 380);
        winX = (this.width - winW) / 2;
        winY = (this.height - winH) / 2;
        listH = winH - HEADER_H - FOOTER_H;

        nameField = new EditBox(this.font,
                winX + 90, winY + winH - 18, 180, 14, Component.literal(""));
        nameField.setBordered(false);
        nameField.setMaxLength(24);
        nameField.setVisible(false);
        this.addRenderableWidget(nameField);
    }

    @Override
    public void render(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
        this.mx = mouseX;
        this.my = mouseY;
        hits.clear();

        long now = System.nanoTime();
        float dt = (lastNano == 0L) ? 0.016f : (now - lastNano) / 1_000_000_000.0f;
        lastNano = now;
        if (dt > 0.1f) dt = 0.1f;
        openAnim += (1f - openAnim) * Math.min(1f, 14f * dt);
        scroll += (scrollTarget - scroll) * Math.min(1f, 18f * dt);

        ctx.fill(0, 0, this.width, this.height, fade(C_DIM, openAnim));
        int accent = Theme.INSTANCE.accent.get() | 0xFF000000;

        roundRect(ctx, winX, winY, winW, winH, fade(C_WINDOW, openAnim));
        ctx.fill(winX, winY, winX + winW, winY + 1, fade(accent, openAnim));

        // Header
        ctx.fill(winX, winY, winX + winW, winY + HEADER_H, fade(C_BAR, openAnim));
        ctx.fill(winX, winY + HEADER_H - 1, winX + winW, winY + HEADER_H, fade(C_LINE, openAnim));

        boolean backHov = in(winX + 8, winY + 8, 16, 16);
        ctx.drawString(this.font, Component.literal("<"),
                winX + 12, winY + 12, backHov ? accent : 0xFF9A9AA6);
        hits.add(new Hit(winX + 8, winY + 8, 16, 16, Act.BACK, null));

        ctx.drawString(this.font, Component.literal("Item counters"),
                winX + 30, winY + 11, 0xFFFFFFFF);

        button(ctx, winX + 12, winY + 30, "New counter", Act.NEW, null, accent, false);

        // Only offered when there is more than one, since applying a colour to
        // a single counter is just setting its colour.
        ItemCounterModule mAll = mod();
        if (mAll != null && mAll.getCounters().size() > 1) {
            int nx = winX + 12 + this.font.width("New counter") + 16 + 6;
            button(ctx, nx, winY + 30, "One colour for all", Act.COLOR_ALL, null, accent, false);
        }

        ItemCounterModule m = mod();
        List<ItemCounter> list = (m == null) ? List.of() : m.getCounters();

        String c = list.size() + " on screen";
        int cw = this.font.width(c);
        ctx.drawString(this.font, Component.literal(c),
                winX + winW - cw - 12, winY + 12, 0xFF74747F, false);

        // List
        ctx.enableScissor(winX, winY + HEADER_H, winX + winW, winY + HEADER_H + listH);
        int y = winY + HEADER_H + 4 - (int) scroll;
        Minecraft client = Minecraft.getInstance();

        if (list.isEmpty()) {
            ctx.drawString(this.font,
                    Component.literal("No counters yet. Press New counter, then pick items."),
                    winX + 16, y + 8, 0xFF6A6A76, false);
        }

        for (ItemCounter counter : new ArrayList<>(list)) {
            if (y + ROW_H >= winY + HEADER_H && y <= winY + HEADER_H + listH) {
                boolean hov = in(winX + 8, y, winW - 16, ROW_H);
                roundRect(ctx, winX + 8, y, winW - 16, ROW_H, hov ? C_HOV : C_CARD);

                ctx.drawString(this.font, Component.literal(counter.name),
                        winX + 16, y + 4, 0xFFFFFFFF, false);

                int n = (client.player == null) ? 0
                        : ItemCounterRenderer.count(client, counter);
                String sub = counter.items.size() + " items  ·  currently " + n;
                ctx.drawString(this.font, Component.literal(sub),
                        winX + 16, y + 14, 0xFF74747F, false);

                int bx = winX + winW - 24;
                ctx.drawString(this.font, Component.literal("x"),
                        bx - 12, y + 9, pendingDelete == counter ? 0xFFFF3030 : 0xFFFF7A7A, false);
                ctx.drawString(this.font, Component.literal("R"),
                        bx - 30, y + 9, 0xFF9A9AA6, false);
                ctx.drawString(this.font, Component.literal("H"),
                        bx - 48, y + 9, counter.hideEmpty.get() ? accent : 0xFF9A9AA6, false);
                ctx.drawString(this.font, Component.literal("S"),
                        bx - 66, y + 9, 0xFF9AD8FF, false);

                // Colour swatch: shows the colour and opens the picker.
                // A square of the actual colour says more than any label.
                int swx = bx - 90;
                roundRect(ctx, swx, y + 6, 16, 14, 0xFF000000);
                ctx.fill(swx + 1, y + 7, swx + 15, y + 19, counter.color.get());

                String pick = "Items";
                int pw = this.font.width(pick) + 14;
                roundRect(ctx, bx - 94 - pw, y + 5, pw, 16, C_INNER);
                ctx.drawString(this.font, Component.literal(pick),
                        bx - 87 - pw, y + 9, 0xFFD0D0DA, false);

                hits.add(new Hit(bx - 94 - pw, y + 5, pw, 16, Act.PICK, counter));
                hits.add(new Hit(swx, y + 6, 16, 14, Act.COLOR, counter));
                hits.add(new Hit(bx - 68, y + 7, 14, 12, Act.STYLE, counter));
                hits.add(new Hit(bx - 50, y + 7, 14, 12, Act.HIDE, counter));
                hits.add(new Hit(bx - 32, y + 7, 14, 12, Act.RENAME, counter));
                hits.add(new Hit(bx - 14, y + 7, 14, 12, Act.DELETE, counter));
            }
            y += ROW_H + 3;
        }
        ctx.disableScissor();

        // Footer
        int fy = winY + winH - FOOTER_H;
        ctx.fill(winX, fy, winX + winW, winY + winH, fade(C_BAR, openAnim));
        ctx.fill(winX, fy, winX + winW, fy + 1, fade(C_LINE, openAnim));

        if (renaming != null) {
            ctx.drawString(this.font, Component.literal("Name:"),
                    winX + 12, fy + 8, 0xFFD0D0DA, false);
            roundRect(ctx, winX + 86, fy + 4, 188, 16, C_INNER);
            String ok = "Apply";
            int okw = this.font.width(ok) + 14;
            boolean hov = in(winX + 282, fy + 4, okw, 16);
            roundRect(ctx, winX + 282, fy + 4, okw, 16,
                    hov ? mix(C_INNER, accent, 0.45f) : C_INNER);
            ctx.drawString(this.font, Component.literal(ok),
                    winX + 289, fy + 8, 0xFFFFFFFF, false);
            hits.add(new Hit(winX + 282, fy + 4, okw, 16, Act.APPLY, null));
        } else {
            String hint = status.isEmpty()
                    ? "Square = colour  ·  S style  ·  H hide at zero  ·  R rename  ·  position in the HUD editor"
                    : status;
            ctx.drawString(this.font, Component.literal(hint),
                    winX + 12, fy + 8, status.isEmpty() ? 0xFF74747F : 0xFFD0D0DA, false);
        }

        if (nameField != null) { nameField.setX(winX + 90); nameField.setY(winY + winH - 18); }
        super.render(ctx, mouseX, mouseY, delta);
    }

    private void button(GuiGraphics ctx, int x, int y, String label, Act act,
                        ItemCounter data, int accent, boolean active) {
        int w = this.font.width(label) + 16;
        boolean hov = in(x, y, w, 20);
        roundRect(ctx, x, y, w, 20,
                active ? mix(C_INNER, accent, 0.5f) : (hov ? mix(C_INNER, accent, 0.3f) : C_INNER));
        ctx.drawString(this.font, Component.literal(label), x + 8, y + 6, 0xFFE6E6EC, false);
        hits.add(new Hit(x, y, w, 20, act, data));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;

        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit h = hits.get(i);
            if (!h.has(mx, my)) continue;
            ItemCounterModule m = mod();
            switch (h.act) {
                case BACK:
                    this.onClose();
                    return true;
                case NEW:
                    if (m != null) {
                        m.create(null);
                        com.vortex.client.core.ConfigManager.save();
                        status = "Created. Pick its items next.";
                    }
                    return true;
                case PICK:
                    Minecraft.getInstance().setScreen(
                            new ItemPickScreen(this, h.data));
                    return true;
                case STYLE:
                    h.data.style.cycle();
                    com.vortex.client.core.ConfigManager.save();
                    status = "Style: " + h.data.style.get();
                    return true;
                case HIDE:
                    h.data.hideEmpty.set(!h.data.hideEmpty.get());
                    com.vortex.client.core.ConfigManager.save();
                    status = h.data.hideEmpty.get()
                            ? "Hidden while empty." : "Always visible.";
                    return true;
                case RENAME:
                    renaming = h.data;
                    if (nameField != null) {
                        nameField.setValue(h.data.name);
                        nameField.setVisible(true);
                        this.setFocused(nameField);
                    }
                    return true;
                case DELETE:
                    if (pendingDelete != h.data) {
                        pendingDelete = h.data;
                        status = "Click x again to delete \"" + h.data.name + "\".";
                        return true;
                    }
                    if (m != null) m.remove(h.data);
                    pendingDelete = null;
                    com.vortex.client.core.ConfigManager.save();
                    status = "Deleted.";
                    return true;
                case COLOR:
                    // The picker writes straight into this counter's setting.
                    Minecraft.getInstance().setScreen(
                            new ColorPickerScreen(this, h.data.color));
                    return true;
                case COLOR_ALL: {
                    // Picks on the first counter, and every change is copied to
                    // the rest -- so the result is visible while choosing,
                    // rather than only after closing the picker.
                    if (m == null || m.getCounters().isEmpty()) return true;
                    ItemCounter first = m.getCounters().get(0);
                    Minecraft.getInstance().setScreen(
                            new ColorPickerScreen(this, first.color, () -> {
                                int col = first.color.get();
                                for (ItemCounter other : m.getCounters()) {
                                    other.color.set(col);
                                }
                                com.vortex.client.core.ConfigManager.save();
                            }));
                    return true;
                }
                case APPLY:
                    applyName();
                    return true;
                default:
                    break;
            }
        }
        return false;
    }

    private void applyName() {
        if (renaming == null || nameField == null) return;
        String n = nameField.getValue().trim();
        if (n.isEmpty()) {
            status = "Name cannot be empty.";
            return;
        }
        renaming.name = n;
        renaming = null;
        nameField.setVisible(false);
        com.vortex.client.core.ConfigManager.save();
        status = "Renamed.";
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        ItemCounterModule m = mod();
        int n = (m == null) ? 0 : m.getCounters().size();
        int content = n * (ROW_H + 3) + 8;
        scrollTarget -= (float) delta * 30f;
        float max = Math.max(0f, content - listH);
        if (scrollTarget < 0f) scrollTarget = 0f;
        if (scrollTarget > max) scrollTarget = max;
        return true;
    }

    private boolean in(int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private void roundRect(GuiGraphics ctx, int x, int y, int w, int h, int color) {
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
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        com.vortex.client.core.ConfigManager.save();
        Minecraft.getInstance().setScreen(parent);
    }
}
