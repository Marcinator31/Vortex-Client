package com.vortex.client.beta;

import com.vortex.client.gui.Theme;
import com.vortex.client.gui.VortexStyle;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Beta-Test-Checkliste im Spiel: jeden neuen Punkt ausprobieren, abhaken oder
 * einen Fehler melden. Die Liste selbst kommt vom Launcher (BetaTest).
 */
public class BetaScreen extends Screen {

    private static final int HEADER_H = 48;
    private static final int FOOTER_H = 22;

    private final Screen parent;
    private int winX, winY, winW, winH, listH;
    private int mx, my;
    private float scroll, scrollTarget, openAnim;
    private long lastNano;
    private int contentH;
    private boolean nurOffene = false;

    private record Hit(int x, int y, int w, int h, Runnable run) {}
    private final List<Hit> hits = new ArrayList<>();

    // Fehler melden
    private BetaTest.Item melden = null;
    private String meldenTitel = "";
    private EditBox eingabe;
    private long gesendetBis = 0;
    private long wartetSeit = 0;

    public BetaScreen(Screen parent) {
        super(Component.literal("Beta test"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        winW = Math.min(this.width - 20, 640);
        winH = Math.min(this.height - 20, 440);
        winX = (this.width - winW) / 2;
        winY = (this.height - winH) / 2;
        listH = winH - HEADER_H - FOOTER_H;
        BetaTest.neuLaden(true);
        if (melden != null) oeffneMelden(melden, meldenTitel);
    }

    private void oeffneMelden(BetaTest.Item item, String titel) {
        melden = item;
        meldenTitel = titel;
        if (eingabe != null) removeWidget(eingabe);
        int pw = Math.min(winW - 40, 460);
        int px = (this.width - pw) / 2;
        eingabe = new EditBox(this.font, px + 14, this.height / 2 + 6, pw - 28, 16, Component.literal(""));
        eingabe.setMaxLength(600);
        eingabe.setBordered(false);
        addRenderableWidget(eingabe);
        setFocused(eingabe);
    }

    private void schliesseMelden() {
        if (eingabe != null) removeWidget(eingabe);
        eingabe = null;
        melden = null;
    }

    private void senden() {
        if (melden == null || eingabe == null) return;
        if (BetaTest.melden(melden.id(), eingabe.getValue())) {
            gesendetBis = System.currentTimeMillis() + 3500;
            schliesseMelden();
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        mx = mouseX; my = mouseY;
        long now = System.nanoTime();
        float dt = lastNano == 0 ? 0.016f : Math.min(0.1f, (now - lastNano) / 1e9f);
        lastNano = now;
        openAnim += (1f - openAnim) * (1f - (float) Math.exp(-14f * dt));
        scroll += (scrollTarget - scroll) * (1f - (float) Math.exp(-18f * dt));
        hits.clear();

        int accent = Theme.INSTANCE.accent.get() | 0xFF000000;
        ctx.fill(0, 0, width, height, VortexStyle.fade(VortexStyle.DIM, openAnim));
        VortexStyle.schatten(ctx, winX, winY, winW, winH, openAnim);
        rund(ctx, winX, winY, winW, winH, VortexStyle.fade(VortexStyle.WINDOW, openAnim));
        VortexStyle.akzentLinie(ctx, winX + 4, winY, winW - 8, openAnim);

        // --- Kopf -------------------------------------------------------------
        ctx.fill(winX, winY + 1, winX + winW, winY + HEADER_H, VortexStyle.BAR);
        ctx.fill(winX, winY + HEADER_H - 1, winX + winW, winY + HEADER_H, VortexStyle.LINE);
        boolean backHov = in(winX + 8, winY + 8, 16, 16);
        ctx.text(font, "<", winX + 12, winY + 12, backHov ? accent : 0xFF9A9AA6, false);
        hits.add(new Hit(winX + 6, winY + 6, 20, 20, this::onClose));
        ctx.text(font, "Beta test", winX + 30, winY + 11, 0xFFFFFFFF, false);

        int[] f = BetaTest.fortschritt();
        String stand = f[0] + " / " + f[1] + " checked" + (f[2] > 0 ? "  ·  " + f[2] + " bug(s) open" : "");
        ctx.text(font, stand, winX + winW - font.width(stand) - 12, winY + 11, f[2] > 0 ? 0xFFFF8A8A : VortexStyle.TEXT_DIM, false);
        int bx = winX + 12, by = winY + 30, bw = winW - 120;
        ctx.fill(bx, by, bx + bw, by + 4, VortexStyle.TRACK);
        int voll = f[1] == 0 ? bw : bw * f[0] / f[1];
        for (int i = 0; i < voll; i += 2) ctx.fill(bx + i, by, bx + Math.min(i + 2, voll), by + 4, VortexStyle.akzent((float) i / Math.max(1, bw)));
        String filt = (nurOffene ? "[x] " : "[ ] ") + "Only open";
        int fx = winX + winW - font.width(filt) - 12;
        boolean fh = in(fx - 4, winY + 26, font.width(filt) + 8, 12);
        ctx.text(font, filt, fx, by - 2, fh ? 0xFFFFFFFF : VortexStyle.TEXT_DIM, false);
        hits.add(new Hit(fx - 4, winY + 26, font.width(filt) + 8, 12, () -> { nurOffene = !nurOffene; scrollTarget = 0; }));

        // --- Liste ------------------------------------------------------------
        int top = winY + HEADER_H, bottom = top + listH;
        ctx.enableScissor(winX, top, winX + winW, bottom);
        int y = top + 6 - (int) scroll;
        int textW = winW - 24 - 20 - 44;
        List<BetaTest.Group> groups = BetaTest.groups();
        if (groups.isEmpty()) {
            ctx.text(font, BetaTest.aktiv() ? "Nothing to test -- no unreleased beta features." : "Start the game from the Vortex launcher with beta updates on.",
                    winX + 16, y + 4, VortexStyle.TEXT_DIM, false);
        }
        for (BetaTest.Group g : groups) {
            List<BetaTest.Item> items = new ArrayList<>();
            for (BetaTest.Item i : g.items()) if (!nurOffene || !i.checked() || i.offenerFehler()) items.add(i);
            if (items.isEmpty()) continue;
            // Gruppenkopf
            String kopf = g.component() + " " + g.version() + (g.heading().isEmpty() ? "" : "  ·  " + g.heading());
            if (y + 16 > top && y < bottom) {
                rund(ctx, winX + 8, y, winW - 16, 15, VortexStyle.INNER);
                int tag = "addon".equals(g.kind()) ? 0xFFF59E0B : accent;
                ctx.fill(winX + 8, y + 2, winX + 10, y + 13, tag);
                ctx.text(font, cut(kopf, winW - 40), winX + 16, y + 4, 0xFFE9E3FF, false);
            }
            y += 18;
            for (BetaTest.Item i : items) {
                List<String> zeilen = wrap(i.text(), textW);
                int berichte = i.reports().size();
                int h = Math.max(18, zeilen.size() * 10 + 8) + berichte * 10;
                if (y + h > top && y < bottom) {
                    boolean hov = in(winX + 8, y, winW - 16, h) && my >= top && my < bottom;
                    int grund = i.offenerFehler() ? 0x30FF5555 : hov ? VortexStyle.HOV : VortexStyle.CARD;
                    rund(ctx, winX + 8, y, winW - 16, h - 2, grund);
                    // Haken
                    int cx = winX + 14, cy = y + 4;
                    rund(ctx, cx, cy, 10, 10, i.checked() ? 0xFF4ADE80 : VortexStyle.TRACK);
                    if (i.checked()) {
                        ctx.fill(cx + 2, cy + 5, cx + 4, cy + 7, 0xFF0B1A10);
                        ctx.fill(cx + 4, cy + 6, cx + 6, cy + 8, 0xFF0B1A10);
                        ctx.fill(cx + 6, cy + 2, cx + 8, cy + 6, 0xFF0B1A10);
                    }
                    int tc = i.checked() ? 0xFF8E88A6 : VortexStyle.TEXT;
                    for (int z = 0; z < zeilen.size(); z++) ctx.text(font, zeilen.get(z), winX + 30, y + 5 + z * 10, tc, false);
                    int ry = y + 5 + zeilen.size() * 10;
                    for (BetaTest.Report r : i.reports()) {
                        String st = "resolved".equals(r.status()) ? "Fixed" : "sent".equals(r.status()) ? "Sent to GitHub" : "Waiting for launcher";
                        int sc = "resolved".equals(r.status()) ? 0xFF4ADE80 : "sent".equals(r.status()) ? 0xFFA78BFA : 0xFFFACC15;
                        ctx.text(font, cut("! " + st + ": " + r.text(), textW), winX + 30, ry, sc, false);
                        ry += 10;
                    }
                    final BetaTest.Item item = i;
                    final String titel = g.component() + " " + g.version();
                    if (my >= top && my < bottom) {
                        hits.add(new Hit(winX + 8, y, winW - 70, h - 2, () -> BetaTest.abhaken(item.id(), !item.checked())));
                    }
                    // "Bug"-Knopf
                    int kx = winX + winW - 56, ky = y + 3;
                    boolean kh = in(kx, ky, 42, 12) && my >= top && my < bottom;
                    rund(ctx, kx, ky, 42, 12, kh ? 0xFFB91C1C : 0xFF3A1D24);
                    ctx.text(font, "Bug", kx + (42 - font.width("Bug")) / 2, ky + 2, 0xFFFFD4D4, false);
                    if (my >= top && my < bottom) hits.add(0, new Hit(kx, ky, 42, 12, () -> oeffneMelden(item, titel)));
                }
                y += h;
            }
            y += 4;
        }
        contentH = (int) (y + scroll - top);
        ctx.disableScissor();

        // --- Fuss -------------------------------------------------------------
        int fy = winY + winH - FOOTER_H;
        ctx.fill(winX, fy, winX + winW, winY + winH, VortexStyle.BAR);
        ctx.fill(winX, fy, winX + winW, fy + 1, VortexStyle.LINE);
        int wartend = BetaTest.wartend();
        if (wartend == 0) wartetSeit = 0; else if (wartetSeit == 0) wartetSeit = System.currentTimeMillis();
        String fuss;
        int fc = VortexStyle.TEXT_DIM;
        if (System.currentTimeMillis() < gesendetBis) { fuss = "Bug report saved -- the launcher sends it to GitHub."; fc = 0xFF4ADE80; }
        else if (wartetSeit != 0 && System.currentTimeMillis() - wartetSeit > 6000) { fuss = wartend + " change(s) waiting -- is the Vortex launcher still open?"; fc = 0xFFFACC15; }
        else fuss = "Click an item once it works. Something broken? Press \"Bug\".";
        ctx.text(font, cut(fuss, winW - 24), winX + 12, fy + 7, fc, false);

        // --- Melden -----------------------------------------------------------
        if (melden != null) {
            ctx.fill(0, 0, width, height, 0xA0000000);
            int pw = Math.min(winW - 40, 460);
            List<String> z = wrap(melden.text(), pw - 28);
            int ph = 86 + Math.min(4, z.size()) * 10;
            int px = (width - pw) / 2, py = height / 2 - ph + 40;
            VortexStyle.schatten(ctx, px, py, pw, ph, 1f);
            rund(ctx, px, py, pw, ph, VortexStyle.WINDOW);
            ctx.fill(px, py, px + pw, py + 1, 0xFFEF4444);
            ctx.text(font, "Report a bug  ·  " + meldenTitel, px + 14, py + 10, 0xFFFFFFFF, false);
            for (int k = 0; k < Math.min(4, z.size()); k++) ctx.text(font, z.get(k), px + 14, py + 24 + k * 10, VortexStyle.TEXT_DIM, false);
            if (eingabe != null) {
                eingabe.setPosition(px + 14, height / 2 + 6);
                rund(ctx, px + 10, height / 2 + 2, pw - 20, 16, VortexStyle.INNER);
                if (eingabe.getValue().isEmpty()) ctx.text(font, "What happens? What did you expect?", px + 14, height / 2 + 6, 0xFF5A5A66, false);
            }
            int sy = py + ph - 20;
            boolean sh = in(px + pw - 74, sy, 60, 14), ah = in(px + pw - 142, sy, 60, 14);
            rund(ctx, px + pw - 74, sy, 60, 14, sh ? 0xFFDC2626 : 0xFFB91C1C);
            ctx.text(font, "Send", px + pw - 74 + (60 - font.width("Send")) / 2, sy + 3, 0xFFFFFFFF, false);
            rund(ctx, px + pw - 142, sy, 60, 14, ah ? VortexStyle.HOV : VortexStyle.CARD);
            ctx.text(font, "Cancel", px + pw - 142 + (60 - font.width("Cancel")) / 2, sy + 3, 0xFFFFFFFF, false);
            ctx.text(font, "Public on GitHub -- no coordinates or server address are sent.", px + 14, sy + 3, 0xFF6A6A76, false);
            hits.clear();
            hits.add(new Hit(px + pw - 74, sy, 60, 14, this::senden));
            hits.add(new Hit(px + pw - 142, sy, 60, 14, this::schliesseMelden));
        }
        super.extractRenderState(ctx, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent click, boolean doubled) {
        if (super.mouseClicked(click, doubled)) return true;
        for (Hit h : new ArrayList<>(hits)) {
            if (in(h.x(), h.y(), h.w(), h.h())) { h.run().run(); return true; }
        }
        return false;
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent key) {
        if (melden != null && (key.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER || key.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER)) {
            senden();
            return true;
        }
        return super.keyPressed(key);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (melden != null) return true;
        scrollTarget -= (float) vertical * 30f;
        float max = Math.max(0f, contentH - listH + 6);
        scrollTarget = Math.max(0f, Math.min(max, scrollTarget));
        return true;
    }

    @Override
    public void onClose() {
        if (melden != null) { schliesseMelden(); return; }
        Minecraft.getInstance().gui.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private boolean in(int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private List<String> wrap(String text, int w) {
        List<String> out = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            String probe = line.length() == 0 ? word : line + " " + word;
            if (font.width(probe) > w && line.length() > 0) {
                out.add(line.toString());
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(probe);
            }
        }
        if (line.length() > 0) out.add(line.toString());
        return out;
    }

    private String cut(String s, int max) {
        if (font.width(s) <= max) return s;
        String c = s;
        while (c.length() > 1 && font.width(c + "..") > max) c = c.substring(0, c.length() - 1);
        return c + "..";
    }

    private static void rund(GuiGraphicsExtractor ctx, int x, int y, int w, int h, int color) {
        if (w <= 0 || h <= 0) return;
        ctx.fill(x + 1, y, x + w - 1, y + h, color);
        ctx.fill(x, y + 1, x + 1, y + h - 1, color);
        ctx.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
    }
}
