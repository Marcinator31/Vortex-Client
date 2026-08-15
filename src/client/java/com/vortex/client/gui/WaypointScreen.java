package com.vortex.client.gui;

import com.vortex.client.waypoint.WaypointManager;
import com.vortex.client.waypoint.WorldProfiles;
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

    /**
     * Mindestbreite. Die tatsaechliche Breite richtet sich nach dem Bildschirm
     * (siehe winW()) -- bei fester Breite wurden Knoepfe abgeschnitten, sobald
     * mehrere nebeneinander standen.
     */
    private static final int HEADER_H = 82;
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
    private TextFieldWidget renameField;
    private TextFieldWidget profileField;
    private WaypointManager.Waypoint renaming = null;

    /** Marker, dessen Loeschung bestaetigt werden muss. */
    private WaypointManager.Waypoint pendingDelete = null;

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
    private int WIN_W;   // tatsaechliche Breite, in init() gesetzt

    public WaypointScreen(Screen parent) {
        super(Text.literal("Waypoints"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        // Breite an den Bildschirm anpassen, damit nichts abgeschnitten wird.
        // Never wider than the screen -- see MacroScreen for why the old
        // minimum width was a mistake.
        WIN_W = Math.min(this.width - 20, 620);
        winH = Math.min(this.height - 20, 400);
        winX = (this.width - WIN_W) / 2;
        winY = (this.height - winH) / 2;
        listH = winH - HEADER_H - FOOTER_H;

        coordField = new TextFieldWidget(this.textRenderer,
                winX + 90, winY + winH - 18, 150, 14, Text.literal(""));
        coordField.setDrawsBackground(false);
        coordField.setMaxLength(32);
        coordField.setVisible(false);
        this.addDrawableChild(coordField);

        renameField = new TextFieldWidget(this.textRenderer,
                winX + 90, winY + winH - 18, 150, 14, Text.literal(""));
        renameField.setDrawsBackground(false);
        renameField.setMaxLength(32);
        renameField.setVisible(false);
        this.addDrawableChild(renameField);

        profileField = new TextFieldWidget(this.textRenderer,
                winX + 90, winY + winH - 18, 150, 14, Text.literal(""));
        profileField.setDrawsBackground(false);
        profileField.setMaxLength(24);
        profileField.setVisible(false);
        this.addDrawableChild(profileField);

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
        // Zweite Zeile: Filter fuer Welt und Dimension nebeneinander.
        String wl = shortWorld(worldFilter);
        int wlw = this.textRenderer.getWidth(wl) + 16;
        int wlx = winX + 12;
        boolean wlHov = inRect(wlx, winY + 56, wlw, 20);
        roundRect(ctx, wlx, winY + 56, wlw, 20,
                wlHov ? mix(C_INNER, accent, 0.4f) : C_INNER);
        ctx.drawText(this.textRenderer, Text.literal(wl),
                wlx + 8, winY + 62, 0xFFD0D0DA, false);

        // Dimensions-Umschalter links neben dem Welt-Umschalter.
        String dl = dimLabel(dimFilter);
        int dlw = this.textRenderer.getWidth(dl) + 16;
        int dlx = wlx + wlw + 8;
        boolean dlHov = inRect(dlx, winY + 56, dlw, 20);
        roundRect(ctx, dlx, winY + 56, dlw, 20,
                dlHov ? mix(C_INNER, accent, 0.4f) : C_INNER);
        ctx.drawText(this.textRenderer, Text.literal(dl),
                dlx + 8, winY + 62, 0xFFD0D0DA, false);

        // Profil-Umschalter -- entscheidend auf Netzwerken, wo alle Server
        // dieselbe Adresse haben und sich sonst nicht unterscheiden lassen.
        String pl = "Profile: " + (WorldProfiles.getActive() == null
                ? "auto" : WorldProfiles.getActive());
        int plw = this.textRenderer.getWidth(pl) + 16;
        int plx = dlx + dlw + 8;
        boolean plHov = inRect(plx, winY + 56, plw, 20);
        roundRect(ctx, plx, winY + 56, plw, 20,
                plHov ? mix(C_INNER, accent, 0.4f) : C_INNER);
        ctx.drawText(this.textRenderer, Text.literal(pl),
                plx + 8, winY + 62, 0xFFD0D0DA, false);

        // Paste button, next to the world and dimension filters.
        String pl3 = "Paste";
        int plw3 = this.textRenderer.getWidth(pl3) + 16;
        int plx3 = dlx + dlw + 8 + this.textRenderer.getWidth(
                "Profil: " + (WorldProfiles.getActive() == null
                        ? "auto" : WorldProfiles.getActive())) + 16 + 8;
        if (plx3 + plw3 < winX + WIN_W - 90) {
            boolean pHov = inRect(plx3, winY + 56, plw3, 20);
            roundRect(ctx, plx3, winY + 56, plw3, 20,
                    pHov ? mix(C_INNER, accent, 0.4f) : C_INNER);
            ctx.drawText(this.textRenderer, Text.literal(pl3),
                    plx3 + 8, winY + 62, 0xFFD0D0DA, false);
        }

        String count = list.size() + " Marker";
        int cw = this.textRenderer.getWidth(count);
        ctx.drawText(this.textRenderer, Text.literal(count),
                winX + WIN_W - cw - 12, winY + 12, 0xFF74747F, false);

        // Eingabefeld-Rahmen + Knopf "Add here"
        roundRect(ctx, winX + 8, winY + 30, 208, 20, C_INNER);
        if (nameField != null && nameField.getText().isEmpty()) {
            ctx.drawText(this.textRenderer, Text.literal("Marker name..."),
                    winX + 12, winY + 36, 0xFF6A6A76, false);
        }
        // "Add here" direkt hinter dem Eingabefeld, die beiden Filter
        // rechts aussen -- so ueberlappt nichts mehr.
        String addLbl = "Add here";
        int aw = this.textRenderer.getWidth(addLbl) + 16;
        int ax = winX + 224;
        boolean addHov = inRect(ax, winY + 30, aw, 20);
        roundRect(ctx, ax, winY + 30, aw, 20,
                addHov ? mix(C_INNER, accent, 0.45f) : C_INNER);
        ctx.drawText(this.textRenderer, Text.literal(addLbl),
                ax + 8, winY + 36, 0xFFFFFFFF, false);

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
                boolean isGroup = com.vortex.client.waypoint.WaypointActions
                        .getBlockGroup() == w;
                if (isGroup) {
                    ctx.fill(winX + 8, y, winX + WIN_W - 8, y + 1,
                            Theme.INSTANCE.accent.get() | 0xFF000000);
                }

                String pos = w.x + ", " + w.y + ", " + w.z;
                if (!w.blocks.isEmpty()) pos += "   " + w.blocks.size() + " blocks";
                // Bei mehreren Welten dazuschreiben, wohin der Marker gehoert --
                // sonst sieht man nur Koordinaten ohne Zusammenhang.
                if ("*".equals(worldFilter)) pos += "   " + shortWorld(w.dimension);
                // Markers saved before the seed was added are not tied to a
                // specific backend server yet, so they still show up on all of
                // them. Flag it, and W (right click) pins it to where you are.
                else if (w.dimension != null && w.dimension.contains("|")
                        && !w.dimension.contains("|s")) {
                    pos += "   not pinned";
                }
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
                        bx - 40, y + 8,
                        pendingDelete == w ? 0xFFFF3030 : 0xFFFF7A7A, false);
                // Weitere Aktionen -- kurze Kuerzel, damit die Zeile schmal bleibt.
                ctx.drawText(this.textRenderer, Text.literal("B"),
                        bx - 58, y + 8,
                        isGroup ? (Theme.INSTANCE.accent.get() | 0xFF000000) : 0xFF9A9AA6,
                        false);
                ctx.drawText(this.textRenderer, Text.literal("N"),
                        bx - 74, y + 8, 0xFF9A9AA6, false);
                ctx.drawText(this.textRenderer, Text.literal("K"),
                        bx - 90, y + 8, 0xFF9A9AA6, false);
                ctx.drawText(this.textRenderer, Text.literal("S"),
                        bx - 186, y + 8, 0xFFD8A0FF, false);
                ctx.drawText(this.textRenderer, Text.literal("W"),
                        bx - 170, y + 8, 0xFF9AD8FF, false);
                ctx.drawText(this.textRenderer, Text.literal("G"),
                        bx - 154, y + 8, 0xFF9AFF9A, false);
                ctx.drawText(this.textRenderer, Text.literal("R"),
                        bx - 138, y + 8,
                        renaming == w ? (Theme.INSTANCE.accent.get() | 0xFF000000)
                                      : 0xFF9A9AA6, false);
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
            String msg = "No markers yet";
            ctx.drawText(this.textRenderer, Text.literal(msg),
                    winX + (WIN_W - this.textRenderer.getWidth(msg)) / 2,
                    winY + HEADER_H + listH / 2 - 4, 0xFF6A6A76, false);
        }

        // Fusszeile
        int fy = winY + winH - FOOTER_H;
        ctx.fill(winX, fy, winX + WIN_W, winY + winH, fade(C_BAR, openAnim));
        ctx.fill(winX, fy, winX + WIN_W, fy + 1, fade(C_LINE, openAnim));
        if (renaming != null) {
            ctx.drawText(this.textRenderer, Text.literal("Name:"),
                    winX + 12, fy + 6, 0xFFD0D0DA, false);
            roundRect(ctx, winX + 86, fy + 2, 158, 16, C_INNER);
            drawApply(ctx, fy, accent);
        } else if (profileField != null && profileField.isVisible()) {
            ctx.drawText(this.textRenderer, Text.literal("Profile:"),
                    winX + 12, fy + 6, 0xFFD0D0DA, false);
            roundRect(ctx, winX + 86, fy + 2, 158, 16, C_INNER);
            drawApply(ctx, fy, accent);
        } else if (editing != null) {
            ctx.drawText(this.textRenderer, Text.literal("X Y Z:"),
                    winX + 12, fy + 6, 0xFFD0D0DA, false);
            roundRect(ctx, winX + 86, fy + 2, 158, 16, C_INNER);
            String ok = "Apply";
            int okw = this.textRenderer.getWidth(ok) + 14;
            boolean hov = inRect(winX + 250, fy + 2, okw, 16);
            roundRect(ctx, winX + 250, fy + 2, okw, 16,
                    hov ? mix(C_INNER, accent, 0.45f) : C_INNER);
            ctx.drawText(this.textRenderer, Text.literal(ok),
                    winX + 257, fy + 6, 0xFFFFFFFF, false);
        } else {
            String hint = status.isEmpty()
                    ? "S share   W world   G go to   R rename   C coords   T tracer   B blocks   N nether   K copy"
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
        int wlw = this.textRenderer.getWidth(wl) + 16;
        int wlx = winX + 12;
        if (inRect(wlx, winY + 56, wlw, 20)) {
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

        // "Apply" bei der Koordinaten-Bearbeitung
        if (editing != null) {
            int fy2 = winY + winH - FOOTER_H;
            String ok = "Apply";
            int okw = this.textRenderer.getWidth(ok) + 14;
            if (inRect(winX + 250, fy2 + 2, okw, 16)) {
                if (renaming != null) applyRename(renaming);
                else if (profileField != null && profileField.isVisible()) applyProfile();
                else applyCoords(editing);
                return true;
            }
        }

        // Dimensions-Umschalter
        String dl = dimLabel(dimFilter);
        int dlw = this.textRenderer.getWidth(dl) + 16;
        int dlx = wlx + wlw + 8;
        if (inRect(dlx, winY + 56, dlw, 20)) {
            dimFilter = nextDim(dimFilter);
            scrollTarget = 0f;
            scroll = 0f;
            return true;
        }

        // Paste: reads a shared marker from the clipboard.
        String pst = "Paste";
        int pstw = this.textRenderer.getWidth(pst) + 16;
        int pstx = dlx + dlw + 8 + this.textRenderer.getWidth(
                "Profil: " + (WorldProfiles.getActive() == null
                        ? "auto" : WorldProfiles.getActive())) + 16 + 8;
        if (inRect(pstx, winY + 56, pstw, 20)) {
            String text = MinecraftClient.getInstance().keyboard.getClipboard();
            String world = com.vortex.client.hud.WaypointRenderer.currentWorldKey(
                    MinecraftClient.getInstance());
            var imported = WaypointManager.importFrom(text, world);
            if (imported == null) {
                status = "Clipboard holds no Vortex marker.";
            } else {
                com.vortex.client.core.ConfigManager.save();
                status = "Imported: " + imported.name;
            }
            return true;
        }

        // Profil: Linksklick schaltet durch, Rechtsklick oeffnet die Eingabe.
        String pl2 = "Profile: " + (WorldProfiles.getActive() == null
                ? "auto" : WorldProfiles.getActive());
        int plw2 = this.textRenderer.getWidth(pl2) + 16;
        int plx2 = dlx + dlw + 8;
        if (inRect(plx2, winY + 56, plw2, 20)) {
            if (click.button() == 1) {
                hideAllInputs();
                if (profileField != null) {
                    profileField.setText(WorldProfiles.getActive() == null
                            ? "" : WorldProfiles.getActive());
                    profileField.setVisible(true);
                    this.setFocused(profileField);
                }
            } else {
                WorldProfiles.setActive(WorldProfiles.next());
                com.vortex.client.hud.WaypointRenderer.invalidateWorldKey();
                com.vortex.client.core.ConfigManager.save();
            }
            return true;
        }

        // "Add here"
        String addLbl = "Add here";
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
                    var cs = new com.vortex.client.core.setting.ColorSetting("Marker Colour", w.color);
                    MinecraftClient.getInstance().setScreen(
                            new ColorPickerScreen(this, cs, () -> w.color = cs.get()));
                    return true;
                }
                if (inRect(bx - 22, y + 6, 14, 12)) {
                    w.visible = !w.visible;
                    return true;
                }
                if (inRect(bx - 42, y + 6, 14, 12)) {
                    // Zweimal klicken zum Loeschen -- ein Fehlklick soll nicht
                    // stillschweigend einen Marker vernichten.
                    if (pendingDelete != w) {
                        pendingDelete = w;
                        status = "Click x again to delete \"" + w.name + "\".";
                        return true;
                    }
                    if (com.vortex.client.waypoint.WaypointActions.getBlockGroup() == w) {
                        com.vortex.client.waypoint.WaypointActions.setBlockGroup(null);
                    }
                    WaypointManager.remove(w);
                    com.vortex.client.core.ConfigManager.save();
                    pendingDelete = null;
                    status = "Deleted.";
                    return true;
                }
                // B: Ziel fuer markierte Bloecke waehlen.
                //    Rechtsklick leert die Bloecke dieser Gruppe.
                if (inRect(bx - 60, y + 6, 14, 12)) {
                    if (click.button() == 1) {
                        int n = w.blocks.size();
                        w.blocks.clear();
                        com.vortex.client.core.ConfigManager.save();
                        status = n + " blocks removed.";
                        return true;
                    }
                    var cur = com.vortex.client.waypoint.WaypointActions.getBlockGroup();
                    com.vortex.client.waypoint.WaypointActions
                            .setBlockGroup(cur == w ? null : w);
                    return true;
                }
                // N: Gegenstueck in der anderen Dimension anlegen
                if (inRect(bx - 76, y + 6, 14, 12)) {
                    com.vortex.client.waypoint.WaypointActions
                            .createCounterpart(MinecraftClient.getInstance(), w);
                    return true;
                }
                // S: Marker als Text kopieren, zum Weitergeben.
                if (inRect(bx - 188, y + 6, 14, 12)) {
                    MinecraftClient.getInstance().keyboard.setClipboard(
                            WaypointManager.export(w));
                    status = "Copied. A friend can paste it with the Paste button.";
                    return true;
                }
                // W: Welt des Markers aendern.
                //    Linksklick schaltet durch die bekannten Welten,
                //    Rechtsklick setzt ihn auf die Welt, in der man gerade ist.
                if (inRect(bx - 172, y + 6, 14, 12)) {
                    if (click.button() == 1) {
                        w.dimension = com.vortex.client.hud.WaypointRenderer
                                .currentWorldKey(MinecraftClient.getInstance());
                        status = "Moved to: " + shortWorld(w.dimension);
                    } else {
                        w.dimension = nextWorldFor(w.dimension);
                        status = "World: " + shortWorld(w.dimension);
                    }
                    com.vortex.client.core.ConfigManager.save();
                    return true;
                }
                // G: hingehen
                if (inRect(bx - 156, y + 6, 14, 12)) {
                    goTo(w);
                    return true;
                }
                // R: umbenennen
                if (inRect(bx - 140, y + 6, 14, 12)) {
                    if (renaming == w) {
                        applyRename(w);
                    } else {
                        hideAllInputs();
                        renaming = w;
                        if (renameField != null) {
                            renameField.setText(w.name);
                            renameField.setVisible(true);
                            this.setFocused(renameField);
                        }
                    }
                    return true;
                }
                // C: Koordinaten bearbeiten
                if (inRect(bx - 124, y + 6, 14, 12)) {
                    if (editing == w) {
                        applyCoords(w);
                    } else {
                        hideAllInputs();
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
                    com.vortex.client.core.ConfigManager.save();
                    return true;
                }
                // K: Koordinaten kopieren
                if (inRect(bx - 92, y + 6, 14, 12)) {
                    com.vortex.client.waypoint.WaypointActions
                            .copyToClipboard(MinecraftClient.getInstance(), w);
                    return true;
                }
            }
            y += ROW_H + 4;
        }
        return false;
    }

    /**
     * Zum Marker gehen.
     *
     * EHRLICH: Ein Client kann nicht teleportieren -- das entscheidet der
     * Server. Hier wird nur der passende Befehl geschickt. Fehlen die Rechte,
     * lehnt der Server ab; dann bleiben die kopierten Koordinaten.
     */
    private void goTo(WaypointManager.Waypoint w) {
        var client = MinecraftClient.getInstance();
        if (client.player == null || client.getNetworkHandler() == null) return;
        try {
            String cmd = "tp " + w.x + " " + w.y + " " + w.z;
            client.getNetworkHandler().sendChatCommand(cmd);
            status = "Command sent: /" + cmd;
        } catch (Throwable pvpErr) {
            com.vortex.client.waypoint.WaypointActions.copyToClipboard(client, w);
            status = "Teleport not possible \u2014 coordinates copied.";
        }
    }

    /**
     * Blendet alle Eingabefelder der Fusszeile aus.
     *
     * Die drei Felder (Name, Profil, Koordinaten) liegen an derselben Stelle --
     * es darf immer nur eines sichtbar sein, sonst tippt man ins falsche.
     */
    private void hideAllInputs() {
        renaming = null;
        editing = null;
        if (renameField != null) renameField.setVisible(false);
        if (profileField != null) profileField.setVisible(false);
        if (coordField != null) coordField.setVisible(false);
    }

    /** Neuen Namen uebernehmen. */
    private void applyRename(WaypointManager.Waypoint w) {
        if (renameField == null) return;
        String n = renameField.getText().trim();
        if (n.isEmpty()) {
            status = "Name cannot be empty.";
            return;
        }
        w.name = n.replace('|', ' ').replace(';', ' ');
        com.vortex.client.core.ConfigManager.save();
        renaming = null;
        renameField.setVisible(false);
        status = "Renamed.";
    }

    /** Profil aus dem Eingabefeld uebernehmen (leer = automatisch). */
    private void applyProfile() {
        if (profileField == null) return;
        String n = profileField.getText().trim();
        WorldProfiles.setActive(n.isEmpty() ? null : n);
        com.vortex.client.hud.WaypointRenderer.invalidateWorldKey();
        com.vortex.client.core.ConfigManager.save();
        profileField.setVisible(false);
        status = n.isEmpty() ? "Profile: automatic" : ("Profile: " + n);
    }

    /** Eingegebene Koordinaten uebernehmen ("x y z" oder "x, y, z"). */
    private void applyCoords(WaypointManager.Waypoint w) {
        if (coordField == null) return;
        String txt = coordField.getText().replace(',', ' ').trim();
        String[] parts = txt.split("\\s+");
        if (parts.length != 3) {
            status = "Enter three numbers, e.g. 120 64 -300";
            return;
        }
        try {
            w.x = Integer.parseInt(parts[0]);
            w.y = Integer.parseInt(parts[1]);
            w.z = Integer.parseInt(parts[2]);
            com.vortex.client.core.ConfigManager.save();
            status = "Coordinates applied.";
        } catch (Throwable pvpErr) {
            status = "Those were not valid numbers.";
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

        String dim = com.vortex.client.hud.WaypointRenderer.currentDimension(client);
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
                : com.vortex.client.hud.WaypointRenderer.currentWorldKey(
                        MinecraftClient.getInstance());
        List<WaypointManager.Waypoint> out = new java.util.ArrayList<>();
        var me = MinecraftClient.getInstance().player;
        for (var w : all) {
            if (!"*".equals(worldFilter) && !WaypointManager.matches(w, key)) continue;
            if (dimFilter != null) {
                String d = (w.dimension == null) ? "" : w.dimension;
                if (!d.contains(dimFilter)) continue;
            }
            out.add(w);
        }
        // Naechste zuerst -- so steht oben, was gerade relevant ist.
        if (me != null) {
            final double mx0 = me.getX(), my0 = me.getY(), mz0 = me.getZ();
            out.sort((a, b) -> Double.compare(distSq(a, mx0, my0, mz0),
                                              distSq(b, mx0, my0, mz0)));
        }
        return out;
    }

    private static double distSq(WaypointManager.Waypoint w,
                                 double x, double y, double z) {
        double dx = w.x - x, dy = w.y - y, dz = w.z - z;
        return dx * dx + dy * dy + dz * dz;
    }

    /** Naechster Dimensions-Filter beim Durchklicken. */
    private String nextDim(String cur) {
        if (cur == null) return "overworld";
        if (cur.equals("overworld")) return "the_nether";
        if (cur.equals("the_nether")) return "the_end";
        return null;
    }

    private String dimLabel(String cur) {
        if (cur == null) return "all dimensions";
        if (cur.equals("overworld")) return "Overworld";
        if (cur.equals("the_nether")) return "Nether";
        return "End";
    }

    /** Gemeinsamer Uebernehmen-Knopf der Fusszeile. */
    private void drawApply(DrawContext ctx, int fy, int accent) {
        String ok = "Apply";
        int okw = this.textRenderer.getWidth(ok) + 14;
        boolean hov = inRect(winX + 250, fy + 2, okw, 16);
        roundRect(ctx, winX + 250, fy + 2, okw, 16,
                hov ? mix(C_INNER, accent, 0.45f) : C_INNER);
        ctx.drawText(this.textRenderer, Text.literal(ok),
                winX + 257, fy + 6, 0xFFFFFFFF, false);
    }

    /**
     * Naechste Welt beim Durchschalten.
     *
     * Die Reihe besteht aus der aktuellen Welt und allen, in denen bereits
     * Marker liegen -- damit kommt man ohne Tipparbeit ueberall hin, auch wenn
     * die Kennung technisch aussieht.
     */
    private String nextWorldFor(String current) {
        java.util.List<String> welten = new java.util.ArrayList<>();
        String hier = com.vortex.client.hud.WaypointRenderer
                .currentWorldKey(MinecraftClient.getInstance());
        welten.add(hier);
        for (String k : WaypointManager.knownWorlds()) {
            if (!welten.contains(k)) welten.add(k);
        }
        if (welten.isEmpty()) return current;
        int i = welten.indexOf(current);
        return welten.get((i + 1) % welten.size());
    }

    /** Kurzform einer Welt-Kennung fuer die Anzeige. */
    private String shortWorld(String key) {
        if (key == null) return "current world";
        if ("*".equals(key)) return "all worlds";
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
        com.vortex.client.core.ConfigManager.save();
        // parent kann null sein, wenn die Verwaltung ueber die Taste geoeffnet
        // wurde -- dann zurueck ins Spiel statt in einen leeren Bildschirm.
        this.client.setScreen(parent);
    }
}
