package com.vortex.client.cosmetics;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Die gewaehlten Cosmetics: Cape, Hut, Partikel.
 *
 * Gespeichert in config/vortex-client/cosmetics.json -- dieselbe Datei, aus
 * der ActiveCape schon das Cape ("cape") liest; die neuen Felder kommen dazu.
 *
 * Cape: Id aus dem Cosmetics-Verzeichnis (siehe ActiveCape) oder "custom"
 * fuer ein eigenes Bild (EigenesCape). Leer = kein Vortex-Cape.
 *
 * Andere Spieler: {@link #setzeFremde} nimmt ihre Auswahl entgegen
 * (CosmeticsSync, ueber den Freunde-Server). Ohne den sieht jeder nur seine eigenen.
 */
public final class Cosmetics {
    private Cosmetics() {}

    /** Auswahl eines Spielers. Leere Strings = nichts gewaehlt. */
    /**
     * Was ein Spieler traegt. 'partikel' ist seit 4.29 die Aura (alte Partikel-IDs
     * werden auf die passende Aura abgebildet, siehe Zubehoer); dichte = Menge 1..3.
     */
    public record Auswahl(String cape, String hut, String partikel, int dichte,
                          String bandana, String gesicht, String ruecken, String schild) {
        public static final Auswahl LEER = new Auswahl("", "", "", 2);

        public Auswahl {
            cape = n(cape); hut = n(hut); partikel = n(partikel);
            bandana = n(bandana); gesicht = n(gesicht); ruecken = n(ruecken); schild = n(schild);
            dichte = Math.max(1, Math.min(3, dichte));
        }

        public Auswahl(String cape, String hut, String partikel, int dichte) {
            this(cape, hut, partikel, dichte, "", "", "", "");
        }

        private static String n(String s) { return s == null ? "" : s; }

        public Auswahl mitCape(String v) { return new Auswahl(v, hut, partikel, dichte, bandana, gesicht, ruecken, schild); }
        public Auswahl mitHut(String v) { return new Auswahl(cape, v, partikel, dichte, bandana, gesicht, ruecken, schild); }
        public Auswahl mitAura(String v) { return new Auswahl(cape, hut, v, dichte, bandana, gesicht, ruecken, schild); }
        public Auswahl mitDichte(int v) { return new Auswahl(cape, hut, partikel, v, bandana, gesicht, ruecken, schild); }
        public Auswahl mitBandana(String v) { return new Auswahl(cape, hut, partikel, dichte, v, gesicht, ruecken, schild); }
        public Auswahl mitGesicht(String v) { return new Auswahl(cape, hut, partikel, dichte, bandana, v, ruecken, schild); }
        public Auswahl mitRuecken(String v) { return new Auswahl(cape, hut, partikel, dichte, bandana, gesicht, v, schild); }
        public Auswahl mitSchild(String v) { return new Auswahl(cape, hut, partikel, dichte, bandana, gesicht, ruecken, v); }
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static volatile Auswahl eigene = null;
    private static volatile boolean partikelErstePerson = false;
    private static volatile boolean capePhysik = true;
    private static final Map<UUID, Auswahl> FREMDE = new ConcurrentHashMap<>();

    /** Einmal beim Start: Hut-Ebene am Spieler-Renderer und Partikel anmelden. */
    public static void register() {
        //#if 26
        net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback.EVENT.register((typ, renderer, helfer, ctx) -> {
        //#else
        //$ net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback.EVENT.register((typ, renderer, helfer, ctx) -> {
        //#endif
            if (renderer instanceof net.minecraft.client.renderer.entity.player.AvatarRenderer<?> spieler) {
                helfer.register(new HutEbene(spieler));
                helfer.register(new CapeEbene(spieler));
                helfer.register(new ZubehoerEbene(spieler));
            }
        });
        // Vanilla-Cape aus, wenn unser bewegliches Cape es zeichnet
        net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRenderEvents.ALLOW_CAPE_RENDER.register(s -> !capePhysik());
        CosmeticsSync.register();
        Emotes.register();
    }

    public static Path ordner() {
        return FabricLoader.getInstance().getConfigDir().resolve("vortex-client");
    }

    private static Path datei() { return ordner().resolve("cosmetics.json"); }

    public static Auswahl eigene() {
        if (eigene == null) laden();
        return eigene;
    }

    public static boolean partikelErstePerson() {
        if (eigene == null) laden();
        return partikelErstePerson;
    }

    private static synchronized void laden() {
        if (eigene != null) return;
        Auswahl a = Auswahl.LEER;
        try {
            if (Files.exists(datei())) {
                JsonObject o = JsonParser.parseString(Files.readString(datei(), StandardCharsets.UTF_8)).getAsJsonObject();
                // Alte Partikel (bis 4.28) werden zur passenden Aura
                a = new Auswahl(text(o, "cape"), text(o, "hat"), Zubehoer.auraFuer(text(o, "particles")),
                        o.has("particleDensity") ? Math.max(1, Math.min(3, o.get("particleDensity").getAsInt())) : 2,
                        text(o, "bandana"), text(o, "face"), text(o, "back"), text(o, "shield"));
                partikelErstePerson = o.has("particlesFirstPerson") && o.get("particlesFirstPerson").getAsBoolean();
                capePhysik = !o.has("capePhysics") || o.get("capePhysics").getAsBoolean();
            }
        } catch (Throwable t) {
            com.vortex.client.core.Errors.report("Cosmetics.laden", t);
        }
        eigene = a;
    }

    private static String text(JsonObject o, String k) {
        return o.has(k) && o.get(k).isJsonPrimitive() ? o.get(k).getAsString() : "";
    }

    /** Neue Auswahl speichern. Ein geaendertes Cape wird sofort neu geladen. */
    public static synchronized void speichern(Auswahl neu, boolean erstePerson) {
        Auswahl alt = eigene();
        eigene = neu;
        partikelErstePerson = erstePerson;
        try {
            JsonObject o = new JsonObject();
            o.addProperty("cape", neu.cape());
            o.addProperty("hat", neu.hut());
            o.addProperty("particles", neu.partikel());
            o.addProperty("particleDensity", neu.dichte());
            o.addProperty("bandana", neu.bandana());
            o.addProperty("face", neu.gesicht());
            o.addProperty("back", neu.ruecken());
            o.addProperty("shield", neu.schild());
            o.addProperty("particlesFirstPerson", erstePerson);
            o.addProperty("capePhysics", capePhysik);
            Files.createDirectories(ordner());
            Path tmp = datei().resolveSibling("cosmetics.json.tmp");
            Files.writeString(tmp, GSON.toJson(o), StandardCharsets.UTF_8);
            Files.move(tmp, datei(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (Throwable t) {
            com.vortex.client.core.Errors.report("Cosmetics.speichern", t);
        }
        if (!alt.cape().equals(neu.cape())) ActiveCape.neuLaden();
        CosmeticsSync.geaendert();
    }

    /** Realistische Capes (CapeEbene) statt der starren Vanilla-Platte -- gilt fuer alle Capes, die du siehst. */
    public static boolean capePhysik() {
        if (eigene == null) laden();
        return capePhysik;
    }

    public static void setzeCapePhysik(boolean an) {
        capePhysik = an;
        speichern(eigene(), partikelErstePerson());
    }

    /** Auswahl anderer Spieler (vom Server). null = entfernen. */
    public static void setzeFremde(UUID spieler, Auswahl a) {
        if (a == null) FREMDE.remove(spieler); else FREMDE.put(spieler, a);
    }

    /** Auswahl fuer einen Spieler: der eigene aus der Datei, andere vom Server. */
    public static Auswahl fuer(UUID spieler) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.getUUID().equals(spieler)) return eigene();
        return FREMDE.getOrDefault(spieler, Auswahl.LEER);
    }

    /** Auswahl fuer die Entity mit dieser Id (aus dem Renderzustand). */
    public static Auswahl fuerEntity(int id) {
        if (id == TitelFigur.ID) return eigene();   // Figur im Hauptmenue (ohne Welt)
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return Auswahl.LEER;
        Entity e = mc.level.getEntity(id);
        return e instanceof Player p ? fuer(p.getUUID()) : Auswahl.LEER;
    }
}
