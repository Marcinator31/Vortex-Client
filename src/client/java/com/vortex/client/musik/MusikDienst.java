package com.vortex.client.musik;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.vortex.client.core.Errors;
import com.vortex.client.module.ModuleManager;
import com.vortex.client.module.modules.SpotifyModule;
import com.vortex.client.social.Social;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Die Musik-Seite des Clients -- alles ausser Anmeldung/API (Spotify) und
 * Anzeige:
 *
 *  1. Eigener Song: mit Anmeldung alle 2,5 s (1 s bei offenem Music-Fenster)
 *     ueber die Web-API, sonst (Windows) aus dem Fenster der Spotify-App.
 *  2. Teilen: an den Freunde-Server ("music.set"), bei jedem Wechsel und alle
 *     20 s; Spotify-Tokens verlassen nie den PC.
 *  3. Andere: alle 3 s fuer die Spieler in der Naehe abfragen ("music.get").
 *  4. Mithoeren: eigenes Spotify spielt den Song eines anderen Spielers an
 *     derselben Stelle (Spotify verlangt dafuer Premium). Vorher laufende
 *     eigene Musik wird gemerkt und danach fortgesetzt.
 */
public final class MusikDienst {
    private MusikDienst() {}

    /** Wie weit Songs anderer abgefragt werden (Bloecke). */
    private static final double ABFRAGE_WEITE = 64;

    // --- eigener Song ---------------------------------------------------
    private static volatile Song eigener;
    private static volatile String quelle = "";       // "spotify", "app" oder ""
    private static volatile String geraet = "";
    private static volatile int lautstaerke = -1;
    private static volatile String apiFehler;
    private static volatile boolean abfrageLaeuft, appAbfrageLaeuft;
    private static long naechsteAbfrage, naechsteApp;
    /** Music-Fenster offen: schneller abfragen */
    public static volatile boolean fensterOffen;

    // --- Teilen ---------------------------------------------------------
    private static Song geteilt;
    private static long geteiltUm;
    private static boolean geteiltAktiv;
    private static String geteiltMit = "";
    private static Object verbindung;
    private static volatile boolean serverKannEs = true;

    // --- andere ----------------------------------------------------------
    public record Fremd(UUID uuid, String name, Song song, long empfangen) {}
    private static final Map<UUID, Fremd> FREMDE = new ConcurrentHashMap<>();
    private static long naechsteFremdAbfrage;
    private static boolean fremdAbfrageLaeuft;

    // --- Mithoeren --------------------------------------------------------
    private static volatile UUID ziel;
    private static boolean zielManuell;
    private static Song gespeichert;               // eigene Musik vor dem Mithoeren
    private static long naechsterPlay;
    private static String suchtGerade;
    private static final Map<String, String> SUCHE = new ConcurrentHashMap<>();   // "titel|kuenstler" -> id ("" = nicht gefunden)
    private static boolean tasteWarUnten;

    // --- Meldungen ---------------------------------------------------------
    private static volatile String meldung;
    private static volatile long meldungBis;

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            try { tick(mc); } catch (Throwable t) { Errors.report("MusikDienst", t); }
        });
    }

    /** Nur fuer Tests: Song eines Spielers setzen (sonst kommt er vom Freunde-Server). */
    public static void testFremd(UUID u, String name, Song s) {
        FREMDE.put(u, new Fremd(u, name, s, System.currentTimeMillis()));
    }

    static SpotifyModule mod() { return ModuleManager.INSTANCE.get(SpotifyModule.class); }

    // ======================================================================
    // Abfragen fuer Anzeige
    // ======================================================================

    /** Was ich gerade hoere (oder null). */
    public static Song eigener() { return eigener; }
    public static String quelle() { return quelle; }
    public static String geraet() { return geraet; }
    public static int lautstaerke() { return lautstaerke; }
    public static String apiFehler() { return apiFehler; }
    public static boolean teile() { return geteiltAktiv; }
    public static boolean serverKannEs() { return serverKannEs; }

    /** Song eines anderen Spielers (oder null). */
    public static Song songVon(UUID spieler) {
        Fremd f = FREMDE.get(spieler);
        return f == null || System.currentTimeMillis() - f.empfangen > 15_000 ? null : f.song;
    }

    /** Spieler in der Naehe mit Song, naechste zuerst. */
    public static List<Fremd> inDerNaehe() {
        Minecraft mc = Minecraft.getInstance();
        List<Fremd> l = new ArrayList<>();
        if (mc.player == null) return l;
        for (Fremd f : FREMDE.values()) if (songVon(f.uuid) != null) l.add(f);
        l.sort(Comparator.comparingDouble(f -> abstand(mc, f.uuid)));
        return l;
    }

    public static double abstand(Minecraft mc, UUID u) {
        if (mc.level == null || mc.player == null) return Double.MAX_VALUE;
        Player p = mc.level.getPlayerByUUID(u);
        return p == null ? Double.MAX_VALUE : p.distanceTo(mc.player);
    }

    /** Bei wem ich mithoere (oder null). */
    public static UUID ziel() { return ziel; }
    public static String zielName() {
        UUID z = ziel;
        if (z == null) return null;
        Fremd f = FREMDE.get(z);
        return f != null ? f.name : "?";
    }

    public static String meldung() { return System.currentTimeMillis() < meldungBis ? meldung : null; }

    /** Text ueber dem Kopf (oder null). */
    public static Component kopfText(Player p) {
        SpotifyModule m = mod();
        if (m == null || !m.isEnabled() || !m.aboveHeads.get()) return null;
        Minecraft mc = Minecraft.getInstance();
        Song s;
        if (p == mc.player) {
            if (!m.ownAboveHead.get()) return null;
            s = eigener;
        } else {
            if (mc.player == null || p.distanceTo(mc.player) > m.headDistance.get()) return null;
            s = songVon(p.getUUID());
        }
        if (s == null || !s.spielt()) return null;
        String z = s.zeile();
        if (z.length() > 42) z = z.substring(0, 41) + "…";
        Component c = Component.literal("♫ " + z).withColor(m.headColor.get() & 0xFFFFFF);
        if (p.getUUID().equals(ziel)) c = c.copy().append(Component.literal("  ◀ you").withColor(0xFFFFFF));
        return c;
    }

    // ======================================================================
    // Takt
    // ======================================================================

    private static void tick(Minecraft mc) {
        SpotifyModule m = mod();
        if (m == null || !m.isEnabled()) {
            if (ziel != null) stoppeMithoeren(null);
            if (geteiltAktiv) teilenAus();
            eigener = null;
            return;
        }
        long t = System.currentTimeMillis();
        eigenenAbfragen(m, t);
        teilen(m, t);
        fremdeAbfragen(mc, t);
        taste(mc, m);
        mithoeren(mc, m, t);
    }

    // --- 1. eigener Song ----------------------------------------------------

    private static void eigenenAbfragen(SpotifyModule m, long t) {
        if (Spotify.verbunden()) {
            if (abfrageLaeuft || t < naechsteAbfrage) return;
            abfrageLaeuft = true;
            naechsteAbfrage = t + (fensterOffen || ziel != null ? 1000 : 2500);
            Spotify.async("GET", "/me/player?additional_types=track", null).thenAccept(a -> {
                try { verarbeite(a); } finally { abfrageLaeuft = false; }
            });
        } else if (m.desktopApp.get() && DesktopSpotify.moeglich()) {
            if (appAbfrageLaeuft || t < naechsteApp) return;
            appAbfrageLaeuft = true;
            naechsteApp = t + 2000;
            Spotify.POOL.execute(() -> {
                try {
                    Song s = DesktopSpotify.song(DesktopSpotify.fensterTitel());
                    Song alt = eigener;
                    // Gleicher Song: Startzeit behalten (die App verraet keine Position)
                    if (s != null && alt != null && s.gleich(alt) && "app".equals(quelle)) s = alt;
                    eigener = s;
                    quelle = s == null ? "" : "app";
                } finally { appAbfrageLaeuft = false; }
            });
        } else {
            eigener = null;
            quelle = "";
        }
    }

    private static void verarbeite(Spotify.Antwort a) {
        if (a.code() == 204) { eigener = null; quelle = ""; apiFehler = null; geraet = ""; return; }
        if (!a.ok() || a.json() == null) {
            if (a.code() != 429) apiFehler = Spotify.grundText(a);
            return;
        }
        apiFehler = null;
        JsonObject j = a.json();
        JsonObject dev = j.has("device") && j.get("device").isJsonObject() ? j.getAsJsonObject("device") : null;
        geraet = dev == null ? "" : Spotify.text(dev, "name");
        lautstaerke = dev != null && dev.has("volume_percent") && !dev.get("volume_percent").isJsonNull() ? dev.get("volume_percent").getAsInt() : -1;
        JsonObject item = j.has("item") && j.get("item").isJsonObject() ? j.getAsJsonObject("item") : null;
        if (item == null || !"track".equals(Spotify.text(j, "currently_playing_type"))) {
            eigener = null;
            quelle = "";
            return;
        }
        StringBuilder kuenstler = new StringBuilder();
        if (item.has("artists") && item.get("artists").isJsonArray()) {
            for (JsonElement e : item.getAsJsonArray("artists")) {
                if (kuenstler.length() > 0) kuenstler.append(", ");
                kuenstler.append(Spotify.text(e.getAsJsonObject(), "name"));
            }
        }
        String album = "", cover = "";
        if (item.has("album") && item.get("album").isJsonObject()) {
            JsonObject al = item.getAsJsonObject("album");
            album = Spotify.text(al, "name");
            if (al.has("images") && al.get("images").isJsonArray()) {
                JsonArray bilder = al.getAsJsonArray("images");
                // Mittlere Groesse (300 px) reicht -- sonst das erste
                for (JsonElement e : bilder) {
                    JsonObject b = e.getAsJsonObject();
                    int w = b.has("width") && !b.get("width").isJsonNull() ? b.get("width").getAsInt() : 0;
                    if (cover.isEmpty() || (w >= 200 && w <= 400)) cover = Spotify.text(b, "url");
                }
            }
        }
        String kontext = j.has("context") && j.get("context").isJsonObject() ? Spotify.text(j.getAsJsonObject("context"), "uri") : "";
        eigener = new Song(Spotify.text(item, "id"), Spotify.text(item, "name"), kuenstler.toString(), album, cover,
                item.has("duration_ms") ? item.get("duration_ms").getAsLong() : 0,
                j.has("progress_ms") && !j.get("progress_ms").isJsonNull() ? j.get("progress_ms").getAsLong() : 0,
                j.has("is_playing") && j.get("is_playing").getAsBoolean(), System.currentTimeMillis(), kontext);
        quelle = "spotify";
    }

    // --- 2. teilen ----------------------------------------------------------

    private static void teilen(SpotifyModule m, long t) {
        if (Social.conn() != Social.Conn.ONLINE || !serverKannEs) { geteilt = null; geteiltAktiv = false; return; }
        if (verbindung != Social.data()) { verbindung = Social.data(); geteilt = null; }   // neu verbunden
        Song s = eigener;
        if (!m.share.get() || s == null) {
            if (geteiltAktiv) teilenAus();
            return;
        }
        String mit = m.shareWith.getIndex() == 1 ? "friends" : "everyone";
        Song g = geteilt;
        boolean neu = g == null || !s.gleich(g) || s.spielt() != g.spielt() || !mit.equals(geteiltMit)
                || t - geteiltUm > 20_000 || Math.abs(s.jetzt() - g.jetzt()) > 3000;
        if (!neu || t - geteiltUm < 1500) return;
        JsonObject track = new JsonObject();
        track.addProperty("id", s.mitId() ? s.id() : "");
        track.addProperty("title", s.titel());
        track.addProperty("artist", s.kuenstler());
        track.addProperty("album", s.album());
        track.addProperty("image", s.cover());
        track.addProperty("durationMs", s.dauer());
        track.addProperty("progressMs", s.jetzt());
        track.addProperty("playing", s.spielt());
        geteilt = s;
        geteiltUm = t;
        geteiltMit = mit;
        geteiltAktiv = true;
        Social.request("music.set", Social.args("track", track, "visibility", mit)).whenComplete((r, e) -> { if (e != null) pruefeFehler(e); });
    }

    private static void teilenAus() {
        geteiltAktiv = false;
        geteilt = null;
        if (Social.conn() == Social.Conn.ONLINE && serverKannEs) Social.request("music.clear", new JsonObject());
    }

    private static void pruefeFehler(Throwable e) {
        if (String.valueOf(Social.errorOf(e)).contains("Unknown request")) serverKannEs = false;
    }

    // --- 3. andere ------------------------------------------------------------

    private static void fremdeAbfragen(Minecraft mc, long t) {
        if (Social.conn() != Social.Conn.ONLINE || !serverKannEs || mc.level == null || mc.player == null) {
            if (mc.level == null) FREMDE.clear();
            return;
        }
        if (fremdAbfrageLaeuft || t < naechsteFremdAbfrage) return;
        naechsteFremdAbfrage = t + 3000;
        List<Player> spieler = new ArrayList<>();
        for (Player p : mc.level.players()) {
            if (p == mc.player || p.distanceTo(mc.player) > ABFRAGE_WEITE) continue;
            spieler.add(p);
            if (spieler.size() >= 100) break;
        }
        // Wer weg ist, faellt raus
        FREMDE.keySet().removeIf(u -> spieler.stream().noneMatch(p -> p.getUUID().equals(u)));
        if (spieler.isEmpty()) return;
        JsonArray liste = new JsonArray();
        for (Player p : spieler) liste.add(p.getUUID().toString().replace("-", ""));
        JsonObject args = new JsonObject();
        args.add("uuids", liste);
        fremdAbfrageLaeuft = true;
        Social.request("music.get", args).whenComplete((r, e) -> {
            fremdAbfrageLaeuft = false;
            if (e != null) { pruefeFehler(e); return; }
            JsonObject sp = r.has("players") && r.get("players").isJsonObject() ? r.getAsJsonObject("players") : new JsonObject();
            long jetzt = System.currentTimeMillis();
            for (Player p : spieler) {
                JsonElement el = sp.get(p.getUUID().toString().replace("-", ""));
                if (el == null || !el.isJsonObject()) { FREMDE.remove(p.getUUID()); continue; }
                JsonObject o = el.getAsJsonObject();
                long alter = o.has("ageMs") ? Math.max(0, o.get("ageMs").getAsLong()) : 0;
                boolean spielt = o.has("playing") && o.get("playing").getAsBoolean();
                long pos = (o.has("progressMs") ? o.get("progressMs").getAsLong() : 0) + (spielt ? alter : 0);
                Song s = new Song(Spotify.text(o, "id"), Spotify.text(o, "title"), Spotify.text(o, "artist"), Spotify.text(o, "album"),
                        Spotify.text(o, "image"), o.has("durationMs") ? o.get("durationMs").getAsLong() : 0, pos, spielt, jetzt, "");
                FREMDE.put(p.getUUID(), new Fremd(p.getUUID(), p.getName().getString(), s, jetzt));
            }
        });
    }

    // --- 4. Mithoeren ---------------------------------------------------------

    /** Knopf "Listen along" / Taste: bei diesem Spieler mithoeren oder (gleicher Spieler) aufhoeren. */
    public static void mithoerenUmschalten(UUID spieler) {
        SpotifyModule m = mod();
        if (m == null) return;
        if (spieler == null || spieler.equals(ziel)) { stoppeMithoeren("Stopped listening along."); return; }
        if (!Spotify.verbunden()) { melde("Connect Spotify first (Music screen)."); return; }
        if (Boolean.FALSE.equals(Spotify.premium())) { melde("Listening along needs Spotify Premium."); return; }
        if (songVon(spieler) == null) { melde("That player is not sharing a song."); return; }
        if (m.listenAlong.getIndex() == 0) m.listenAlong.set("Manual");
        zielManuell = true;
        starteMithoeren(spieler);
    }

    private static void taste(Minecraft mc, SpotifyModule m) {
        int code = m.listenKey.getKeyCode();
        if (code == org.lwjgl.glfw.GLFW.GLFW_KEY_UNKNOWN || mc.gui.screen() != null) { tasteWarUnten = false; return; }
        boolean unten = com.mojang.blaze3d.platform.InputConstants.isKeyDown(mc.getWindow(), code);
        if (unten && !tasteWarUnten) {
            UUID wen = null;
            Entity blick = mc.crosshairPickEntity;
            if (blick instanceof Player p && songVon(p.getUUID()) != null) wen = p.getUUID();
            if (wen == null && ziel != null) { stoppeMithoeren("Stopped listening along."); }
            else if (wen == null) {
                List<Fremd> nah = inDerNaehe();
                if (!nah.isEmpty() && abstand(mc, nah.get(0).uuid) <= 8) wen = nah.get(0).uuid;
                if (wen != null) mithoerenUmschalten(wen); else melde("Look at a player who shares a song.");
            } else mithoerenUmschalten(wen);
        }
        tasteWarUnten = unten;
    }

    private static void mithoeren(Minecraft mc, SpotifyModule m, long t) {
        int modus = m.listenAlong.getIndex();
        if (modus == 0 || !Spotify.verbunden() || mc.player == null) {
            if (ziel != null) stoppeMithoeren(null);
            return;
        }
        UUID neu = ziel;
        double weite = m.listenRange.get();
        if (ziel != null) {
            Song s = songVon(ziel);
            double d = abstand(mc, ziel);
            boolean weg = s == null || d > (zielManuell ? ABFRAGE_WEITE : weite + 3);
            if (weg) {
                String n = zielName();
                stoppeMithoeren(s == null ? n + " stopped sharing music." : n + " is too far away.");
                neu = null;
            }
        }
        if (modus == 2 && (ziel == null || !zielManuell)) {
            UUID bester = null;
            double bd = weite;
            for (Fremd f : FREMDE.values()) {
                Song s = songVon(f.uuid);
                if (s == null || !s.spielt()) continue;
                double d = abstand(mc, f.uuid);
                if (f.uuid.equals(ziel)) d -= 3;      // beim bisherigen bleiben, solange es nicht deutlich naeher geht
                if (d <= bd) { bd = d; bester = f.uuid; }
            }
            neu = bester;
            if (neu != null && !neu.equals(ziel)) { zielManuell = false; starteMithoeren(neu); }
            else if (neu == null && ziel != null) stoppeMithoeren(null);
        }
        if (ziel != null) synchronisieren(t);
    }

    private static void starteMithoeren(UUID spieler) {
        if (gespeichert == null && "spotify".equals(quelle) && eigener != null) gespeichert = eigener;
        ziel = spieler;
        naechsterPlay = 0;
        melde("Listening along with " + zielName() + ".");
    }

    /** Aufhoeren; eigene Musik von vorher fortsetzen (Einstellung). grund = Meldung oder null. */
    public static void stoppeMithoeren(String grund) {
        if (ziel == null) return;
        ziel = null;
        zielManuell = false;
        SpotifyModule m = mod();
        Song g = gespeichert;
        gespeichert = null;
        if (Spotify.verbunden() && m != null && m.resumeMine.get()) {
            if (g != null && g.mitId() && g.spielt()) Spotify.fortsetzen(g.kontext(), g.id(), g.position());
            else Spotify.pause();
        }
        naechsteAbfrage = 0;
        if (grund != null) melde(grund);
    }

    private static void synchronisieren(long t) {
        if (t < naechsterPlay) return;
        Song s = songVon(ziel);
        if (s == null) return;
        Song ich = eigener;
        if (!s.spielt()) {
            // Der andere hat pausiert: hier auch
            if (ich != null && ich.spielt() && s.gleich(ich)) { Spotify.pause(); naechsterPlay = t + 3000; naechsteAbfrage = t + 800; }
            return;
        }
        String id = s.mitId() ? s.id() : null;
        if (id == null) {
            String schluessel = s.titel().toLowerCase() + "|" + s.kuenstler().toLowerCase();
            id = SUCHE.get(schluessel);
            if (id == null) {
                if (!schluessel.equals(suchtGerade)) {
                    suchtGerade = schluessel;
                    Spotify.suche(s.titel(), s.kuenstler()).thenAccept(gefunden -> {
                        SUCHE.put(schluessel, gefunden == null ? "" : gefunden);
                        suchtGerade = null;
                    });
                }
                return;
            }
            if (id.isEmpty()) { naechsterPlay = t + 10_000; return; }     // nicht auf Spotify gefunden
        }
        boolean passt = ich != null && id.equals(ich.id()) && ich.spielt();
        // Position nur bei Spotify-Quelle bekannt (Desktop-App teilt keine)
        if (passt && s.mitId() && Math.abs(ich.jetzt() - s.jetzt()) > 4000) passt = false;
        if (passt) return;
        naechsterPlay = t + 5000;
        naechsteAbfrage = t + 900;
        long pos = s.mitId() ? s.jetzt() + 400 : 0;
        Spotify.abspielen(id, pos).thenAccept(a -> {
            if (a.ok()) return;
            melde(Spotify.grundText(a));
            if ("PREMIUM_REQUIRED".equals(a.grund())) Minecraft.getInstance().execute(() -> { gespeichert = null; ziel = null; });
        });
    }

    // --- Steuerung (Music-Fenster) -------------------------------------------------

    public static void spielenPause() {
        Song s = eigener;
        var f = s != null && s.spielt() ? Spotify.pause() : Spotify.weiter();
        nachSteuerung(f);
    }
    public static void naechster() { nachSteuerung(Spotify.naechster()); }
    public static void vorheriger() { nachSteuerung(Spotify.vorheriger()); }
    public static void lautstaerkeSetzen(int p) { lautstaerke = p; nachSteuerung(Spotify.lautstaerke(p)); }
    public static void springen(long ms) { nachSteuerung(Spotify.springen(ms)); }

    private static void nachSteuerung(java.util.concurrent.CompletableFuture<Spotify.Antwort> f) {
        f.thenAccept(a -> {
            if (!a.ok()) melde(Spotify.grundText(a));
            naechsteAbfrage = 0;
        });
    }

    static void melde(String text) {
        meldung = text;
        meldungBis = System.currentTimeMillis() + 6000;
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (mc.player != null && mc.gui.screen() == null) {
                mc.player.sendOverlayMessage(Component.literal("♫ " + text).withColor(0x1ED760));
            }
        });
    }
}
