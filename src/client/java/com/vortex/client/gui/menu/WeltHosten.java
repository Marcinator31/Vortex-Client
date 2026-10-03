package com.vortex.client.gui.menu;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.HttpUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;

import java.io.InputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Welt fuer Freunde oeffnen ("Host" in der Menue-Leiste).
 *
 * Oeffnet die Einzelspielerwelt wie "Im LAN oeffnen". Ist die Mod e4mc
 * installiert, bekommt die Welt dabei eine Adresse wie abc.e4mc.link, ueber
 * die Freunde von ueberall joinen -- ohne Portfreigabe. Ohne e4mc geht es
 * nur im selben Netzwerk.
 *
 * e4mc speichert die Adresse nirgends, es schreibt sie nur ins Log
 * ("Domain assigned: ...") -- deshalb haengt hier ein Log-Abgreifer am
 * Logger "e4mc".
 *
 * Platz: Hoster + 4 Freunde. Wer als Sechster joint, wird mit einer
 * Meldung wieder getrennt (LAN-Welten haetten sonst 8 Plaetze).
 */
public final class WeltHosten {
    private WeltHosten() {}

    public static final int MAX_FREUNDE = 4;
    private static final Pattern DOMAIN = Pattern.compile("Domain assigned: ([a-z0-9-]+(?:\\.[a-z0-9-]+)+)", Pattern.CASE_INSENSITIVE);

    private static volatile String e4mcAdresse;
    private static volatile String installStatus;      // null = nichts los
    private static volatile boolean installiert;        // frisch geladen, Neustart noetig

    /** Einmal beim Start: Log-Abgreifer und Platzbegrenzung anmelden. */
    public static void register() {
        try {
            org.apache.logging.log4j.core.Logger logger = (org.apache.logging.log4j.core.Logger) LogManager.getLogger("e4mc");
            AbstractAppender abgreifer = new AbstractAppender("VortexE4mcDomain", null, null, true, Property.EMPTY_ARRAY) {
                @Override
                public void append(LogEvent event) {
                    Matcher m = DOMAIN.matcher(event.getMessage().getFormattedMessage());
                    if (m.find()) e4mcAdresse = m.group(1).toLowerCase(java.util.Locale.ROOT);
                }
            };
            abgreifer.start();
            logger.addAppender(abgreifer);
        } catch (Throwable t) {
            com.vortex.client.core.Errors.report("WeltHosten.log", t);
        }

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            if (!(server instanceof IntegratedServer) || !server.isPublished()) return;
            if (server.getPlayerCount() > MAX_FREUNDE + 1) {
                handler.disconnect(Component.literal("This world is full (host + " + MAX_FREUNDE + " friends)."));
            }
        });
    }

    private static IntegratedServer server() {
        return Minecraft.getInstance().getSingleplayerServer();
    }

    public static boolean moeglich() { return server() != null; }

    public static boolean offen() {
        IntegratedServer s = server();
        return s != null && s.isPublished();
    }

    public static boolean e4mcDa() { return FabricLoader.getInstance().isModLoaded("e4mc"); }

    /**
     * e4mc und Krypton zusammen: Einzelspielerwelten oeffnen sich nicht mehr
     * (Welt startet und schliesst sofort, e4mc-Issues #298/#307). Dann wird
     * e4mc gar nicht erst angeboten -- der Launcher schaltet es sonst wieder aus.
     */
    public static boolean e4mcMoeglich() { return !FabricLoader.getInstance().isModLoaded("krypton"); }

    /** Welt oeffnen. Spielmodus und Cheats wie in der Welt eingestellt. */
    public static boolean oeffnen() {
        IntegratedServer s = server();
        if (s == null || s.isPublished()) return false;
        e4mcAdresse = null;
        var daten = s.getWorldData();
        int port = HttpUtil.getAvailablePort();
        //#if 26.2
        return s.publishServer(net.minecraft.server.MinecraftServer.MultiplayerScope.LAN, daten.getGameType(), daten.isAllowCommands(), port);
        //#else
        //$ return s.publishServer(daten.getGameType(), daten.isAllowCommands(), port);
        //#endif
    }

    /** Ab 26.2 kann man eine geoeffnete Welt wieder schliessen. */
    public static boolean kannSchliessen() {
        //#if 26.2
        return true;
        //#else
        //$ return false;
        //#endif
    }

    public static void schliessen() {
        IntegratedServer s = server();
        if (s == null || !s.isPublished()) return;
        //#if 26.2
        s.unpublishServer();
        //#endif
        e4mcAdresse = null;
    }

    /** Adresse fuer Freunde ueber e4mc (null, solange noch keine da ist). */
    public static String e4mcAdresse() { return offen() ? e4mcAdresse : null; }

    /** Adresse im eigenen Netzwerk, z. B. 192.168.1.20:51234. */
    public static String lanAdresse() {
        IntegratedServer s = server();
        if (s == null || !s.isPublished()) return null;
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback() || ni.isVirtual()) continue;
                for (InetAddress a : Collections.list(ni.getInetAddresses())) {
                    if (a instanceof Inet4Address && a.isSiteLocalAddress()) return a.getHostAddress() + ":" + s.getPort();
                }
            }
        } catch (Exception ignored) {}
        return "localhost:" + s.getPort();
    }

    /** Namen aller Spieler in der Welt (Hoster zuerst). */
    public static List<String> spieler() {
        List<String> out = new ArrayList<>();
        IntegratedServer s = server();
        if (s == null) return out;
        try {
            for (ServerPlayer p : new ArrayList<>(s.getPlayerList().getPlayers())) out.add(p.getName().getString());
        } catch (Exception ignored) {}
        return out;
    }

    // -----------------------------------------------------------------------
    // e4mc installieren (Modrinth), danach Neustart
    // -----------------------------------------------------------------------

    public static String installStatus() { return installStatus; }
    public static boolean neustartNoetig() { return installiert; }

    public static void e4mcInstallieren() {
        if (!e4mcMoeglich()) {
            installStatus = "e4mc cannot be used with Krypton -- together they stop singleplayer worlds from opening.";
            return;
        }
        if (installStatus != null && !installiert) return;   // laeuft schon
        installStatus = "Downloading e4mc...";
        Thread t = new Thread(() -> {
            try {
                laden();
                installiert = true;
                installStatus = "e4mc installed -- restart the game to use it.";
            } catch (Throwable e) {
                installStatus = "Could not install e4mc: " + e.getMessage();
                com.vortex.client.core.Errors.report("WeltHosten.e4mc", e);
            }
        }, "Vortex-e4mc-Install");
        t.setDaemon(true);
        t.start();
    }

    private static void laden() throws Exception {
        String mc = FabricLoader.getInstance().getModContainer("minecraft")
                .map(c -> c.getMetadata().getVersion().getFriendlyString()).orElseThrow();
        String query = "loaders=" + URLEncoder.encode("[\"fabric\"]", StandardCharsets.UTF_8)
                + "&game_versions=" + URLEncoder.encode("[\"" + mc + "\"]", StandardCharsets.UTF_8);
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).followRedirects(HttpClient.Redirect.NORMAL).build();
        HttpResponse<String> res = http.send(HttpRequest.newBuilder(URI.create("https://api.modrinth.com/v2/project/e4mc/version?" + query))
                .header("User-Agent", "Vortex-Client").timeout(Duration.ofSeconds(20)).build(), HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() != 200) throw new IllegalStateException("Modrinth answered " + res.statusCode());
        JsonArray versionen = JsonParser.parseString(res.body()).getAsJsonArray();
        if (versionen.isEmpty()) throw new IllegalStateException("e4mc is not available for Minecraft " + mc);
        JsonObject datei = null;
        for (JsonElement f : versionen.get(0).getAsJsonObject().getAsJsonArray("files")) {
            if (datei == null || f.getAsJsonObject().get("primary").getAsBoolean()) datei = f.getAsJsonObject();
        }
        if (datei == null) throw new IllegalStateException("No e4mc file found");
        String name = datei.get("filename").getAsString();
        if (!name.matches("[\\w.+-]+\\.jar")) throw new IllegalStateException("Unexpected file name " + name);
        String sha1 = datei.getAsJsonObject("hashes").get("sha1").getAsString();

        HttpResponse<InputStream> jar = http.send(HttpRequest.newBuilder(URI.create(datei.get("url").getAsString()))
                .header("User-Agent", "Vortex-Client").timeout(Duration.ofMinutes(2)).build(), HttpResponse.BodyHandlers.ofInputStream());
        if (jar.statusCode() != 200) throw new IllegalStateException("Download failed (" + jar.statusCode() + ")");
        byte[] daten;
        try (InputStream in = jar.body()) { daten = in.readAllBytes(); }
        String summe = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(daten));
        if (!summe.equalsIgnoreCase(sha1)) throw new IllegalStateException("Download is damaged");

        Path mods = FabricLoader.getInstance().getGameDir().resolve("mods");
        Files.createDirectories(mods);
        Path tmp = mods.resolve(name + ".download");
        Files.write(tmp, daten);
        Files.move(tmp, mods.resolve(name), StandardCopyOption.REPLACE_EXISTING);
    }
}
