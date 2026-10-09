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
 * Das Cosmetics-Menue: Capes, Huete, Bandanas, Face, Back, Auras, Shields, Emotes -- mit drehbarer 3D-Vorschau
 * des eigenen Charakters (ziehen mit der Maus).
 *
 * Eine Auswahl gilt sofort und wird gespeichert (Cosmetics). Capes: aus dem
 * Vortex-Verzeichnis oder ein eigenes Bild (EigenesCape).
 */
public class CosmeticsScreen extends Screen {
    private enum Reiter { CAPES, HATS, BANDANAS, FACE, BACK, AURAS, SHIELDS, EMOTES }

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
        fw = Math.min(this.width - 24, 640);
        fh = Math.min(this.height - 24, 360);
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
                for (CapeKunst.Design d : CapeKunst.alle().values()) k.add(new Kachel(d.id(), d.name(), d.text()));
                if (katalog == null) k.add(new Kachel("#laedt", "Loading...", "Vortex capes"));
                else for (String[] c : katalog) k.add(new Kachel(c[0], c[1], "Vortex cape"));
                if (EigenesCape.vorhanden()) k.add(new Kachel(EigenesCape.ID, "Your picture", "Custom cape"));
                k.add(new Kachel("#upload", EigenesCape.vorhanden() ? "Change picture..." : "Upload picture...", "PNG or JPG on the back"));
            }
            case HATS -> {
                k.add(new Kachel("", "None", "No hat"));
                for (Huete.Hut h : Huete.alle().values()) k.add(new Kachel(h.id(), h.name(), h.text()));
            }
            case BANDANAS, FACE, BACK, AURAS, SHIELDS -> {
                Zubehoer.Kategorie kat = kategorie(reiter);
                k.add(new Kachel("", "None", "Nothing"));
                for (Zubehoer.Design d : Zubehoer.von(kat)) k.add(new Kachel(d.id(), d.name(), ""));
            }
            case EMOTES -> {
                for (Emotes.Emote e : Emotes.alle().values()) k.add(new Kachel(e.id(), e.name(), "Click to play -- or press " + emoteTaste()));
            }
        }
        return k;
    }

    private String gewaehlt() {
        Cosmetics.Auswahl a = Cosmetics.eigene();
        return switch (reiter) {
            case CAPES -> a.cape();
            case HATS -> a.hut();
            case BANDANAS -> a.bandana();
            case FACE -> a.gesicht();
            case BACK -> a.ruecken();
            case AURAS -> a.partikel();
            case SHIELDS -> a.schild();
            case EMOTES -> "";
        };
    }

    private static String emoteTaste() {
        return com.vortex.client.VortexClientMod.emoteKeyName();
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
                Cosmetics.speichern(b.mitCape(EigenesCape.ID), Cosmetics.partikelErstePerson());
                ActiveCape.neuLaden();
            }));
            return;
        }
        meldung = null;
        if (reiter == Reiter.EMOTES) { Emotes.spielen(id); return; }
        Cosmetics.Auswahl neu = switch (reiter) {
            case CAPES -> a.mitCape(id);
            case HATS -> a.mitHut(id);
            case BANDANAS -> a.mitBandana(id);
            case FACE -> a.mitGesicht(id);
            case BACK -> a.mitRuecken(id);
            case AURAS -> a.mitAura(id);
            case SHIELDS -> a.mitSchild(id);
            case EMOTES -> a;
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
            String name = switch (r) {
                case CAPES -> "Capes"; case HATS -> "Hats"; case BANDANAS -> "Bandanas"; case FACE -> "Face";
                case BACK -> "Back"; case AURAS -> "Auras"; case SHIELDS -> "Shields"; case EMOTES -> "Emotes";
            };
            Symbole.Symbol sym = switch (r) {
                case CAPES -> Symbole.Symbol.HEMD; case HATS, BANDANAS -> Symbole.Symbol.STERN; case FACE -> Symbole.Symbol.PERSON;
                case BACK -> Symbole.Symbol.HEMD; case AURAS -> Symbole.Symbol.BLITZ; case SHIELDS -> Symbole.Symbol.HAKEN; case EMOTES -> Symbole.Symbol.PERSON;
            };
            int rh = Math.max(16, Math.min(22, (fh - 52) / Reiter.values().length - 3)), rabstand = rh + 3;
            boolean an = r == reiter, hov = !an && mouseX >= fx + 10 && mouseX < fx + 100 && mouseY >= ry && mouseY < ry + rh;
            if (an || hov) Glatt.rund(g, fx + 10, ry, 90, rh, 6, an ? 0x338B5CF6 : 0x18FFFFFF);
            if (an) Glatt.rund(g, fx + 10, ry + (rh - 12) / 2f, 2.5f, 12, 1.2f, AKZENT);
            Glatt.symbol(g, sym, fx + 18, ry + (rh - 12) / 2f, 12, an ? TEXT : DIM);
            Glatt.text(g, name, fx + 36, ry + (rh - 8) / 2f, an ? TEXT : DIM, Glatt.Schrift.NORMAL);
            final Reiter ziel = r;
            flaechen.add(new Flaeche(fx + 10, ry, 90, rh, () -> { reiter = ziel; scroll = 0; meldung = null; }));
            ry += rabstand;
        }

        // Vorschau rechts
        int pw = 150, px = fx + fw - pw - 12, py = fy + 40, ph = fh - 52;
        Glatt.rund(g, px, py, pw, ph, 8, 0xF0120E1B);
        vorschau(g, px, py, pw, ph);

        // Kacheln in der Mitte
        int lx = fx + 110, lw = px - lx - 10, ly = fy + 40, lh = fh - 52;
        if (reiter == Reiter.AURAS) {
            partikelOptionen(g, lx, ly, lw, mouseX, mouseY);
            ly += 26; lh -= 26;
        } else if (reiter == Reiter.CAPES) {
            boolean an = Cosmetics.capePhysik();
            knopf(g, lx, ly, Math.min(170, lw), 18, "Cape physics: " + (an ? "On" : "Off"), mouseX, mouseY,
                    () -> Cosmetics.setzeCapePhysik(!Cosmetics.capePhysik()), an);
            ly += 26; lh -= 26;
        }
        if (reiter == Reiter.CAPES) {
            capeKacheln(g, lx, ly, lw, lh, mouseX, mouseY);
        } else if (kategorie(reiter) != null) {
            galerie(g, lx, ly, lw, lh, mouseX, mouseY, kategorie(reiter));
        } else {
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
        }

        if (meldung != null) Glatt.textMitte(g, meldung, lx + lw / 2f, fy + fh - 11, DIM, Glatt.Schrift.NORMAL);
        super.extractRenderState(g, mouseX, mouseY, delta);
    }

    /**
     * Capes als Galerie: jede Karte zeigt das Cape selbst -- animierte Capes
     * laufen live (gleiche Texturen wie am Spieler, inklusive Leuchten).
     */
    private void capeKacheln(GuiGraphicsExtractor g, int lx, int ly, int lw, int lh, int mouseX, int mouseY) {
        List<Kachel> liste = kacheln();
        int spalten = Math.max(2, Math.min(5, lw / 74));
        int kw = (lw - (spalten - 1) * 6) / spalten;
        int tw = Math.min(44, kw - 20), th = Math.round(tw * 1.6f), kh = th + 34;
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
            Glatt.rund(g, kx, ky, kw, kh, 8, hov ? KARTE_HOV : KARTE);
            int bx = kx + (kw - tw) / 2, by = ky + 7;
            CapeKunst.Design d = CapeKunst.design(k.id());
            if (d != null) {
                Glatt.schatten(g, bx, by, tw, th, 4, 6, 0x70000000);
                Glatt.schatten(g, bx, by, tw, th, 4, 10, 0x38000000 | (d.akzent() & 0xFFFFFF));
                var basis = AnimCapes.basis(d.id());
                var glow = AnimCapes.glow(d.id());
                if (basis != null) {
                    g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, basis, bx, by, CapeKunst.S, CapeKunst.S, tw, th, 10 * CapeKunst.S, 16 * CapeKunst.S, CapeKunst.W, CapeKunst.H, 0xFFFFFFFF);
                    if (glow != null) g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, glow, bx, by, CapeKunst.S, CapeKunst.S, tw, th, 10 * CapeKunst.S, 16 * CapeKunst.S, CapeKunst.W, CapeKunst.H, 0xFFFFFFFF);
                }
            } else {
                Glatt.rund(g, bx, by, tw, th, 4, 0xFF1B1726);
                Symbole.Symbol sym = k.id().equals("#upload") ? Symbole.Symbol.PLUS : k.id().isEmpty() ? Symbole.Symbol.KREUZ : Symbole.Symbol.HEMD;
                Glatt.symbol(g, sym, bx + tw / 2f - 8, by + th / 2f - 8, 16, k.id().isEmpty() ? DIM : AKZENT);
            }
            Glatt.textMitte(g, Glatt.kuerzen(k.name(), kw - 8, Glatt.Schrift.FETT), kx + kw / 2f, by + th + 7, an ? TEXT : 0xFFD9D4E8, Glatt.Schrift.FETT);
            Glatt.textMitte(g, Glatt.kuerzen(d != null ? "Animated" : k.unter(), kw - 8, Glatt.Schrift.NORMAL), kx + kw / 2f, by + th + 17, d != null ? 0xFFB79CFF : DIM, Glatt.Schrift.NORMAL);
            if (an) {
                Glatt.rahmen(g, kx, ky, kw, kh, 8, 1, AKZENT);
                Glatt.kreis(g, kx + kw - 9, ky + 9, 12, AKZENT);
                Glatt.symbol(g, Symbole.Symbol.HAKEN, kx + kw - 14, ky + 4, 10, 0xFFFFFFFF);
            }
            if (ky >= ly && ky + kh <= ly + lh) flaechen.add(new Flaeche(kx, ky, kw, kh, () -> waehle(k.id())));
        }
        g.disableScissor();
    }

    private void partikelOptionen(GuiGraphicsExtractor g, int x, int y, int w, int mouseX, int mouseY) {
        Cosmetics.Auswahl a = Cosmetics.eigene();
        String dichte = "Amount: " + (a.dichte() == 1 ? "Low" : a.dichte() == 3 ? "High" : "Medium");
        knopf(g, x, y, 120, 18, dichte, mouseX, mouseY, () -> {
            Cosmetics.Auswahl b = Cosmetics.eigene();
            Cosmetics.speichern(b.mitDichte(b.dichte() % 3 + 1), Cosmetics.partikelErstePerson());
        }, false);
    }

    private static Zubehoer.Kategorie kategorie(Reiter r) {
        return switch (r) {
            case BANDANAS -> Zubehoer.Kategorie.BANDANA;
            case FACE -> Zubehoer.Kategorie.FACE;
            case BACK -> Zubehoer.Kategorie.BACK;
            case AURAS -> Zubehoer.Kategorie.AURA;
            case SHIELDS -> Zubehoer.Kategorie.SHIELD;
            default -> null;
        };
    }

    /** Galerie mit Bild je Karte: das Pixelbild des Designs (Schilde: ihre Textur). */
    private void galerie(GuiGraphicsExtractor g, int lx, int ly, int lw, int lh, int mouseX, int mouseY, Zubehoer.Kategorie kat) {
        List<Kachel> liste = kacheln();
        int spalten = Math.max(2, Math.min(5, lw / 76));
        int kw = (lw - (spalten - 1) * 6) / spalten;
        int bild = Math.min(46, kw - 16), kh = bild + 30;
        int zeilen = (liste.size() + spalten - 1) / spalten;
        float max = Math.max(0, zeilen * (kh + 6) - 6 - lh);
        scroll = Math.max(0, Math.min(scroll, max));
        String sel = gewaehlt();
        g.enableScissor(lx, ly, lx + lw, ly + lh);
        for (int i = 0; i < liste.size(); i++) {
            Kachel k = liste.get(i);
            int kx = lx + (i % spalten) * (kw + 6), ky = ly + (i / spalten) * (kh + 6) - Math.round(scroll);
            if (ky + kh < ly || ky > ly + lh) continue;
            boolean an = k.id().equals(sel);
            boolean hov = mouseX >= kx && mouseX < kx + kw && mouseY >= ky && mouseY < ky + kh && mouseY >= ly && mouseY < ly + lh;
            Glatt.rund(g, kx, ky, kw, kh, 8, hov ? KARTE_HOV : KARTE);
            int bx = kx + (kw - bild) / 2, by = ky + 6;
            Zubehoer.Design d = Zubehoer.get(kat, k.id());
            // heller Grund, damit dunkle Teile (schwarze Brillen, Masken) sichtbar sind
            if (d != null) Glatt.rund(g, bx - 3, by - 3, bild + 6, bild + 6, 6, hov ? 0xFF56506E : 0xFF48435E);
            if (d == null) {
                Glatt.symbol(g, Symbole.Symbol.KREUZ, bx + bild / 2f - 8, by + bild / 2f - 8, 16, DIM);
            } else if (kat == Zubehoer.Kategorie.SHIELD) {
                int sh = bild, sw = Math.round(sh * 24f / 44f);
                g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,
                        net.minecraft.resources.Identifier.fromNamespaceAndPath("vortexclient", "textures/cosmetics/shield/" + d.id() + ".png"),
                        bx + (bild - sw) / 2, by, 2, 2, sw, sh, 24, 44, 128, 128, 0xFFFFFFFF);
            } else {
                pixelBild(g, vorschauBild(d), bx, by, bild, bild);
            }
            Glatt.textMitte(g, Glatt.kuerzen(k.name(), kw - 8, Glatt.Schrift.FETT), kx + kw / 2f, by + bild + 6, an ? TEXT : 0xFFD9D4E8, Glatt.Schrift.FETT);
            if (an) {
                Glatt.rahmen(g, kx, ky, kw, kh, 8, 1, AKZENT);
                Glatt.kreis(g, kx + kw - 9, ky + 9, 12, AKZENT);
                Glatt.symbol(g, Symbole.Symbol.HAKEN, kx + kw - 14, ky + 4, 10, 0xFFFFFFFF);
            }
            if (ky >= ly && ky + kh <= ly + lh) flaechen.add(new Flaeche(kx, ky, kw, kh, () -> waehle(k.id())));
        }
        g.disableScissor();
    }

    /** Bild fuer die Karte: Bandana = Band vorne, Aura = erstes Sprite, sonst das groesste Teil. */
    private static int[][] vorschauBild(Zubehoer.Design d) {
        if (d.ring() != null && d.teile().isEmpty()) {
            int[][] r = d.ring().farben();
            // vordere Seite (ein Viertel der Spalten), Zeilen doppelt, damit das Band nicht zu duenn wirkt
            int[][] o = new int[r.length * 2][];
            for (int y = 0; y < r.length; y++) {
                o[y * 2] = java.util.Arrays.copyOfRange(r[y], 0, Math.max(1, r[y].length / 4));
                o[y * 2 + 1] = o[y * 2];
            }
            return o;
        }
        if (d.aura() != null && !d.aura().sprites().isEmpty()) return d.aura().sprites().get(0).farben();
        int[][] best = new int[0][];
        int groesse = -1;
        for (Zubehoer.Teil t : d.teile()) {
            int n = 0;
            for (int[] z : t.farben()) n += z.length;
            if (n > groesse) { groesse = n; best = t.farben(); }
        }
        return best;
    }

    private static void pixelBild(GuiGraphicsExtractor g, int[][] bild, int x, int y, int w, int h) {
        int bh = bild.length, bw = 0;
        for (int[] z : bild) bw = Math.max(bw, z.length);
        if (bh == 0 || bw == 0) return;
        float s = Math.min(w / (float) bw, h / (float) bh);
        float ox = x + (w - bw * s) / 2f, oy = y + (h - bh * s) / 2f;
        for (int yy = 0; yy < bh; yy++) {
            for (int xx = 0; xx < bild[yy].length; xx++) {
                int f = bild[yy][xx];
                if ((f >>> 24) == 0) continue;
                int x0 = Math.round(ox + xx * s), y0 = Math.round(oy + yy * s), x1 = Math.round(ox + (xx + 1) * s), y1 = Math.round(oy + (yy + 1) * s);
                if (x1 > x0 && y1 > y0) g.fill(x0, y0, x1, y1, f);
            }
        }
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
        CapeKunst.Design gross = CapeKunst.design(Cosmetics.eigene().cape());
        if (p == null && gross != null) {
            // Ohne Welt: das gewaehlte animierte Cape gross
            int th = Math.min(h - 40, Math.round((w - 40) * 1.6f)), tw = Math.round(th / 1.6f);
            int bx = x + (w - tw) / 2, by = y + 12;
            Glatt.schatten(g, bx, by, tw, th, 6, 14, 0x50000000 | (gross.akzent() & 0xFFFFFF));
            var basis = AnimCapes.basis(gross.id());
            var glow = AnimCapes.glow(gross.id());
            if (basis != null) {
                g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, basis, bx, by, CapeKunst.S, CapeKunst.S, tw, th, 10 * CapeKunst.S, 16 * CapeKunst.S, CapeKunst.W, CapeKunst.H, 0xFFFFFFFF);
                if (glow != null) g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, glow, bx, by, CapeKunst.S, CapeKunst.S, tw, th, 10 * CapeKunst.S, 16 * CapeKunst.S, CapeKunst.W, CapeKunst.H, 0xFFFFFFFF);
            }
            Glatt.textMitte(g, gross.name(), x + w / 2f, by + th + 8, TEXT, Glatt.Schrift.FETT);
            return;
        }
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

    /** Reiter waehlen (0 = Capes ... 7 = Emotes). Auch fuer den Cosmetics-Test. */
    public void zeigeReiter(int i) {
        reiter = Reiter.values()[Math.max(0, Math.min(Reiter.values().length - 1, i))];
        scroll = 0;
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(parent);
    }
}
