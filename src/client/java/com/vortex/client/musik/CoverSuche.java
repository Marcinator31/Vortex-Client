package com.vortex.client.musik;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cover und Laenge fuer Songs OHNE Spotify-Daten -- z. B. aus der Spotify-App
 * gelesen (nur "Kuenstler - Titel" bekannt) oder von anderen Spielern ohne
 * Anmeldung. Gesucht wird in der oeffentlichen iTunes-Suche (ohne Konto).
 *
 * Ergebnis je "titel|kuenstler" gemerkt (auch "nichts gefunden"), hoechstens
 * eine Suche gleichzeitig, mindestens 3 s Abstand -- die Suche erlaubt nur
 * wenige Anfragen pro Minute.
 */
public final class CoverSuche {
    private CoverSuche() {}

    /** Gefunden: Cover-Adresse (600 px) und Laenge in ms (0 = unbekannt). */
    public record Treffer(String cover, long dauer) {}

    private static final Treffer NICHTS = new Treffer("", 0);
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.NORMAL).build();
    private static final Map<String, Treffer> FERTIG = new ConcurrentHashMap<>();
    private static final Set<String> OFFEN = ConcurrentHashMap.newKeySet();
    private static volatile boolean laeuft;
    private static volatile long letzte;

    private static String schluessel(String titel, String kuenstler) {
        return (titel + "|" + kuenstler).toLowerCase(Locale.ROOT).trim();
    }

    /** Treffer, falls schon bekannt (sonst null -- dann wird im Hintergrund gesucht). */
    public static Treffer fuer(String titel, String kuenstler) {
        if (titel == null || titel.isBlank()) return NICHTS;
        String k = schluessel(titel, kuenstler == null ? "" : kuenstler);
        Treffer t = FERTIG.get(k);
        if (t != null) return t;
        OFFEN.add(k);
        starte();
        return null;
    }

    /** Song mit Cover/Laenge ergaenzen, wenn er keins hat und eins bekannt ist. */
    public static Song ergaenze(Song s) {
        if (s == null || !s.cover().isEmpty()) return s;
        Treffer t = fuer(s.titel(), s.kuenstler());
        if (t == null || t.cover().isEmpty()) return s;
        return new Song(s.id(), s.titel(), s.kuenstler(), s.album(), t.cover(), s.dauer() > 0 ? s.dauer() : t.dauer(),
                s.position(), s.spielt(), s.stand(), s.kontext());
    }

    private static synchronized void starte() {
        if (laeuft || OFFEN.isEmpty()) return;
        laeuft = true;
        Spotify.POOL.execute(() -> {
            try {
                while (!OFFEN.isEmpty()) {
                    String k = OFFEN.iterator().next();
                    OFFEN.remove(k);
                    if (FERTIG.containsKey(k)) continue;
                    long warte = 3000 - (System.currentTimeMillis() - letzte);
                    if (warte > 0) Thread.sleep(warte);
                    letzte = System.currentTimeMillis();
                    FERTIG.put(k, suche(k));
                }
            } catch (InterruptedException ignored) {
            } finally {
                laeuft = false;
            }
        });
    }

    private static Treffer suche(String k) {
        try {
            int i = k.indexOf('|');
            String titel = k.substring(0, i), kuenstler = k.substring(i + 1);
            String q = URLEncoder.encode((kuenstler + " " + titel).trim(), StandardCharsets.UTF_8);
            HttpResponse<String> r = HTTP.send(HttpRequest.newBuilder(URI.create(
                            "https://itunes.apple.com/search?media=music&entity=song&limit=8&term=" + q))
                    .header("User-Agent", "VortexClient").timeout(Duration.ofSeconds(12)).build(), HttpResponse.BodyHandlers.ofString());
            if (r.statusCode() != 200) return NICHTS;
            JsonArray liste = JsonParser.parseString(r.body()).getAsJsonObject().getAsJsonArray("results");
            JsonObject best = null;
            int bestPunkte = -1;
            for (var e : liste) {
                JsonObject o = e.getAsJsonObject();
                String tn = text(o, "trackName").toLowerCase(Locale.ROOT), an = text(o, "artistName").toLowerCase(Locale.ROOT);
                int p = 0;
                if (tn.equals(titel)) p += 4; else if (tn.startsWith(titel) || titel.startsWith(tn)) p += 2;
                String ersterKuenstler = kuenstler.split(",")[0].trim();
                if (!ersterKuenstler.isEmpty() && an.contains(ersterKuenstler)) p += 3;
                if (p > bestPunkte) { bestPunkte = p; best = o; }
            }
            if (best == null || bestPunkte < 3) return NICHTS;
            String bild = text(best, "artworkUrl100").replace("100x100bb", "600x600bb");
            if (!bild.startsWith("https://")) return NICHTS;
            long dauer = best.has("trackTimeMillis") ? best.get("trackTimeMillis").getAsLong() : 0;
            return new Treffer(bild, dauer);
        } catch (Throwable t) {
            return NICHTS;
        }
    }

    private static String text(JsonObject o, String k) {
        return o.has(k) && o.get(k).isJsonPrimitive() ? o.get(k).getAsString() : "";
    }
}
