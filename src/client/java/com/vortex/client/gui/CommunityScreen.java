package com.vortex.client.gui;

import com.vortex.client.macro.Macro;
import com.vortex.client.macro.MacroManager;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Macros and presets shared by other people.
 *
 * The list is fetched from the website, and a click imports an entry straight
 * into your own macros. Nothing is uploaded from here: sharing happens on the
 * site, where you are signed in and can put a name to what you post.
 *
 * Fetching runs on a thread of its own. On the game thread a slow reply would
 * freeze the whole client, and a website that is briefly unreachable would look
 * exactly like a crash.
 */
public class CommunityScreen extends Screen {

    private static final String SITE = "https://vortex-client.onrender.com";
    private static final String LIST_URL = SITE + "/api/presets";

    private static final int HEADER_H = 58;
    private static final int FOOTER_H = 24;
    private static final int ROW_H = 30;

    private static final int C_DIM    = 0xB4000000;
    private static final int C_WINDOW = 0xF21B1B21;
    private static final int C_BAR    = 0xFF16161B;
    private static final int C_CARD   = 0xFF24242B;
    private static final int C_HOV    = 0xFF2E2E38;
    private static final int C_INNER  = 0xFF1C1C22;
    private static final int C_LINE   = 0xFF31313A;

    /** One shared entry. */
    private record Entry(String name, String author, String description,
                         String kind, String shareCode, int downloads) {}

    private final Screen parent;
    private final List<Entry> entries = new ArrayList<>();

    private volatile String status = "Loading...";
    private volatile boolean loading = true;

    /** Show macros only, presets only, or both. */
    private String filter = "all";

    /**
     * The preset waiting for a slot to be chosen, or null.
     *
     * Importing a preset overwrites one of the three slots, and that is not
     * something to do without asking -- the slot may hold a setup someone spent
     * an evening on.
     */
    private Entry pendingPreset = null;

    private float openAnim = 0f;
    private long lastNano = 0L;
    private int mx = 0, my = 0;
    private float scroll = 0f, scrollTarget = 0f;
    private int winX, winY, winW, winH, listH;

    public CommunityScreen(Screen parent) {
        super(Component.literal("Community"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        winW = Math.min(this.width - 20, 620);
        winH = Math.min(this.height - 20, 400);
        winX = (this.width - winW) / 2;
        winY = (this.height - winH) / 2;
        listH = winH - HEADER_H - FOOTER_H;

        if (entries.isEmpty() && loading) {
            fetch();
        }
    }

    /** Fetches the list in the background. */
    private void fetch() {
        loading = true;
        status = "Loading...";
        Thread t = new Thread(() -> {
            try {
                String json = com.vortex.client.community.CommunityApi.get(LIST_URL);
                List<Entry> parsed = parse(json);
                synchronized (entries) {
                    entries.clear();
                    entries.addAll(parsed);
                }
                status = parsed.isEmpty() ? "Nothing shared yet." : "";
            } catch (Throwable pvpErr) {
                // The site being unreachable is not a fault worth a stack
                // trace -- it is a thing that happens. Say so and move on.
                status = "Could not reach the website.";
                com.vortex.client.core.Errors.report("Community.fetch", pvpErr);
            } finally {
                loading = false;
            }
        }, "vortex-community");
        t.setDaemon(true);
        t.start();
    }

    /**
     * Reads the entries out of the reply.
     *
     * Deliberately a small hand-written reader rather than a JSON library: the
     * shape is fixed and known, and pulling in a dependency for six fields
     * would be more to go wrong than it saves.
     */
    private static List<Entry> parse(String json) {
        List<Entry> out = new ArrayList<>();
        if (json == null) return out;
        for (String part : json.split("\\},\\s*\\{")) {
            String name = field(part, "name");
            if (name == null) continue;
            String author = field(part, "display_name");
            if (author == null || author.isEmpty()) author = field(part, "username");
            out.add(new Entry(
                    name,
                    author == null ? "unknown" : author,
                    field(part, "description"),
                    "macro".equals(field(part, "kind")) ? "macro" : "preset",
                    field(part, "share_code"),
                    number(part, "downloads")));
        }
        return out;
    }

    private static String field(String src, String key) {
        int i = src.indexOf("\"" + key + "\"");
        if (i < 0) return null;
        int colon = src.indexOf(':', i);
        if (colon < 0) return null;
        int q1 = src.indexOf('"', colon);
        if (q1 < 0) return null;
        StringBuilder sb = new StringBuilder();
        for (int k = q1 + 1; k < src.length(); k++) {
            char c = src.charAt(k);
            if (c == '\\' && k + 1 < src.length()) {
                char n = src.charAt(++k);
                sb.append(n == 'n' ? '\n' : n);
                continue;
            }
            if (c == '"') break;
            sb.append(c);
        }
        return sb.toString();
    }

    private static int number(String src, String key) {
        int i = src.indexOf("\"" + key + "\"");
        if (i < 0) return 0;
        int colon = src.indexOf(':', i);
        if (colon < 0) return 0;
        StringBuilder sb = new StringBuilder();
        for (int k = colon + 1; k < src.length(); k++) {
            char c = src.charAt(k);
            if (Character.isDigit(c)) sb.append(c);
            else if (sb.length() > 0) break;
        }
        try {
            return Integer.parseInt(sb.toString());
        } catch (Throwable pvpErr) {
            return 0;
        }
    }

    /** Entries after the filter. */
    private List<Entry> shown() {
        List<Entry> out = new ArrayList<>();
        synchronized (entries) {
            for (Entry e : entries) {
                if ("all".equals(filter) || filter.equals(e.kind())) out.add(e);
            }
        }
        return out;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
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
        ctx.text(this.font, Component.literal("<"),
                winX + 12, winY + 12, backHov ? accent : 0xFF9A9AA6);
        ctx.text(this.font, Component.literal("Community"),
                winX + 30, winY + 11, 0xFFFFFFFF);

        int bx = winX + 12;
        bx = tab(ctx, bx, winY + 30, "All", "all", accent);
        bx = tab(ctx, bx, winY + 30, "Macros", "macro", accent);
        bx = tab(ctx, bx, winY + 30, "Presets", "preset", accent);
        bx = button(ctx, bx + 8, winY + 30, loading ? "Loading..." : "Refresh", accent);
        button(ctx, bx, winY + 30, "Open website", accent);

        // List
        List<Entry> list = shown();
        ctx.enableScissor(winX, winY + HEADER_H, winX + winW, winY + HEADER_H + listH);
        int y = winY + HEADER_H + 4 - (int) scroll;

        if (list.isEmpty()) {
            ctx.text(this.font,
                    Component.literal(loading ? "Loading..." : status),
                    winX + 16, y + 8, 0xFF6A6A76, false);
        }

        for (Entry e : list) {
            if (y + ROW_H >= winY + HEADER_H && y <= winY + HEADER_H + listH) {
                boolean hov = in(winX + 8, y, winW - 16, ROW_H);
                roundRect(ctx, winX + 8, y, winW - 16, ROW_H, hov ? C_HOV : C_CARD);

                int tagColor = "macro".equals(e.kind()) ? 0xFF9AD8FF : 0xFFD8A0FF;
                ctx.text(this.font, Component.literal(e.kind().toUpperCase()),
                        winX + 16, y + 5, tagColor, false);
                ctx.text(this.font, Component.literal(e.name()),
                        winX + 68, y + 5, 0xFFFFFFFF, false);

                String sub = "by " + e.author();
                if (e.description() != null && !e.description().isEmpty()) {
                    sub += "  ·  " + e.description();
                }
                ctx.text(this.font, Component.literal(shorten(sub, winW - 160)),
                        winX + 16, y + 17, 0xFF74747F, false);

                String get = "Import";
                int gw = this.font.width(get) + 14;
                boolean gHov = in(winX + winW - gw - 16, y + 7, gw, 16);
                roundRect(ctx, winX + winW - gw - 16, y + 7, gw, 16,
                        gHov ? mix(C_INNER, accent, 0.45f) : C_INNER);
                ctx.text(this.font, Component.literal(get),
                        winX + winW - gw - 9, y + 11, 0xFFD0D0DA, false);
            }
            y += ROW_H + 3;
        }
        ctx.disableScissor();

        // The slot question, drawn over the list so it cannot be missed.
        if (pendingPreset != null) {
            int bw = Math.min(winW - 40, 320);
            int bh = 96;
            int bxx = winX + (winW - bw) / 2;
            int byy = winY + (winH - bh) / 2;

            ctx.fill(winX, winY + HEADER_H, winX + winW,
                    winY + winH - FOOTER_H, 0xC0000000);
            roundRect(ctx, bxx, byy, bw, bh, 0xFF24242B);
            ctx.fill(bxx, byy, bxx + bw, byy + 1, accent);

            ctx.text(this.font,
                    Component.literal("Import into which preset?"), bxx + 12, byy + 10, 0xFFFFFFFF);
            ctx.text(this.font,
                    Component.literal(shorten("\"" + pendingPreset.name() + "\" replaces that slot.", bw - 24)),
                    bxx + 12, byy + 24, 0xFF9A9AA6, false);

            for (int i = 0; i < 3; i++) {
                String label = "Preset " + (i + 1);
                boolean active = com.vortex.client.core.ConfigManager.getActivePreset() == i;
                int sw = (bw - 36) / 3;
                int sx = bxx + 12 + i * (sw + 6);
                boolean sHov = in(sx, byy + 44, sw, 20);
                roundRect(ctx, sx, byy + 44, sw, 20,
                        sHov ? mix(C_INNER, accent, 0.45f) : C_INNER);
                ctx.text(this.font, Component.literal(label),
                        sx + 6, byy + 50, active ? accent : 0xFFD0D0DA, false);
            }

            String cancel = "Cancel";
            int cw2 = this.font.width(cancel) + 16;
            boolean cHov = in(bxx + bw - cw2 - 12, byy + 70, cw2, 18);
            roundRect(ctx, bxx + bw - cw2 - 12, byy + 70, cw2, 18,
                    cHov ? C_HOV : C_INNER);
            ctx.text(this.font, Component.literal(cancel),
                    bxx + bw - cw2 - 4, byy + 75, 0xFF9A9AA6, false);

            ctx.text(this.font, Component.literal("The one in colour is active now"),
                    bxx + 12, byy + 76, 0xFF5A5A66, false);
        }

        // Footer
        int fy = winY + winH - FOOTER_H;
        ctx.fill(winX, fy, winX + winW, winY + winH, fade(C_BAR, openAnim));
        ctx.fill(winX, fy, winX + winW, fy + 1, fade(C_LINE, openAnim));
        ctx.text(this.font,
                Component.literal("Macros land in your list without a key. Presets ask which slot to replace."),
                winX + 12, fy + 8, 0xFF74747F, false);

        super.extractRenderState(ctx, mouseX, mouseY, delta);
    }

    private int tab(GuiGraphicsExtractor ctx, int x, int y, String label, String value, int accent) {
        int w = this.font.width(label) + 16;
        boolean active = filter.equals(value);
        boolean hov = in(x, y, w, 18);
        roundRect(ctx, x, y, w, 18,
                active ? mix(C_INNER, accent, 0.5f) : (hov ? C_HOV : C_INNER));
        ctx.text(this.font, Component.literal(label), x + 8, y + 5, 0xFFE6E6EC, false);
        return x + w + 4;
    }

    private int button(GuiGraphicsExtractor ctx, int x, int y, String label, int accent) {
        int w = this.font.width(label) + 16;
        boolean hov = in(x, y, w, 18);
        roundRect(ctx, x, y, w, 18, hov ? mix(C_INNER, accent, 0.35f) : C_INNER);
        ctx.text(this.font, Component.literal(label), x + 8, y + 5, 0xFFD0D0DA, false);
        return x + w + 6;
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent click, boolean doubled) {
        if (super.mouseClicked(click, doubled)) return true;

        // While the question is up, nothing behind it reacts.
        if (pendingPreset != null) {
            int bw = Math.min(winW - 40, 320);
            int bh = 96;
            int bxx = winX + (winW - bw) / 2;
            int byy = winY + (winH - bh) / 2;

            for (int i = 0; i < 3; i++) {
                int sw = (bw - 36) / 3;
                int sx = bxx + 12 + i * (sw + 6);
                if (in(sx, byy + 44, sw, 20)) {
                    Entry e = pendingPreset;
                    pendingPreset = null;
                    importPresetInto(e, i);
                    return true;
                }
            }
            String cancel = "Cancel";
            int cw2 = this.font.width(cancel) + 16;
            if (in(bxx + bw - cw2 - 12, byy + 70, cw2, 18)) {
                pendingPreset = null;
                status = "";
            }
            return true;
        }

        if (in(winX + 8, winY + 8, 16, 16)) {
            this.onClose();
            return true;
        }

        // Tabs and buttons, measured the same way they are drawn.
        int bx = winX + 12;
        for (String[] t : new String[][] { {"All","all"}, {"Macros","macro"}, {"Presets","preset"} }) {
            int w = this.font.width(t[0]) + 16;
            if (in(bx, winY + 30, w, 18)) {
                filter = t[1];
                scrollTarget = 0f;
                return true;
            }
            bx += w + 4;
        }
        bx += 8;
        int rw = this.font.width(loading ? "Loading..." : "Refresh") + 16;
        if (in(bx, winY + 30, rw, 18)) {
            if (!loading) fetch();
            return true;
        }
        bx += rw + 6;
        int ow = this.font.width("Open website") + 16;
        if (in(bx, winY + 30, ow, 18)) {
            net.minecraft.util.Util.getPlatform().openUri(SITE + "/presets.html");
            return true;
        }

        // Import buttons
        List<Entry> list = shown();
        int y = winY + HEADER_H + 4 - (int) scroll;
        for (Entry e : list) {
            int gw = this.font.width("Import") + 14;
            if (in(winX + winW - gw - 16, y + 7, gw, 16)) {
                doImport(e);
                return true;
            }
            y += ROW_H + 3;
        }
        return false;
    }

    /** Starts an import. Presets ask for a slot first. */
    private void doImport(Entry e) {
        if (!"macro".equals(e.kind())) {
            pendingPreset = e;
            status = "";
            return;
        }
        status = "Importing " + e.name() + "...";
        Thread t = new Thread(() -> {
            try {
                String content = com.vortex.client.community.CommunityApi.get(
                        SITE + "/api/presets/" + e.shareCode() + "/download");

                Macro m = MacroManager.importFrom(content);
                status = (m == null)
                        ? "That does not read as a macro."
                        : "Imported: " + m.name + " -- no key bound yet.";
                com.vortex.client.core.ConfigManager.save();
            } catch (Throwable pvpErr) {
                status = "Import failed.";
                com.vortex.client.core.Errors.report("Community.import", pvpErr);
            }
        }, "vortex-community-import");
        t.setDaemon(true);
        t.start();
    }

    /**
     * Fetches a preset and writes it into the chosen slot.
     *
     * The slot is then made active straight away. Importing something and
     * having to go and select it afterwards is a step nobody wants, and
     * forgetting it looks exactly like the import failed.
     */
    private void importPresetInto(Entry e, int slot) {
        status = "Importing " + e.name() + "...";
        Thread t = new Thread(() -> {
            try {
                String content = com.vortex.client.community.CommunityApi.get(
                        SITE + "/api/presets/" + e.shareCode() + "/download");

                // Writing files and reloading settings belongs on the game
                // thread: doing it here would have the client reading settings
                // that are being rewritten underneath it.
                Minecraft.getInstance().execute(() -> {
                    boolean ok = com.vortex.client.core.ConfigManager.importInto(slot, content);
                    status = ok
                            ? "Imported into preset " + (slot + 1) + " and selected."
                            : "Could not write that preset.";
                });
            } catch (Throwable pvpErr) {
                status = "Import failed.";
                com.vortex.client.core.Errors.report("Community.importPreset", pvpErr);
            }
        }, "vortex-community-import");
        t.setDaemon(true);
        t.start();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY,
                                 double horizontal, double vertical) {
        int content = shown().size() * (ROW_H + 3) + 8;
        scrollTarget -= (float) vertical * 30f;
        float max = Math.max(0f, content - listH);
        if (scrollTarget < 0f) scrollTarget = 0f;
        if (scrollTarget > max) scrollTarget = max;
        return true;
    }

    private String shorten(String s, int maxW) {
        if (this.font.width(s) <= maxW) return s;
        String cur = s;
        while (cur.length() > 1 && this.font.width(cur + "..") > maxW) {
            cur = cur.substring(0, cur.length() - 1);
        }
        return cur + "..";
    }

    private boolean in(int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private void roundRect(GuiGraphicsExtractor ctx, int x, int y, int w, int h, int color) {
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
        Minecraft.getInstance().setScreen(parent);
    }
}
