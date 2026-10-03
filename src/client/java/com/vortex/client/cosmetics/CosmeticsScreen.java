package com.vortex.client.cosmetics;

import com.vortex.client.gui.glatt.Glatt;
import com.vortex.client.gui.glatt.Symbole;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Das Cosmetics-Menue: Capes, Huete, Partikel -- mit drehbarer 3D-Vorschau
 * des eigenen Charakters (ziehen mit der Maus).
 *
 * Eine Auswahl gilt sofort und wird gespeichert (Cosmetics). Capes: aus dem
 * Vortex-Verzeichnis oder ein eigenes Bild (EigenesCape).
 */
public class CosmeticsScreen extends Screen {
    private enum Reiter { CAPES, HATS, PARTICLES }

    private record Kachel(String id, String name, String unter) {}
    private record Flaeche(int x, int y, int w, int h, Runnable aktion) {
        boolean in(double mx, double my) { return mx >= x && mx < x + w && my >= y && my < y + h; }
    }

    private static final int TEXT = 0xFFF2F0F8, DIM = 0xFF8D86A6, KARTE = 0xF0181424, KARTE_HOV = 0xF0221C34;
    private static final int FENSTER = 0xF20E0B16, AKZENT = 0xFF8B5CF6;

    private final Screen parent;
    private Reiter reiter = Reiter.CAPES;
    private final List<Flaeche> flaechen = new ArrayList<>();
    private volatile List<String[]> katalog = null;
    private volatile String meldung = null;
    private float scroll, drehung = 160f, neigung = 0f;
    private boolean zieht;
    private int fx, fy, fw, fh;

    public CosmeticsScreen(Screen parent) {
        super(Component.literal("Cosmetics"));
        this.parent = parent;
        Thread t = new Thread(() -> katalog = ActiveCape.katalog(), "Vortex-Cape-Catalogue");
        t.setDaemon(true);
        t.start();
    }

    @Override
    protected void init() {
        fw = Math.min(this.width - 24, 560);
        fh = Math.min(this.height - 24, 320);
        fx = (this.width - fw) / 2;
        fy = (this.height - fh) / 2;
    }

    // -----------------------------------------------------------------------
    // Inhalt je Reiter
    // -----------------------------------------------------------------------

    private List<Kachel> kacheln() {
        List<Kachel> k = new ArrayList<>();
        switch (reiter) {
            case CAPES -> {
                k.add(new Kachel("", "None", "No Vortex cape"));
                if (katalog == null) k.add(new Kachel("#laedt", "Loading...", "Vortex capes"));
                else for (String[] c : katalog) k.add(new Kachel(c[0], c[1], "Vortex cape"));
                if (EigenesCape.vorhanden()) k.add(new Kachel(EigenesCape.ID, "Your picture", "Custom cape"));
                k.add(new Kachel("#upload", EigenesCape.vorhanden() ? "Change picture..." : "Upload picture...", "PNG or JPG on the back"));
            }
            case HATS -> {
                k.add(new Kachel("", "None", "No hat"));
                for (Huete.Hut h : Huete.alle().values()) k.add(new Kachel(h.id(), h.name(), "Hat"));
            }
            case PARTICLES -> {
                k.add(new Kachel("", "None", "No particles"));
                for (Partikel.Effekt e : Partikel.alle().values()) k.add(new Kachel(e.id(), e.name(), switch (e.art()) {
                    case KREIS -> "Circle around you";
                    case SPIRALE -> "Spiral upwards";
                    case REGEN -> "Falling from above";
                    case WOLKE -> "Cloud around you";
                }));
            }
        }
        return k;
    }

    private String gewaehlt() {
        Cosmetics.Auswahl a = Cosmetics.eigene();
        return switch (reiter) {
            case CAPES -> a.cape();
            case HATS -> a.hut();
            case PARTICLES -> a.partikel();
        };
    }

    private void waehle(String id) {
        Cosmetics.Auswahl a = Cosmetics.eigene();
        if ("#laedt".equals(id)) return;
        if ("#upload".equals(id)) {
            meldung = "Choose a picture in the window that opened...";
            EigenesCape.waehlen(fehler -> this.minecraft.execute(() -> {
                if (fehler != null) { meldung = fehler; return; }
                if (!EigenesCape.vorhanden()) { meldung = null; return; }
                meldung = "Your picture is on the cape now.";
                Cosmetics.Auswahl b = Cosmetics.eigene();
                // Auch wenn "custom" schon gewaehlt war: das neue Bild laden
                Cosmetics.speichern(new Cosmetics.Auswahl(EigenesCape.ID, b.hut(), b.partikel(), b.dichte()), Cosmetics.partikelErstePerson());
                ActiveCape.neuLaden();
            }));
            return;
        }
        meldung = null;
        Cosmetics.Auswahl neu = switch (reiter) {
            case CAPES -> new Cosmetics.Auswahl(id, a.hut(), a.partikel(), a.dichte());
            case HATS -> new Cosmetics.Auswahl(a.cape(), id, a.partikel(), a.dichte());
            case PARTICLES -> new Cosmetics.Auswahl(a.cape(), a.hut(), id, a.dichte());
        };
        Cosmetics.speichern(neu, Cosmetics.partikelErstePerson());
    }

    // -----------------------------------------------------------------------
    // Zeichnen
    // -----------------------------------------------------------------------

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        flaechen.clear();
        g.fill(0, 0, this.width, this.height, 0xC8060409);
        Glatt.schatten(g, fx, fy, fw, fh, 10, 12, 0x90000000);
        Glatt.rund(g, fx, fy, fw, fh, 10, FENSTER);
        Glatt.linieW(g, fx + 12, fx + fw - 12, fy + 30, 0x30FFFFFF);

        Glatt.symbol(g, Symbole.Symbol.STERN, fx + 14, fy + 10, 11, AKZENT);
        Glatt.text(g, "Cosmetics", fx + 30, fy + 11, TEXT, Glatt.Schrift.FETT);
        knopf(g, fx + fw - 64, fy + 7, 52, 16, "Done", mouseX, mouseY, this::onClose, false);

        // Reiter links
        int ry = fy + 40;
        for (Reiter r : Reiter.values()) {
            String name = switch (r) { case CAPES -> "Capes"; case HATS -> "Hats"; case PARTICLES -> "Particles"; };
            Symbole.Symbol sym = switch (r) { case CAPES -> Symbole.Symbol.HEMD; case HATS -> Symbole.Symbol.STERN; case PARTICLES -> Symbole.Symbol.BLITZ; };
            boolean an = r == reiter, hov = !an && mouseX >= fx + 10 && mouseX < fx + 100 && mouseY >= ry && mouseY < ry + 22;
            if (an || hov) Glatt.rund(g, fx + 10, ry, 90, 22, 6, an ? 0x338B5CF6 : 0x18FFFFFF);
            if (an) Glatt.rund(g, fx + 10, ry + 5, 2.5f, 12, 1.2f, AKZENT);
            Glatt.symbol(g, sym, fx + 18, ry + 5, 12, an ? TEXT : DIM);
            Glatt.text(g, name, fx + 36, ry + 7, an ? TEXT : DIM, Glatt.Schrift.NORMAL);
            final Reiter ziel = r;
            flaechen.add(new Flaeche(fx + 10, ry, 90, 22, () -> { reiter = ziel; scroll = 0; meldung = null; }));
            ry += 26;
        }

        // Vorschau rechts
        int pw = 150, px = fx + fw - pw - 12, py = fy + 40, ph = fh - 52;
        Glatt.rund(g, px, py, pw, ph, 8, 0xF0120E1B);
        vorschau(g, px, py, pw, ph);

        // Kacheln in der Mitte
        int lx = fx + 110, lw = px - lx - 10, ly = fy + 40, lh = fh - 52;
        if (reiter == Reiter.PARTICLES) {
            partikelOptionen(g, lx, ly, lw, mouseX, mouseY);
            ly += 26; lh -= 26;
        } else if (reiter == Reiter.CAPES) {
            boolean an = Cosmetics.capePhysik();
            knopf(g, lx, ly, Math.min(170, lw), 18, "Cape physics: " + (an ? "On" : "Off"), mouseX, mouseY,
                    () -> Cosmetics.setzeCapePhysik(!Cosmetics.capePhysik()), an);
            ly += 26; lh -= 26;
        }
        int spalten = lw >= 260 ? 2 : 1, kw = (lw - (spalten - 1) * 6) / spalten, kh = 34;
        List<Kachel> liste = kacheln();
        int zeilen = (liste.size() + spalten - 1) / spalten;
        float max = Math.max(0, zeilen * (kh + 6) - 6 - lh);
        scroll = Math.max(0, Math.min(scroll, max));
        String sel = gewaehlt();
        g.enableScissor(lx, ly, lx + lw, ly + lh);
        for (int i = 0; i < liste.size(); i++) {
            Kachel k = liste.get(i);
            int kx = lx + (i % spalten) * (kw + 6), ky = ly + (i / spalten) * (kh + 6) - Math.round(scroll);
            if (ky + kh < ly || ky > ly + lh) continue;
            boolean an = k.id().equals(sel) && !k.id().startsWith("#");
            boolean hov = mouseX >= kx && mouseX < kx + kw && mouseY >= ky && mouseY < ky + kh && mouseY >= ly && mouseY < ly + lh;
            Glatt.rund(g, kx, ky, kw, kh, 7, hov ? KARTE_HOV : KARTE);
            if (an) Glatt.rahmen(g, kx, ky, kw, kh, 7, 1, AKZENT);
            Glatt.text(g, Glatt.kuerzen(k.name(), kw - 30, Glatt.Schrift.FETT), kx + 10, ky + 7, TEXT, Glatt.Schrift.FETT);
            Glatt.text(g, Glatt.kuerzen(k.unter(), kw - 30, Glatt.Schrift.NORMAL), kx + 10, ky + 19, DIM, Glatt.Schrift.NORMAL);
            if (an) Glatt.symbol(g, Symbole.Symbol.HAKEN, kx + kw - 20, ky + 11, 11, AKZENT);
            else if (k.id().equals("#upload")) Glatt.symbol(g, Symbole.Symbol.PLUS, kx + kw - 20, ky + 11, 11, DIM);
            if (ky >= ly && ky + kh <= ly + lh) flaechen.add(new Flaeche(kx, ky, kw, kh, () -> waehle(k.id())));
        }
        g.disableScissor();

        if (meldung != null) Glatt.textMitte(g, meldung, lx + lw / 2f, fy + fh - 11, DIM, Glatt.Schrift.NORMAL);
        super.extractRenderState(g, mouseX, mouseY, delta);
    }

    private void partikelOptionen(GuiGraphicsExtractor g, int x, int y, int w, int mouseX, int mouseY) {
        Cosmetics.Auswahl a = Cosmetics.eigene();
        String dichte = "Amount: " + (a.dichte() == 1 ? "Low" : a.dichte() == 3 ? "High" : "Medium");
        knopf(g, x, y, 110, 18, dichte, mouseX, mouseY, () -> {
            Cosmetics.Auswahl b = Cosmetics.eigene();
            Cosmetics.speichern(new Cosmetics.Auswahl(b.cape(), b.hut(), b.partikel(), b.dichte() % 3 + 1), Cosmetics.partikelErstePerson());
        }, false);
        boolean ich = Cosmetics.partikelErstePerson();
        knopf(g, x + 116, y, Math.min(170, w - 116), 18, "First person: " + (ich ? "On" : "Off"), mouseX, mouseY,
                () -> Cosmetics.speichern(Cosmetics.eigene(), !Cosmetics.partikelErstePerson()), ich);
    }

    private void knopf(GuiGraphicsExtractor g, int x, int y, int w, int h, String text, int mouseX, int mouseY, Runnable aktion, boolean an) {
        boolean hov = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
        Glatt.rund(g, x, y, w, h, h / 2f, hov ? 0x40FFFFFF : 0x24FFFFFF);
        if (an) Glatt.rahmen(g, x, y, w, h, h / 2f, 1, AKZENT);
        Glatt.textMitte(g, text, x + w / 2f, y + (h - 8) / 2f, TEXT, Glatt.Schrift.NORMAL);
        flaechen.add(new Flaeche(x, y, w, h, aktion));
    }

    /** Eigener Spieler, frei drehbar. Ohne Welt (Hauptmenue) gibt es nichts zu zeigen. */
    private void vorschau(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        Player p = this.minecraft.player;
        if (p == null) {
            Glatt.textMitte(g, "Join a world", x + w / 2f, y + h / 2f - 10, DIM, Glatt.Schrift.NORMAL);
            Glatt.textMitte(g, "to see the preview", x + w / 2f, y + h / 2f + 2, DIM, Glatt.Schrift.NORMAL);
            return;
        }
        Glatt.textMitte(g, "Drag to turn", x + w / 2f, y + h - 12, DIM, Glatt.Schrift.NORMAL);
        float bodyRot = p.yBodyRot, bodyRotO = p.yBodyRotO, yRot = p.getYRot(), yRotO = p.yRotO, xRot = p.getXRot(), xRotO = p.xRotO, head = p.yHeadRot, headO = p.yHeadRotO;
        try {
            // Wie die Inventar-Vorschau: Spieler schaut nach vorne, gedreht wird die Kamera
            p.yBodyRot = p.yBodyRotO = 180f;
            p.setYRot(180f); p.yRotO = 180f;
            p.setXRot(0f); p.xRotO = 0f;
            p.yHeadRot = p.yHeadRotO = 180f;
            var state = this.minecraft.getEntityRenderDispatcher().extractEntity(p, 1f);
            state.lightCoords = 0xF000F0;
            state.shadowPieces.clear();
            state.outlineColor = 0;
            Quaternionf drehen = new Quaternionf().rotateZ((float) Math.PI)
                    .mul(new Quaternionf().rotateX((float) Math.toRadians(neigung)))
                    .mul(new Quaternionf().rotateY((float) Math.toRadians(drehung - 180f)));
            float groesse = Math.min(w, h - 20) * 0.42f;
            Vector3f mitte = new Vector3f(0, p.getBbHeight() / 2f + 0.06f, 0);
            //#if 26
            g.entity(state, groesse, mitte, drehen, null, x, y, x + w, y + h - 16);
            //#else
            //$ g.submitEntityRenderState(state, groesse, mitte, drehen, null, x, y, x + w, y + h - 16);
            //#endif
        } catch (Throwable t) {
            com.vortex.client.core.Errors.report("CosmeticsScreen.vorschau", t);
        } finally {
            p.yBodyRot = bodyRot; p.yBodyRotO = bodyRotO; p.setYRot(yRot); p.yRotO = yRotO;
            p.setXRot(xRot); p.xRotO = xRotO; p.yHeadRot = head; p.yHeadRotO = headO;
        }
    }

    // -----------------------------------------------------------------------
    // Bedienung
    // -----------------------------------------------------------------------

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent click, boolean doubled) {
        if (super.mouseClicked(click, doubled)) return true;
        if (click.button() != 0) return false;
        for (int i = flaechen.size() - 1; i >= 0; i--) {
            if (flaechen.get(i).in(click.x(), click.y())) { flaechen.get(i).aktion().run(); return true; }
        }
        int px = fx + fw - 150 - 12;
        zieht = click.x() >= px && click.x() < px + 150 && click.y() >= fy + 40 && click.y() < fy + fh - 12;
        return zieht;
    }

    @Override
    public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent event, double dx, double dy) {
        if (!zieht) return super.mouseDragged(event, dx, dy);
        drehung = (drehung + (float) dx * 2.2f) % 360f;
        neigung = Math.max(-30f, Math.min(30f, neigung - (float) dy * 1.2f));
        return true;
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event) {
        zieht = false;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        scroll -= (float) vertical * 24f;
        return true;
    }

    /** Vorschau auf einen Winkel drehen (Grad; 160 = schraeg von vorne). Auch fuer den Cosmetics-Test. */
    public void vorschauDrehen(float grad, float neigen) {
        drehung = grad;
        neigung = neigen;
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(parent);
    }
}
