package com.vortex.client.gui;

import com.vortex.client.core.ConfigManager;
import com.vortex.client.core.setting.NumberSetting;
import com.vortex.client.hud.HudElement;
import com.vortex.client.module.Module;
import com.vortex.client.module.ModuleManager;
import com.vortex.client.module.modules.ArmorHudModule;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * HUD-Editor: HUD-Elemente und die einzelnen ArmorHUD-Teile mit der Maus
 * verschieben, statt Zahlen einzutippen.
 *
 * Neu gegenueber der ersten Fassung:
 *   - Raster im Hintergrund als optische Orientierung
 *   - Einrasten an Raster, Bildschirmkanten, Bildschirmmitte und an den Kanten
 *     der uebrigen Elemente -- mit Hilfslinien, die zeigen, woran gerade
 *     ausgerichtet wird
 *   - Werkzeugleiste zum Umschalten von Raster und Einrasten
 *   - Positionsanzeige am gezogenen Element
 *
 * Bedienung: linke Maustaste halten und ziehen, ESC schliesst und speichert.
 */
public class HudEditorScreen extends Screen {

    /** Abstand, ab dem eingerastet wird. */
    private static final int SNAP_DIST = 6;
    /** Rasterweite in Pixeln. */
    private static final int GRID = 8;

    private static final int C_BAR   = 0xF016161B;
    private static final int C_INNER = 0xFF1C1C22;

    // Einstellungen des Editors (bleiben waehrend der Sitzung erhalten).
    private static boolean showGrid = true;
    private static boolean snapping = true;

    private int mx = 0, my = 0;

    // Was wird gerade gezogen?
    private HudElement draggingElement = null;
    private NumberSetting dragPartOffX = null;
    private NumberSetting dragPartOffY = null;
    private int partStartOffX = 0, partStartOffY = 0;
    private int dragStartMouseX = 0, dragStartMouseY = 0;
    private int grabOffsetX = 0, grabOffsetY = 0;

    // Hilfslinien, die beim Einrasten gezeichnet werden.
    private final List<int[]> guidesV = new ArrayList<>(); // {x, y1, y2}
    private final List<int[]> guidesH = new ArrayList<>(); // {y, x1, x2}

    public HudEditorScreen() {
        super(Component.literal("HUD Editor"));
    }

    private List<HudElement> elements() {
        List<HudElement> list = new ArrayList<>();
        for (Module m : ModuleManager.INSTANCE.getModules()) {
            if (m instanceof HudElement he && m.isEnabled()) list.add(he);
            // Item counters are HudElements too, but there are several per
            // module -- so they are added by hand rather than found by the
            // instanceof above, which only ever sees the module itself.
            if (m instanceof com.vortex.client.module.modules.ItemCounterModule ic
                    && m.isEnabled()) {
                list.addAll(ic.getCounters());
            }
        }
        return list;
    }

    private ArmorHudModule armor() {
        ArmorHudModule a = ModuleManager.INSTANCE.get(ArmorHudModule.class);
        return (a != null && a.isEnabled()) ? a : null;
    }

    // -------------------------------------------------------------- Zeichnen

    @Override
    public void render(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
        this.mx = mouseX;
        this.my = mouseY;

        // Kein renderBackground() -- loest in 1.21.11 einen Blur aus, der pro
        // Bild nur einmal erlaubt ist.
        ctx.fill(0, 0, this.width, this.height, 0x88000000);

        int accent = Theme.INSTANCE.accent.get() | 0xFF000000;

        if (showGrid) drawGrid(ctx);

        // Mittellinien als staendige Orientierung.
        int cxm = this.width / 2, cym = this.height / 2;
        ctx.fill(cxm, 0, cxm + 1, this.height, 0x18FFFFFF);
        ctx.fill(0, cym, this.width, cym + 1, 0x18FFFFFF);

        // 1) Normale HUD-Elemente.
        for (HudElement he : elements()) {
            int x = he.hudX().getInt();
            int y = he.hudY().getInt();
            int w = he.hudWidth();
            int h = he.hudHeight();

            boolean hovered = inside(mouseX, mouseY, x, y, w, h);
            boolean active = (he == draggingElement);
            int border = active ? accent : (hovered ? 0xFF9AD8FF : 0x99FFFFFF);
            int fill = active ? withAlpha(accent, 0x50) : (hovered ? 0x30FFFFFF : 0x1AFFFFFF);

            ctx.fill(x, y, x + w, y + h, fill);
            drawBorder(ctx, x, y, w, h, border);
            ctx.drawString(this.font, he.hudName(), x + 3, y + 3, 0xFFFFFFFF);

            if (active) {
                String pos = x + ", " + y;
                ctx.drawString(this.font, Component.literal(pos),
                        x, y - 11, accent);
            }
        }

        // 2) ArmorHUD-Teile.
        ArmorHudModule a = armor();
        if (a != null) {
            for (ArmorHudModule.ArmorPart part : a.computeParts(this.width, this.height)) {
                int x = part.x, y = part.y, s = part.size;
                boolean hovered = inside(mouseX, mouseY, x, y, s, s);
                boolean active = (dragPartOffX != null
                        && dragPartOffX == part.offsetX && dragPartOffY == part.offsetY);
                int border = active ? accent : (hovered ? 0xFF9AD8FF : 0x99CCCCCC);
                int fill = active ? withAlpha(accent, 0x50) : 0x2A55AAFF;

                ctx.fill(x, y, x + s, y + s, fill);
                drawBorder(ctx, x, y, s, s, border);
                ctx.drawString(this.font,
                        part.name.substring(0, 1), x + 5, y + 4, 0xFFFFFFFF);
            }
        }

        // 3) Hilfslinien des aktuellen Einrastens.
        for (int[] g : guidesV) {
            ctx.fill(g[0], Math.min(g[1], g[2]), g[0] + 1, Math.max(g[1], g[2]), accent);
        }
        for (int[] g : guidesH) {
            ctx.fill(Math.min(g[1], g[2]), g[0], Math.max(g[1], g[2]), g[0] + 1, accent);
        }

        drawToolbar(ctx, accent);
        super.render(ctx, mouseX, mouseY, delta);
    }

    private void drawGrid(GuiGraphics ctx) {
        for (int x = 0; x < this.width; x += GRID) {
            ctx.fill(x, 0, x + 1, this.height, 0x0CFFFFFF);
        }
        for (int y = 0; y < this.height; y += GRID) {
            ctx.fill(0, y, this.width, y + 1, 0x0CFFFFFF);
        }
    }

    private void drawToolbar(GuiGraphics ctx, int accent) {
        int h = 26;
        ctx.fill(0, 0, this.width, h, C_BAR);
        ctx.fill(0, h, this.width, h + 1, 0xFF31313A);

        ctx.drawString(this.font, Component.literal("HUD Editor"),
                8, 9, 0xFFFFFFFF);
        ctx.drawString(this.font,
                Component.literal("Drag to move  \u00B7  ESC saves and closes"),
                74, 9, 0xFF74747F, false);

        // Umschalter rechts.
        drawToggle(ctx, toolbarX(0), 5, "Raster", showGrid, accent);
        drawToggle(ctx, toolbarX(1), 5, "Einrasten", snapping, accent);
    }

    /** X-Position der Werkzeug-Knoepfe (von rechts gezaehlt). */
    private int toolbarX(int index) {
        int w1 = this.font.width("Raster") + 16;
        int w2 = this.font.width("Einrasten") + 16;
        if (index == 1) return this.width - w2 - 8;
        return this.width - w2 - 8 - w1 - 6;
    }

    private void drawToggle(GuiGraphics ctx, int x, int y, String label,
                            boolean on, int accent) {
        int w = this.font.width(label) + 16;
        boolean hov = inside(mx, my, x, y, w, 16);
        int bg = on ? mixColor(C_INNER, accent, 0.45f) : (hov ? 0xFF2E2E38 : C_INNER);
        ctx.fill(x + 1, y, x + w - 1, y + 16, bg);
        ctx.fill(x, y + 1, x + 1, y + 15, bg);
        ctx.fill(x + w - 1, y + 1, x + w, y + 15, bg);
        ctx.drawString(this.font, Component.literal(label),
                x + 8, y + 4, on ? 0xFFFFFFFF : 0xFF9A9AA6, false);
    }

    // ---------------------------------------------------------------- Eingabe

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        this.mx = (int) mouseX;
        this.my = (int) mouseY;
        if (button == 0) {
            // Werkzeugleiste zuerst.
            int w1 = this.font.width("Raster") + 16;
            int w2 = this.font.width("Einrasten") + 16;
            if (inside(mx, my, toolbarX(0), 5, w1, 16)) {
                showGrid = !showGrid;
                return true;
            }
            if (inside(mx, my, toolbarX(1), 5, w2, 16)) {
                snapping = !snapping;
                return true;
            }
            if (my < 26) return true; // restliche Leiste schluckt Klicks

            // ArmorHUD-Teile (liegen oft ueber anderen Elementen).
            ArmorHudModule a = armor();
            if (a != null) {
                List<ArmorHudModule.ArmorPart> parts = a.computeParts(this.width, this.height);
                for (int i = parts.size() - 1; i >= 0; i--) {
                    ArmorHudModule.ArmorPart part = parts.get(i);
                    if (inside(mx, my, part.x, part.y, part.size, part.size)) {
                        dragPartOffX = part.offsetX;
                        dragPartOffY = part.offsetY;
                        partStartOffX = part.offsetX.getInt();
                        partStartOffY = part.offsetY.getInt();
                        dragStartMouseX = mx;
                        dragStartMouseY = my;
                        return true;
                    }
                }
            }

            List<HudElement> els = elements();
            for (int i = els.size() - 1; i >= 0; i--) {
                HudElement he = els.get(i);
                int x = he.hudX().getInt();
                int y = he.hudY().getInt();
                if (inside(mx, my, x, y, he.hudWidth(), he.hudHeight())) {
                    draggingElement = he;
                    grabOffsetX = mx - x;
                    grabOffsetY = my - y;
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double offsetX, double offsetY) {
        this.mx = (int) mouseX;
        this.my = (int) mouseY;
        guidesV.clear();
        guidesH.clear();

        // ArmorHUD-Teil: nur den Versatz veraendern (kein Einrasten, weil die
        // Teile relativ zu einem Ankerpunkt sitzen).
        if (dragPartOffX != null && dragPartOffY != null) {
            dragPartOffX.set(partStartOffX + (mx - dragStartMouseX));
            dragPartOffY.set(partStartOffY + (my - dragStartMouseY));
            return true;
        }

        if (draggingElement != null) {
            int w = draggingElement.hudWidth();
            int h = draggingElement.hudHeight();
            int newX = clamp(mx - grabOffsetX, 0, this.width - w);
            int newY = clamp(my - grabOffsetY, 0, this.height - h);

            if (snapping) {
                newX = snapX(newX, w);
                newY = snapY(newY, h);
            }
            draggingElement.hudX().set(newX);
            draggingElement.hudY().set(newY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, offsetX, offsetY);
    }

    /**
     * Waagerechtes Einrasten. Geprueft werden linke Kante, rechte Kante und
     * Mitte des gezogenen Elements gegen: Bildschirmkanten, Bildschirmmitte,
     * Rasterlinien und die Kanten der uebrigen Elemente.
     */
    private int snapX(int x, int w) {
        List<Integer> targets = new ArrayList<>();
        targets.add(0);
        targets.add(this.width);
        targets.add(this.width / 2);
        for (HudElement other : elements()) {
            if (other == draggingElement) continue;
            int ox = other.hudX().getInt();
            targets.add(ox);
            targets.add(ox + other.hudWidth());
        }

        int best = x;
        int bestDist = SNAP_DIST + 1;
        int guide = Integer.MIN_VALUE;

        for (int tX : targets) {
            // linke Kante
            if (Math.abs(x - tX) < bestDist) {
                bestDist = Math.abs(x - tX); best = tX; guide = tX;
            }
            // rechte Kante
            if (Math.abs((x + w) - tX) < bestDist) {
                bestDist = Math.abs((x + w) - tX); best = tX - w; guide = tX;
            }
            // Mitte
            if (Math.abs((x + w / 2) - tX) < bestDist) {
                bestDist = Math.abs((x + w / 2) - tX); best = tX - w / 2; guide = tX;
            }
        }

        if (bestDist <= SNAP_DIST) {
            guidesV.add(new int[] { guide, 0, this.height });
            return clamp(best, 0, this.width - w);
        }
        // Sonst auf das Raster runden.
        int snapped = Math.round(x / (float) GRID) * GRID;
        if (Math.abs(snapped - x) <= 3) return clamp(snapped, 0, this.width - w);
        return x;
    }

    /** Senkrechtes Einrasten -- gleiche Logik wie {@link #snapX}. */
    private int snapY(int y, int h) {
        List<Integer> targets = new ArrayList<>();
        targets.add(0);
        targets.add(this.height);
        targets.add(this.height / 2);
        for (HudElement other : elements()) {
            if (other == draggingElement) continue;
            int oy = other.hudY().getInt();
            targets.add(oy);
            targets.add(oy + other.hudHeight());
        }

        int best = y;
        int bestDist = SNAP_DIST + 1;
        int guide = Integer.MIN_VALUE;

        for (int tY : targets) {
            if (Math.abs(y - tY) < bestDist) {
                bestDist = Math.abs(y - tY); best = tY; guide = tY;
            }
            if (Math.abs((y + h) - tY) < bestDist) {
                bestDist = Math.abs((y + h) - tY); best = tY - h; guide = tY;
            }
            if (Math.abs((y + h / 2) - tY) < bestDist) {
                bestDist = Math.abs((y + h / 2) - tY); best = tY - h / 2; guide = tY;
            }
        }

        if (bestDist <= SNAP_DIST) {
            guidesH.add(new int[] { guide, 0, this.width });
            return clamp(best, 0, this.height - h);
        }
        int snapped = Math.round(y / (float) GRID) * GRID;
        if (Math.abs(snapped - y) <= 3) return clamp(snapped, 0, this.height - h);
        return y;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        boolean was = (draggingElement != null) || (dragPartOffX != null);
        draggingElement = null;
        dragPartOffX = null;
        dragPartOffY = null;
        guidesV.clear();
        guidesH.clear();
        if (was) return true;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public void removed() {
        ConfigManager.save();
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ----------------------------------------------------------- Hilfsmittel

    private static int clamp(int v, int min, int max) {
        if (max < min) return min;
        return v < min ? min : (v > max ? max : v);
    }

    private boolean inside(double px, double py, int x, int y, int w, int h) {
        return px >= x && px <= x + w && py >= y && py <= y + h;
    }

    private void drawBorder(GuiGraphics ctx, int x, int y, int w, int h, int color) {
        ctx.fill(x, y, x + w, y + 1, color);
        ctx.fill(x, y + h - 1, x + w, y + h, color);
        ctx.fill(x, y, x + 1, y + h, color);
        ctx.fill(x + w - 1, y, x + w, y + h, color);
    }

    private static int withAlpha(int color, int alpha) {
        return (alpha << 24) | (color & 0x00FFFFFF);
    }

    private static int mixColor(int a, int b, float t) {
        if (t < 0f) t = 0f;
        if (t > 1f) t = 1f;
        int aa = (a >>> 24) & 0xFF, ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int ba = (b >>> 24) & 0xFF, br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        return (((int) (aa + (ba - aa) * t)) << 24)
                | (((int) (ar + (br - ar) * t)) << 16)
                | (((int) (ag + (bg - ag) * t)) << 8)
                | ((int) (ab + (bb - ab) * t));
    }
}
