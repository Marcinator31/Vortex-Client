package com.vortex.client.beta;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;

/**
 * Beta-Test-Checkliste im Spiel.
 *
 * Der Launcher legt die Liste an (alle noch nicht freigegebenen Neuerungen)
 * und gibt den Ordner per -Dvortex.beta.dir mit -- nur, wenn der Beta-Kanal
 * aktiv ist. Ohne diese Angabe ist hier alles aus.
 *
 *   state.json   -- schreibt NUR der Launcher (Punkte, Haken, Berichte)
 *   inbox/*.json -- schreibt NUR das Spiel: je Aktion eine Datei
 *
 * Der Launcher uebernimmt die Aktionen (alle 2 s) und schickt Fehlerberichte
 * an GitHub. Bis dahin zeigt das Spiel die eigenen, noch nicht abgeholten
 * Aktionen schon an -- abhaken wirkt also sofort.
 */
public final class BetaTest {

    private BetaTest() {}

    public record Report(String text, String status, String url) {}
    public record Item(String id, String text, boolean checked, List<Report> reports) {
        public boolean offenerFehler() {
            for (Report r : reports) if (!"resolved".equals(r.status())) return true;
            return false;
        }
    }
    public record Group(String kind, String component, String version, String heading, List<Item> items) {}

    private static final Path DIR;
    static {
        String p = System.getProperty("vortex.beta.dir", "").trim();
        DIR = p.isEmpty() ? null : Path.of(p);
    }

    private static List<Group> groups = List.of();
    private static long geladen = 0;
    private static long dateiStand = -1;
    /** Eigene Aktionen, die der Launcher noch nicht abgeholt hat: itemId -> Haken. */
    private static final Map<String, Boolean> WARTEND = new HashMap<>();
    private static final Map<String, String> WARTEND_BERICHT = new HashMap<>();

    /** Laeuft das Spiel als Beta-Test (vom Launcher so gestartet)? */
    public static boolean aktiv() {
        return DIR != null && Files.isDirectory(DIR);
    }

    public static synchronized List<Group> groups() {
        neuLaden(false);
        return groups;
    }

    public static synchronized int[] fortschritt() {
        neuLaden(false);
        int total = 0, done = 0, offen = 0;
        for (Group g : groups) for (Item i : g.items()) {
            total++;
            if (i.checked()) done++;
            if (i.offenerFehler()) offen++;
        }
        return new int[]{done, total, offen};
    }

    /** Wie viele eigene Aktionen noch beim Launcher liegen (laeuft er?). */
    public static int wartend() {
        if (DIR == null) return 0;
        try (var s = Files.list(DIR.resolve("inbox"))) {
            return (int) s.filter(p -> p.toString().endsWith(".json")).count();
        } catch (Exception e) {
            return 0;
        }
    }

    /** state.json lesen -- hoechstens einmal pro Sekunde, und nur wenn geaendert. */
    public static synchronized void neuLaden(boolean sofort) {
        if (DIR == null) return;
        long jetzt = System.currentTimeMillis();
        if (!sofort && jetzt - geladen < 1000) return;
        geladen = jetzt;
        Path f = DIR.resolve("state.json");
        try {
            long stand = Files.exists(f) ? Files.getLastModifiedTime(f).toMillis() : 0;
            if (stand == dateiStand && !sofort) { anwenden(); return; }
            dateiStand = stand;
            if (stand == 0) { groups = List.of(); return; }
            JsonObject s = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonObject checked = s.has("checked") && s.get("checked").isJsonObject() ? s.getAsJsonObject("checked") : new JsonObject();
            Map<String, List<Report>> berichte = new HashMap<>();
            if (s.has("reports") && s.get("reports").isJsonArray()) {
                for (JsonElement e : s.getAsJsonArray("reports")) {
                    JsonObject r = e.getAsJsonObject();
                    berichte.computeIfAbsent(str(r, "itemId"), k -> new ArrayList<>())
                            .add(new Report(str(r, "text"), str(r, "status"), str(r, "url")));
                }
            }
            List<Group> out = new ArrayList<>();
            JsonArray gs = s.has("groups") && s.get("groups").isJsonArray() ? s.getAsJsonArray("groups") : new JsonArray();
            for (JsonElement ge : gs) {
                JsonObject g = ge.getAsJsonObject();
                List<Item> items = new ArrayList<>();
                for (JsonElement ie : g.getAsJsonArray("items")) {
                    JsonObject i = ie.getAsJsonObject();
                    String id = str(i, "id");
                    items.add(new Item(id, str(i, "text"), checked.has(id), berichte.getOrDefault(id, List.of())));
                }
                out.add(new Group(str(g, "kind"), str(g, "component"), str(g, "version"), str(g, "heading"), items));
            }
            groups = out;
            // Was der Launcher schon uebernommen hat, nicht mehr als "wartend" fuehren.
            if (wartend() == 0) { WARTEND.clear(); WARTEND_BERICHT.clear(); }
            anwenden();
        } catch (Exception e) {
            com.vortex.client.core.Errors.report("BetaTest.load", e);
        }
    }

    /** Eigene, noch nicht abgeholte Aktionen ueber den Stand legen. */
    private static void anwenden() {
        if (WARTEND.isEmpty() && WARTEND_BERICHT.isEmpty()) return;
        List<Group> out = new ArrayList<>();
        for (Group g : groups) {
            List<Item> items = new ArrayList<>();
            for (Item i : g.items()) {
                boolean haken = WARTEND.getOrDefault(i.id(), i.checked());
                List<Report> reps = i.reports();
                String neu = WARTEND_BERICHT.get(i.id());
                if (neu != null && reps.stream().noneMatch(r -> r.text().equals(neu))) {
                    reps = new ArrayList<>(reps);
                    reps.add(new Report(neu, "pending", ""));
                    haken = false;
                }
                items.add(new Item(i.id(), i.text(), haken, reps));
            }
            out.add(new Group(g.kind(), g.component(), g.version(), g.heading(), items));
        }
        groups = out;
    }

    public static synchronized void abhaken(String itemId, boolean wert) {
        JsonObject a = new JsonObject();
        a.addProperty("type", "check");
        a.addProperty("itemId", itemId);
        a.addProperty("value", wert);
        if (schreiben(a)) {
            WARTEND.put(itemId, wert);
            anwenden();
        }
    }

    public static synchronized boolean melden(String itemId, String text) {
        String t = text == null ? "" : text.trim();
        if (t.isEmpty()) return false;
        JsonObject a = new JsonObject();
        a.addProperty("type", "report");
        a.addProperty("itemId", itemId);
        a.addProperty("text", t);
        a.addProperty("attachLog", true);
        a.addProperty("context", kontext());
        if (!schreiben(a)) return false;
        WARTEND_BERICHT.put(itemId, t);
        WARTEND.put(itemId, false);
        anwenden();
        return true;
    }

    /** Was beim Melden mitgeschickt wird -- bewusst ohne Koordinaten und Serveradresse (Issues sind oeffentlich). */
    private static String kontext() {
        Minecraft mc = Minecraft.getInstance();
        StringBuilder b = new StringBuilder();
        try {
            b.append("Client ").append(net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer("vortexclient")
                    .map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("?"));
            b.append(", ").append(mc.level == null ? "main menu" : mc.isLocalServer() ? "singleplayer" : "multiplayer");
            if (mc.level != null) b.append(", ").append(mc.level.dimension().identifier());
            b.append(", ").append(mc.getFps()).append(" FPS");
        } catch (Throwable ignored) { }
        return b.toString();
    }

    /** Aktion als eigene Datei in inbox/ ablegen (erst .tmp, dann umbenennen). */
    private static boolean schreiben(JsonObject a) {
        if (DIR == null) return false;
        try {
            Path inbox = DIR.resolve("inbox");
            Files.createDirectories(inbox);
            String name = System.currentTimeMillis() + "-" + Long.toHexString(Double.doubleToLongBits(Math.random()) & 0xFFFFFF);
            Path tmp = inbox.resolve(name + ".tmp");
            Files.writeString(tmp, a.toString(), StandardCharsets.UTF_8);
            Files.move(tmp, inbox.resolve(name + ".json"), StandardCopyOption.ATOMIC_MOVE);
            return true;
        } catch (Exception e) {
            com.vortex.client.core.Errors.report("BetaTest.write", e);
            return false;
        }
    }

    private static String str(JsonObject o, String k) {
        return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : "";
    }

    // ------------------------------------------------------------------

    private static boolean hinweisGezeigt = false;

    /** /vbeta und ein einmaliger Hinweis nach dem Betreten einer Welt. */
    public static void register() {
        if (DIR == null) return;
        net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback.EVENT.register((d, access) ->
                d.register(net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal("vbeta").executes(c -> {
                    Minecraft mc = Minecraft.getInstance();
                    mc.execute(() -> mc.gui.setScreen(new BetaScreen(null)));
                    return 1;
                })));
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.JOIN.register((h, s, mc) -> {
            if (hinweisGezeigt) return;
            hinweisGezeigt = true;
            int[] f = fortschritt();
            if (f[1] == 0) return;
            com.vortex.client.social.SocialToasts.push("Beta test: " + f[0] + " / " + f[1] + " checked",
                    "Right Shift > Beta test, or /vbeta", true);
        });
    }
}
