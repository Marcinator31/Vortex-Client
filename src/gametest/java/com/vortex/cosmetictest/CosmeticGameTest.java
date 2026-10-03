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
            waehle(ctx, "", "", "");
        }
        // Hauptmenue mit der Vortex-Leiste
        ctx.waitTicks(40);
        ctx.takeScreenshot("title-screen");
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
