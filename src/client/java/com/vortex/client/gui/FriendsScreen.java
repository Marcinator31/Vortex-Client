package com.vortex.client.gui;

import com.google.gson.JsonObject;
import com.vortex.client.social.Social;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import static com.vortex.client.social.Social.bool;
import static com.vortex.client.social.Social.obj;
import static com.vortex.client.social.Social.str;

/**
 * Freunde im Spiel (neu in 4.8.0): Liste mit Online-Status und Server,
 * Chats (auch Gruppen), Anfragen, Einladungen, Nachjoinen.
 *
 * Links: Freund hinzufuegen, Reiter (Freunde / Chats / Anfragen), Liste.
 * Rechts: der gewaehlte Freund oder die Gruppe -- Knoepfe (Beitreten,
 * Einladen, Mitspielen fragen, Favorit, Stumm) und der Chat.
 *
 * Die ausfuehrlichen Privatsphaere- und Benachrichtigungs-Einstellungen
 * stehen im Launcher (Freunde -> Privatsphaere & Benachrichtigungen); sie
 * gelten hier genauso, weil sie auf dem Server liegen.
 *
 * Gezeichnet wie die anderen Vortex-Fenster; Klickflaechen werden beim
 * Zeichnen gesammelt, damit Zeichnen und Klicken nie auseinanderlaufen.
 */
public class FriendsScreen extends Screen {

    private record Hit(int x, int y, int w, int h, Runnable run) {}

    private final Screen parent;
    private final List<Hit> hits = new ArrayList<>();
    private EditBox addField, chatField;
    private int mx, my;
    private int winX, winY, winW, winH, leftW;
    private float open = 0f;
    private long last = 0;

    private String tab = "friends";
    private String selFriend = null;     // uuid
    private String selConv = null;       // Gruppe
    private float listScroll = 0f;
    private float msgScroll = 0f;        // Abstand vom unteren Ende
    private int listContent = 0, listView = 0, msgContent = 0, msgView = 0;
    private String info = null;
    private long infoAt = 0;
    private boolean infoError = false;
    private long lastTypingSent = 0;
    private int seenRevision = -1;

    private static final int GREEN = 0xFF22C55E, BLUE = 0xFF60A5FA, YELLOW = 0xFFF5B942, RED = 0xFFEF4444, GREY = 0xFF4B4560;

    public FriendsScreen(Screen parent) {
        super(Component.literal("Friends"));
        this.parent = parent;
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    protected void init() {
        winW = Math.min(this.width - 16, 680);
        winH = Math.min(this.height - 16, 420);
        winX = (this.width - winW) / 2;
        winY = (this.height - winH) / 2;
        leftW = Math.max(150, Math.min(210, winW / 3));

        String keepAdd = addField != null ? addField.getValue() : "";
        String keepChat = chatField != null ? chatField.getValue() : "";
        addField = new EditBox(this.font, winX + 12, winY + 38, leftW - 44, 12, Component.literal(""));
        addField.setBordered(false);
        addField.setMaxLength(16);
        addField.setValue(keepAdd);
        this.addRenderableWidget(addField);

        chatField = new EditBox(this.font, winX + leftW + 14, winY + winH - 20, winW - leftW - 70, 12, Component.literal(""));
        chatField.setBordered(false);
        chatField.setMaxLength(1000);
        chatField.setValue(keepChat);
        chatField.setResponder(s -> typing());
        this.addRenderableWidget(chatField);
    }

    // =========================================================================
    // Zeichnen
    // =========================================================================

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        mx = mouseX; my = mouseY;
        long now = System.nanoTime();
        float dt = last == 0 ? 0.016f : Math.min(0.1f, (now - last) / 1e9f);
        last = now;
        open += (1f - open) * (1f - (float) Math.exp(-14f * dt));
        hits.clear();
        int accent = Theme.INSTANCE.accent.get() | 0xFF000000;

        ctx.fill(0, 0, this.width, this.height, VortexStyle.fade(VortexStyle.DIM, open));
        VortexStyle.schatten(ctx, winX, winY, winW, winH, open);
        rect(ctx, winX, winY, winW, winH, VortexStyle.fade(VortexStyle.WINDOW, open));
        VortexStyle.akzentLinie(ctx, winX + 4, winY, winW - 8, open);

        // --- Kopf -----------------------------------------------------------
        ctx.fill(winX, winY + 1, winX + winW, winY + 28, VortexStyle.BAR);
        ctx.fill(winX, winY + 28, winX + winW, winY + 29, VortexStyle.LINE);
        text(ctx, "Friends", winX + 12, winY + 10, VortexStyle.TEXT);
        int hx = winX + winW - 22;
        button(ctx, hx, winY + 6, 16, 16, "x", false, this::onClose);

        Social.Conn c = Social.conn();
        if (c != Social.Conn.ONLINE || Social.data() == null) {
            offline(ctx, c);
            info(ctx);
            super.extractRenderState(ctx, mouseX, mouseY, delta);
            addField.setVisible(false);
            chatField.setVisible(false);
            return;
        }
        addField.setVisible(true);

        // Eigener Status (klicken = wechseln)
        String mode = str(obj(obj(Social.data(), "me"), "status"), "mode");
        String modeLabel = switch (mode) { case "away" -> "Away"; case "dnd" -> "Do not disturb"; case "invisible" -> "Invisible"; default -> "Online"; };
        int dotCol = switch (mode) { case "away" -> YELLOW; case "dnd" -> RED; case "invisible" -> GREY; default -> GREEN; };
        String me = Social.myName() + "  ·  " + modeLabel;
        int sw = this.font.width(me) + 22;
        int sx = hx - sw - 8;
        boolean sHov = in(sx, winY + 6, sw, 16);
        rect(ctx, sx, winY + 6, sw, 16, sHov ? VortexStyle.HOV : VortexStyle.INNER);
        dot(ctx, sx + 6, winY + 11, dotCol);
        text(ctx, me, sx + 15, winY + 10, VortexStyle.TEXT);
        hits.add(new Hit(sx, winY + 6, sw, 16, this::cycleStatus));
        int unread = Social.unreadTotal();
        if (unread > 0) text(ctx, unread + " new", sx - this.font.width(unread + " new") - 10, winY + 10, 0xFFA78BFA);

        // --- Links ------------------------------------------------------------
        int lx = winX, ly = winY + 29;
        ctx.fill(lx + leftW, ly, lx + leftW + 1, winY + winH, VortexStyle.LINE);
        rect(ctx, lx + 8, ly + 5, leftW - 40, 18, VortexStyle.INNER);
        if (addField.getValue().isEmpty() && !addField.isFocused()) text(ctx, "Add friend (name)", lx + 12, ly + 10, VortexStyle.TEXT_DIM);
        button(ctx, lx + leftW - 28, ly + 5, 20, 18, "+", true, this::sendRequest);

        int tw = (leftW - 16) / 3;
        int reqCount = Social.incoming().size();
        tabButton(ctx, lx + 8, ly + 28, tw, "Friends", "friends", accent);
        tabButton(ctx, lx + 8 + tw, ly + 28, tw, "Chats", "chats", accent);
        tabButton(ctx, lx + 8 + tw * 2, ly + 28, tw, reqCount > 0 ? "Requests " + reqCount : "Requests", "requests", accent);

        int listTop = ly + 50, listBottom = winY + winH - 6;
        listView = listBottom - listTop;
        ctx.enableScissor(lx + 1, listTop, lx + leftW, listBottom);
        int y = listTop - (int) listScroll;
        y = switch (tab) {
            case "chats" -> drawChats(ctx, lx, y, listTop, listBottom);
            case "requests" -> drawRequests(ctx, lx, y, listTop, listBottom);
            default -> drawFriends(ctx, lx, y, listTop, listBottom);
        };
        listContent = y + (int) listScroll - listTop;
        ctx.disableScissor();

        // --- Rechts -----------------------------------------------------------
        drawRight(ctx, accent);
        info(ctx);
        super.extractRenderState(ctx, mouseX, mouseY, delta);
    }

    private void offline(GuiGraphicsExtractor ctx, Social.Conn c) {
        String title, body;
        switch (c) {
            case DISABLED -> { title = "Friends are not set up"; body = "Start the game from the Vortex Launcher to use friends."; }
            case CONNECTING -> { title = "Connecting..."; body = "Signing in with your Minecraft account."; }
            case AUTH_FAILED -> { title = "Sign-in failed"; body = Social.error() == null ? "Mojang did not confirm your account." : Social.error(); }
            default -> { title = "Friends server not reachable"; body = Social.error() == null ? "Trying again in the background." : Social.error(); }
        }
        int cy = winY + winH / 2 - 20;
        center(ctx, title, cy, VortexStyle.TEXT);
        center(ctx, body, cy + 14, VortexStyle.TEXT_DIM);
        if (c != Social.Conn.CONNECTING && c != Social.Conn.DISABLED) {
            int bw = this.font.width("Try again") + 20;
            button(ctx, winX + (winW - bw) / 2, cy + 32, bw, 18, "Try again", true, Social::reconnect);
        }
    }

    // ---- Liste: Freunde --------------------------------------------------------

    private int drawFriends(GuiGraphicsExtractor ctx, int lx, int y, int top, int bottom) {
        List<JsonObject> list = new ArrayList<>(Social.friends());
        list.sort(Comparator.comparingInt(FriendsScreen::rank)
                .thenComparing(f -> !bool(f, "favorite"))
                .thenComparing(f -> Social.label(f, str(f, "name")).toLowerCase()));
        if (list.isEmpty()) {
            text(ctx, "No friends yet.", lx + 12, y + 6, VortexStyle.TEXT_DIM);
            text(ctx, "Add someone by name above.", lx + 12, y + 18, VortexStyle.TEXT_DIM);
            return y + 30;
        }
        int lastRank = -1;
        for (JsonObject f : list) {
            int r = rank(f);
            if (r != lastRank) {
                lastRank = r;
                long n = list.stream().filter(x -> rank(x) == r).count();
                text(ctx, (r == 0 ? "PLAYING" : r == 1 ? "ONLINE" : "OFFLINE") + "  " + n, lx + 12, y + 5, VortexStyle.TEXT_DIM);
                y += 16;
            }
            String uuid = str(f, "uuid");
            JsonObject p = obj(f, "presence");
            boolean sel = uuid.equals(selFriend);
            boolean hov = in(lx + 6, y, leftW - 12, 26) && my >= top && my < bottom;
            if (sel || hov) rect(ctx, lx + 6, y, leftW - 12, 26, sel ? VortexStyle.mix(VortexStyle.INNER, VortexStyle.VIOLETT, 0.25f) : VortexStyle.HOV);
            dot(ctx, lx + 12, y + 6, dotColor(p));
            String name = (bool(f, "favorite") ? "* " : "") + Social.label(f, str(f, "name"));
            text(ctx, cut(name, leftW - 50), lx + 20, y + 4, VortexStyle.TEXT);
            text(ctx, cut(Social.presenceText(p, false), leftW - 34), lx + 20, y + 15, VortexStyle.TEXT_DIM);
            JsonObject dm = Social.dmWith(uuid);
            int unread = dm != null && dm.has("unread") && !bool(dm, "muted") ? dm.get("unread").getAsInt() : 0;
            if (unread > 0) badge(ctx, lx + leftW - 14, y + 4, unread);
            if (my >= top && my < bottom) hits.add(new Hit(lx + 6, y, leftW - 12, 26, () -> selectFriend(uuid)));
            y += 28;
        }
        return y;
    }

    private static int rank(JsonObject f) {
        String s = str(obj(f, "presence"), "state");
        return "playing".equals(s) ? 0 : "online".equals(s) ? 1 : 2;
    }

    // ---- Liste: Chats ------------------------------------------------------------

    private int drawChats(GuiGraphicsExtractor ctx, int lx, int y, int top, int bottom) {
        boolean any = false;
        for (JsonObject c : Social.convs()) {
            boolean group = "group".equals(str(c, "kind"));
            JsonObject lastMsg = obj(c, "last");
            if (!group && lastMsg.size() == 0) continue;
            any = true;
            String id = str(c, "id");
            String title = group ? str(c, "name") : Social.nameOf(str(c, "with"));
            boolean sel = group ? id.equals(selConv) : str(c, "with").equals(selFriend);
            boolean hov = in(lx + 6, y, leftW - 12, 26) && my >= top && my < bottom;
            if (sel || hov) rect(ctx, lx + 6, y, leftW - 12, 26, sel ? VortexStyle.mix(VortexStyle.INNER, VortexStyle.VIOLETT, 0.25f) : VortexStyle.HOV);
            text(ctx, cut((group ? "# " : "") + title, leftW - 50), lx + 12, y + 4, VortexStyle.TEXT);
            text(ctx, cut(preview(c, lastMsg), leftW - 26), lx + 12, y + 15, VortexStyle.TEXT_DIM);
            int unread = c.has("unread") && !bool(c, "muted") ? c.get("unread").getAsInt() : 0;
            if (unread > 0) badge(ctx, lx + leftW - 14, y + 4, unread);
            if (my >= top && my < bottom) hits.add(new Hit(lx + 6, y, leftW - 12, 26, () -> {
                if (group) selectGroup(id); else selectFriend(str(c, "with"));
            }));
            y += 28;
        }
        if (!any) {
            text(ctx, "No chats yet.", lx + 12, y + 6, VortexStyle.TEXT_DIM);
            y += 20;
        }
        text(ctx, "Groups: create them in the launcher.", lx + 12, y + 6, 0xFF5A5470);
        return y + 20;
    }

    private String preview(JsonObject c, JsonObject m) {
        if (m.size() == 0) return "No messages yet";
        if (bool(m, "deleted")) return "Message deleted";
        String kind = str(m, "kind");
        if ("system".equals(kind)) return "Group update";
        if ("invite".equals(kind)) return "Server invite";
        String who = str(m, "sender").equals(Social.myUuid()) ? "You: " : "group".equals(str(c, "kind")) ? str(m, "senderName") + ": " : "";
        return who + str(m, "body");
    }

    // ---- Liste: Anfragen ----------------------------------------------------------

    private int drawRequests(GuiGraphicsExtractor ctx, int lx, int y, int top, int bottom) {
        List<JsonObject> in = Social.incoming(), out = Social.outgoing();
        if (in.isEmpty() && out.isEmpty()) {
            text(ctx, "No open requests.", lx + 12, y + 6, VortexStyle.TEXT_DIM);
            return y + 20;
        }
        if (!in.isEmpty()) { text(ctx, "RECEIVED", lx + 12, y + 5, VortexStyle.TEXT_DIM); y += 16; }
        for (JsonObject r : in) {
            String uuid = str(r, "uuid");
            text(ctx, cut(str(r, "name"), leftW - 70), lx + 12, y + 4, VortexStyle.TEXT);
            int mutual = r.has("mutual") ? r.get("mutual").getAsInt() : 0;
            text(ctx, mutual > 0 ? mutual + " mutual" : Social.ago(r.get("created").getAsLong()), lx + 12, y + 15, VortexStyle.TEXT_DIM);
            if (my >= top && my < bottom) {
                button(ctx, lx + leftW - 56, y + 4, 22, 16, "+", true, () -> act("friend.accept", Social.args("uuid", uuid), "Friend request accepted."));
                button(ctx, lx + leftW - 30, y + 4, 22, 16, "x", false, () -> act("friend.decline", Social.args("uuid", uuid), null));
            }
            y += 28;
        }
        if (!out.isEmpty()) { text(ctx, "SENT", lx + 12, y + 5, VortexStyle.TEXT_DIM); y += 16; }
        for (JsonObject r : out) {
            String uuid = str(r, "uuid");
            text(ctx, cut(str(r, "name"), leftW - 60), lx + 12, y + 4, VortexStyle.TEXT);
            text(ctx, "Waiting", lx + 12, y + 15, VortexStyle.TEXT_DIM);
            if (my >= top && my < bottom) button(ctx, lx + leftW - 30, y + 4, 22, 16, "x", false, () -> act("friend.cancel", Social.args("uuid", uuid), null));
            y += 28;
        }
        return y;
    }

    // ---- Rechte Seite ---------------------------------------------------------------

    private void drawRight(GuiGraphicsExtractor ctx, int accent) {
        int rx = winX + leftW + 1, rw = winW - leftW - 1, top = winY + 29;
        int y = top;

        // Offene Einladungen immer oben
        for (JsonObject inv : Social.invites()) {
            String id = str(inv, "id");
            rect(ctx, rx + 8, y + 6, rw - 16, 22, VortexStyle.mix(VortexStyle.INNER, VortexStyle.VIOLETT, 0.3f));
            String what = str(obj(inv, "from"), "name") + " invites you to " + (str(inv, "serverName").isEmpty() ? str(inv, "address") : str(inv, "serverName"));
            text(ctx, cut(what, rw - 120), rx + 14, y + 13, VortexStyle.TEXT);
            button(ctx, rx + rw - 90, y + 9, 42, 16, "Join", true, () -> Social.answerInvite(id, true).exceptionally(e -> { err(e); return null; }));
            button(ctx, rx + rw - 44, y + 9, 30, 16, "No", false, () -> Social.answerInvite(id, false).exceptionally(e -> { err(e); return null; }));
            y += 26;
        }

        JsonObject friend = selFriend == null ? null : Social.friend(selFriend);
        JsonObject conv = currentConv();
        if (selFriend == null && selConv == null) {
            chatField.setVisible(false);
            int cy = winY + winH / 2 - 10;
            centerIn(ctx, "Pick a friend", rx, rw, cy, VortexStyle.TEXT);
            centerIn(ctx, "Chat, invite them or join their server.", rx, rw, cy + 13, VortexStyle.TEXT_DIM);
            centerIn(ctx, "Privacy & notification settings: in the launcher.", rx, rw, cy + 26, 0xFF5A5470);
            return;
        }

        // Kopf der Unterhaltung
        int hy = y + 6;
        String title, sub;
        if (selConv != null) {
            title = str(conv, "name");
            List<String> names = new ArrayList<>();
            for (JsonObject m : Social.list(conv, "members")) names.add(str(m, "uuid").equals(Social.myUuid()) ? "You" : Social.nameOf(str(m, "uuid")));
            sub = String.join(", ", names);
        } else {
            title = friend != null ? Social.label(friend, str(friend, "name")) + (str(friend, "nickname").isEmpty() ? "" : " (" + str(friend, "name") + ")") : Social.nameOf(selFriend);
            sub = friend != null ? Social.presenceText(obj(friend, "presence"), true) : "Not your friend";
        }
        // Knoepfe von rechts nach links
        int bx = rx + rw - 8;
        List<Object[]> btns = new ArrayList<>();
        if (selConv != null) {
            boolean muted = bool(conv, "muted");
            btns.add(new Object[]{muted ? "Unmute" : "Mute", false, (Runnable) () -> act("chat.mute", Social.args("conv", selConv, "muted", !muted), null)});
            btns.add(new Object[]{"Leave", false, (Runnable) this::leaveGroup});
        } else if (friend != null) {
            JsonObject a = obj(obj(friend, "presence"), "activity");
            boolean fav = bool(friend, "favorite"), muted = bool(friend, "muted");
            btns.add(new Object[]{muted ? "Unmute" : "Mute", false, (Runnable) () -> act("friend.update", Social.args("uuid", selFriend, "muted", !muted), null)});
            btns.add(new Object[]{fav ? "Unfav" : "Fav", false, (Runnable) () -> act("friend.update", Social.args("uuid", selFriend, "favorite", !fav), null)});
            if (!Social.currentAddress().isEmpty()) btns.add(new Object[]{"Invite", false, (Runnable) this::invite});
            if (bool(a, "joinable")) btns.add(new Object[]{"Join", true, (Runnable) () -> Social.joinFriend(selFriend).exceptionally(e -> { err(e); return null; })});
            else if (a.size() > 0 && !"offline".equals(str(obj(friend, "presence"), "state"))) {
                btns.add(new Object[]{"Ask to join", false, (Runnable) () -> act("join.ask", Social.args("to", selFriend), "Asked. They get a notification.")});
            }
        }
        for (Object[] b : btns) {
            String label = (String) b[0];
            int w = this.font.width(label) + 14;
            bx -= w;
            button(ctx, bx, hy + 4, w, 18, label, (Boolean) b[1], (Runnable) b[2]);
            bx -= 4;
        }
        text(ctx, cut(title, bx - rx - 20), rx + 12, hy + 4, VortexStyle.TEXT);
        text(ctx, cut(sub, bx - rx - 20), rx + 12, hy + 15, VortexStyle.TEXT_DIM);
        ctx.fill(rx, hy + 28, rx + rw, hy + 29, VortexStyle.LINE);

        // Nachrichten
        int msgTop = hy + 30, msgBottom = winY + winH - 38;
        msgView = msgBottom - msgTop;
        String convId = conv == null ? null : str(conv, "id");
        Social.openConv = convId;
        List<JsonObject> msgs = convId == null ? List.of() : Social.HISTORY.get(convId);
        if (convId != null && msgs == null) {
            Social.HISTORY.put(convId, new ArrayList<>());
            Social.loadHistory(convId, false);
            msgs = List.of();
        }
        if (convId != null && seenRevision != Social.revision) { seenRevision = Social.revision; Social.markRead(convId); }

        ctx.enableScissor(rx, msgTop, rx + rw, msgBottom);
        int maxW = (int) (rw * 0.72f);
        // Von unten nach oben zeichnen
        int yy = msgBottom - 4 + (int) msgScroll;
        String me = Social.myUuid();
        boolean group = selConv != null;
        long readUpTo = conv != null && conv.has("readUpTo") ? conv.get("readUpTo").getAsLong() : 0;
        long lastMineId = 0;
        for (JsonObject m : msgs) if (str(m, "sender").equals(me) && !bool(m, "deleted")) lastMineId = m.get("id").getAsLong();
        int total = 0;
        for (int i = msgs.size() - 1; i >= 0; i--) {
            JsonObject m = msgs.get(i);
            String kind = str(m, "kind");
            boolean mine = str(m, "sender").equals(me);
            if ("system".equals(kind)) {
                String s = systemText(m);
                yy -= 12; total += 12;
                centerIn(ctx, cut(s, rw - 20), rx, rw, yy + 2, VortexStyle.TEXT_DIM);
                continue;
            }
            String body;
            if (bool(m, "deleted")) body = "(message deleted)";
            else if (bool(m, "blocked")) body = "(message from a blocked player)";
            else if ("invite".equals(kind)) {
                JsonObject e = obj(m, "extra");
                body = (mine ? "You invited them to " : "Invites you to ") + (str(e, "serverName").isEmpty() ? str(e, "address") : str(e, "serverName") + " (" + str(e, "address") + ")");
            } else body = str(m, "body");
            List<String> lines = wrap(body, maxW - 12);
            int bw = 0;
            for (String l : lines) bw = Math.max(bw, this.font.width(l));
            bw += 12;
            int bh = lines.size() * 10 + 6;
            String meta = time(m.get("created").getAsLong()) + (m.has("edited") && m.get("edited").getAsLong() > 0 ? " · edited" : "")
                    + (mine && m.get("id").getAsLong() == lastMineId && !group && readUpTo >= lastMineId ? " · seen" : "");
            boolean showName = group && !mine && (i == 0 || !str(msgs.get(i - 1), "sender").equals(str(m, "sender")));
            int block = bh + 11 + (showName ? 10 : 0) + 3;
            yy -= block; total += block;
            if (yy + block < msgTop || yy > msgBottom) continue;
            int bxm = mine ? rx + rw - 10 - bw : rx + 10;
            int by = yy + (showName ? 10 : 0);
            if (showName) text(ctx, Social.nameOf(str(m, "sender")), bxm + 2, yy, 0xFFA78BFA);
            int bg = bool(m, "deleted") || bool(m, "blocked") ? VortexStyle.INNER
                    : "invite".equals(kind) ? VortexStyle.mix(VortexStyle.INNER, VortexStyle.BLAU, 0.35f)
                    : mine ? VortexStyle.mix(VortexStyle.INNER, VortexStyle.VIOLETT, 0.5f) : VortexStyle.CARD;
            rect(ctx, bxm, by, bw, bh, bg);
            for (int li = 0; li < lines.size(); li++) text(ctx, lines.get(li), bxm + 6, by + 4 + li * 10, bool(m, "deleted") ? VortexStyle.TEXT_DIM : VortexStyle.TEXT);
            int metaW = this.font.width(meta);
            text(ctx, meta, mine ? bxm + bw - metaW : bxm + 2, by + bh + 2, 0xFF5A5470);
        }
        if (msgs.isEmpty()) {
            centerIn(ctx, friend != null ? "Say hi to " + Social.label(friend, str(friend, "name")) + "!" : "No messages yet", rx, rw, msgTop + msgView / 2 - 4, VortexStyle.TEXT_DIM);
        }
        msgContent = total;
        ctx.disableScissor();
        // Aeltere laden, wenn oben angekommen
        if (convId != null && msgScroll >= Math.max(0, msgContent - msgView) - 2 && Boolean.TRUE.equals(Social.MORE.get(convId)) && msgContent > msgView) {
            Social.MORE.put(convId, false);
            Social.loadHistory(convId, true);
        }

        // Schreibt ...
        Map<String, Long> ty = convId == null ? null : Social.TYPING.get(convId);
        if (ty != null) {
            List<String> who = new ArrayList<>();
            long now = System.currentTimeMillis();
            for (var e : ty.entrySet()) if (now - e.getValue() < 5000 && !e.getKey().equals(me)) who.add(Social.nameOf(e.getKey()));
            if (!who.isEmpty()) text(ctx, String.join(", ", who) + (who.size() == 1 ? " is typing..." : " are typing..."), rx + 12, msgBottom + 2, VortexStyle.TEXT_DIM);
        }

        // Eingabe
        boolean canWrite = selConv != null || friend != null || conv != null;
        chatField.setVisible(canWrite);
        if (canWrite) {
            rect(ctx, rx + 8, winY + winH - 26, rw - 60, 20, VortexStyle.INNER);
            if (chatField.getValue().isEmpty() && !chatField.isFocused()) text(ctx, "Message " + (selConv != null ? str(conv, "name") : Social.nameOf(selFriend)), rx + 14, winY + winH - 20, VortexStyle.TEXT_DIM);
            button(ctx, rx + rw - 48, winY + winH - 26, 40, 20, "Send", true, this::send);
        }
    }

    // =========================================================================
    // Aktionen
    // =========================================================================

    private JsonObject currentConv() {
        if (selConv != null) return Social.conv(selConv);
        if (selFriend != null) return Social.dmWith(selFriend);
        return null;
    }

    private void selectFriend(String uuid) {
        selFriend = uuid; selConv = null; msgScroll = 0; seenRevision = -1;
        this.setFocused(chatField);
    }

    private void selectGroup(String id) {
        selConv = id; selFriend = null; msgScroll = 0; seenRevision = -1;
        this.setFocused(chatField);
    }

    private void cycleStatus() {
        String mode = str(obj(obj(Social.data(), "me"), "status"), "mode");
        String next = switch (mode) { case "online" -> "away"; case "away" -> "dnd"; case "dnd" -> "invisible"; default -> "online"; };
        act("status.set", Social.args("mode", next), null);
    }

    private void sendRequest() {
        String name = addField.getValue().trim();
        if (name.isEmpty()) { setInfo("Enter a Minecraft name.", true); return; }
        Social.request("friend.request", Social.args("name", name)).thenAccept(r -> {
            addField.setValue("");
            setInfo(bool(r, "friends") ? "You and " + str(r, "name") + " are now friends." : "Friend request sent to " + str(r, "name") + ".", false);
        }).exceptionally(e -> { err(e); return null; });
    }

    private void send() {
        String body = chatField.getValue().trim();
        if (body.isEmpty()) return;
        JsonObject a = Social.args("body", body);
        JsonObject conv = currentConv();
        if (conv != null) a.addProperty("conv", str(conv, "id"));
        else if (selFriend != null) a.addProperty("to", selFriend);
        else return;
        chatField.setValue("");
        msgScroll = 0;
        Social.request("chat.send", a).thenAccept(r -> {
            JsonObject m = obj(r, "message");
            String cid = str(m, "conv");
            List<JsonObject> h = Social.HISTORY.computeIfAbsent(cid, k -> new ArrayList<>());
            if (h.stream().noneMatch(x -> x.get("id").getAsLong() == m.get("id").getAsLong())) h.add(m);
            Social.bump();
        }).exceptionally(e -> { chatField.setValue(body); err(e); return null; });
    }

    private void typing() {
        JsonObject conv = currentConv();
        long now = System.currentTimeMillis();
        if (conv == null || chatField.getValue().isBlank() || now - lastTypingSent < 3000) return;
        lastTypingSent = now;
        Social.request("chat.typing", Social.args("conv", str(conv, "id")));
    }

    private void invite() {
        String addr = Social.currentAddress();
        var sd = Minecraft.getInstance().getCurrentServer();
        act("invite.send", Social.args("to", selFriend, "address", addr, "serverName", sd == null ? "" : sd.name, "version", Social.mcVersion()),
                "Invite sent.");
    }

    private void leaveGroup() {
        String id = selConv;
        Social.request("group.leave", Social.args("conv", id)).thenAccept(r -> { selConv = null; setInfo("You left the group.", false); })
                .exceptionally(e -> { err(e); return null; });
    }

    private void act(String op, JsonObject args, String ok) {
        Social.request(op, args).thenAccept(r -> { if (ok != null) setInfo(ok, false); }).exceptionally(e -> { err(e); return null; });
    }

    private void err(Throwable e) { setInfo(Social.errorOf(e), true); }

    private void setInfo(String s, boolean error) { info = s; infoAt = System.currentTimeMillis(); infoError = error; }

    private void info(GuiGraphicsExtractor ctx) {
        if (info == null || System.currentTimeMillis() - infoAt > 5000) return;
        int w = Math.min(winW - 40, this.font.width(info) + 20);
        int x = winX + (winW - w) / 2, y = winY + winH + 4;
        if (y + 18 > this.height) y = winY + winH - 22;
        rect(ctx, x, y, w, 16, infoError ? 0xF03A1520 : 0xF0152A1E);
        text(ctx, cut(info, w - 12), x + 6, y + 4, infoError ? 0xFFFF9AA3 : 0xFF7EE2A0);
    }

    private String systemText(JsonObject m) {
        JsonObject e = obj(m, "extra");
        String who = str(m, "senderName");
        return switch (str(e, "event")) {
            case "created" -> who + " created the group \"" + str(e, "name") + "\".";
            case "added" -> who + " added " + str(e, "name") + ".";
            case "removed" -> who + " removed " + str(e, "name") + ".";
            case "left" -> str(e, "name") + " left the group.";
            case "renamed" -> who + " renamed the group to \"" + str(e, "name") + "\".";
            default -> "";
        };
    }

    // =========================================================================
    // Eingaben
    // =========================================================================

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent click, boolean doubled) {
        if (super.mouseClicked(click, doubled)) return true;
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit h = hits.get(i);
            if (mx >= h.x && mx < h.x + h.w && my >= h.y && my < h.y + h.h) { h.run.run(); return true; }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (mouseX < winX + leftW) {
            listScroll = clamp(listScroll - (float) vertical * 24f, 0, Math.max(0, listContent - listView));
        } else {
            msgScroll = clamp(msgScroll + (float) vertical * 24f, 0, Math.max(0, msgContent - msgView + 8));
        }
        return true;
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        int key = event.key();
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER || key == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER) {
            if (chatField.isFocused() && chatField.isVisible()) { send(); return true; }
            if (addField.isFocused()) { sendRequest(); return true; }
        }
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_TAB && addField.isVisible()) {
            tab = switch (tab) { case "friends" -> "chats"; case "chats" -> "requests"; default -> "friends"; };
            listScroll = 0;
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        Social.openConv = null;
        Minecraft.getInstance().gui.setScreen(parent);
    }

    // =========================================================================
    // Zeichenhilfen
    // =========================================================================

    private void tabButton(GuiGraphicsExtractor ctx, int x, int y, int w, String label, String value, int accent) {
        boolean active = tab.equals(value);
        boolean hov = in(x, y, w - 2, 16);
        rect(ctx, x, y, w - 2, 16, active ? VortexStyle.mix(VortexStyle.INNER, accent, 0.5f) : hov ? VortexStyle.HOV : VortexStyle.INNER);
        String l = cut(label, w - 8);
        text(ctx, l, x + (w - 2 - this.font.width(l)) / 2, y + 4, active ? VortexStyle.TEXT : 0xFFB9B2CC);
        hits.add(new Hit(x, y, w - 2, 16, () -> { tab = value; listScroll = 0; }));
    }

    private void button(GuiGraphicsExtractor ctx, int x, int y, int w, int h, String label, boolean primary, Runnable run) {
        boolean hov = in(x, y, w, h);
        int accent = Theme.INSTANCE.accent.get() | 0xFF000000;
        int bg = primary ? VortexStyle.mix(VortexStyle.INNER, accent, hov ? 0.85f : 0.6f) : hov ? VortexStyle.HOV : VortexStyle.INNER;
        rect(ctx, x, y, w, h, bg);
        text(ctx, label, x + (w - this.font.width(label)) / 2, y + (h - 8) / 2, VortexStyle.TEXT);
        hits.add(new Hit(x, y, w, h, run));
    }

    private void badge(GuiGraphicsExtractor ctx, int rightX, int y, int n) {
        String s = n > 99 ? "99+" : String.valueOf(n);
        int w = this.font.width(s) + 6;
        rect(ctx, rightX - w, y, w, 10, VortexStyle.VIOLETT);
        text(ctx, s, rightX - w + 3, y + 1, 0xFFFFFFFF);
    }

    private void dot(GuiGraphicsExtractor ctx, int x, int y, int color) {
        ctx.fill(x + 1, y, x + 4, y + 5, color);
        ctx.fill(x, y + 1, x + 5, y + 4, color);
    }

    private static int dotColor(JsonObject p) {
        String state = str(p, "state"), mode = str(p, "mode");
        if ("offline".equals(state) || state.isEmpty()) return GREY;
        if ("dnd".equals(mode)) return RED;
        if ("away".equals(mode)) return YELLOW;
        return "playing".equals(state) ? BLUE : GREEN;
    }

    private void text(GuiGraphicsExtractor ctx, String s, int x, int y, int color) {
        ctx.text(this.font, Component.literal(s), x, y, color, false);
    }

    private void center(GuiGraphicsExtractor ctx, String s, int y, int color) { centerIn(ctx, s, winX, winW, y, color); }

    private void centerIn(GuiGraphicsExtractor ctx, String s, int x, int w, int y, int color) {
        text(ctx, s, x + (w - this.font.width(s)) / 2, y, color);
    }

    private void rect(GuiGraphicsExtractor ctx, int x, int y, int w, int h, int color) {
        if (w <= 0 || h <= 0) return;
        ctx.fill(x + 1, y, x + w - 1, y + h, color);
        ctx.fill(x, y + 1, x + 1, y + h - 1, color);
        ctx.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
    }

    private String cut(String s, int maxW) {
        if (maxW <= 8) return "";
        if (this.font.width(s) <= maxW) return s;
        String c = s;
        while (c.length() > 1 && this.font.width(c + "..") > maxW) c = c.substring(0, c.length() - 1);
        return c + "..";
    }

    /** Zeilenumbruch an Wortgrenzen; ueberlange Woerter werden hart getrennt. */
    private List<String> wrap(String s, int maxW) {
        List<String> out = new ArrayList<>();
        for (String para : s.split("\n", -1)) {
            StringBuilder line = new StringBuilder();
            for (String word : para.split(" ", -1)) {
                String tryLine = line.length() == 0 ? word : line + " " + word;
                if (this.font.width(tryLine) <= maxW) { line.setLength(0); line.append(tryLine); continue; }
                if (line.length() > 0) { out.add(line.toString()); line.setLength(0); }
                String w = word;
                while (this.font.width(w) > maxW && w.length() > 1) {
                    int n = w.length();
                    while (n > 1 && this.font.width(w.substring(0, n)) > maxW) n--;
                    out.add(w.substring(0, n));
                    w = w.substring(n);
                }
                line.append(w);
            }
            out.add(line.toString());
        }
        return out;
    }

    private static String time(long ts) {
        return new java.text.SimpleDateFormat("HH:mm").format(new java.util.Date(ts));
    }

    private boolean in(int x, int y, int w, int h) { return mx >= x && mx < x + w && my >= y && my < y + h; }

    private static float clamp(float v, float lo, float hi) { return Math.max(lo, Math.min(hi, v)); }
}
