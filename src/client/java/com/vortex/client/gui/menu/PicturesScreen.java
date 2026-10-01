package com.vortex.client.gui.menu;

import com.mojang.blaze3d.platform.NativeImage;
import com.vortex.client.gui.VortexStyle;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;

/**
 * Screenshot-Galerie im Spiel ("Pictures" in der Menue-Leiste).
 *
 * Raster mit Vorschaubildern aus dem screenshots-Ordner, neueste zuerst.
 * Ein Klick zeigt das Bild gross; dort: Ordner oeffnen, Loeschen (zweimal
 * klicken), Zurueck.
 *
 * Vorschaubilder werden in einem eigenen Thread verkleinert (ImageIO) und
 * erst danach als kleine Textur angelegt -- ein Screenshot in voller Groesse
 * wuerde pro Bild viele MB Grafikspeicher belegen. Beim Schliessen werden
 * alle Texturen wieder freigegeben.
 */
public class PicturesScreen extends Screen {
    private static final int ZELLE_W = 128, BILD_H = 72, ZELLE_H = BILD_H + 14, LUECKE = 6, KOPF = 34;
    private static final DateTimeFormatter DATUM = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneId.systemDefault());

    private record Textur(Identifier id, int w, int h) {}

    private final Screen parent;
    private final Path ordner;
    private final List<Path> bilder = new ArrayList<>();
    private final Map<Path, Textur> vorschau = new ConcurrentHashMap<>();
    private final Set<Path> angefragt = ConcurrentHashMap.newKeySet();
    private final ExecutorService lader = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Vortex-Pictures");
        t.setDaemon(true);
        return t;
    });
    private int naechsteId;

    private Path gross;            // gerade gross angezeigtes Bild (null = Raster)
    private Textur grossTextur;
    private boolean loeschenBestaetigen;
    private float scroll;
    private int spalten = 1, links;

    public PicturesScreen(Screen parent) {
        super(Component.literal("Pictures"));
        this.parent = parent;
        this.ordner = net.minecraft.client.Minecraft.getInstance().gameDirectory.toPath().resolve("screenshots");
        einlesen();
    }

    private void einlesen() {
        bilder.clear();
        if (!Files.isDirectory(ordner)) return;
        try (Stream<Path> s = Files.list(ordner)) {
            s.filter(p -> p.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".png"))
                    .sorted(Comparator.comparingLong(PicturesScreen::zeit).reversed())
                    .forEach(bilder::add);
        } catch (Exception e) {
            com.vortex.client.core.Errors.report("PicturesScreen.einlesen", e);
        }
    }

    private static long zeit(Path p) {
        try { return Files.getLastModifiedTime(p).toMillis(); } catch (Exception e) { return 0L; }
    }

    @Override
    protected void init() {
        if (gross == null) {
            addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
                    .bounds(this.width - 70, 8, 62, 20).build());
            addRenderableWidget(Button.builder(Component.literal("Open folder"), b -> ordnerOeffnen())
                    .bounds(this.width - 160, 8, 86, 20).build());
        } else {
            int y = this.height - 28, mitte = this.width / 2;
            addRenderableWidget(Button.builder(Component.literal("Back"), b -> zeigeRaster())
                    .bounds(mitte - 154, y, 100, 20).build());
            addRenderableWidget(Button.builder(Component.literal("Open folder"), b -> ordnerOeffnen())
                    .bounds(mitte - 50, y, 100, 20).build());
            addRenderableWidget(Button.builder(Component.literal(loeschenBestaetigen ? "Click again to delete" : "Delete"), b -> loeschen())
                    .bounds(mitte + 54, y, 100, 20).build());
        }
    }

    // -----------------------------------------------------------------------
    // Zeichnen
    // -----------------------------------------------------------------------

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        g.fill(0, 0, this.width, this.height, VortexStyle.DIM);
        if (gross != null) zeichneGross(g);
        else zeichneRaster(g, mouseX, mouseY);
        super.extractRenderState(g, mouseX, mouseY, delta);
    }

    private void zeichneRaster(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        g.text(this.font, "Pictures", 12, 14, VortexStyle.TEXT, true);
        g.text(this.font, bilder.size() == 1 ? "1 screenshot" : bilder.size() + " screenshots", 12 + this.font.width("Pictures") + 8, 14, VortexStyle.TEXT_DIM, false);
        VortexStyle.akzentLinie(g, 8, KOPF - 4, this.width - 16, 1f);

        if (bilder.isEmpty()) {
            String t = "No screenshots yet -- press F2 in game to take one.";
            g.text(this.font, t, (this.width - this.font.width(t)) / 2, this.height / 2, VortexStyle.TEXT_DIM, false);
            return;
        }
        spalten = Math.max(1, (this.width - 16 + LUECKE) / (ZELLE_W + LUECKE));
        links = (this.width - (spalten * (ZELLE_W + LUECKE) - LUECKE)) / 2;
        scroll = Math.max(0, Math.min(scroll, maxScroll()));

        g.enableScissor(0, KOPF, this.width, this.height);
        for (int i = 0; i < bilder.size(); i++) {
            int x = links + (i % spalten) * (ZELLE_W + LUECKE);
            int y = KOPF + 4 + (i / spalten) * (ZELLE_H + LUECKE) - Math.round(scroll);
            if (y + ZELLE_H < KOPF || y > this.height) continue;   // nicht sichtbar: auch nicht laden
            Path p = bilder.get(i);
            boolean hover = mouseX >= x && mouseX < x + ZELLE_W && mouseY >= y && mouseY < y + ZELLE_H && mouseY >= KOPF;
            g.fill(x - 1, y - 1, x + ZELLE_W + 1, y + ZELLE_H + 1, hover ? VortexStyle.VIOLETT : VortexStyle.LINE);
            g.fill(x, y, x + ZELLE_W, y + ZELLE_H, hover ? VortexStyle.HOV : VortexStyle.CARD);
            Textur t = vorschau.get(p);
            if (t == null) {
                laden(p);
                g.text(this.font, "...", x + ZELLE_W / 2 - 6, y + BILD_H / 2 - 4, VortexStyle.TEXT_DIM, false);
            } else {
                zeichneEingepasst(g, t, x, y, ZELLE_W, BILD_H);
            }
            String name = kuerzen(DATUM.format(Instant.ofEpochMilli(zeit(p))), ZELLE_W - 6);
            g.text(this.font, name, x + 3, y + BILD_H + 3, VortexStyle.TEXT_DIM, false);
        }
        g.disableScissor();
    }

    private void zeichneGross(GuiGraphicsExtractor g) {
        String titel = gross.getFileName().toString();
        g.text(this.font, titel, (this.width - this.font.width(titel)) / 2, 10, VortexStyle.TEXT, true);
        if (grossTextur == null) {
            String t = "Loading...";
            g.text(this.font, t, (this.width - this.font.width(t)) / 2, this.height / 2, VortexStyle.TEXT_DIM, false);
            return;
        }
        zeichneEingepasst(g, grossTextur, 16, 26, this.width - 32, this.height - 26 - 36);
    }

    /** Bild in ein Feld einpassen (Seitenverhaeltnis bleibt). */
    private static void zeichneEingepasst(GuiGraphicsExtractor g, Textur t, int x, int y, int w, int h) {
        float s = Math.min(w / (float) t.w(), h / (float) t.h());
        int bw = Math.max(1, Math.round(t.w() * s)), bh = Math.max(1, Math.round(t.h() * s));
        int bx = x + (w - bw) / 2, by = y + (h - bh) / 2;
        g.blit(RenderPipelines.GUI_TEXTURED, t.id(), bx, by, 0f, 0f, bw, bh, t.w(), t.h(), t.w(), t.h());
    }

    private String kuerzen(String s, int max) {
        if (this.font.width(s) <= max) return s;
        while (s.length() > 1 && this.font.width(s + "...") > max) s = s.substring(0, s.length() - 1);
        return s + "...";
    }

    // -----------------------------------------------------------------------
    // Laden
    // -----------------------------------------------------------------------

    /** Vorschaubild im Hintergrund verkleinern, dann auf dem Spiel-Thread anlegen. */
    private void laden(Path p) {
        if (!angefragt.add(p)) return;
        lader.submit(() -> {
            byte[] png = verkleinern(p, 256);
            if (png == null) return;
            this.minecraft.execute(() -> {
                Textur t = anlegen(png);
                if (t == null) return;
                if (lader.isShutdown()) { this.minecraft.getTextureManager().release(t.id()); return; }
                vorschau.put(p, t);
            });
        });
    }

    private static byte[] verkleinern(Path p, int breite) {
        try {
            BufferedImage src = ImageIO.read(p.toFile());
            if (src == null) return null;
            int w = Math.min(breite, src.getWidth());
            int h = Math.max(1, Math.round(src.getHeight() * (w / (float) src.getWidth())));
            BufferedImage klein = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            Graphics2D gr = klein.createGraphics();
            gr.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            gr.drawImage(src, 0, 0, w, h, null);
            gr.dispose();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(klein, "png", out);
            return out.toByteArray();
        } catch (Throwable t) {
            com.vortex.client.core.Errors.report("PicturesScreen.verkleinern", t);
            return null;
        }
    }

    private Textur anlegen(byte[] png) {
        try (InputStream in = new ByteArrayInputStream(png)) {
            NativeImage image = NativeImage.read(in);
            int w = image.getWidth(), h = image.getHeight();
            Identifier id = Identifier.fromNamespaceAndPath("vortexclient", "pictures/" + (naechsteId++));
            this.minecraft.getTextureManager().register(id, new DynamicTexture(() -> "vortexclient-picture", image));
            return new Textur(id, w, h);
        } catch (Throwable t) {
            com.vortex.client.core.Errors.report("PicturesScreen.anlegen", t);
            return null;
        }
    }

    // -----------------------------------------------------------------------
    // Bedienung
    // -----------------------------------------------------------------------

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent click, boolean doubled) {
        if (super.mouseClicked(click, doubled)) return true;
        if (gross != null || click.button() != 0 || click.y() < KOPF) return false;
        int mx = (int) click.x(), my = (int) click.y();
        for (int i = 0; i < bilder.size(); i++) {
            int x = links + (i % spalten) * (ZELLE_W + LUECKE);
            int y = KOPF + 4 + (i / spalten) * (ZELLE_H + LUECKE) - Math.round(scroll);
            if (mx >= x && mx < x + ZELLE_W && my >= y && my < y + ZELLE_H) {
                zeigeGross(bilder.get(i));
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (gross == null) scroll = Math.max(0, Math.min(maxScroll(), scroll - (float) vertical * 30f));
        return true;
    }

    private float maxScroll() {
        int zeilen = (bilder.size() + spalten - 1) / spalten;
        return Math.max(0, zeilen * (ZELLE_H + LUECKE) + 8 - (this.height - KOPF));
    }

    private void zeigeGross(Path p) {
        gross = p;
        grossTextur = null;
        loeschenBestaetigen = false;
        rebuildWidgets();
        lader.submit(() -> {
            byte[] png = verkleinern(p, 1920);
            if (png == null) return;
            this.minecraft.execute(() -> {
                Textur t = anlegen(png);
                if (t == null) return;
                if (gross != p || lader.isShutdown()) { this.minecraft.getTextureManager().release(t.id()); return; }
                grossTextur = t;
            });
        });
    }

    private void zeigeRaster() {
        if (grossTextur != null) this.minecraft.getTextureManager().release(grossTextur.id());
        gross = null;
        grossTextur = null;
        loeschenBestaetigen = false;
        rebuildWidgets();
    }

    private void loeschen() {
        if (gross == null) return;
        if (!loeschenBestaetigen) { loeschenBestaetigen = true; rebuildWidgets(); return; }
        Path p = gross;
        try {
            Files.deleteIfExists(p);
        } catch (Exception e) {
            com.vortex.client.core.Errors.report("PicturesScreen.loeschen", e);
        }
        Textur t = vorschau.remove(p);
        if (t != null) this.minecraft.getTextureManager().release(t.id());
        angefragt.remove(p);
        bilder.remove(p);
        zeigeRaster();
    }

    private void ordnerOeffnen() {
        try {
            Files.createDirectories(ordner);
            Util.getPlatform().openUri(ordner.toUri());
        } catch (Exception e) {
            com.vortex.client.core.Errors.report("PicturesScreen.ordner", e);
        }
    }

    @Override
    public void removed() {
        lader.shutdownNow();
        for (Textur t : vorschau.values()) this.minecraft.getTextureManager().release(t.id());
        vorschau.clear();
        if (grossTextur != null) this.minecraft.getTextureManager().release(grossTextur.id());
        grossTextur = null;
        super.removed();
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
