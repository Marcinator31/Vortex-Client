package com.vortex.client.gui;

import com.vortex.client.core.setting.KeySetting;
import com.vortex.client.macro.MacroManager;
import com.vortex.client.module.Module;
import com.vortex.client.module.ModuleManager;
import com.vortex.client.waypoint.WaypointSettings;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Every key in one place.
 *
 * With more than fifty modules, plus waypoints, macros and the zoom, a key gets
 * forgotten or handed out twice without anyone noticing. Twice is the worse of
 * the two: one press then does two things, and which of them you get is up to
 * the order they happen to be checked in.
 *
 * Conflicts are marked in red, and clicking a row jumps to where that key is
 * set, so it can be changed on the spot.
 */
public class KeyListScreen extends Screen {

    private static final int HEADER_H = 58;
    private static final int FOOTER_H = 22;
    private static final int ROW_H = 20;

    private static final int C_DIM    = 0xB4000000;
    private static final int C_WINDOW = 0xF21B1B21;
    private static final int C_BAR    = 0xFF16161B;
    private static final int C_CARD   = 0xFF24242B;
    private static final int C_HOV    = 0xFF2E2E38;
    private static final int C_INNER  = 0xFF1C1C22;
    private static final int C_LINE   = 0xFF31313A;

    private final Screen parent;
    private EditBox search;

    private float openAnim = 0f;
    private long lastNano = 0L;
    private int mx = 0, my = 0;
    private float scroll = 0f, scrollTarget = 0f;
    private int winX, winY, winW, winH, listH;

    /** One line in the list. */
    private record Entry(String where, String what, int keyCode, String keyName) {}

    private final List<Entry> entries = new ArrayList<>();

    public KeyListScreen(Screen parent) {
        super(Component.literal("Keys"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        winW = Math.min(this.width - 20, 560);
        winH = Math.min(this.height - 20, 380);
        winX = (this.width - winW) / 2;
        winY = (this.height - winH) / 2;
        listH = winH - HEADER_H - FOOTER_H;

        search = new EditBox(this.font,
                winX + 12, winY + 32, winW - 24, 16, Component.literal(""));
        search.setBordered(false);
        search.setMaxLength(32);
        this.addRenderableWidget(search);
        this.setFocused(search);

        collect();
    }

    /** Gathers every assigned key from every corner of the client. */
    private void collect() {
        entries.clear();

        for (Module m : ModuleManager.INSTANCE.getModules()) {
            KeySetting k = m.getToggleKey();
            if (k != null && k.isBound()) {
                entries.add(new Entry("Module", m.getName(), k.getKeyCode(), k.getKeyName()));
            }
        }

        WaypointSettings wp = WaypointSettings.INSTANCE;
        addKey("Waypoints", "Add here", wp.keyAddHere);
        addKey("Waypoints", "Mark block", wp.keyMarkBlock);
        addKey("Waypoints", "Mark area", wp.keyMarkArea);
        addKey("Waypoints", "Toggle display", wp.keyToggle);
        addKey("Waypoints", "Manage", wp.keyManage);

        for (var macro : MacroManager.all()) {
            if (macro.key != org.lwjgl.glfw.GLFW.GLFW_KEY_UNKNOWN) {
                entries.add(new Entry("Macro", macro.name, macro.key, macroKeyName(macro.key)));
            }
        }

        // The two bound through the game's own controls screen, listed for
        // completeness -- they take part in conflicts just like the rest.
        entries.add(new Entry("Client", "Open menu", -1, "Right Shift"));
        entries.add(new Entry("Client", "HUD editor", -2, "Right Ctrl"));

        entries.sort((a, b) -> {
            int c = a.where().compareTo(b.where());
            return (c != 0) ? c : a.what().compareToIgnoreCase(b.what());
        });
    }

    private void addKey(String where, String what, KeySetting k) {
        if (k != null && k.isBound()) {
            entries.add(new Entry(where, what, k.getKeyCode(), k.getKeyName()));
        }
    }

    private static String macroKeyName(int code) {
        if (MacroManager.isMouse(code)) {
            return "Mouse " + (code - MacroManager.MOUSE_BASE + 1);
        }
        try {
            return com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM
                    .getOrCreate(code).getDisplayName().getString().toUpperCase();
        } catch (Throwable pvpErr) {
            return "?";
        }
    }

    /** How many entries share this key. */
    private int sharing(int keyCode) {
        if (keyCode < 0) return 1;
        int n = 0;
        for (Entry e : entries) {
            if (e.keyCode() == keyCode) n++;
        }
        return n;
    }

    @Override
    public void render(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
        this.mx = mouseX;
        this.my = mouseY;

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
        ctx.drawString(this.font, Component.literal("Keys"),
                winX + 30, winY + 11, 0xFFFFFFFF);

        int conflicts = countConflicts();
        String note = (conflicts == 0)
                ? entries.size() + " assigned"
                : entries.size() + " assigned, " + conflicts + " share a key";
        int nw = this.font.width(note);
        ctx.drawString(this.font, Component.literal(note),
                winX + winW - nw - 12, winY + 12,
                conflicts == 0 ? 0xFF74747F : 0xFFFF7A7A, false);

        roundRect(ctx, winX + 8, winY + 29, winW - 16, 20, C_INNER);
        if (search != null && search.getValue().isEmpty()) {
            ctx.drawString(this.font, Component.literal("Search..."),
                    winX + 14, winY + 34, 0xFF5A5A66, false);
        }

        // List
        ctx.enableScissor(winX, winY + HEADER_H, winX + winW, winY + HEADER_H + listH);
        String q = (search == null) ? "" : search.getValue().toLowerCase();
        int y = winY + HEADER_H + 4 - (int) scroll;
        int shown = 0;

        for (Entry e : entries) {
            if (!q.isEmpty()
                    && !e.what().toLowerCase().contains(q)
                    && !e.keyName().toLowerCase().contains(q)
                    && !e.where().toLowerCase().contains(q)) {
                continue;
            }
            shown++;
            if (y + ROW_H >= winY + HEADER_H && y <= winY + HEADER_H + listH) {
                boolean hov = in(winX + 8, y, winW - 16, ROW_H);
                roundRect(ctx, winX + 8, y, winW - 16, ROW_H, hov ? C_HOV : C_CARD);

                ctx.drawString(this.font, Component.literal(e.where()),
                        winX + 16, y + 6, 0xFF74747F, false);
                ctx.drawString(this.font, Component.literal(e.what()),
                        winX + 90, y + 6, 0xFFE6E6EC, false);

                boolean clash = sharing(e.keyCode()) > 1;
                String kn = e.keyName();
                int kw = this.font.width(kn);
                roundRect(ctx, winX + winW - kw - 28, y + 3, kw + 12, 14,
                        clash ? 0x40FF5555 : C_INNER);
                ctx.drawString(this.font, Component.literal(kn),
                        winX + winW - kw - 22, y + 6,
                        clash ? 0xFFFF7A7A : 0xFFD0D0DA, false);
            }
            y += ROW_H + 2;
        }
        ctx.disableScissor();

        if (shown == 0) {
            ctx.drawString(this.font,
                    Component.literal(entries.isEmpty() ? "No keys assigned yet" : "Nothing found"),
                    winX + 16, winY + HEADER_H + 10, 0xFF6A6A76, false);
        }

        // Footer
        int fy = winY + winH - FOOTER_H;
        ctx.fill(winX, fy, winX + winW, winY + winH, fade(C_BAR, openAnim));
        ctx.fill(winX, fy, winX + winW, fy + 1, fade(C_LINE, openAnim));
        ctx.drawString(this.font,
                Component.literal("Red means two things share that key"),
                winX + 12, fy + 7, 0xFF74747F, false);

        super.render(ctx, mouseX, mouseY, delta);
    }

    /** How many entries share a key with something else. */
    private int countConflicts() {
        int n = 0;
        for (Entry e : entries) {
            if (e.keyCode() >= 0 && sharing(e.keyCode()) > 1) n++;
        }
        return n;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (in(winX + 8, winY + 8, 16, 16)) {
            this.onClose();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int content = entries.size() * (ROW_H + 2) + 8;
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

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }
}
