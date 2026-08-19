package com.vortex.client.gui;

import com.vortex.client.skin.SkinFetcher;
import com.vortex.client.skin.SkinTextureCache;
import com.vortex.client.skin.SkinWardrobe;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Die Skin-Garderobe.
 *
 * Oben ein Suchfeld: gibt man einen Spielernamen ein, wird dessen Skin ueber die
 * oeffentliche Mojang-Schnittstelle geholt, heruntergeladen und der Sammlung
 * hinzugefuegt. Darunter die Sammlung als Raster mit Vorschau (Kopf und
 * Oberkoerper), Name und Herkunft.
 *
 * Je Eintrag: umbenennen, Modell umschalten (klassisch/schlank), loeschen.
 * Ausserdem ein Knopf, der den Skin-Ordner im Dateimanager oeffnet -- so kann
 * man eigene PNG-Dateien einfach hineinkopieren und mit "Ordner einlesen"
 * uebernehmen.
 */
public class SkinScreen extends Screen {

    private static final int WIN_MAX_W = 560;
    private static final int HEADER_H = 62;
    private static final int FOOTER_H = 22;
    private static final int CELL_W = 104;
    private static final int CELL_H = 104;
    private static final int PAD = 10;

    private static final int C_DIM    = 0xB4000000;
    private static final int C_WINDOW = 0xF21B1B21;
    private static final int C_BAR    = 0xFF16161B;
    private static final int C_CARD   = 0xFF24242B;
    private static final int C_HOV    = 0xFF2E2E38;
    private static final int C_INNER  = 0xFF1C1C22;
    private static final int C_LINE   = 0xFF31313A;

    private final Screen parent;

    private TextFieldWidget searchField;
    private TextFieldWidget renameField;
    private SkinWardrobe.Skin renaming = null;

    private float openAnim = 0f;
    private long lastNano = 0L;
    private int mx = 0, my = 0;
    private float scroll = 0f, scrollTarget = 0f;

    private volatile String status = "";
    private volatile boolean busy = false;

    private int winX, winY, winW, winH, listH;
    private int columns = 4;

    /** Klickflaechen, beim Zeichnen gefuellt. */
    private enum Act { OPEN_FOLDER, IMPORT, SEARCH, BACK, PICK, RENAME, MODEL,
                       DELETE, UPLOAD, VARIANT }
    private static final class Hit {
        final int x, y, w, h; final Act act; final SkinWardrobe.Skin skin;
        Hit(int x, int y, int w, int h, Act act, SkinWardrobe.Skin skin) {
            this.x = x; this.y = y; this.w = w; this.h = h; this.act = act; this.skin = skin;
        }
        boolean has(int px, int py) {
            return px >= x && px < x + w && py >= y && py < y + h;
        }
    }
    private final List<Hit> hits = new ArrayList<>();

    public SkinScreen(Screen parent) {
        super(Text.literal("Skins"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        winW = Math.min(this.width - 40, WIN_MAX_W);
        winH = Math.min(this.height - 40, 400);
        winX = (this.width - winW) / 2;
        winY = (this.height - winH) / 2;
        listH = winH - HEADER_H - FOOTER_H;
        columns = Math.max(1, (winW - PAD * 2) / CELL_W);

        searchField = new TextFieldWidget(this.textRenderer,
                winX + 14, winY + 38, 180, 14, Text.literal(""));
        searchField.setDrawsBackground(false);
        searchField.setMaxLength(16);
        this.addDrawableChild(searchField);

        renameField = new TextFieldWidget(this.textRenderer,
                winX + 14, winY + winH - 18, 180, 14, Text.literal(""));
        renameField.setDrawsBackground(false);
        renameField.setMaxLength(32);
        renameField.setVisible(false);
        this.addDrawableChild(renameField);

        // Beim Oeffnen lose PNG-Dateien uebernehmen -- bequemer als ein Knopf.
        int found = SkinWardrobe.importLooseFiles();
        if (found > 0) status = found + " file(s) imported from the folder.";
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

        ctx.fill(0, 0, this.width, this.height, fade(C_DIM, openAnim));
        int accent = Theme.INSTANCE.accent.get() | 0xFF000000;

        roundRect(ctx, winX, winY, winW, winH, fade(C_WINDOW, openAnim));
        ctx.fill(winX, winY, winX + winW, winY + 1, fade(accent, openAnim));

        drawHeader(ctx, accent);
        drawGrid(ctx, accent);
        drawFooter(ctx, accent);

        if (searchField != null) {
            searchField.setX(winX + 20);
            searchField.setY(winY + 40);
        }
        if (renameField != null) {
            renameField.setX(winX + 76);
            renameField.setY(winY + winH - 16);
        }
        super.render(ctx, mouseX, mouseY, delta);
    }

    private void drawHeader(DrawContext ctx, int accent) {
        ctx.fill(winX, winY, winX + winW, winY + HEADER_H, fade(C_BAR, openAnim));
        ctx.fill(winX, winY + HEADER_H - 1, winX + winW, winY + HEADER_H, fade(C_LINE, openAnim));

        boolean backHov = in(winX + 8, winY + 8, 16, 16);
        ctx.drawTextWithShadow(this.textRenderer, Text.literal("<"),
                winX + 12, winY + 12, backHov ? accent : 0xFF9A9AA6);
        hits.add(new Hit(winX + 8, winY + 8, 16, 16, Act.BACK, null));

        ctx.drawTextWithShadow(this.textRenderer, Text.literal("Skin Wardrobe"),
                winX + 30, winY + 11, 0xFFFFFFFF);

        int count = SkinWardrobe.all().size();
        String c = count + (count == 1 ? " Skin" : " Skins");
        int cw = this.textRenderer.getWidth(c);
        ctx.drawText(this.textRenderer, Text.literal(c),
                winX + winW - cw - 12, winY + 12, 0xFF74747F, false);

        // Suchfeld
        roundRect(ctx, winX + 14, winY + 34, 192, 20, C_INNER);
        if (searchField != null && searchField.getText().isEmpty()) {
            ctx.drawText(this.textRenderer, Text.literal("Enter a player name..."),
                    winX + 20, winY + 40, 0xFF6A6A76, false);
        }

        // Knopf: holen
        String lbl = busy ? "..." : "Fetch skin";
        int bw = this.textRenderer.getWidth(lbl) + 18;
        boolean hov = in(winX + 212, winY + 34, bw, 20);
        roundRect(ctx, winX + 212, winY + 34, bw, 20,
                hov && !busy ? mix(C_INNER, accent, 0.45f) : C_INNER);
        ctx.drawText(this.textRenderer, Text.literal(lbl),
                winX + 221, winY + 40, busy ? 0xFF74747F : 0xFFFFFFFF, false);
        if (!busy) hits.add(new Hit(winX + 212, winY + 34, bw, 20, Act.SEARCH, null));

        // Knopf: Ordner oeffnen
        String ol = "Folder";
        int ow = this.textRenderer.getWidth(ol) + 18;
        int ox = winX + winW - ow - 12;
        boolean ohov = in(ox, winY + 34, ow, 20);
        roundRect(ctx, ox, winY + 34, ow, 20, ohov ? mix(C_INNER, accent, 0.45f) : C_INNER);
        ctx.drawText(this.textRenderer, Text.literal(ol), ox + 9, winY + 40, 0xFFD0D0DA, false);
        hits.add(new Hit(ox, winY + 34, ow, 20, Act.OPEN_FOLDER, null));

        // Knopf: Ordner einlesen
        String il = "Scan";
        int iw = this.textRenderer.getWidth(il) + 18;
        int ix = ox - iw - 6;
        boolean ihov = in(ix, winY + 34, iw, 20);
        roundRect(ctx, ix, winY + 34, iw, 20, ihov ? mix(C_INNER, accent, 0.45f) : C_INNER);
        ctx.drawText(this.textRenderer, Text.literal(il), ix + 9, winY + 40, 0xFFD0D0DA, false);
        hits.add(new Hit(ix, winY + 34, iw, 20, Act.IMPORT, null));
    }

    private void drawGrid(DrawContext ctx, int accent) {
        ctx.enableScissor(winX, winY + HEADER_H, winX + winW, winY + HEADER_H + listH);

        List<SkinWardrobe.Skin> list = SkinWardrobe.all();
        int i = 0;
        for (SkinWardrobe.Skin skin : list) {
            int col = i % columns;
            int row = i / columns;
            int cx = winX + PAD + col * CELL_W;
            int cy = winY + HEADER_H + PAD + row * CELL_H - (int) scroll;
            i++;
            if (cy + CELL_H < winY + HEADER_H || cy > winY + HEADER_H + listH) continue;

            boolean isActive = com.vortex.client.skin.ActiveSkin.get() == skin;
            boolean hov = in(cx, cy, CELL_W - 6, CELL_H - 6);
            int bg = isActive ? mix(C_CARD, accent, 0.32f) : (hov ? C_HOV : C_CARD);
            roundRect(ctx, cx, cy, CELL_W - 6, CELL_H - 6, bg);
            if (isActive) {
                // Farbiger Streifen oben zeigt den aktiven Skin.
                ctx.fill(cx, cy, cx + CELL_W - 6, cy + 2, accent);
            }

            // WICHTIG: Die Klickflaeche der Karte wird ZUERST eingetragen.
            // Die Auswertung laeuft rueckwaerts durch die Liste, dadurch
            // gewinnen die spaeter eingetragenen Knoepfe (umbenennen, hochladen,
            // loeschen) gegen die Karte. Andersherum waeren sie unerreichbar --
            // genau das war vorher der Fehler.
            hits.add(new Hit(cx, cy, CELL_W - 6, CELL_H - 30, Act.PICK, skin));

            drawSkinPreview(ctx, skin, cx + (CELL_W - 6) / 2 - 16, cy + 6, 4);

            // Name mittig, bei Bedarf gekuerzt.
            String name = shorten(skin.name, CELL_W - 16);
            int nw = this.textRenderer.getWidth(name);
            ctx.drawText(this.textRenderer, Text.literal(name),
                    cx + (CELL_W - 6 - nw) / 2, cy + CELL_H - 54, 0xFFFFFFFF, false);

            String src = shorten(skin.source + (skin.slim ? "  schlank" : ""), CELL_W - 16);
            int sw = this.textRenderer.getWidth(src);
            ctx.drawText(this.textRenderer, Text.literal(src),
                    cx + (CELL_W - 6 - sw) / 2, cy + CELL_H - 44, 0xFF74747F, false);

            // Werkzeuge nur unter der Maus einblenden, damit das Raster ruhig bleibt.
            if (hov) {
                // Werkzeug-Reihe oberhalb des Hochladen-Knopfes, damit sich
                // die Klickflaechen nicht ueberlappen.
                int bx = cx + 4, by = cy + CELL_H - 40;
                ctx.drawText(this.textRenderer, Text.literal("rename"),
                        bx, by, 0xFF9AD8FF, false);
                hits.add(new Hit(bx, by, this.textRenderer.getWidth("rename"), 9,
                        Act.RENAME, skin));

                String ml = skin.slim ? "classic" : "slim";
                int mxp = cx + CELL_W - 10 - this.textRenderer.getWidth(ml) - 12;
                ctx.drawText(this.textRenderer, Text.literal(ml), mxp, by, 0xFFB4B4C0, false);
                hits.add(new Hit(mxp, by, this.textRenderer.getWidth(ml), 9, Act.MODEL, skin));

                ctx.drawText(this.textRenderer, Text.literal("x"),
                        cx + CELL_W - 16, by, 0xFFFF7A7A, false);
                hits.add(new Hit(cx + CELL_W - 18, by - 1, 12, 11, Act.DELETE, skin));

            }

            // Hochladen-Knopf: dauerhaft sichtbar und ausserhalb der
            // Karten-Klickflaeche, damit er zuverlaessig treffbar ist.
            // Bewusst deutlich abgesetzt -- das ist eine ECHTE Konto-Aenderung,
            // im Gegensatz zum Anklicken der Karte.
            boolean can = com.vortex.client.skin.SkinUploader.canUpload();
            int ubX = cx + 6;
            int ubY = cy + CELL_H - 24;
            int ubW = CELL_W - 18;
            boolean ubHov = can && in(ubX, ubY, ubW, 14);
            roundRect(ctx, ubX, ubY, ubW, 14,
                    can ? (ubHov ? mix(C_INNER, 0xFF55FF7A, 0.5f) : C_INNER) : 0xFF202027);
            String up = can ? "Visible to everyone" : "Anmeldung fehlt";
            String upShort = shorten(up, ubW - 8);
            int uw2 = this.textRenderer.getWidth(upShort);
            ctx.drawText(this.textRenderer, Text.literal(upShort),
                    ubX + (ubW - uw2) / 2, ubY + 3,
                    can ? 0xFF9AFF9A : 0xFF5A5A66, false);
            if (can) {
                hits.add(new Hit(ubX, ubY, ubW, 14, Act.UPLOAD, skin));
            }

        }

        int rows = (list.size() + columns - 1) / columns;
        int content = rows * CELL_H + PAD * 2;
        ctx.disableScissor();

        if (content > listH) {
            int trackH = listH - 8;
            int barH = Math.max(24, (int) (trackH * (listH / (float) content)));
            float p = scroll / Math.max(1f, content - listH);
            if (p < 0f) p = 0f;
            if (p > 1f) p = 1f;
            int barY = winY + HEADER_H + 4 + (int) ((trackH - barH) * p);
            ctx.fill(winX + winW - 5, winY + HEADER_H + 4, winX + winW - 3,
                    winY + HEADER_H + 4 + trackH, 0x30FFFFFF);
            ctx.fill(winX + winW - 5, barY, winX + winW - 3, barY + barH,
                    mix(accent, 0xFFFFFFFF, 0.15f));
        }

        if (list.isEmpty()) {
            String msg = "No skins yet — search for a player name above";
            ctx.drawText(this.textRenderer, Text.literal(msg),
                    winX + (winW - this.textRenderer.getWidth(msg)) / 2,
                    winY + HEADER_H + listH / 2 - 4, 0xFF6A6A76, false);
        }
    }

    /**
     * Zeichnet Kopf und Hut-Ebene eines Skins.
     *
     * Ein Skin-PNG ist 64x64 gross. Der Kopf von vorn liegt bei (8,8) mit 8x8
     * Pixeln, die Hut-Ebene darueber bei (40,8). Beide werden vergroessert
     * uebereinander gezeichnet, damit auch Skins mit Hut korrekt aussehen.
     */
    private void drawSkinPreview(DrawContext ctx, SkinWardrobe.Skin skin,
                                 int x, int y, int scale) {
        Identifier tex = SkinTextureCache.get(skin);
        int size = 8 * scale;
        if (tex == null) {
            // Platzhalter, wenn die Datei fehlt oder unlesbar ist.
            roundRect(ctx, x, y, size, size, 0xFF3A3A45);
            String q = "?";
            ctx.drawText(this.textRenderer, Text.literal(q),
                    x + size / 2 - this.textRenderer.getWidth(q) / 2, y + size / 2 - 4,
                    0xFF74747F, false);
            return;
        }
        try {
            // Grundschicht (Gesicht)
            ctx.drawTexture(RenderPipelines.GUI_TEXTURED, tex,
                    x, y, 8.0F, 8.0F, size, size, 8, 8, 64, 64);
            // Hut-Ebene darueber
            ctx.drawTexture(RenderPipelines.GUI_TEXTURED, tex,
                    x, y, 40.0F, 8.0F, size, size, 8, 8, 64, 64);
        } catch (Throwable pvpErr) {
            com.vortex.client.core.Errors.report("SkinScreen.preview", pvpErr);
        }
    }

    private void drawFooter(DrawContext ctx, int accent) {
        int fy = winY + winH - FOOTER_H;
        ctx.fill(winX, fy, winX + winW, winY + winH, fade(C_BAR, openAnim));
        ctx.fill(winX, fy, winX + winW, fy + 1, fade(C_LINE, openAnim));

        if (renaming != null) {
            ctx.drawText(this.textRenderer, Text.literal("New name:"),
                    winX + 12, fy + 7, 0xFFD0D0DA, false);
            roundRect(ctx, winX + 72, fy + 3, 188, 16, C_INNER);
            String ok = "Save";
            int okw = this.textRenderer.getWidth(ok) + 14;
            boolean hov = in(winX + 266, fy + 3, okw, 16);
            roundRect(ctx, winX + 266, fy + 3, okw, 16,
                    hov ? mix(C_INNER, accent, 0.45f) : C_INNER);
            ctx.drawText(this.textRenderer, Text.literal(ok),
                    winX + 273, fy + 7, 0xFFFFFFFF, false);
            hits.add(new Hit(winX + 266, fy + 3, okw, 16, Act.RENAME, renaming));
            return;
        }

        // Umschalter fuer die Einbindungs-Variante -- nur sichtbar, wenn ein
        // Skin aktiv ist. Hilft, wenn der Skin pink-schwarz erscheint.
        var actNow = com.vortex.client.skin.ActiveSkin.get();
        if (actNow != null) {
            String vl = "Variant " + com.vortex.client.skin.ActiveSkin.getVariant();
            int vw = this.textRenderer.getWidth(vl) + 14;
            int vx = winX + winW - vw - 10;
            boolean vhov = in(vx, fy + 3, vw, 16);
            roundRect(ctx, vx, fy + 3, vw, 16,
                    vhov ? mix(C_INNER, accent, 0.45f) : C_INNER);
            ctx.drawText(this.textRenderer, Text.literal(vl),
                    vx + 7, fy + 7, 0xFFD0D0DA, false);
            hits.add(new Hit(vx, fy + 3, vw, 16, Act.VARIANT, null));
        }

        String msg;
        if (!status.isEmpty()) {
            msg = status;
        } else {
            var act = com.vortex.client.skin.ActiveSkin.get();
            msg = (act == null)
                    ? "Click a skin to apply it (visible only to you)"
                    : ("Active: " + act.name + "  \u2014  click again for your own skin");
        }
        ctx.drawText(this.textRenderer, Text.literal(shorten(msg, winW - 24)),
                winX + 12, fy + 7, status.isEmpty() ? 0xFF74747F : 0xFFD0D0DA, false);
    }

    // ---------------------------------------------------------------- Eingabe

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
                case SEARCH:
                    startSearch();
                    return true;
                case OPEN_FOLDER:
                    openFolder();
                    return true;
                case IMPORT: {
                    int n = SkinWardrobe.importLooseFiles();
                    status = (n > 0) ? (n + " file(s) imported.")
                                     : "No new files found.";
                    return true;
                }
                case RENAME:
                    if (renaming == h.skin && renameField != null) {
                        SkinWardrobe.rename(h.skin, renameField.getText());
                        renaming = null;
                        renameField.setVisible(false);
                        status = "Renamed.";
                    } else {
                        renaming = h.skin;
                        if (renameField != null) {
                            renameField.setText(h.skin.name);
                            renameField.setVisible(true);
                            this.setFocused(renameField);
                        }
                    }
                    return true;
                case MODEL:
                    h.skin.slim = !h.skin.slim;
                    SkinWardrobe.save();
                    status = h.skin.name + ": Modell "
                            + (h.skin.slim ? "slim" : "classic");
                    return true;
                case DELETE:
                    if (com.vortex.client.skin.ActiveSkin.get() == h.skin) {
                        com.vortex.client.skin.ActiveSkin.clear();
                    }
                    SkinTextureCache.forget(h.skin.fileName);
                    SkinWardrobe.remove(h.skin);
                    if (renaming == h.skin) {
                        renaming = null;
                        if (renameField != null) renameField.setVisible(false);
                    }
                    status = "Deleted.";
                    return true;
                case VARIANT:
                    com.vortex.client.skin.ActiveSkin.toggleVariant();
                    status = "Variant " + com.vortex.client.skin.ActiveSkin.getVariant()
                            + " \u2014 look away and back to see it.";
                    return true;
                case UPLOAD:
                    startUpload(h.skin);
                    return true;
                case PICK:
                    // Nochmal auf denselben Skin klicken -> wieder eigener Skin.
                    if (com.vortex.client.skin.ActiveSkin.get() == h.skin) {
                        com.vortex.client.skin.ActiveSkin.clear();
                        status = "Back to your own skin.";
                    } else {
                        com.vortex.client.skin.ActiveSkin.set(h.skin);
                        status = h.skin.name + " applied (visible only to you).";
                    }
                    return true;
                default:
                    break;
            }
        }
        return false;
    }

    /** Sucht den eingegebenen Spielernamen und legt den Skin in der Sammlung ab. */
    private void startSearch() {
        if (busy || searchField == null) return;
        String name = searchField.getText().trim();
        if (name.isEmpty()) {
            status = "Enter a player name.";
            return;
        }
        busy = true;
        status = "Searching " + name + " ...";

        Thread t = new Thread(() -> {
            try {
                SkinFetcher.Result r = SkinFetcher.lookup(name);
                String file = SkinWardrobe.freeFileName(r.userName);
                SkinFetcher.download(r.textureUrl, file);
                SkinWardrobe.add(r.userName, file, r.userName, r.slim);
                status = r.userName + " added.";
                MinecraftClient.getInstance().execute(() -> {
                    if (searchField != null) searchField.setText("");
                });
            } catch (Throwable e) {
                String m = e.getMessage();
                status = (m == null || m.isEmpty())
                        ? ("Error: " + e.getClass().getSimpleName()) : m;
                com.vortex.client.core.Errors.report("SkinScreen.search", e);
            } finally {
                busy = false;
            }
        }, "vortexclient-skin-fetch");
        t.setDaemon(true);
        t.start();
    }

    /**
     * Laedt den Skin auf das Konto hoch -- danach sehen ihn auch alle anderen.
     * Laeuft im Hintergrund, damit das Spiel nicht stehen bleibt.
     */
    private void startUpload(SkinWardrobe.Skin skin) {
        if (busy) return;
        if (!com.vortex.client.skin.SkinUploader.canUpload()) {
            status = "Not signed in \u2014 upload unavailable.";
            return;
        }
        busy = true;
        status = "Uploading " + skin.name + " to your account...";

        Thread t = new Thread(() -> {
            try {
                com.vortex.client.skin.SkinUploader.upload(skin.path(), skin.slim);
                status = skin.name + " is now your account skin \u2014 visible to everyone.";
            } catch (Throwable e) {
                String m = e.getMessage();
                status = (m == null || m.isEmpty())
                        ? ("Error: " + e.getClass().getSimpleName()) : m;
                com.vortex.client.core.Errors.report("SkinScreen.upload", e);
            } finally {
                busy = false;
            }
        }, "vortexclient-skin-upload");
        t.setDaemon(true);
        t.start();
    }

    private void openFolder() {
        try {
            java.nio.file.Files.createDirectories(SkinWardrobe.skinDir());
            Util.getOperatingSystem().open(SkinWardrobe.skinDir().toUri());
            status = "Folder opened \u2014 copy PNGs in, then press Scan.";
        } catch (Throwable pvpErr) {
            status = "Folder: " + SkinWardrobe.skinDir();
            com.vortex.client.core.Errors.report("SkinScreen.openFolder", pvpErr);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY,
                                 double horizontal, double vertical) {
        int rows = (SkinWardrobe.all().size() + columns - 1) / columns;
        int content = rows * CELL_H + PAD * 2;
        scrollTarget -= (float) vertical * 34f;
        float max = Math.max(0f, content - listH);
        if (scrollTarget < 0f) scrollTarget = 0f;
        if (scrollTarget > max) scrollTarget = max;
        return true;
    }

    // ----------------------------------------------------------- Hilfsmittel

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
        SkinWardrobe.save();
        this.client.setScreen(parent);
    }
}
