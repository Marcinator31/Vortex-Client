package com.example.pvpclient.gui;

import com.example.pvpclient.waypoint.WaypointManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.List;

/**
 * Verwaltung der Waypoints im Stil des uebrigen Clients.
 *
 * Oben ein Eingabefeld mit Knopf, um an der aktuellen Position einen Marker zu
 * setzen. Darunter die Liste: Name, Koordinaten und Entfernung, dazu je Zeile
 * ein Farbfeld (oeffnet den Farbwaehler), ein Auge (ein-/ausblenden) und ein
 * Kreuz (loeschen).
 */
public class WaypointScreen extends Screen {

    private static final int WIN_W = 380;
    private static final int HEADER_H = 58;
    private static final int FOOTER_H = 20;
    private static final int ROW_H = 24;

    private static final int C_DIM    = 0xB4000000;
    private static final int C_WINDOW = 0xF21B1B21;
    private static final int C_BAR    = 0xFF16161B;
    private static final int C_CARD   = 0xFF24242B;
    private static final int C_HOV    = 0xFF2E2E38;
    private static final int C_INNER  = 0xFF1C1C22;
    private static final int C_LINE   = 0xFF31313A;

    private final Screen parent;
    private TextFieldWidget nameField;
    private TextFieldWidget coordField;

    private float openAnim = 0f;
    private long lastNano = 0L;
    private int mx = 0, my = 0;
    private float scroll = 0f, scrollTarget = 0f;

    /**
     * Welche Welt angezeigt wird: null = nur die aktuelle, "*" = alle.
     * Sonst die gespeicherte Welt-Kennung.
     */
    private String worldFilter = null;

    /** Dimensions-Filter: null = alle, sonst "overworld"/"the_nether"/"the_end". */
    private String dimFilter = null;

    /** Marker, dessen Koordinaten gerade bearbeitet werden. */
    private WaypointManager.Waypoint editing = null;

    /** Kurze Rueckmeldung in der Fusszeile. */
    private String status = "";

    private int winX, winY, winH, listH;

    public WaypointScreen(Screen parent) {
        super(Text.literal("Waypoints"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        winH = Math.min(this.height - 40, 360);
        winX = (this.width - WIN_W) / 2;
        winY = (this.height - winH) / 2;
        listH = winH - HEADER_H - FOOTER_H;

        coordField = new TextFieldWidget(this.textRenderer,
                winX + 90, winY + winH - 18, 150, 14, Text.literal(""));
        coordField.setDrawsBackground(false);
        coordField.setMaxLength(32);
        coordField.setVisible(false);
        this.addDrawableChild(coordField);

        nameField = new TextFieldWidget(this.textRenderer,
                winX + 12, winY + 34, 200, 14, Text.literal(""));
        nameField.setDrawsBackground(false);
        nameField.setMaxLength(32);
        this.addDrawableChild(nameField);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
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
        roundRect(ctx, winX, winY, WIN_W, winH, fade(C_WINDOW, openAnim));
        ctx.fill(winX, winY, winX + WIN_W, winY + 1, fade(accent, openAnim));

        // Kopfzeile
        ctx.fill(winX, winY, winX + WIN_W, winY + HEADER_H, fade(C_BAR, openAnim));
        ctx.fill(winX, winY + HEADER_H - 1, winX + WIN_W, winY + HEADER_H, fade(C_LINE, openAnim));

        boolean backHov = inRect(winX + 8, winY + 8, 16, 16);
        ctx.drawTextWithShadow(this.textRenderer, Text.literal("<"),
                winX + 12, winY + 12, backHov ? accent : 0xFF9A9AA6);
        ctx.drawTextWithShadow(this.textRenderer, Text.literal("Waypoints"),
                winX + 30, winY + 11, 0xFFFFFFFF);

        List<WaypointManager.Waypoint> list = filtered();
        // Welt-Umschalter: aktuelle Welt / alle / einzelne gespeicherte Welten.
        String wl = shortWorld(worldFilter);
        int wlw = this.textRenderer.getWidth(wl) + 14;
        int wlx = winX + WIN_W - wlw - 12;
        boolean wlHov = inRect(wlx, winY + 30, wlw, 20);
        roundRect(ctx, wlx, winY + 30, wlw, 20,
                wlHov ? mix(C_INNER, accent, 0.4f) : C_INNER);
        ctx.drawText(this.textRenderer, Text.literal(wl),
                wlx + 7, winY + 36, 0xFFD0D0DA, false);

        // Dimensions-Umschalter links neben dem Welt-Umschalter.
        String dl = dimLabel(dimFilter);
        int dlw = this.textRenderer.getWidth(dl) + 14;
        int dlx = wlx - dlw - 6;
        boolean dlHov = inRect(dlx, winY + 30, dlw, 20);
        roundRect(ctx, dlx, winY + 30, dlw, 20,
                dlHov ? mix(C_INNER, accent, 0.4f) : C_INNER);
        ctx.drawText(this.textRenderer, Text.literal(dl),
                dlx + 7, winY + 36, 0xFFD0D0DA, false);

        String count = list.size() + " Marker";
        int cw = this.textRenderer.getWidth(count);
        ctx.drawText(this.textRenderer, Text.literal(count),
                winX + WIN_W - cw - 12, winY + 12, 0xFF74747F, false);

        // Eingabefeld-Rahmen + Knopf "Hier setzen"
        roundRect(ctx, winX + 8, winY + 30, 208, 20, C_INNER);
        if (nameField != null && nameField.getText().isEmpty()) {
            ctx.drawText(this.textRenderer, Text.literal("Name des Markers ..."),
                    winX + 12, winY + 36, 0xFF6A6A76, false);
        }
        String addLbl = "Hier setzen";
        int aw = this.textRenderer.getWidth(addLbl) + 16;
        boolean addHov = inRect(winX + 224, winY + 30, aw, 20);
        roundRect(ctx, winX + 224, winY + 30, aw, 20,
                addHov ? mix(C_INNER, accent, 0.45f) : C_INNER);
        ctx.drawText(this.textRenderer, Text.literal(addLbl),
                winX + 232, winY + 36, 0xFFFFFFFF, false);

        // Liste
        ctx.enableScissor(winX, winY + HEADER_H, winX + WIN_W, winY + HEADER_H + listH);
        MinecraftClient client = MinecraftClient.getInstance();
        int y = winY + HEADER_H + 6 - (int) scroll;

        for (WaypointManager.Waypoint w : list) {
            if (y + ROW_H >= winY + HEADER_H && y <= winY + HEADER_H + listH) {
                boolean hov = inRect(winX + 8, y, WIN_W - 16, ROW_H);
                roundRect(ctx, winX + 8, y, WIN_W - 16, ROW_H, hov ? C_HOV : C_CARD);

                int col = w.visible ? (w.color | 0xFF000000) : 0xFF5A5A66;
                ctx.fill(winX + 8, y + 4, winX + 10, y + ROW_H - 4, col);

                ctx.drawTextWithShadow(this.textRenderer, Text.literal(w.name),
                        winX + 18, y + 4, w.visible ? 0xFFFFFFFF : 0xFF8A8A96);
                // Art des Markers als kleine Kennzeichnung dahinter.
                int nw = this.textRenderer.getWidth(w.name);
                ctx.drawText(this.textRenderer, Text.literal(w.kind.label),
                        winX + 24 + nw, y + 4, w.kind.color, false);

                // Aktive Block-Gruppe hervorheben.
                boolean isGroup = com.example.pvpclient.waypoint.WaypointActions
                        .getBlockGroup() == w;
                if (isGroup) {
                    ctx.fill(winX + 8, y, winX + WIN_W - 8, y + 1,
                            Theme.INSTANCE.accent.get() | 0xFF000000);
                }

                String pos = w.x + ", " + w.y + ", " + w.z;
                if (!w.blocks.isEmpty()) pos += "   " + w.blocks.size() + " Bloecke";
                if (client.player != null) {
                    double dx = w.x - client.player.getX();
                    double dz = w.z - client.player.getZ();
                    pos += "   " + (int) Math.sqrt(dx * dx + dz * dz) + "m";
                }
                ctx.drawText(this.textRenderer, Text.literal(pos),
                        winX + 18, y + 14, 0xFF74747F, false);

                // Farbfeld / Auge / Kreuz rechts
                int bx = winX + WIN_W - 26;
                roundRect(ctx, bx, y + 6, 12, 12, w.color | 0xFF000000);
                ctx.drawText(this.textRenderer, Text.literal(w.visible ? "o" : "-"),
                        bx - 20, y + 8, 0xFFB4B4C0, false);
                ctx.drawText(this.textRenderer, Text.literal("x"),
                        bx - 40, y + 8, 0xFFFF7A7A, false);
                // Weitere Aktionen -- kurze Kuerzel, damit die Zeile schmal bleibt.
                ctx.drawText(this.textRenderer, Text.literal("B"),
                        bx - 58, y + 8,
                        isGroup ? (Theme.INSTANCE.accent.get() | 0xFF000000) : 0xFF9A9AA6,
                        false);
                ctx.drawText(this.textRenderer, Text.literal("N"),
                        bx - 74, y + 8, 0xFF9A9AA6, false);
                ctx.drawText(this.textRenderer, Text.literal("K"),
                        bx - 90, y + 8, 0xFF9A9AA6, false);
                ctx.drawText(this.textRenderer, Text.literal("C"),
                        bx - 122, y + 8,
                        editing == w ? (Theme.INSTANCE.accent.get() | 0xFF000000)
                                     : 0xFF9A9AA6, false);
                ctx.drawText(this.textRenderer, Text.literal("T"),
                        bx - 106, y + 8,
                        w.tracer ? (Theme.INSTANCE.accent.get() | 0xFF000000) : 0xFF9A9AA6,
                        false);
            }
            y += ROW_H + 4;
        }
        ctx.disableScissor();

        if (list.isEmpty()) {
            String msg = "Noch keine Marker gesetzt";
            ctx.drawText(this.textRenderer, Text.literal(msg),
                    winX + (WIN_W - this.textRenderer.getWidth(msg)) / 2,
                    winY + HEADER_H + listH / 2 - 4, 0xFF6A6A76, false);
        }

        // Fusszeile
        int fy = winY + winH - FOOTER_H;
        ctx.fill(winX, fy, winX + WIN_W, winY + winH, fade(C_BAR, openAnim));
        ctx.fill(winX, fy, winX + WIN_W, fy + 1, fade(C_LINE, openAnim));
        if (editing != null) {
            ctx.drawText(this.textRenderer, Text.literal("X Y Z:"),
                    winX + 12, fy + 6, 0xFFD0D0DA, false);
            roundRect(ctx, winX + 86, fy + 2, 158, 16, C_INNER);
            String ok = "Uebernehmen";
            int okw = this.textRenderer.getWidth(ok) + 14;
            boolean hov = inRect(winX + 250, fy + 2, okw, 16);
            roundRect(ctx, winX + 250, fy + 2, okw, 16,
                    hov ? mix(C_INNER, accent, 0.45f) : C_INNER);
            ctx.drawText(this.textRenderer, Text.literal(ok),
                    winX + 257, fy + 6, 0xFFFFFFFF, false);
        } else {
            String hint = status.isEmpty()
                    ? "C = Koordinaten   T = Tracer   B = Block-Ziel   N = Nether   K = Kopieren"
                    : status;
            ctx.drawText(this.textRenderer, Text.literal(hint),
                    winX + 10, fy + 6,
                    status.isEmpty() ? 0xFF74747F : 0xFFD0D0DA, false);
        }

        if (nameField != null) {
            nameField.setX(winX + 12);
            nameField.setY(winY + 36);
        }
        super.render(ctx, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.gui.Click click, boolean doubled) {
        if (super.mouseClicked(click, doubled)) return true;

        if (inRect(winX + 8, winY + 8, 16, 16)) {
            this.close();
            return true;
        }

        // Welt-Umschalter durchklicken: aktuelle -> alle -> einzelne Welten.
        String wl = shortWorld(worldFilter);
        int wlw = this.textRenderer.getWidth(wl) + 14;
        int wlx = winX + WIN_W - wlw - 12;
        if (inRect(wlx, winY + 30, wlw, 20)) {
            var worlds = WaypointManager.knownWorlds();
            if (worldFilter == null) {
                worldFilter = "*";
            } else if ("*".equals(worldFilter)) {
                worldFilter = worlds.isEmpty() ? null : worlds.get(0);
            } else {
                int i = worlds.indexOf(worldFilter);
                worldFilter = (i < 0 || i + 1 >= worlds.size()) ? null : worlds.get(i + 1);
            }
            scrollTarget = 0f;
            scroll = 0f;
            return true;
        }

        // "Uebernehmen" bei der Koordinaten-Bearbeitung
        if (editing != null) {
            int fy2 = winY + winH - FOOTER_H;
            String ok = "Uebernehmen";
            int okw = this.textRenderer.getWidth(ok) + 14;
            if (inRect(winX + 250, fy2 + 2, okw, 16)) {
                applyCoords(editing);
                return true;
            }
        }

        // Dimensions-Umschalter
        String dl = dimLabel(dimFilter);
        int dlw = this.textRenderer.getWidth(dl) + 14;
        int dlx = wlx - dlw - 6;
        if (inRect(dlx, winY + 30, dlw, 20)) {
            dimFilter = nextDim(dimFilter);
            scrollTarget = 0f;
            scroll = 0f;
            return true;
        }

        // "Hier setzen"
        String addLbl = "Hier setzen";
        int aw = this.textRenderer.getWidth(addLbl) + 16;
        if (inRect(winX + 224, winY + 30, aw, 20)) {
            addHere();
            return true;
        }

        // Zeilen-Knoepfe
        List<WaypointManager.Waypoint> list = filtered();
        int y = winY + HEADER_H + 6 - (int) scroll;
        for (int i = 0; i < list.size(); i++) {
            WaypointManager.Waypoint w = list.get(i);
            if (y + ROW_H >= winY + HEADER_H && y <= winY + HEADER_H + listH) {
                int bx = winX + WIN_W - 26;
                if (inRect(bx, y + 6, 12, 12)) {
                    // Farbe aendern -- ueber ein kurzlebiges ColorSetting.
                    var cs = new com.example.pvpclient.core.setting.ColorSetting("Markerfarbe", w.color);
                    MinecraftClient.getInstance().setScreen(
                            new ColorPickerScreen(this, cs, () -> w.color = cs.get()));
                    return true;
                }
                if (inRect(bx - 22, y + 6, 14, 12)) {
                    w.visible = !w.visible;
                    return true;
                }
                if (inRect(bx - 42, y + 6, 14, 12)) {
                    if (com.example.pvpclient.waypoint.WaypointActions.getBlockGroup() == w) {
                        com.example.pvpclient.waypoint.WaypointActions.setBlockGroup(null);
                    }
                    WaypointManager.remove(w);
                    return true;
                }
                // B: diesen Marker als Ziel fuer markierte Bloecke waehlen
                if (inRect(bx - 60, y + 6, 14, 12)) {
                    var cur = com.example.pvpclient.waypoint.WaypointActions.getBlockGroup();
                    com.example.pvpclient.waypoint.WaypointActions
                            .setBlockGroup(cur == w ? null : w);
                    return true;
                }
                // N: Gegenstueck in der anderen Dimension anlegen
                if (inRect(bx - 76, y + 6, 14, 12)) {
                    com.example.pvpclient.waypoint.WaypointActions
                            .createCounterpart(MinecraftClient.getInstance(), w);
                    return true;
                }
                // C: Koordinaten bearbeiten
                if (inRect(bx - 124, y + 6, 14, 12)) {
                    if (editing == w) {
                        applyCoords(w);
                    } else {
                        editing = w;
                        if (coordField != null) {
                            coordField.setText(w.x + " " + w.y + " " + w.z);
                            coordField.setVisible(true);
                            this.setFocused(coordField);
                        }
                    }
                    return true;
                }
                // T: Tracer fuer diesen Marker ein/aus
                if (inRect(bx - 108, y + 6, 14, 12)) {
                    w.tracer = !w.tracer;
                    com.example.pvpclient.core.ConfigManager.save();
                    return true;
                }
                // K: Koordinaten kopieren
                if (inRect(bx - 92, y + 6, 14, 12)) {
                    com.example.pvpclient.waypoint.WaypointActions
                            .copyToClipboard(MinecraftClient.getInstance(), w);
                    return true;
                }
            }
            y += ROW_H + 4;
        }
        return false;
    }

    /** Eingegebene Koordinaten uebernehmen ("x y z" oder "x, y, z"). */
    private void applyCoords(WaypointManager.Waypoint w) {
        if (coordField == null) return;
        String txt = coordField.getText().replace(',', ' ').trim();
        String[] parts = txt.split("\\s+");
        if (parts.length != 3) {
            status = "Bitte drei Zahlen eingeben, z.B. 120 64 -300";
            return;
        }
        try {
            w.x = Integer.parseInt(parts[0]);
            w.y = Integer.parseInt(parts[1]);
            w.z = Integer.parseInt(parts[2]);
            com.example.pvpclient.core.ConfigManager.save();
            status = "Koordinaten uebernommen.";
        } catch (Throwable pvpErr) {
            status = "Das waren keine gueltigen Zahlen.";
            return;
        }
        editing = null;
        coordField.setVisible(false);
    }

    /** Legt an der aktuellen Spielerposition einen Marker an. */
    private void addHere() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;
        String name = (nameField == null) ? "" : nameField.getText().trim();
        if (name.isEmpty()) name = "Marker " + (WaypointManager.all().size() + 1);

        String dim = com.example.pvpclient.hud.WaypointRenderer.currentDimension(client);
        WaypointManager.add(name,
                client.player.getBlockX(), client.player.getBlockY(),
                client.player.getBlockZ(), dim);
        if (nameField != null) nameField.setText("");
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY,
                                 double horizontal, double vertical) {
        int content = WaypointManager.all().size() * (ROW_H + 4) + 12;
        scrollTarget -= (float) vertical * 30f;
        float max = Math.max(0f, content - listH);
        if (scrollTarget < 0f) scrollTarget = 0f;
        if (scrollTarget > max) scrollTarget = max;
        return true;
    }

    /** Marker nach dem gewaehlten Welt-Filter. */
    private List<WaypointManager.Waypoint> filtered() {
        var all = WaypointManager.all();
        String key = (worldFilter != null && !"*".equals(worldFilter)) ? worldFilter
                : com.example.pvpclient.hud.WaypointRenderer.currentWorldKey(
                        MinecraftClient.getInstance());
        List<WaypointManager.Waypoint> out = new java.util.ArrayList<>();
        for (var w : all) {
            if (!"*".equals(worldFilter) && !WaypointManager.matches(w, key)) continue;
            if (dimFilter != null) {
                String d = (w.dimension == null) ? "" : w.dimension;
                if (!d.contains(dimFilter)) continue;
            }
            out.add(w);
        }
        return out;
    }

    /** Naechster Dimensions-Filter beim Durchklicken. */
    private String nextDim(String cur) {
        if (cur == null) return "overworld";
        if (cur.equals("overworld")) return "the_nether";
        if (cur.equals("the_nether")) return "the_end";
        return null;
    }

    private String dimLabel(String cur) {
        if (cur == null) return "alle Dimensionen";
        if (cur.equals("overworld")) return "Oberwelt";
        if (cur.equals("the_nether")) return "Nether";
        return "End";
    }

    /** Kurzform einer Welt-Kennung fuer die Anzeige. */
    private String shortWorld(String key) {
        if (key == null) return "aktuelle Welt";
        if ("*".equals(key)) return "alle Welten";
        String s = key;
        int bar = s.indexOf('|');
        String place = (bar > 0) ? s.substring(0, bar) : s;
        String dim = (bar > 0) ? s.substring(bar + 1) : "";
        place = place.replace("mp:", "").replace("sp:", "");
        dim = dim.replace("minecraft:", "");
        return place + (dim.isEmpty() ? "" : " / " + dim);
    }

    private boolean inRect(int x, int y, int w, int h) {
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
        com.example.pvpclient.core.ConfigManager.save();
        // parent kann null sein, wenn die Verwaltung ueber die Taste geoeffnet
        // wurde -- dann zurueck ins Spiel statt in einen leeren Bildschirm.
        this.client.setScreen(parent);
    }
}
