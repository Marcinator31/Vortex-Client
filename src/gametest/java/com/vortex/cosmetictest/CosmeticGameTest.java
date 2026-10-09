package com.vortex.cosmetictest;

import com.vortex.client.cosmetics.ActiveCape;
import com.vortex.client.cosmetics.Cosmetics;
import com.vortex.client.cosmetics.CosmeticsScreen;
import com.vortex.client.cosmetics.EigenesCape;
import com.vortex.client.cosmetics.Huete;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Cosmetics im echten Spiel: jeder Hut, ein eigenes Cape-Bild und Partikel,
 * jeweils von hinten und vorne fotografiert. Die Bilder landen im
 * screenshots-Ordner des Testlaufs und werden von Hand angesehen -- ob ein
 * Hut schief sitzt, kann kein Assert pruefen.
 *
 * Start:  ./gradlew runClientGameTest -PcosmeticTests
 */
public class CosmeticGameTest implements FabricClientGameTest {

    @Override
    public void runTest(ClientGameTestContext ctx) {
        try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
            TestServerContext srv = sp.getServer();
            srv.runCommand("gamemode creative @a");
            srv.runCommand("time set noon");
            srv.runCommand("weather clear 1000000");
            srv.runCommand("tp @a 0.5 ~ 0.5 0 0");
            ctx.waitTicks(40);
            ctx.runOnClient(mc -> { if (!mc.gui.hud.isHidden()) mc.gui.hud.toggle(); });

            if ("pixel".equals(System.getenv("VORTEX_COSMETICS_ONLY"))) { pixel(ctx, srv); return; }
            animiert(ctx, srv);
            if ("anim".equals(System.getenv("VORTEX_COSMETICS_ONLY"))) return;

            // Huete
            for (Huete.Hut h : Huete.alle().values()) {
                waehle(ctx, "", h.id(), "");
                fotos(ctx, "hat-" + h.id());
            }

            // Eigenes Cape-Bild: Verlauf mit Schrift, oben ein roter Balken (zeigt, wo "oben" ist)
            Path bild = Path.of(System.getProperty("java.io.tmpdir"), "vortex-cape-test.png");
            ctx.runOnClient(mc -> {
                try {
                    BufferedImage img = new BufferedImage(400, 640, BufferedImage.TYPE_INT_RGB);
                    Graphics2D g = img.createGraphics();
                    g.setPaint(new GradientPaint(0, 0, new Color(0x8B5CF6), 400, 640, new Color(0x3B82F6)));
                    g.fillRect(0, 0, 400, 640);
                    g.setColor(new Color(0xE0234E));
                    g.fillRect(0, 0, 400, 70);
                    g.setColor(Color.WHITE);
                    g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 90));
                    g.drawString("V", 160, 380);
                    g.dispose();
                    ImageIO.write(img, "png", bild.toFile());
                } catch (Exception e) { throw new RuntimeException(e); }
                String fehler = EigenesCape.bauen(bild);
                if (fehler != null) throw new AssertionError("Cape picture failed: " + fehler);
            });
            waehle(ctx, EigenesCape.ID, "", "");
            ctx.runOnClient(mc -> ActiveCape.neuLaden());
            ctx.waitTicks(20);
            fotos(ctx, "cape-custom");

            // Cape aus dem Vortex-Verzeichnis (braucht Netz)
            waehle(ctx, "vortex_blue", "", "");
            ctx.waitTicks(60);
            fotos(ctx, "cape-vortex-blue");

            // Cape-Physik: unsymmetrisches Bild (links rot + weisse Ecke oben links, rechts blau),
            // einmal mit dem Vanilla-Cape (Physik aus) als Vergleich, einmal mit unserem
            Path seiten = Path.of(System.getProperty("java.io.tmpdir"), "vortex-cape-sides.png");
            ctx.runOnClient(mc -> {
                try {
                    BufferedImage img = new BufferedImage(400, 640, BufferedImage.TYPE_INT_RGB);
                    Graphics2D g = img.createGraphics();
                    g.setColor(new Color(0xD02030)); g.fillRect(0, 0, 200, 640);
                    g.setColor(new Color(0x2050D0)); g.fillRect(200, 0, 200, 640);
                    g.setColor(Color.WHITE); g.fillRect(0, 0, 120, 120);
                    g.setColor(Color.YELLOW); g.fillRect(0, 560, 400, 80);
                    g.dispose();
                    ImageIO.write(img, "png", seiten.toFile());
                } catch (Exception e) { throw new RuntimeException(e); }
                String fehler = EigenesCape.bauen(seiten);
                if (fehler != null) throw new AssertionError("Cape picture failed: " + fehler);
            });
            waehle(ctx, EigenesCape.ID, "", "");
            ctx.runOnClient(mc -> ActiveCape.neuLaden());
            ctx.waitTicks(20);
            ctx.runOnClient(mc -> Cosmetics.setzeCapePhysik(false));
            fotos(ctx, "physics-off-vanilla");
            ctx.runOnClient(mc -> Cosmetics.setzeCapePhysik(true));
            fotos(ctx, "physics-on");
            // Seitenansicht in der Menue-Vorschau
            for (boolean an : new boolean[] { false, true }) {
                ctx.runOnClient(mc -> {
                    Cosmetics.setzeCapePhysik(an);
                    CosmeticsScreen sc = new CosmeticsScreen(null);
                    mc.gui.setScreen(sc);
                    sc.vorschauDrehen(90f, 0f);
                });
                ctx.waitTicks(10);
                ctx.takeScreenshot("physics-side-" + (an ? "on" : "off"));
                ctx.runOnClient(mc -> mc.gui.setScreen(null));
            }
            // Seitenansicht in Bewegung: Spieler wird geschoben, waehrend die Vorschau offen ist
            for (boolean an : new boolean[] { false, true }) {
                ctx.runOnClient(mc -> {
                    Cosmetics.setzeCapePhysik(an);
                    CosmeticsScreen sc = new CosmeticsScreen(null);
                    mc.gui.setScreen(sc);
                    sc.vorschauDrehen(90f, 0f);
                });
                for (int i = 0; i < 20; i++) {
                    ctx.runOnClient(mc -> mc.player.setDeltaMovement(0, mc.player.getDeltaMovement().y, 0.28));
                    ctx.waitTick();
                }
                ctx.takeScreenshot("physics-side-moving-" + (an ? "on" : "off"));
                ctx.runOnClient(mc -> mc.gui.setScreen(null));
                srv.runCommand("tp @a 0.5 ~ 0.5 0 0");
                ctx.waitTicks(20);
            }
            ctx.runOnClient(mc -> Cosmetics.setzeCapePhysik(true));
            // Laufen: das Cape weht und wellt sich
            ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
            ctx.getInput().holdKey(o -> o.keyUp);
            ctx.waitTicks(25);
            ctx.takeScreenshot("physics-walking-1");
            ctx.waitTicks(3);
            ctx.takeScreenshot("physics-walking-2");
            ctx.getInput().releaseKey(o -> o.keyUp);
            ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
            srv.runCommand("tp @a 0.5 ~ 0.5 0 0");
            ctx.waitTicks(20);

            // Emotes: jedes einmal von vorne (die Kamera springt dafuer selbst nach vorne)
            waehle(ctx, "", "", "");
            for (String e : com.vortex.client.cosmetics.Emotes.alle().keySet()) {
                ctx.runOnClient(mc -> com.vortex.client.cosmetics.Emotes.spielen(e));
                ctx.waitTicks(16);
                ctx.takeScreenshot("emote-" + e);
                ctx.waitTicks(70);
            }
            ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
            ctx.runOnClient(mc -> { if (mc.gui.hud.isHidden()) mc.gui.hud.toggle(); mc.gui.setScreen(new com.vortex.client.cosmetics.EmoteRadScreen()); });
            ctx.waitTicks(10);
            ctx.takeScreenshot("emote-wheel");
            ctx.runOnClient(mc -> { mc.gui.setScreen(null); if (!mc.gui.hud.isHidden()) mc.gui.hud.toggle(); });

            // Alles zusammen + Partikel
            waehle(ctx, EigenesCape.ID, "crown", "hearts");
            ctx.waitTicks(40);
            fotos(ctx, "combo-crown-hearts");
            waehle(ctx, "", "halo", "flames");
            ctx.waitTicks(40);
            fotos(ctx, "combo-halo-flames");

            // Das Menue selbst
            ctx.runOnClient(mc -> { if (mc.gui.hud.isHidden()) mc.gui.hud.toggle(); mc.options.setCameraType(CameraType.FIRST_PERSON); });
            ctx.runOnClient(mc -> mc.gui.setScreen(new CosmeticsScreen(null)));
            ctx.waitTicks(40);
            ctx.takeScreenshot("menu-cosmetics");
            ctx.runOnClient(mc -> mc.gui.setScreen(null));
            // Fuers Hauptmenue: eigenes Cape, Krone, Herzen
            waehle(ctx, EigenesCape.ID, "crown", "hearts");
            ctx.runOnClient(mc -> ActiveCape.neuLaden());
        }
        // Hauptmenue mit der Vortex-Leiste
        ctx.waitTicks(60);
        ctx.takeScreenshot("title-screen");
        ctx.waitTicks(30);
        ctx.takeScreenshot("title-screen-later");
    }

    /** Animierte Capes (CapeKunst), Glanz auf Hueten, Song-Karte ueber dem Kopf, Cape-Galerie. */
    private static void animiert(ClientGameTestContext ctx, TestServerContext srv) {
        String[] capes = com.vortex.client.cosmetics.CapeKunst.alle().keySet().toArray(new String[0]);
        for (String c : capes) {
            waehle(ctx, c, "", "");
            ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
            ctx.waitTicks(12);
            ctx.takeScreenshot("anim-" + c + "-a");
            ctx.waitTicks(14);
            ctx.takeScreenshot("anim-" + c + "-b");
        }
        // Nachts: das Leuchten muss trotzdem hell sein
        srv.runCommand("time set midnight");
        for (String c : new String[] { "anim_nether", "anim_end", "anim_neon" }) {
            waehle(ctx, c, "crown", "");
            ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
            ctx.waitTicks(12);
            ctx.takeScreenshot("anim-night-" + c);
        }
        srv.runCommand("time set noon");
        // Laufen mit animiertem Cape (Physik + Animation)
        waehle(ctx, "anim_vortex", "halo", "");
        ctx.getInput().holdKey(o -> o.keyUp);
        ctx.waitTicks(20);
        ctx.takeScreenshot("anim-walking");
        ctx.getInput().releaseKey(o -> o.keyUp);
        srv.runCommand("tp @a 0.5 ~ 0.5 0 0");
        ctx.waitTicks(10);
        // Huete mit Glanz von vorne
        for (String h : Huete.alle().keySet()) {
            waehle(ctx, "anim_royal", h, "");
            ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
            ctx.waitTicks(8);
            ctx.takeScreenshot("hat3d-" + h + "-front");
            ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
            ctx.waitTicks(6);
            ctx.takeScreenshot("hat3d-" + h + "-back");
        }
        srv.runCommand("time set midnight");
        for (String h : new String[] { "halo", "flame", "headphones" }) {
            waehle(ctx, "anim_vortex", h, "");
            ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
            ctx.waitTicks(8);
            ctx.takeScreenshot("hat3d-night-" + h);
        }
        srv.runCommand("time set noon");
        // Song ueber dem Kopf: mit Cover und ohne
        ctx.runOnClient(mc -> {
            var m = com.vortex.client.module.ModuleManager.INSTANCE.get(com.vortex.client.module.modules.SpotifyModule.class);
            if (m != null && !m.isEnabled()) m.toggle();
            if (m != null) m.ownAboveHead.set(true);
            try {
                BufferedImage img = new BufferedImage(128, 128, BufferedImage.TYPE_INT_RGB);
                Graphics2D g = img.createGraphics();
                g.setPaint(new GradientPaint(0, 0, new Color(0xFF5E62), 128, 128, new Color(0x2B1055)));
                g.fillRect(0, 0, 128, 128);
                g.setColor(Color.WHITE);
                g.fillOval(34, 34, 60, 60);
                g.dispose();
                var out = new java.io.ByteArrayOutputStream();
                ImageIO.write(img, "png", out);
                com.vortex.client.musik.Cover.testBild("https://i.scdn.co/image/test", out.toByteArray(), 0xFF5E62);
            } catch (Exception e) { throw new RuntimeException(e); }
            long jetzt = System.currentTimeMillis();
            com.vortex.client.musik.MusikDienst.testEigener(new com.vortex.client.musik.Song("t1", "Midnight City Lights", "Vortex Test Band", "Album",
                    "https://i.scdn.co/image/test", 215000, 64000, true, jetzt, ""));
        });
        waehle(ctx, "anim_aurora", "", "");
        ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
        ctx.waitTicks(10);
        ctx.takeScreenshot("song-card-cover");
        // HUD "Now Playing" mit derselben Karte
        ctx.runOnClient(mc -> {
            var np = com.vortex.client.module.ModuleManager.INSTANCE.get(com.vortex.client.module.modules.NowPlayingModule.class);
            if (np != null && !np.isEnabled()) np.toggle();
            if (mc.gui.hud.isHidden()) mc.gui.hud.toggle();
        });
        ctx.waitTicks(5);
        ctx.takeScreenshot("song-hud-cover");
        // Song aus der Spotify-App (ohne Cover/Laenge): Cover und Laenge kommen aus der iTunes-Suche
        ctx.runOnClient(mc -> com.vortex.client.musik.MusikDienst.testEigener(new com.vortex.client.musik.Song("", "Blinding Lights", "The Weeknd", "",
                "", 0, 0, true, System.currentTimeMillis(), "")));
        ctx.waitTicks(200);
        ctx.runOnClient(mc -> {
            var e = com.vortex.client.musik.MusikDienst.eigener();
            System.out.println("[vortex-test] app song cover=" + e.cover() + " dauer=" + e.dauer());
        });
        ctx.takeScreenshot("song-app-itunes");
        ctx.runOnClient(mc -> { if (!mc.gui.hud.isHidden()) mc.gui.hud.toggle(); });
        ctx.runOnClient(mc -> com.vortex.client.musik.MusikDienst.testEigener(new com.vortex.client.musik.Song("", "Song From The App", "Some Artist", "",
                "", 0, 0, true, System.currentTimeMillis(), "")));
        ctx.waitTicks(5);
        ctx.takeScreenshot("song-card-nocover");
        ctx.runOnClient(mc -> com.vortex.client.musik.MusikDienst.testEigener(null));
        ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
        // Music-Fenster mit Cover (Testsong als eigener)
        ctx.runOnClient(mc -> { if (mc.gui.hud.isHidden()) mc.gui.hud.toggle(); });
        // Galerie im Cosmetics-Menue
        ctx.runOnClient(mc -> mc.gui.setScreen(new CosmeticsScreen(null)));
        ctx.waitTicks(30);
        ctx.takeScreenshot("menu-capes-gallery");
        ctx.runOnClient(mc -> mc.gui.setScreen(null));
        ctx.runOnClient(mc -> { if (!mc.gui.hud.isHidden()) mc.gui.hud.toggle(); });
    }

    /** Pixel-Cosmetics (4.29): Bandanas, Face, Back, Auras, Shields -- nah (kleines Sichtfeld), gross. */
    private static void pixel(ClientGameTestContext ctx, TestServerContext srv) {
        ctx.getInput().resizeWindow(1920, 1080);
        ctx.runOnClient(mc -> { mc.options.fov().set(30); mc.options.guiScale().set(3); mc.resizeGui(); });
        ctx.waitTicks(10);
        java.util.function.BiConsumer<String, java.util.function.UnaryOperator<Cosmetics.Auswahl>> setze = (n, f) ->
                ctx.runOnClient(mc -> Cosmetics.speichern(f.apply(Cosmetics.Auswahl.LEER), false));
        for (var d : com.vortex.client.cosmetics.Zubehoer.von(com.vortex.client.cosmetics.Zubehoer.Kategorie.FACE)) {
            setze.accept(d.id(), a -> a.mitGesicht(d.id()));
            ansicht(ctx, "face-" + d.id() + "-front", CameraType.THIRD_PERSON_FRONT, 0);
            vorschau(ctx, "face-" + d.id() + "-side", 3, 90f);
        }
        for (var d : com.vortex.client.cosmetics.Zubehoer.von(com.vortex.client.cosmetics.Zubehoer.Kategorie.BANDANA)) {
            setze.accept(d.id(), a -> a.mitBandana(d.id()));
            ansicht(ctx, "bandana-" + d.id() + "-front", CameraType.THIRD_PERSON_FRONT, 25);
            ansicht(ctx, "bandana-" + d.id() + "-back", CameraType.THIRD_PERSON_BACK, 20);
            vorschau(ctx, "bandana-" + d.id() + "-side", 2, 90f);
        }
        for (var d : com.vortex.client.cosmetics.Zubehoer.von(com.vortex.client.cosmetics.Zubehoer.Kategorie.BACK)) {
            setze.accept(d.id(), a -> a.mitRuecken(d.id()));
            ansicht(ctx, "back-" + d.id() + "-back", CameraType.THIRD_PERSON_BACK, 0);
            vorschau(ctx, "back-" + d.id() + "-side", 4, 90f);
        }
        // Back-Item mit Cape: das Teil sitzt hinter dem Cape
        for (String id : new String[] { "teddy_backpack", "greatsword" }) {
            setze.accept(id, a -> a.mitRuecken(id).mitCape("anim_vortex"));
            ansicht(ctx, "back-" + id + "-cape", CameraType.THIRD_PERSON_BACK, 0);
            vorschau(ctx, "back-" + id + "-cape-side", 4, 90f);
        }
        for (var d : com.vortex.client.cosmetics.Zubehoer.von(com.vortex.client.cosmetics.Zubehoer.Kategorie.AURA)) {
            setze.accept(d.id(), a -> a.mitAura(d.id()));
            ansicht(ctx, "aura-" + d.id() + "-a", CameraType.THIRD_PERSON_FRONT, 10);
            ctx.waitTicks(17);
            ctx.takeScreenshot("aura-" + d.id() + "-b");
        }
        srv.runCommand("item replace entity @a weapon.offhand with minecraft:shield");
        for (var d : com.vortex.client.cosmetics.Zubehoer.von(com.vortex.client.cosmetics.Zubehoer.Kategorie.SHIELD)) {
            setze.accept(d.id(), a -> a.mitSchild(d.id()));
            // Blocken: der Schild zeigt mit der Vorderseite nach vorne
            ctx.getInput().holdKey(o -> o.keyUse);
            ansicht(ctx, "shield-" + d.id() + "-block", CameraType.THIRD_PERSON_FRONT, 0);
            ctx.getInput().releaseKey(o -> o.keyUse);
            ctx.waitTicks(4);
            ansicht(ctx, "shield-" + d.id() + "-hold", CameraType.THIRD_PERSON_FRONT, 0);
            vorschau(ctx, "shield-" + d.id() + "-back", 6, 200f);
        }
        // Ich-Perspektive (Hand nur mit sichtbarem HUD)
        ctx.runOnClient(mc -> { if (mc.gui.hud.isHidden()) mc.gui.hud.toggle(); mc.options.fov().set(70); });
        for (String id : new String[] { "vortex", "sakura" }) {
            setze.accept(id, a -> a.mitSchild(id));
            ansicht(ctx, "shield-" + id + "-firstperson", CameraType.FIRST_PERSON, 0);
            ctx.getInput().holdKey(o -> o.keyUse);
            ctx.waitTicks(8);
            ctx.takeScreenshot("shield-" + id + "-firstperson-block");
            ctx.getInput().releaseKey(o -> o.keyUse);
        }
        ctx.runOnClient(mc -> { if (!mc.gui.hud.isHidden()) mc.gui.hud.toggle(); mc.options.fov().set(30); });
        // Alles zusammen, auch nachts
        setze.accept("combo", a -> a.mitBandana("lightning").mitGesicht("pixel_shades").mitRuecken("teddy_backpack").mitAura("sakura").mitSchild("vortex").mitCape("anim_vortex"));
        ansicht(ctx, "combo-front", CameraType.THIRD_PERSON_FRONT, 25);
        ansicht(ctx, "combo-back", CameraType.THIRD_PERSON_BACK, 25);
        srv.runCommand("time set midnight");
        setze.accept("night", a -> a.mitGesicht("neon_visor").mitRuecken("rocket_pack").mitAura("soul").mitBandana("toxic"));
        ansicht(ctx, "night-front", CameraType.THIRD_PERSON_FRONT, 25);
        ansicht(ctx, "night-back", CameraType.THIRD_PERSON_BACK, 25);
        srv.runCommand("time set noon");
        // Menue: jeder neue Reiter
        ctx.runOnClient(mc -> { mc.options.fov().set(70); mc.options.setCameraType(CameraType.FIRST_PERSON); });
        for (int i = 2; i <= 6; i++) {
            final int r = i;
            ctx.runOnClient(mc -> { var s = new CosmeticsScreen(null); mc.gui.setScreen(s); s.zeigeReiter(r); });
            ctx.waitTicks(20);
            ctx.takeScreenshot("menu-tab-" + i);
        }
        ctx.runOnClient(mc -> mc.gui.setScreen(null));
    }

    /** Menue-Vorschau mit Reiter und Drehwinkel (die Kamera im Spiel folgt der Blickrichtung, Seiten gehen nur hier). */
    private static void vorschau(ClientGameTestContext ctx, String name, int reiter, float grad) {
        ctx.runOnClient(mc -> { var s = new CosmeticsScreen(null); mc.gui.setScreen(s); s.zeigeReiter(reiter); s.vorschauDrehen(grad, 0f); });
        ctx.waitTicks(10);
        ctx.takeScreenshot(name);
        ctx.runOnClient(mc -> mc.gui.setScreen(null));
        ctx.waitTicks(2);
    }

    private static void ansicht(ClientGameTestContext ctx, String name, CameraType cam, float yaw) {
        ctx.runOnClient(mc -> {
            mc.options.setCameraType(cam);
            mc.player.setYRot(yaw); mc.player.yRotO = yaw; mc.player.setYHeadRot(yaw); mc.player.yBodyRot = yaw; mc.player.yBodyRotO = yaw;
            mc.player.setXRot(0);
        });
        ctx.waitTicks(8);
        ctx.takeScreenshot(name);
    }

    private static void waehle(ClientGameTestContext ctx, String cape, String hut, String partikel) {
        ctx.runOnClient(mc -> Cosmetics.speichern(new Cosmetics.Auswahl(cape, hut, partikel, 2), false));
        ctx.waitTicks(5);
    }

    private static void fotos(ClientGameTestContext ctx, String name) {
        ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
        ctx.waitTicks(10);
        ctx.takeScreenshot(name + "-back");
        ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
        ctx.waitTicks(10);
        ctx.takeScreenshot(name + "-front");
        ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
    }
}
