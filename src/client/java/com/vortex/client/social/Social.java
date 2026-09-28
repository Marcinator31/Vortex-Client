package com.vortex.client.social;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.vortex.client.core.Errors;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;

/**
 * Vortex-Freunde im Spiel (neu in 4.8.0).
 *
 * Spricht mit demselben Freunde-Server wie der Launcher (server/ im
 * Launcher-Repo). Die Adresse kommt vom Launcher (-Dvortex.friends.url).
 *
 * ANMELDUNG wie beim Beitreten eines Minecraft-Servers: Der Server schickt
 * eine Zufallszahl, wir melden sie mit der Minecraft-Sitzung bei Mojang an
 * (sessionserver /join), der Server prueft das bei Mojang. Der Zugangstoken
 * geht nur an Mojang, nie an den Freunde-Server.
 *
 * Das Spiel meldet, WO man spielt (Hauptmenue, Einzelspieler, Server mit
 * Adresse) -- wer das sehen darf, entscheidet der Server anhand der
 * Privatsphaere-Einstellungen des Spielers.
 *
 * Alles Netzwerk laeuft auf eigenen Threads; das Datenmodell wird nur auf dem
 * Spiel-Thread veraendert (mc.execute), damit die Oberflaeche nie halbe
 * Zustaende sieht.
 */
public final class Social {

    /** Ein gemeinsamer Client: jeder neue startet eigene Threads, bei jedem Neuverbinden einer war Verschwendung. */
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();


    /** Fest eingebaute Adresse, falls das Spiel ohne Launcher gestartet wird. Leer = aus. */
    public static final String DEFAULT_URL = "";

    public enum Conn { DISABLED, OFFLINE, CONNECTING, ONLINE, AUTH_FAILED }

    private Social() {}

    private static volatile Conn conn = Conn.OFFLINE;
    private static volatile String error = null;
    private static volatile WebSocket ws;
    private static CompletableFuture<WebSocket> sendChain = CompletableFuture.completedFuture(null);
    private static final Map<Integer, CompletableFuture<JsonObject>> PENDING = new ConcurrentHashMap<>();
    private static final AtomicInteger IDS = new AtomicInteger();
    private static String token;               // nur im Speicher: pro Spielstart neu ueber Mojang
    private static long nextTry = 0, backoff = 5000, lastPing = 0, lastActivityCheck = 0;
    private static String lastActivity = null;
    private static long activitySince = System.currentTimeMillis();
    private static int openScreenIn = -1;

    /** Letzter Stand vom Server (nur Spiel-Thread). */
    private static JsonObject data;
    private static JsonObject selfPresence;
    /** Chatverlaeufe, soweit geladen: conv -> Nachrichten (aufsteigend). */
    public static final Map<String, List<JsonObject>> HISTORY = new HashMap<>();
    public static final Map<String, Boolean> MORE = new HashMap<>();
    /** conv -> (uuid -> Zeitpunkt) fuer "schreibt ..." */
    public static final Map<String, Map<String, Long>> TYPING = new HashMap<>();
    /** Wird bei jeder Aenderung hochgezaehlt -- die Oberflaeche baut dann neu. */
    public static volatile int revision = 0;
    /** Welche Unterhaltung ist gerade im Freunde-Fenster offen (keine Toasts dafuer). */
    public static volatile String openConv = null;
    private static String lastDmFrom = null;

    // =========================================================================
    // Start / Takt
    // =========================================================================

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(Social::tick);
        SocialToasts.register();
    }

    public static String url() {
        String u = System.getProperty("vortex.friends.url", "").trim();
        return u.isEmpty() ? DEFAULT_URL : u;
    }

    private static void tick(Minecraft mc) {
        try {
            if (openScreenIn >= 0 && --openScreenIn < 0) {
                mc.gui.setScreen(new com.vortex.client.gui.FriendsScreen(null));
            }
            long now = System.currentTimeMillis();
            if (url().isEmpty()) { conn = Conn.DISABLED; return; }
            if ((conn == Conn.OFFLINE || conn == Conn.AUTH_FAILED) && now >= nextTry && mc.getUser() != null) {
                connect(mc);
            }
            if (conn == Conn.ONLINE) {
                if (now - lastPing > 25000) { lastPing = now; sendRaw("{\"t\":\"ping\"}"); }
                if (now - lastActivityCheck > 1000) { lastActivityCheck = now; sendActivity(mc, false); }
            }
        } catch (Throwable e) {
            Errors.report("Social.tick", e);
        }
    }

    /** Freunde-Fenster in ein paar Ticks oeffnen (nach einem Chat-Befehl schliesst sich erst der Chat). */
    public static void openScreenSoon() { openScreenIn = 2; }

    public static Conn conn() { return conn; }
    public static String error() { return error; }
    public static JsonObject data() { return data; }
    public static JsonObject selfPresence() { return selfPresence; }

    // =========================================================================
    // Verbindung
    // =========================================================================

    private static void connect(Minecraft mc) {
        conn = Conn.CONNECTING;
        nextTry = System.currentTimeMillis() + backoff;
        backoff = Math.min(backoff * 2, 120000);
        final String name = mc.getUser().getName();
        final String uuid = mc.getUser().getProfileId().toString().replace("-", "");
        final String access = mc.getUser().getAccessToken();
        HTTP.newWebSocketBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .buildAsync(URI.create(url()), new Listener(name, uuid, access))
                .whenComplete((sock, err) -> {
                    if (err != null) {
                        conn = Conn.OFFLINE;
                        error = "The friends server is not reachable.";
                        return;
                    }
                    ws = sock;
                    synchronized (Social.class) { sendChain = CompletableFuture.completedFuture(sock); }
                    JsonObject hello = new JsonObject();
                    hello.addProperty("t", "hello");
                    hello.addProperty("client", "game");
                    hello.addProperty("version", modVersion());
                    if (token != null) hello.addProperty("token", token);
                    sendRaw(hello.toString());
                });
    }

    public static void reconnect() {
        WebSocket w = ws;
        if (w != null) { try { w.abort(); } catch (Throwable ignored) {} }
        ws = null;
        conn = Conn.OFFLINE;
        backoff = 5000;
        nextTry = 0;
    }

    private static final class Listener implements WebSocket.Listener {
        private final StringBuilder buf = new StringBuilder();
        private final String name, uuid, access;
        Listener(String name, String uuid, String access) { this.name = name; this.uuid = uuid; this.access = access; }

        @Override
        public CompletionStage<?> onText(WebSocket w, CharSequence part, boolean last) {
            buf.append(part);
            if (last) {
                String text = buf.toString();
                buf.setLength(0);
                try { onMessage(w, JsonParser.parseString(text).getAsJsonObject(), name, uuid, access); }
                catch (Throwable e) { Errors.report("Social.onText", e); }
            }
            w.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket w, int code, String reason) { closed(w); return null; }

        @Override
        public void onError(WebSocket w, Throwable e) { closed(w); }
    }

    private static void closed(WebSocket w) {
        if (ws != w) return;
        ws = null;
        if (conn != Conn.AUTH_FAILED) conn = Conn.OFFLINE;
        for (var f : PENDING.values()) f.completeExceptionally(new IllegalStateException("The connection to the friends server was lost."));
        PENDING.clear();
        bump();
    }

    private static void onMessage(WebSocket w, JsonObject m, String name, String uuid, String access) {
        String t = str(m, "t");
        switch (t) {
            case "challenge" -> {
                String serverId = str(m, "serverId");
                Thread th = new Thread(() -> answerChallenge(w, name, uuid, access, serverId), "vortex-friends-login");
                th.setDaemon(true);
                th.start();
            }
            case "ready" -> {
                token = str(m, "token");
                conn = Conn.ONLINE;
                error = null;
                backoff = 5000;
                lastActivity = null;
                bump();
            }
            case "error" -> {
                error = str(m, "error");
                if (error.contains("Mojang") || error.contains("banned")) {
                    conn = Conn.AUTH_FAILED;
                    nextTry = System.currentTimeMillis() + 10 * 60 * 1000;
                }
                bump();
            }
            case "res" -> {
                CompletableFuture<JsonObject> f = PENDING.remove(m.get("id").getAsInt());
                if (f == null) return;
                if (m.has("ok") && m.get("ok").getAsBoolean()) {
                    JsonElement d = m.get("data");
                    Minecraft.getInstance().execute(() -> f.complete(d != null && d.isJsonObject() ? d.getAsJsonObject() : new JsonObject()));
                } else {
                    String err = str(m, "error");
                    Minecraft.getInstance().execute(() -> f.completeExceptionally(new IllegalStateException(err)));
                }
            }
            case "ev" -> {
                String ev = str(m, "ev");
                JsonElement d = m.get("data");
                Minecraft.getInstance().execute(() -> {
                    try { onEvent(ev, d != null && d.isJsonObject() ? d.getAsJsonObject() : new JsonObject()); }
                    catch (Throwable e) { Errors.report("Social.event", e); }
                });
            }
            default -> { }
        }
    }

    private static void answerChallenge(WebSocket w, String name, String uuid, String access, String serverId) {
        try {
            JsonObject body = new JsonObject();
            body.addProperty("accessToken", access);
            body.addProperty("selectedProfile", uuid);
            body.addProperty("serverId", serverId);
            HttpResponse<String> r = HTTP.send(
                    HttpRequest.newBuilder(URI.create("https://sessionserver.mojang.com/session/minecraft/join"))
                            .timeout(Duration.ofSeconds(15))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(body.toString())).build(),
                    HttpResponse.BodyHandlers.ofString());
            if (r.statusCode() != 204 && r.statusCode() != 200) {
                error = "Mojang refused the sign-in (" + r.statusCode() + "). Restart the game from the launcher.";
                conn = Conn.AUTH_FAILED;
                nextTry = System.currentTimeMillis() + 10 * 60 * 1000;
                try { w.abort(); } catch (Throwable ignored) {}
                return;
            }
            JsonObject login = new JsonObject();
            login.addProperty("t", "login");
            login.addProperty("name", name);
            login.addProperty("serverId", serverId);
            sendRaw(login.toString());
        } catch (Throwable e) {
            error = "Could not reach Mojang to sign in.";
            try { w.abort(); } catch (Throwable ignored) {}
        }
    }

    private static void sendRaw(String text) {
        synchronized (Social.class) {
            sendChain = sendChain.thenCompose(w -> {
                WebSocket cur = ws;
                if (cur == null) return CompletableFuture.completedFuture(w);
                return cur.sendText(text, true);
            }).exceptionally(e -> ws);
        }
    }

    /** Anfrage an den Server. Die Antwort kommt auf dem Spiel-Thread an. */
    public static CompletableFuture<JsonObject> request(String op, JsonObject args) {
        CompletableFuture<JsonObject> f = new CompletableFuture<>();
        if (conn != Conn.ONLINE || ws == null) {
            f.completeExceptionally(new IllegalStateException(conn == Conn.DISABLED
                    ? "Friends are not set up. Start the game from the Vortex launcher."
                    : "Not connected to the friends server."));
            return f;
        }
        int id = IDS.incrementAndGet();
        PENDING.put(id, f);
        JsonObject m = new JsonObject();
        m.addProperty("t", "req");
        m.addProperty("id", id);
        m.addProperty("op", op);
        m.add("args", args == null ? new JsonObject() : args);
        sendRaw(m.toString());
        f.orTimeout(15, TimeUnit.SECONDS).whenComplete((r, e) -> PENDING.remove(id));
        return f;
    }

    public static JsonObject args(Object... kv) {
        JsonObject o = new JsonObject();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            Object v = kv[i + 1];
            if (v instanceof Boolean b) o.addProperty((String) kv[i], b);
            else if (v instanceof Number n) o.addProperty((String) kv[i], n);
            else if (v instanceof JsonElement el) o.add((String) kv[i], el);
            else if (v != null) o.addProperty((String) kv[i], v.toString());
        }
        return o;
    }

    /** Fehlertext einer fehlgeschlagenen Anfrage. */
    public static String errorOf(Throwable e) {
        Throwable c = e;
        while (c.getCause() != null && c.getCause() != c) c = c.getCause();
        if (c instanceof java.util.concurrent.TimeoutException) return "The friends server did not answer.";
        return c.getMessage() == null ? "Something went wrong." : c.getMessage();
    }

    // =========================================================================
    // Wo spiele ich?
    // =========================================================================

    private static void sendActivity(Minecraft mc, boolean force) {
        String mode, address = "", serverName = "";
        ServerData sd = mc.getCurrentServer();
        if (mc.level == null) mode = "menu";
        else if (mc.isLocalServer() || mc.hasSingleplayerServer()) mode = "singleplayer";
        else if (sd != null && sd.isRealm()) mode = "realms";
        else if (sd != null) { mode = "server"; address = sd.ip == null ? "" : sd.ip; serverName = sd.name == null ? "" : sd.name; }
        else mode = "menu";
        String key = mode + "|" + address;
        if (!force && key.equals(lastActivity)) return;
        if (!key.equals(lastActivity)) activitySince = System.currentTimeMillis();
        lastActivity = key;
        request("activity", args("mode", mode, "address", address, "serverName", serverName,
                "version", mcVersion(), "client", "vortex", "since", activitySince));
    }

    public static String mcVersion() {
        try {
            return net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer("minecraft")
                    .map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("");
        } catch (Throwable e) { return ""; }
    }

    private static String modVersion() {
        try {
            return net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer("vortexclient")
                    .map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("?");
        } catch (Throwable e) { return "?"; }
    }

    /** Server, auf dem ich gerade bin (fuer Einladungen), sonst "". */
    public static String currentAddress() {
        ServerData sd = Minecraft.getInstance().getCurrentServer();
        if (Minecraft.getInstance().level == null || sd == null || sd.isRealm() || Minecraft.getInstance().isLocalServer()) return "";
        return sd.ip == null ? "" : sd.ip;
    }

    // =========================================================================
    // Ereignisse vom Server
    // =========================================================================

    private static void onEvent(String ev, JsonObject d) {
        switch (ev) {
            case "state" -> data = d;
            case "self" -> {
                selfPresence = obj(d, "presence");
                if (data != null && d.has("status")) obj(data, "me").add("status", d.get("status"));
            }
            case "presence" -> {
                JsonObject f = friend(str(d, "uuid"));
                if (f != null) {
                    JsonObject prev = obj(f, "presence");
                    JsonObject next = obj(d, "presence");
                    f.add("presence", next);
                    presenceNotice(f, prev, next);
                }
            }
            case "message" -> {
                JsonObject conv = obj(d, "conv");
                JsonObject msg = obj(d, "message");
                putConv(conv);
                String id = str(conv, "id");
                List<JsonObject> h = HISTORY.get(id);
                if (h != null && h.stream().noneMatch(x -> x.get("id").getAsLong() == msg.get("id").getAsLong())) h.add(msg);
                Map<String, Long> ty = TYPING.get(id);
                if (ty != null) ty.remove(str(msg, "sender"));
                messageNotice(conv, msg);
            }
            case "messageUpdate" -> {
                JsonObject msg = obj(d, "message");
                List<JsonObject> h = HISTORY.get(str(d, "conv"));
                if (h != null) for (int i = 0; i < h.size(); i++) if (h.get(i).get("id").getAsLong() == msg.get("id").getAsLong()) h.set(i, msg);
            }
            case "read" -> {
                JsonObject c = conv(str(d, "conv"));
                if (c != null) {
                    if (str(d, "uuid").equals(myUuid())) c.addProperty("unread", 0);
                    else c.addProperty("readUpTo", d.get("upTo").getAsLong());
                }
            }
            case "typing" -> TYPING.computeIfAbsent(str(d, "conv"), k -> new HashMap<>()).put(str(d, "uuid"), System.currentTimeMillis());
            case "invite" -> {
                if (data != null) {
                    JsonArray inv = arr(data, "invites");
                    inv.add(d);
                    data.add("invites", inv);
                }
                JsonObject from = obj(d, "from");
                JsonObject f = friend(str(from, "uuid"));
                if (notifyBool("invites") && !bool(f, "muted")) {
                    notice("invite", label(f, str(from, "name")) + " invites you",
                            (str(d, "serverName").isEmpty() ? str(d, "address") : str(d, "serverName")) + "  -  press " + keyName() + " to join", true);
                    chat("§d[Friends] §f" + label(f, str(from, "name")) + " §7invites you to §f" + str(d, "address") + "§7. §8/vf yes §7or §8/vf no");
                }
            }
            case "inviteAnswer" -> {
                JsonObject from = obj(d, "from");
                boolean ok = d.has("accept") && d.get("accept").getAsBoolean();
                notice("inviteAnswer", str(from, "name") + (ok ? " is coming" : " declined"), ok ? "They accepted your invite." : "They cannot join right now.", false);
            }
            case "joinRequest" -> {
                JsonObject f = friend(str(d, "uuid"));
                if (notifyBool("joinRequests") && !bool(f, "muted")) {
                    notice("joinRequest", label(f, str(d, "name")) + " wants to join you", "/vf invite " + str(d, "name"), true);
                }
            }
            case "request" -> {
                if (notifyBool("requests")) {
                    notice("request", "Friend request", str(d, "name") + " wants to be your friend.", false);
                    chat("§d[Friends] §f" + str(d, "name") + " §7wants to be your friend. §8/vf accept " + str(d, "name"));
                }
            }
            case "friendAdded" -> notice("friendAdded", "New friend", str(d, "name") + " is now your friend.", false);
            case "convRemoved" -> {
                if (data != null) {
                    JsonArray out = new JsonArray();
                    for (JsonElement e : arr(data, "convs")) if (!str(e.getAsJsonObject(), "id").equals(str(d, "conv"))) out.add(e);
                    data.add("convs", out);
                }
            }
            default -> { }
        }
        bump();
    }

    private static void putConv(JsonObject conv) {
        if (data == null) return;
        JsonArray out = new JsonArray();
        out.add(conv);
        for (JsonElement e : arr(data, "convs")) if (!str(e.getAsJsonObject(), "id").equals(str(conv, "id"))) out.add(e);
        data.add("convs", out);
    }

    // =========================================================================
    // Benachrichtigungen (gleiche Regeln wie im Launcher)
    // =========================================================================

    private static JsonObject notifySettings() {
        return data == null ? null : obj(obj(obj(data, "me"), "settings"), "notify");
    }

    private static boolean notifyBool(String key) {
        JsonObject n = notifySettings();
        return n != null && bool(n, key);
    }

    private static final java.util.Set<String> IMPORTANT = java.util.Set.of("message", "invite", "joinRequest");

    private static void notice(String kind, String title, String body, boolean important) {
        JsonObject n = notifySettings();
        if (n == null || !bool(n, "inGame")) return;
        String mode = str(obj(obj(data, "me"), "status"), "mode");
        if ("dnd".equals(mode) && bool(n, "dndSilence") && !"inviteAnswer".equals(kind)) return;
        String wp = str(n, "whilePlaying");
        if (Minecraft.getInstance().level != null) {
            if ("off".equals(wp)) return;
            if ("important".equals(wp) && !IMPORTANT.contains(kind)) return;
        }
        SocialToasts.push(title, body, important);
        if (bool(n, "sound")) {
            try {
                Minecraft.getInstance().getSoundManager().play(
                        net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                                net.minecraft.sounds.SoundEvents.NOTE_BLOCK_PLING, important ? 1.6f : 1.2f));
            } catch (Throwable ignored) { }
        }
    }

    private static void presenceNotice(JsonObject f, JsonObject prev, JsonObject next) {
        JsonObject n = notifySettings();
        if (n == null || bool(f, "muted")) return;
        boolean fav = bool(f, "favorite");
        String on = str(n, "friendOnline"), off = str(n, "friendOffline"), join = str(n, "friendJoin");
        boolean wasOff = "offline".equals(str(prev, "state")) || prev.size() == 0;
        boolean isOff = "offline".equals(str(next, "state"));
        String who = label(f, str(f, "name"));
        if (wasOff && !isOff && ("all".equals(on) || ("favorites".equals(on) && fav))) {
            notice("online", who + " is online", presenceText(next, false), false);
        } else if (!wasOff && isOff && ("all".equals(off) || ("favorites".equals(off) && fav))) {
            notice("offline", who + " went offline", "", false);
        }
        String addr = str(obj(next, "activity"), "address");
        if (!addr.isEmpty() && !addr.equals(str(obj(prev, "activity"), "address"))
                && ("all".equals(join) || ("favorites".equals(join) && fav))
                && !addr.equalsIgnoreCase(currentAddress())) {
            boolean joinable = bool(obj(next, "activity"), "joinable");
            notice("join", who + " joined a server", addr + (joinable ? "  -  /vf join " + str(f, "name") : ""), false);
        }
    }

    private static void messageNotice(JsonObject conv, JsonObject msg) {
        JsonObject n = notifySettings();
        if (n == null || str(msg, "sender").equals(myUuid()) || bool(conv, "muted")) return;
        String kind = str(msg, "kind");
        if ("system".equals(kind) || bool(msg, "blocked")) return;
        JsonObject f = friend(str(msg, "sender"));
        if (bool(f, "muted")) return;
        boolean group = "group".equals(str(conv, "kind"));
        String level = str(n, group ? "groupMessages" : "messages");
        if ("off".equals(level)) return;
        if (!group && "favorites".equals(level) && !bool(f, "favorite")) return;
        String body = str(msg, "body");
        if (group && "mentions".equals(level) && !body.toLowerCase(Locale.ROOT).contains("@" + myName().toLowerCase(Locale.ROOT))) return;
        String who = label(f, str(msg, "senderName"));
        if ("invite".equals(kind)) return;       // kommt als eigenes "invite"-Ereignis
        if (!group) lastDmFrom = str(msg, "sender");
        String convId = str(conv, "id");
        if (convId.equals(openConv) && Minecraft.getInstance().gui.screen() instanceof com.vortex.client.gui.FriendsScreen) return;
        notice("message", group ? who + " in " + str(conv, "name") : who, body.length() > 90 ? body.substring(0, 89) + "..." : body, true);
        chat(group ? "§d[" + str(conv, "name") + "] §f" + who + " §8» §7" + body : "§d[Friends] §f" + who + " §8» §7" + body);
    }

    /** Zeile im Chat (nur in einer Welt). */
    private static void chat(String text) {
        var p = Minecraft.getInstance().player;
        if (p == null) return;
        JsonObject n = notifySettings();
        if (n != null && !bool(n, "inGame")) return;
        p.sendSystemMessage(net.minecraft.network.chat.Component.literal(text));
    }

    private static String keyName() {
        try { return com.vortex.client.VortexClientMod.friendsKeyName(); } catch (Throwable e) { return "O"; }
    }

    // =========================================================================
    // Hilfen fuers Modell
    // =========================================================================

    public static void bump() { revision++; }

    public static String myUuid() { return data == null ? "" : str(obj(data, "me"), "uuid"); }
    public static String myName() { return data == null ? "" : str(obj(data, "me"), "name"); }
    public static String lastDmFrom() { return lastDmFrom; }

    public static List<JsonObject> friends() { return list(data, "friends"); }
    public static List<JsonObject> convs() { return list(data, "convs"); }
    public static List<JsonObject> incoming() { return list(data, "incoming"); }
    public static List<JsonObject> outgoing() { return list(data, "outgoing"); }
    public static List<JsonObject> blocked() { return list(data, "blocked"); }
    public static List<JsonObject> invites() {
        List<JsonObject> out = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (JsonObject i : list(data, "invites")) if (i.has("expires") && i.get("expires").getAsLong() > now) out.add(i);
        return out;
    }

    public static void dropInvite(String id) {
        if (data == null) return;
        JsonArray out = new JsonArray();
        for (JsonObject i : list(data, "invites")) if (!str(i, "id").equals(id)) out.add(i);
        data.add("invites", out);
        bump();
    }

    public static JsonObject friend(String uuid) {
        for (JsonObject f : friends()) if (str(f, "uuid").equals(uuid)) return f;
        return null;
    }

    public static JsonObject friendByName(String name) {
        for (JsonObject f : friends()) {
            if (str(f, "name").equalsIgnoreCase(name) || str(f, "nickname").equalsIgnoreCase(name)) return f;
        }
        return null;
    }

    /** Ist <name> ein Vortex-Freund? (fuer "Freunde nicht angreifen") */
    public static boolean isFriendName(String name) {
        if (data == null || name == null) return false;
        for (JsonObject f : friends()) if (str(f, "name").equalsIgnoreCase(name)) return true;
        return false;
    }

    public static JsonObject conv(String id) {
        for (JsonObject c : convs()) if (str(c, "id").equals(id)) return c;
        return null;
    }

    public static JsonObject dmWith(String uuid) {
        for (JsonObject c : convs()) if ("dm".equals(str(c, "kind")) && str(c, "with").equals(uuid)) return c;
        return null;
    }

    public static int unreadTotal() {
        int n = incoming().size();
        for (JsonObject c : convs()) if (!bool(c, "muted") && c.has("unread")) n += c.get("unread").getAsInt();
        return n;
    }

    public static String nameOf(String uuid) {
        JsonObject f = friend(uuid);
        if (f != null) return label(f, str(f, "name"));
        if (uuid.equals(myUuid())) return myName();
        for (JsonObject c : convs()) for (JsonObject m : list(c, "members")) if (str(m, "uuid").equals(uuid)) return str(m, "name");
        return "?";
    }

    public static String label(JsonObject f, String fallback) {
        if (f == null) return fallback;
        String nick = str(f, "nickname");
        return nick.isEmpty() ? str(f, "name") : nick;
    }

    /** "Spielt auf Hypixel", "Im Launcher", "Offline" ... */
    public static String presenceText(JsonObject p, boolean longForm) {
        if (p == null || p.size() == 0 || "offline".equals(str(p, "state"))) {
            if (p != null && p.has("lastSeen") && !p.get("lastSeen").isJsonNull()) return "Offline - last seen " + ago(p.get("lastSeen").getAsLong());
            return "Offline";
        }
        String mode = str(p, "mode");
        String pre = "dnd".equals(mode) ? "Do not disturb - " : "away".equals(mode) ? "Away - " : "";
        JsonObject a = obj(p, "activity");
        String what;
        if (a.size() == 0) what = "In the launcher";
        else switch (str(a, "mode")) {
            case "server" -> {
                String sn = str(a, "serverName"), ad = str(a, "address");
                what = "Playing on " + (longForm && !sn.isEmpty() ? sn + " (" + ad + ")" : sn.isEmpty() ? ad : sn);
            }
            case "singleplayer" -> what = "Playing singleplayer";
            case "realms" -> what = "Playing on Realms";
            case "menu" -> what = "In the main menu";
            default -> what = "Playing Minecraft " + str(a, "version");
        }
        String text = str(p, "text");
        return pre + what + (text.isEmpty() ? "" : " - \"" + text + "\"");
    }

    public static String ago(long ts) {
        long s = Math.max(0, (System.currentTimeMillis() - ts) / 1000);
        if (s < 60) return "just now";
        if (s < 3600) return (s / 60) + " min ago";
        if (s < 86400) return (s / 3600) + " h ago";
        return (s / 86400) + " days ago";
    }

    public static String str(JsonObject o, String k) {
        if (o == null || !o.has(k) || o.get(k).isJsonNull()) return "";
        JsonElement e = o.get(k);
        return e.isJsonPrimitive() ? e.getAsString() : e.toString();
    }

    public static boolean bool(JsonObject o, String k) {
        return o != null && o.has(k) && o.get(k).isJsonPrimitive() && o.get(k).getAsBoolean();
    }

    public static JsonObject obj(JsonObject o, String k) {
        return o != null && o.has(k) && o.get(k).isJsonObject() ? o.getAsJsonObject(k) : new JsonObject();
    }

    public static JsonArray arr(JsonObject o, String k) {
        return o != null && o.has(k) && o.get(k).isJsonArray() ? o.getAsJsonArray(k) : new JsonArray();
    }

    public static List<JsonObject> list(JsonObject o, String k) {
        List<JsonObject> out = new ArrayList<>();
        for (JsonElement e : arr(o, k)) if (e.isJsonObject()) out.add(e.getAsJsonObject());
        return out;
    }

    // =========================================================================
    // Aktionen (fuer Fenster und Befehle)
    // =========================================================================

    /** Verlauf einer Unterhaltung laden (neueste 50, oder aeltere). */
    public static void loadHistory(String conv, boolean older) {
        List<JsonObject> h = HISTORY.get(conv);
        JsonObject a = args("conv", conv);
        if (older && h != null && !h.isEmpty()) a.addProperty("before", h.get(0).get("id").getAsLong());
        request("chat.history", a).thenAccept(r -> {
            List<JsonObject> cur = HISTORY.computeIfAbsent(conv, k -> new ArrayList<>());
            java.util.Set<Long> known = new java.util.HashSet<>();
            for (JsonObject m : cur) known.add(m.get("id").getAsLong());
            List<JsonObject> merged = new ArrayList<>();
            for (JsonObject m : list(r, "messages")) if (!known.contains(m.get("id").getAsLong())) merged.add(m);
            merged.addAll(cur);
            merged.sort((x, y) -> Long.compare(x.get("id").getAsLong(), y.get("id").getAsLong()));
            HISTORY.put(conv, merged);
            MORE.put(conv, bool(r, "more"));
            bump();
        }).exceptionally(e -> null);
    }

    public static void markRead(String conv) {
        List<JsonObject> h = HISTORY.get(conv);
        JsonObject c = conv(conv);
        if (c == null || h == null || h.isEmpty()) return;
        JsonObject last = h.get(h.size() - 1);
        if ((c.has("unread") && c.get("unread").getAsInt() > 0) || !str(last, "sender").equals(myUuid())) {
            c.addProperty("unread", 0);
            request("chat.read", args("conv", conv, "upTo", last.get("id").getAsLong()));
            bump();
        }
    }

    /** Einem Freund nachjoinen (wenn er es erlaubt). */
    public static CompletableFuture<Void> joinFriend(String uuid) {
        return request("join.check", args("uuid", uuid)).thenAccept(r -> connectTo(str(r, "address"), str(r, "serverName")));
    }

    /** Auf einen Server verbinden -- vorher die aktuelle Welt verlassen. */
    public static void connectTo(String address, String name) {
        Minecraft mc = Minecraft.getInstance();
        try {
            if (mc.level != null) mc.disconnectFromWorld(net.minecraft.client.multiplayer.ClientLevel.DEFAULT_QUIT_MESSAGE);
            var parent = new net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen(new net.minecraft.client.gui.screens.TitleScreen());
            ServerData sd = new ServerData(name == null || name.isEmpty() ? address : name, address, ServerData.Type.OTHER);
            net.minecraft.client.gui.screens.ConnectScreen.startConnecting(parent, mc,
                    net.minecraft.client.multiplayer.resolver.ServerAddress.parseString(address), sd, false, null);
        } catch (Throwable e) {
            Errors.report("Social.connectTo", e);
        }
    }

    /** Einladung annehmen/ablehnen. */
    public static CompletableFuture<JsonObject> answerInvite(String id, boolean accept) {
        dropInvite(id);
        return request("invite.respond", args("id", id, "accept", accept)).thenApply(r -> {
            if (accept) connectTo(str(r, "address"), "");
            return r;
        });
    }
}
