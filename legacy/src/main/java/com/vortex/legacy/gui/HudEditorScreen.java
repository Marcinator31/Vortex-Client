package com.vortex.legacy.gui;

import com.vortex.legacy.core.Config;
import com.vortex.legacy.core.Module;
import com.vortex.legacy.core.ModuleManager;
import com.vortex.legacy.hud.HudModule;
import com.vortex.legacy.hud.HudRenderer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.screen.Screen;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/** HUD-Elemente verschieben (ziehen) und skalieren (Mausrad). Raster-Einrasten an Raendern und Mitte. */
public class HudEditorScreen extends Screen {
    private final Screen parent;
    private HudModule drag;
    private float offX, offY;
    private boolean snapX, snapY;

    public HudEditorScreen(Screen parent) { this.parent = parent; }

    private List<HudModule> elements() {
        List<HudModule> l = new ArrayList<HudModule>();
        for (Module m : ModuleManager.INSTANCE.all()) if (m instanceof HudModule && m.isEnabled()) l.add((HudModule) m);
        return l;
    }

    private static boolean inside(HudModule h, int mx, int my) {
        float s = h.scale.getFloat();
        float[] p = HudRenderer.position(h);
        return mx >= p[0] && my >= p[1] && mx <= p[0] + h.width() * s && my <= p[1] + h.height() * s;
    }

    @Override
    public void render(int mx, int my, float delta) {
        Render2D.rect(0, 0, width, height, 0x50000000);
        // Hilfslinien
        if (drag != null && snapX) Render2D.rect(width / 2f - 0.5f, 0, 1, height, Render2D.alpha(Theme.accent(), 0.7f));
        if (drag != null && snapY) Render2D.rect(0, height / 2f - 0.5f, width, 1, Render2D.alpha(Theme.accent(), 0.7f));
        HudModule hover = null;
        for (HudModule h : elements()) {
            HudRenderer.draw(h, true);
            float s = h.scale.getFloat();
            boolean over = drag == h || (drag == null && inside(h, mx, my));
            if (over) hover = h;
            float[] p = HudRenderer.position(h);
            Render2D.roundOutline(p[0] - 2, p[1] - 2, h.width() * s + 4, h.height() * s + 4, 3, 1,
                    over ? Theme.accent() : 0x55FFFFFF);
        }
        String hint = "Drag to move  •  Scroll to resize  •  Right click to hide  •  Esc to close";
        float hw = Render2D.width(hint) + 16;
        Render2D.round(width / 2f - hw / 2f, height - 24, hw, 16, 4, 0xC0100D18);
        Render2D.textCentered(hint, width / 2f, height - 20, 0xFFB9B3CC, false);
        if (hover != null) {
            String n = hover.getName() + "  " + Math.round(hover.scale.get() * 100) + "%";
            Render2D.round(mx + 8, my - 14, Render2D.width(n) + 8, 13, 3, 0xE0100D18);
            Render2D.text(n, mx + 12, my - 11, 0xFFFFFFFF, false);
        }
        super.render(mx, my, delta);
    }

    @Override
    protected void mouseClicked(int mx, int my, int button) {
        List<HudModule> l = elements();
        for (int i = l.size() - 1; i >= 0; i--) {
            HudModule h = l.get(i);
            if (!inside(h, mx, my)) continue;
            if (button == 0) { float[] p = HudRenderer.position(h); drag = h; offX = mx - p[0]; offY = my - p[1]; }
            else if (button == 1) { h.setEnabled(false); Config.markDirty(); }
            return;
        }
    }

    @Override
    protected void mouseDragged(int mx, int my, int button, long time) {
        if (drag == null) return;
        float s = drag.scale.getFloat();
        float w = drag.width() * s, h = drag.height() * s;
        float nx = mx - offX, ny = my - offY;
        nx = Math.max(0, Math.min(width - w, nx));
        ny = Math.max(0, Math.min(height - h, ny));
        snapX = Math.abs(nx + w / 2 - width / 2f) < 4;
        snapY = Math.abs(ny + h / 2 - height / 2f) < 4;
        if (snapX) nx = width / 2f - w / 2;
        if (snapY) ny = height / 2f - h / 2;
        if (nx < 4) nx = 2; if (ny < 4) ny = 2;
        if (width - (nx + w) < 4) nx = width - w - 2;
        if (height - (ny + h) < 4) ny = height - h - 2;
        drag.x.setRaw(Math.round(nx));
        drag.y.setRaw(Math.round(ny));
    }

    @Override
    protected void mouseReleased(int mx, int my, int button) {
        if (drag != null) Config.markDirty();
        drag = null;
        snapX = snapY = false;
    }

    @Override
    public void handleMouse() {
        super.handleMouse();
        int wheel = Mouse.getEventDWheel();
        if (wheel == 0) return;
        int mx = Mouse.getEventX() * width / client.width, my = height - Mouse.getEventY() * height / client.height - 1;
        for (HudModule h : elements()) {
            if (!inside(h, mx, my)) continue;
            h.scale.set(h.scale.get() + (wheel > 0 ? 0.05 : -0.05));
            Config.markDirty();
            return;
        }
    }

    @Override
    protected void keyPressed(char c, int key) {
        if (key == Keyboard.KEY_ESCAPE) { Config.save(); client.setScreen(parent); }
    }

    @Override public boolean shouldPauseGame() { return false; }
}
