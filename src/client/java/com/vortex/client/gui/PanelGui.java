package com.vortex.client.gui;

import com.vortex.client.core.setting.BooleanSetting;
import com.vortex.client.core.setting.ColorSetting;
import com.vortex.client.core.setting.KeySetting;
import com.vortex.client.core.setting.ModeSetting;
import com.vortex.client.core.setting.NumberSetting;
import com.vortex.client.core.setting.Setting;
import com.vortex.client.module.Module;
import com.vortex.client.module.ModuleManager;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Klassisches Spaltenmenue.
 *
 * Eine Spalte je Kategorie, der Name oben, die Module darunter -- so wie bei
 * den grossen Clients. Alles auf einen Blick, kein Umschalten zwischen
 * Seiten, kein Detailfeld.
 *
 * BEDIENUNG
 *   Linksklick auf ein Modul     ein- und ausschalten
 *   Rechtsklick auf ein Modul    Einstellungen darunter auf- und zuklappen
 *   Mausrad ueber einer Spalte   diese Spalte scrollen
 *   Suchfeld oben                filtert alle Spalten gleichzeitig
 *   Klick auf einen Spaltenkopf  Kategorie ein- und ausklappen
 *
 * Die Einstellungen werden ueber genau dieselben Aufrufe geaendert wie im
 * bisherigen Menue (ClickGui) -- dort sind sie erprobt. Gespeichert wird beim
 * Schliessen, ebenfalls wie dort.
 */
public class PanelGui extends Screen {

    // --- Masse ------------------------------------------------------------
    private static final int SPALTE_B = 118;   // Breite einer Spalte
    private static final int SPALTE_MIN = 92;  // schmaler wird keine Spalte
    private static final int KOPF_H   = 20;    // Kopf mit Kategorienamen
    private static final int ZEILE_H  = 16;    // eine Modulzeile
    private static final int EINST_H  = 14;    // eine Einstellungszeile
    private static final int INNEN_RAND = 4;   // Luft oben/unten im Einstellungsfeld
    private static final int AUSWAHL_H = 15;   // Knopf fuer die Auswahlliste
    private static final int ABSTAND  = 8;     // zwischen den Spalten
    private static final int OBEN     = 34;    // Platz fuer das Suchfeld

    /** Nur diese Kategorie zeigen (fuer die Bots-Kachel), sonst null. */
    private final Module.Category nur;

    private EditBox search;
    private int mx, my;
    private long letzteZeit = 0;
    private float oeffnen = 0f;
    /** Sekunden seit dem Oeffnen -- fuer das gestaffelte Erscheinen. */
    private float seit = 0f;

    /** Aufgeklappte Module und ihr Klappzustand 0..1. */
    private final Map<Module, Float> klapp = new HashMap<>();
    private final Map<Module, Boolean> offen = new HashMap<>();
    /** Hervorhebung je Modul beim Ueberfahren, 0..1. */
    private final Map<Module, Float> hover = new HashMap<>();
    /** Ein/Aus je Modul, gleitend 0..1. */
    private final Map<Module, Float> anAnim = new HashMap<>();
    /** Gleitende Werte je Einstellung (Schalter, Schieberegler). */
    private final Map<Setting, Float> einstAnim = new HashMap<>();
    /** Zeit des letzten Bildes, fuer Animationen in Hilfsmethoden. */
    private float letzteDt = 0.016f;
    /** Bildlauf je Spalte. */
    private final Map<Module.Category, float[]> lauf = new HashMap<>();
    /**
     * Eingeklappte Kategorien. Beim Oeffnen des Menues ist alles aufgeklappt;
     * der Zustand bleibt erhalten, solange das Menue offen ist (auch beim
     * Zurueckkommen aus Farbwaehler oder Auswahlliste).
     */
    private final Map<Module.Category, Boolean> katZu = new HashMap<>();
    /** Klappzustand je Kategorie, gleitend: 1 = offen, 0 = zu. */
    private final Map<Module.Category, Float> katAnim = new HashMap<>();
    /** Hervorhebung des Kopfes beim Ueberfahren, 0..1. */
    private final Map<Module.Category, Float> kopfHover = new HashMap<>();

    // Schieberegler, der gerade gezogen wird
    /** Tatsaechliche Spaltenbreite dieses Bildes (<= SPALTE_B). */
    private int spalteB = SPALTE_B;
    /** Unterkante der aktuellen Spalte (Ende ihrer Reihe). */
    private int spalteBoden = 0;

    private NumberSetting zieht = null;
    private int ziehX, ziehB;

    // --- Klickflaechen ------------------------------------------------------
    private enum Art { KOPF, MODUL, BOOL, NUM, MODUS, FARBE, TASTE, AUSWAHL }

    private static final class Treffer {
        final int x, y, w, h;
        final Art art;
        final Module modul;
        final Setting einst;
        Module.Category kat;
        Treffer(int x, int y, int w, int h, Art art, Module m, Setting s) {
            this.x = x; this.y = y; this.w = w; this.h = h;
            this.art = art; this.modul = m; this.einst = s;
        }
        boolean in(double px, double py) {
            return px >= x && px < x + w && py >= y && py < y + h;
        }
    }
    private final List<Treffer> treffer = new ArrayList<>();
    /** Spaltenbereiche fuer das Mausrad. */
    private final List<int[]> spaltenFlaeche = new ArrayList<>();
    private final List<Module.Category> spaltenKat = new ArrayList<>();

    public PanelGui() { this(null); }

    public PanelGui(Module.Category nur) {
        super(Component.literal("Vortex Client"));
        this.nur = nur;
    }

    @Override
    protected void init() {
        // init() laeuft erneut, wenn man aus einer Auswahlliste zurueckkommt
        // oder das Fenster in der Groesse aendert. Der Suchbegriff wird dabei
        // uebernommen -- sonst waere er nach jedem Zurueckkommen weg.
        String bisher = (this.search != null) ? this.search.getValue() : "";
        int sw = 180;
        this.search = new EditBox(this.font, this.width / 2 - sw / 2 + 8, 11, sw - 16, 12,
                Component.literal(""));
        this.search.setBordered(false);
        this.search.setMaxLength(32);
        this.search.setValue(bisher);
        // Beim Tippen alle Spalten an den Anfang -- sonst liegen die Treffer
        // oberhalb des sichtbaren Bereichs.
        this.search.setResponder(text -> lauf.clear());
        this.addRenderableWidget(this.search);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ======================================================================
    // Zeichnen
    // ======================================================================

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        this.mx = mouseX;
        this.my = mouseY;
        long jetzt = System.nanoTime();
        float dt = letzteZeit == 0 ? 0.016f : Math.min(0.1f, (jetzt - letzteZeit) / 1e9f);
        letzteZeit = jetzt;
        oeffnen = weich(oeffnen, 1f, 10f, dt);
        seit += dt;
        letzteDt = dt;
        float a = oeffnen;

        treffer.clear();
        spaltenFlaeche.clear();
        spaltenKat.clear();
        tasteAufnehmen();

        // Leicht abdunkeln -- das Spiel soll dahinter sichtbar bleiben, das
        // ist der Charakter dieser Menueform.
        ctx.fill(0, 0, this.width, this.height, VortexStyle.fade(0x66000000, a));

        // Vignette: oben und unten dunkler, zur Mitte hin auslaufend. Lenkt
        // den Blick zu den Spalten, ohne das Spiel ganz zu verdecken.
        // Zwoelf Baender je Rand -- guenstig und fuer das Auge stufenlos.
        int rand = Math.max(40, this.height / 4);
        for (int i = 0; i < 12; i++) {
            float t = 1f - i / 12f;
            int al = (int) (70 * t * t * a);
            int h1 = rand * i / 12, h2 = rand * (i + 1) / 12;
            ctx.fill(0, h1, this.width, h2, al << 24);
            ctx.fill(0, this.height - h2, this.width, this.height - h1, al << 24);
        }

        zeichneSuche(ctx, a);

        // Spalten sammeln
        List<Module.Category> kats = new ArrayList<>();
        for (Module.Category c : Module.Category.values()) {
            if (nur != null && c != nur) continue;
            if (nur == null && c == Module.Category.BOTS) continue;   // Bots: eigene Kachel am Start
            if (!module(c).isEmpty()) kats.add(c);
        }

        // --- PASST SICH DER FENSTERGROESSE AN ------------------------------
        //
        // Vorher: immer eine Reihe, Spalten hoechstens bis 80 Pixel schmal.
        // Bei kleinem Fenster (Minecraft verkleinert die Oberflaeche bis auf
        // 320 x 240) brauchten fuenf Spalten aber 432 Pixel -- die rechten
        // hingen aus dem Bild.
        //
        // Jetzt: so viele Spalten pro Reihe, wie in Mindestbreite passen.
        // Reichen die nicht fuer alle, gibt es eine zweite Reihe, und jede
        // Reihe bekommt ihren Anteil der Hoehe. Den Rest erledigt der
        // Bildlauf jeder Spalte.
        int n = Math.max(1, kats.size());
        int verfuegbar = this.width - 12;
        int proReihe = Math.max(1, Math.min(n, (verfuegbar + ABSTAND) / (SPALTE_MIN + ABSTAND)));
        int reihen = (n + proReihe - 1) / proReihe;
        spalteB = Math.min(SPALTE_B, (verfuegbar - (proReihe - 1) * ABSTAND) / proReihe);
        int reiheB = proReihe * spalteB + (proReihe - 1) * ABSTAND;
        int x0 = Math.max(6, (this.width - reiheB) / 2);
        int reiheH = (this.height - OBEN - 6) / reihen;

        // GESTAFFELTES ERSCHEINEN.
        //
        // Jede Spalte startet 45 Millisekunden nach der vorigen und gleitet
        // von oben herein. Die Kurve ist ein weiches Auslaufen (1 - (1-t)^3):
        // schnell am Anfang, sanft am Ende.
        for (int i = 0; i < kats.size(); i++) {
            float t = Math.max(0f, Math.min(1f, (seit - i * 0.045f) / 0.32f));
            float e = 1f - (1f - t) * (1f - t) * (1f - t);
            int sx = x0 + (i % proReihe) * (spalteB + ABSTAND);
            int sy = OBEN + (i / proReihe) * reiheH;
            int gleiten = (int) ((1f - e) * -16f);
            // Hoehenbegrenzung: bis zur naechsten Reihe, nicht bis zum Rand
            spalteBoden = sy + reiheH - 6;
            zeichneSpalte(ctx, kats.get(i), sx, sy + gleiten, a * e, dt);
        }

        super.extractRenderState(ctx, mouseX, mouseY, delta);
    }

    private void zeichneSuche(GuiGraphicsExtractor ctx, float a) {
        int sw = 180, sx = this.width / 2 - sw / 2, sy = 7;
        boolean tippt = search != null && !search.getValue().isEmpty();
        roundRect(ctx, sx, sy, sw, 18, VortexStyle.fade(VortexStyle.CARD, a));
        if (tippt) {
            ctx.fill(sx + 3, sy + 17, sx + sw - 3, sy + 18, VortexStyle.fade(VortexStyle.akzent(0.5f), a));
        }
        if (search != null && search.getValue().isEmpty()) {
            ctx.text(this.font, Component.literal("Search..."), sx + 8, sy + 5,
                    VortexStyle.fade(VortexStyle.TEXT_DIM, a), false);
        }
    }

    private void zeichneSpalte(GuiGraphicsExtractor ctx, Module.Category kat,
                               int x, int y, float a, float dt) {
        List<Module> liste = gefiltert(kat);

        // --- Kopf ----------------------------------------------------------
        int an = 0;
        for (Module m : module(kat)) if (m.isEnabled()) an++;
        // Kopf: dunkle Flaeche mit einem Hauch Violett links, nach rechts
        // auslaufend. Hebt ihn vom Inhalt ab, ohne einen harten Rand.
        // EIN- UND AUSKLAPPEN.
        //
        // Klick auf den Kopf klappt die Kategorie zu oder auf. Der Inhalt
        // faehrt dabei weich ein und aus (gleiche Kurve wie ueberall), der
        // Pfeil rechts blendet von "unten" nach "rechts" ueber.
        // Waehrend der Suche ist alles offen -- sonst laegen Treffer
        // unsichtbar in einer eingeklappten Kategorie. Nach dem Leeren des
        // Suchfelds gilt wieder der eigene Zustand.
        boolean sucht = search != null && !search.getValue().trim().isEmpty();
        boolean zu = katZu.getOrDefault(kat, false) && !sucht;
        float ka = weich(katAnim.getOrDefault(kat, zu ? 0f : 1f), zu ? 0f : 1f, 13f, dt);
        katAnim.put(kat, ka);
        boolean kopfHov = mx >= x && mx < x + spalteB && my >= y && my < y + KOPF_H;
        float kh = weich(kopfHover.getOrDefault(kat, 0f), kopfHov ? 1f : 0f, 18f, dt);
        kopfHover.put(kat, kh);

        roundRect(ctx, x, y, spalteB, KOPF_H, VortexStyle.fade(
                VortexStyle.mix(VortexStyle.BAR, VortexStyle.HOV, kh * 0.8f), a));
        verlaufBand(ctx, x + 2, y + 1, spalteB - 4, KOPF_H - 3,
                VortexStyle.fade(VortexStyle.VIOLETT, a * (0.16f + 0.10f * kh)),
                VortexStyle.fade(VortexStyle.BLAU, 0));
        // Akzentlinie unter dem Namen: das Erkennungszeichen jeder Spalte.
        // Eingeklappt wird sie schwaecher -- die Spalte "ruht".
        VortexStyle.akzentLinie(ctx, x + 3, y + KOPF_H - 2, spalteB - 6, a * (0.45f + 0.55f * ka));
        ctx.text(this.font, Component.literal(schoen(kat.name())), x + 8, y + 6,
                VortexStyle.fade(VortexStyle.TEXT, a), false);
        // Pfeil ganz rechts, die Zahl links daneben
        pfeil(ctx, x + spalteB - 12, y + 7, ka,
                VortexStyle.fade(VortexStyle.mix(VortexStyle.TEXT_DIM, VortexStyle.TEXT, kh), a));
        // Die Zahl nur, wenn sie neben den Namen passt -- bei kleinem Fenster
        // sind die Spalten schmal, und sie soll nie ueber dem Namen liegen.
        String zahl = an + "/" + module(kat).size();
        int zahlX = x + spalteB - 17 - this.font.width(zahl);
        if (zahlX > x + 8 + this.font.width(schoen(kat.name())) + 4) {
            ctx.text(this.font, Component.literal(zahl), zahlX,
                    y + 6, VortexStyle.fade(VortexStyle.TEXT_DIM, a), false);
        }
        Treffer kopf = new Treffer(x, y, spalteB, KOPF_H, Art.KOPF, null, null);
        kopf.kat = kat;
        treffer.add(kopf);

        // --- Inhalt ----------------------------------------------------------
        int maxH = Math.max(ZEILE_H, spalteBoden - y - KOPF_H);
        int inhaltH = inhaltHoehe(liste);
        int sichtbar = Math.min(inhaltH, maxH);
        float[] l = lauf.computeIfAbsent(kat, k -> new float[2]);   // [ist, ziel]
        float maxLauf = Math.max(0, inhaltH - sichtbar);
        if (l[1] > maxLauf) l[1] = maxLauf;
        l[0] = weich(l[0], l[1], 16f, dt);

        int top = y + KOPF_H;
        // Koerperhoehe folgt dem Klappzustand. Der Inhalt wird nicht
        // gestaucht, sondern von unten abgeschnitten -- er faehrt wie ein
        // Rollo ein und aus.
        int koerper = (int) ((Math.max(4, sichtbar + 2)) * ka);
        if (koerper < 2) {
            // Ganz eingeklappt: nur der Kopf, mit Schatten.
            VortexStyle.schatten(ctx, x, y, spalteB, KOPF_H, a * 0.9f);
            spaltenFlaeche.add(new int[]{x, y, spalteB, KOPF_H});
            spaltenKat.add(kat);
            return;
        }
        // Weicher Schatten um die ganze Spalte -- sie schwebt ueber dem Spiel.
        VortexStyle.schatten(ctx, x, y, spalteB, KOPF_H + koerper, a * 0.9f);
        roundRect(ctx, x, top, spalteB, koerper,
                VortexStyle.fade(VortexStyle.WINDOW, a * 0.94f));
        // Lichtkante direkt unter dem Kopf: trennt ihn vom Inhalt wie eine
        // Kante, an die Licht faellt.
        ctx.fill(x + 3, top, x + spalteB - 3, top + 1,
                VortexStyle.fade(VortexStyle.mix(VortexStyle.WINDOW, VortexStyle.TEXT, 0.06f), a));
        spaltenFlaeche.add(new int[]{x, y, spalteB, KOPF_H + koerper});
        spaltenKat.add(kat);

        // Beim Klappen blendet der Inhalt mit -- sonst wirkt das Abschneiden hart.
        float ia = a * Math.min(1f, ka * 1.4f);
        ctx.enableScissor(x, top, x + spalteB, top + koerper);
        int cy = top + 1 - (int) l[0];
        for (Module m : liste) {
            cy = zeichneModul(ctx, m, x, cy, top, top + koerper, ia, dt);
        }
        ctx.disableScissor();

        // Duenner Bildlaufbalken rechts -- nur, wenn es mehr gibt als passt.
        // Er zeigt, dass man scrollen kann, und wo man gerade ist.
        if (ka > 0.98f && inhaltH > sichtbar && sichtbar > 20) {
            int spur = sichtbar - 6;
            int balken = Math.max(14, (int) (spur * (sichtbar / (float) inhaltH)));
            float pos = maxLauf <= 0 ? 0f : l[0] / maxLauf;
            int by = top + 3 + (int) ((spur - balken) * Math.max(0f, Math.min(1f, pos)));
            ctx.fill(x + spalteB - 3, by, x + spalteB - 1, by + balken,
                    VortexStyle.fade(VortexStyle.mix(VortexStyle.akzent(0.5f), VortexStyle.TEXT, 0.2f), a * 0.7f));
        }

        if (liste.isEmpty()) {
            ctx.text(this.font, Component.literal("-"), x + 8, top + 4,
                    VortexStyle.fade(VortexStyle.TEXT_DIM, a), false);
        }
    }

    private int zeichneModul(GuiGraphicsExtractor ctx, Module m, int x, int y,
                             int clipOben, int clipUnten, float a, float dt) {
        boolean imBild = y + ZEILE_H > clipOben && y < clipUnten;
        boolean hov = imBild && mx >= x && mx < x + spalteB && my >= Math.max(y, clipOben)
                && my < Math.min(y + ZEILE_H, clipUnten);
        float hv = weich(hover.getOrDefault(m, 0f), hov ? 1f : 0f, 18f, dt);
        hover.put(m, hv);

        if (imBild) {
            // EIN/AUS GLEITET.
            //
            // Vorher sprang die Farbe beim Umschalten sofort um. Jetzt hat
            // jedes Modul einen eigenen Wert zwischen 0 und 1, der dem
            // Zustand folgt: die Zeile fuellt sich mit dem Akzentverlauf, der
            // Balken links waechst heraus, der Text hellt auf.
            float an = weich(anAnim.getOrDefault(m, m.isEnabled() ? 1f : 0f),
                    m.isEnabled() ? 1f : 0f, 12f, dt);
            anAnim.put(m, an);

            // Ist das Modul aufgeklappt, bekommt seine Zeile dieselbe dunkle
            // Flaeche wie das Feld darunter. Zeile und Einstellungen lesen
            // sich dadurch als EIN Block -- vorher sah das Feld aus, als
            // gehoere es zu keinem Modul.
            float offenAnim = klapp.getOrDefault(m, 0f);
            if (offenAnim > 0.01f) {
                ctx.fill(x + 3, y, x + spalteB - 3, y + ZEILE_H,
                        VortexStyle.fade(VortexStyle.BAR, a * offenAnim));
                ctx.fill(x + 3, y, x + 4, y + ZEILE_H,
                        VortexStyle.fade(VortexStyle.akzent(0.4f), a * 0.8f * offenAnim));
            }

            // Ueberfahren: eine zarte Flaeche, auch bei eingeschalteten
            if (hv > 0.01f) {
                ctx.fill(x + 2, y, x + spalteB - 2, y + ZEILE_H,
                        VortexStyle.fade(VortexStyle.HOV, a * hv * (1f - an * 0.5f)));
            }
            if (an > 0.01f) {
                // Verlauf Violett -> Blau, nach rechts auslaufend. In Baendern
                // gezeichnet, nicht pixelweise -- das hatte im alten Menue
                // tausende Aufrufe je Bild gekostet.
                verlaufBand(ctx, x + 2, y, spalteB - 4, ZEILE_H,
                        VortexStyle.fade(VortexStyle.akzent(0f), a * an * (0.42f + 0.18f * hv)),
                        VortexStyle.fade(VortexStyle.akzent(1f), a * an * 0.06f));
                // Balken links, waechst von der Mitte aus
                int halb = (int) ((ZEILE_H / 2 - 3) * an);
                int mitte = y + ZEILE_H / 2;
                ctx.fill(x + 2, mitte - halb, x + 4, mitte + halb,
                        VortexStyle.fade(VortexStyle.akzent(0.2f), a));
            }

            // Text: gedaempft -> weiss, und beim Ueberfahren ein Stueck nach
            // rechts. Die kleine Bewegung macht die Liste lebendig, ohne zu
            // wackeln.
            int farbe = VortexStyle.mix(VortexStyle.mix(VortexStyle.TEXT_DIM, VortexStyle.TEXT, hv),
                    0xFFFFFFFF, an);
            int tx = x + 9 + (int) (hv * 2f);
            ctx.text(this.font, Component.literal(m.getName()), tx, y + 4,
                    VortexStyle.fade(farbe, a), false);
            // Hinweis, dass es Einstellungen gibt
            if (hatEinstellungen(m)) {
                boolean auf = offen.getOrDefault(m, false);
                ctx.text(this.font, Component.literal(auf ? "-" : "+"),
                        x + spalteB - 12, y + 4,
                        VortexStyle.fade(auf ? VortexStyle.akzent(0.5f) : VortexStyle.TEXT_DIM, a), false);
            }
            treffer.add(new Treffer(x, Math.max(y, clipOben), spalteB,
                    Math.min(y + ZEILE_H, clipUnten) - Math.max(y, clipOben),
                    Art.MODUL, m, null));
        }
        y += ZEILE_H;

        // --- Einstellungen, weich aufgeklappt -------------------------------
        float k = weich(klapp.getOrDefault(m, 0f), offen.getOrDefault(m, false) ? 1f : 0f, 14f, dt);
        klapp.put(m, k);
        if (k > 0.01f) {
            int voll = einstellungsHoehe(m);
            int h = (int) (voll * k);
            int oben = Math.max(y, clipOben), unten = Math.min(y + h, clipUnten);
            // Nur zeichnen, wenn etwas davon im Bild ist. Ist das aufgeklappte
            // Modul ganz herausgescrollt, haette der Bereich sonst eine
            // negative Hoehe.
            if (unten > oben) {
                ctx.enableScissor(x, oben, x + spalteB, unten);

                // EINGELASSENES FELD.
                //
                // Vorher standen die Einstellungen ohne Rahmen direkt unter
                // dem Modul -- man sah nicht, wo sie anfangen und wo das
                // naechste Modul beginnt. Jetzt liegen sie in einem dunkleren,
                // leicht eingerueckten Feld mit einer Akzentlinie links, die
                // den ganzen Block zusammenhaelt, und einer Kante unten.
                ctx.fill(x + 3, y, x + spalteB - 3, y + h,
                        VortexStyle.fade(VortexStyle.BAR, a));
                ctx.fill(x + 3, y, x + 4, y + h,
                        VortexStyle.fade(VortexStyle.akzent(0.4f), a * 0.8f));
                ctx.fill(x + 3, y + h - 1, x + spalteB - 3, y + h,
                        VortexStyle.fade(VortexStyle.LINE, a));

                int sy = y + INNEN_RAND;

                // Auswahlliste des Moduls (Bloecke, Mobs, Items ...).
                //
                // FEHLTE im Spaltenmenue: Block ESP, Mob ESP, Item Counter,
                // No Render Blocks und Anti Render oeffnen darueber ihre
                // Auswahl. Ohne den Knopf liess sich dort nichts auswaehlen.
                if (m instanceof com.vortex.client.module.HasOwnScreen hos) {
                    if (sy + AUSWAHL_H > oben && sy < unten) {
                        zeichneAuswahlKnopf(ctx, m, hos.screenButtonLabel(),
                                x + 7, sy, spalteB - 14, a);
                    }
                    sy += AUSWAHL_H + 2;
                }

                for (Setting s : m.getSettings()) {
                    if (s == m.getEnabledSetting()) continue;
                    if (sy + EINST_H > oben && sy < unten) {
                        zeichneEinstellung(ctx, m, s, x + 8, sy, spalteB - 14, a);
                    }
                    sy += EINST_H;
                }
                // Schliessen kehrt von selbst zum aeusseren Bereich zurueck --
                // Minecraft verwaltet die Bereiche als Stapel.
                ctx.disableScissor();
            }
            y += h;
        }
        return y;
    }

    private void zeichneEinstellung(GuiGraphicsExtractor ctx, Module m, Setting s,
                                    int x, int y, int w, float a) {
        int dim = VortexStyle.fade(VortexStyle.TEXT_DIM, a);
        int txt = VortexStyle.fade(VortexStyle.TEXT, a);
        int akz = VortexStyle.fade(VortexStyle.akzent(0.5f), a);
        if (mx >= x && mx < x + w && my >= y && my < y + EINST_H) {
            ctx.fill(x - 2, y, x + w + 2, y + EINST_H,
                    VortexStyle.fade(VortexStyle.HOV, a * 0.7f));
        }

        // NAME UND WERT UEBERLAPPEN NICHT MEHR.
        //
        // Vorher wurde der Name pauschal auf "Breite minus 40" gekuerzt -- egal
        // wie breit der Wert rechts war. "Not bound" ist breiter als 40 Pixel
        // und lag deshalb ueber "Toggle Key". Jetzt wird zuerst der Wert
        // gemessen, und der Name bekommt genau den Platz, der links davon
        // uebrig bleibt.

        if (s instanceof BooleanSetting b) {
            String name = kuerzen(s.getName(), w - 16 - 8);
            ctx.text(this.font, Component.literal(name), x + 2, y + 3, dim, false);
            // Kleiner Schalter statt Kaestchen, der Knauf gleitet.
            float an = weich(einstAnim.getOrDefault(s, b.get() ? 1f : 0f),
                    b.get() ? 1f : 0f, 16f, letzteDt);
            einstAnim.put(s, an);
            int bx = x + w - 16;
            ctx.fill(bx, y + 4, bx + 16, y + 10,
                    VortexStyle.fade(VortexStyle.mix(VortexStyle.TRACK, VortexStyle.akzent(0.5f), an), a));
            int kx = bx + 1 + (int) (an * 9f);
            ctx.fill(kx, y + 3, kx + 6, y + 11,
                    VortexStyle.fade(VortexStyle.mix(0xFFB8B2CC, 0xFFFFFFFF, an), a));
            treffer.add(new Treffer(x, y, w, EINST_H, Art.BOOL, m, s));

        } else if (s instanceof NumberSetting n) {
            String wert = zahl(n);
            int wertB = this.font.width(wert);
            String name = kuerzen(s.getName(), w - wertB - 8);
            ctx.text(this.font, Component.literal(name), x + 2, y + 1, dim, false);
            ctx.text(this.font, Component.literal(wert), x + w - wertB, y + 1, txt, false);
            // Schiene unter dem Text
            int ty = y + 11;
            ctx.fill(x + 2, ty, x + w, ty + 2, VortexStyle.fade(VortexStyle.TRACK, a));
            double ziel = (n.get() - n.getMin()) / Math.max(1e-9, n.getMax() - n.getMin());
            ziel = Math.max(0, Math.min(1, ziel));
            // Die Fuellung gleitet zum neuen Wert, statt zu springen. Beim
            // Ziehen folgt sie praktisch sofort, beim Klicken auf eine Stelle
            // sieht man sie hinlaufen.
            float p = weich(einstAnim.getOrDefault(s, (float) ziel), (float) ziel,
                    zieht == n ? 40f : 14f, letzteDt);
            einstAnim.put(s, p);
            int fx = x + 2 + (int) ((w - 2) * p);
            verlaufBand(ctx, x + 2, ty, fx - x - 2, 2,
                    VortexStyle.fade(VortexStyle.akzent(0f), a), VortexStyle.fade(VortexStyle.akzent(1f), a));
            // Knauf am Ende der Fuellung
            ctx.fill(fx - 2, ty - 2, fx + 2, ty + 4, VortexStyle.fade(0xFFFFFFFF, a));
            treffer.add(new Treffer(x + 2, y, w - 2, EINST_H, Art.NUM, m, s));

        } else if (s instanceof ModeSetting mode) {
            // Der Wert darf hoechstens die halbe Breite nehmen -- sonst bliebe
            // fuer den Namen nichts.
            String wert = kuerzen(String.valueOf(mode.get()), Math.max(24, w / 2));
            int wertB = this.font.width(wert);
            String name = kuerzen(s.getName(), w - wertB - 8);
            ctx.text(this.font, Component.literal(name), x + 2, y + 3, dim, false);
            ctx.text(this.font, Component.literal(wert), x + w - wertB, y + 3, akz, false);
            treffer.add(new Treffer(x, y, w, EINST_H, Art.MODUS, m, s));

        } else if (s instanceof ColorSetting c) {
            String name = kuerzen(s.getName(), w - 12 - 8);
            ctx.text(this.font, Component.literal(name), x + 2, y + 3, dim, false);
            int bx = x + w - 12;
            ctx.fill(bx, y + 2, bx + 10, y + 12, VortexStyle.fade(0xFF000000, a));
            ctx.fill(bx + 1, y + 3, bx + 9, y + 11, VortexStyle.fade(c.get() | 0xFF000000, a));
            treffer.add(new Treffer(x, y, w, EINST_H, Art.FARBE, m, s));

        } else if (s instanceof KeySetting k) {
            // Taste als kleine Kappe rechts. Nicht belegt heisst hier kurz
            // "None" statt "Not bound" -- das spart den Platz, der vorher
            // fehlte. Lange Tastennamen ("LEFT SHIFT") werden gekuerzt.
            boolean lauscht = k.isListening();
            String wert = lauscht ? "..." : (k.isBound() ? kuerzen(k.getKeyName(), Math.max(20, w / 2 - 8)) : "None");
            int kappeB = this.font.width(wert) + 8;
            int kx = x + w - kappeB;
            ctx.fill(kx, y + 1, kx + kappeB, y + EINST_H - 1,
                    VortexStyle.fade(lauscht ? VortexStyle.mix(VortexStyle.CARD, VortexStyle.akzent(0.5f), 0.45f)
                                             : VortexStyle.CARD, a));
            ctx.fill(kx, y + EINST_H - 2, kx + kappeB, y + EINST_H - 1,
                    VortexStyle.fade(lauscht ? VortexStyle.akzent(0.5f) : VortexStyle.LINE, a));
            ctx.text(this.font, Component.literal(wert), kx + 4, y + 3,
                    lauscht ? txt : (k.isBound() ? txt : dim), false);
            String name = kuerzen(s.getName(), w - kappeB - 8);
            ctx.text(this.font, Component.literal(name), x + 2, y + 3, dim, false);
            treffer.add(new Treffer(x, y, w, EINST_H, Art.TASTE, m, s));
        }
    }

    // ======================================================================
    // Bedienung
    // ======================================================================

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent click, boolean doubled) {
        if (super.mouseClicked(click, doubled)) return true;
        int knopf = click.button();
        // Von hinten nach vorn: zuletzt Gezeichnetes liegt oben.
        for (int i = treffer.size() - 1; i >= 0; i--) {
            Treffer t = treffer.get(i);
            if (!t.in(mx, my)) continue;
            switch (t.art) {
                case KOPF:
                    // Links- oder Rechtsklick: Kategorie auf- oder zuklappen
                    katZu.put(t.kat, !katZu.getOrDefault(t.kat, false));
                    return true;
                case MODUL:
                    if (knopf == 1) {
                        if (hatEinstellungen(t.modul)) {
                            offen.put(t.modul, !offen.getOrDefault(t.modul, false));
                        }
                    } else {
                        t.modul.toggle();
                    }
                    return true;
                case BOOL:
                    schalte(t.modul, (BooleanSetting) t.einst);
                    return true;
                case NUM:
                    zieht = (NumberSetting) t.einst;
                    ziehX = t.x;
                    ziehB = t.w;
                    schieber(zieht, mx);
                    return true;
                case MODUS:
                    if (knopf == 1) zurueck((ModeSetting) t.einst);
                    else ((ModeSetting) t.einst).cycle();
                    return true;
                case FARBE:
                    farbe(t.modul, (ColorSetting) t.einst);
                    return true;
                case AUSWAHL:
                    // Das Modul erzeugt seinen Bildschirm selbst -- genau wie
                    // im bisherigen Menue. ESC fuehrt hierher zurueck.
                    if (t.modul instanceof com.vortex.client.module.HasOwnScreen hos) {
                        Minecraft.getInstance().gui.setScreen(hos.createScreen(this));
                    }
                    return true;
                case TASTE:
                    ((KeySetting) t.einst).setListening(true);
                    return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent click, double dx, double dy) {
        if (zieht != null) {
            schieber(zieht, mx);
            return true;
        }
        return super.mouseDragged(click, dx, dy);
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent click) {
        zieht = null;
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        // Die Spalte unter dem Zeiger scrollen.
        for (int i = 0; i < spaltenFlaeche.size(); i++) {
            int[] f = spaltenFlaeche.get(i);
            if (mouseX >= f[0] && mouseX < f[0] + f[2] && mouseY >= f[1] && mouseY < f[1] + f[3]) {
                float[] l = lauf.computeIfAbsent(spaltenKat.get(i), k -> new float[2]);
                l[1] = Math.max(0f, l[1] - (float) vertical * 24f);
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        // Waehrend eine Taste aufgenommen wird, bricht ESC nur die Aufnahme ab.
        return lauschend() == null;
    }

    @Override
    public void removed() {
        // Speichern beim Schliessen -- genau wie im bisherigen Menue.
        com.vortex.client.core.ConfigManager.save();
        super.removed();
    }

    // ======================================================================
    // Einstellungen aendern -- dieselben Aufrufe wie im bisherigen Menue
    // ======================================================================

    private void schalte(Module m, BooleanSetting b) {
        if (m instanceof com.vortex.client.module.modules.GlobalHudColorModule ghc) {
            if (b == ghc.apply) { ghc.applyToAll(); return; }
            if (b == ghc.reset) { ghc.resetToWhite(); return; }
        }
        if (b == m.getEnabledSetting()) m.toggle();
        else b.toggle();
    }

    private void schieber(NumberSetting n, int mausX) {
        if (ziehB <= 0) return;
        float p = (mausX - ziehX) / (float) ziehB;
        if (p < 0f) p = 0f;
        if (p > 1f) p = 1f;
        double roh = n.getMin() + p * (n.getMax() - n.getMin());
        double schritt = n.getStep();
        if (schritt > 0) roh = Math.round(roh / schritt) * schritt;
        roh = Math.round(roh * 1000.0) / 1000.0;
        if (roh < n.getMin()) roh = n.getMin();
        if (roh > n.getMax()) roh = n.getMax();
        n.set(roh);
    }

    private void zurueck(ModeSetting mode) {
        int start = mode.getIndex();
        int anzahl = 0;
        do { mode.cycle(); anzahl++; } while (mode.getIndex() != start && anzahl < 64);
        if (anzahl <= 1) return;
        for (int i = 0; i < anzahl - 1; i++) mode.cycle();
    }

    private void farbe(Module m, ColorSetting c) {
        if (m instanceof com.vortex.client.module.modules.GlobalHudColorModule ghc && c == ghc.color) {
            Minecraft.getInstance().gui.setScreen(new ColorPickerScreen(this, c, ghc::applyToAll));
            return;
        }
        Minecraft.getInstance().gui.setScreen(new ColorPickerScreen(this, c));
    }

    private KeySetting lauschend() {
        for (Module m : ModuleManager.INSTANCE.getModules()) {
            for (Setting s : m.getSettings()) {
                if (s instanceof KeySetting k && k.isListening()) return k;
            }
        }
        return null;
    }

    /** Nimmt die naechste gedrueckte Taste auf -- wortgleich zum bisherigen Menue. */
    private void tasteAufnehmen() {
        KeySetting k = lauschend();
        if (k == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (com.mojang.blaze3d.platform.InputConstants.isKeyDown(
                mc.getWindow(), org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE)) {
            k.setKeyCode(org.lwjgl.glfw.GLFW.GLFW_KEY_UNKNOWN);
            k.setListening(false);
            com.vortex.client.core.ConfigManager.save();
            return;
        }
        for (int code = org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE;
             code <= org.lwjgl.glfw.GLFW.GLFW_KEY_LAST; code++) {
            if (com.mojang.blaze3d.platform.InputConstants.isKeyDown(mc.getWindow(), code)) {
                k.setKeyCode(code);
                k.setListening(false);
                com.vortex.client.core.ConfigManager.save();
                return;
            }
        }
    }

    // ======================================================================
    // Hilfen
    // ======================================================================

    private List<Module> module(Module.Category c) {
        return ModuleManager.INSTANCE.getByCategory(c);
    }

    private List<Module> gefiltert(Module.Category c) {
        String q = (search == null) ? "" : search.getValue().trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) return module(c);
        List<Module> out = new ArrayList<>();
        for (Module m : module(c)) {
            if (m.getName().toLowerCase(Locale.ROOT).contains(q)) out.add(m);
        }
        return out;
    }

    private boolean hatEinstellungen(Module m) {
        // Ein Modul mit eigener Auswahlliste ist IMMER aufklappbar -- auch
        // wenn es sonst keine Einstellungen hat. Vorher fehlte dann sogar das
        // Plus, und man kam an die Auswahl gar nicht heran.
        if (m instanceof com.vortex.client.module.HasOwnScreen) return true;
        for (Setting s : m.getSettings()) if (s != m.getEnabledSetting()) return true;
        return false;
    }

    private int einstellungsHoehe(Module m) {
        int n = 0;
        for (Setting s : m.getSettings()) if (s != m.getEnabledSetting()) n++;
        int h = n * EINST_H + 2 * INNEN_RAND;
        if (m instanceof com.vortex.client.module.HasOwnScreen) h += AUSWAHL_H + 2;
        return h;
    }

    private int inhaltHoehe(List<Module> liste) {
        int h = 0;
        for (Module m : liste) {
            h += ZEILE_H;
            float k = klapp.getOrDefault(m, 0f);
            if (k > 0.01f) h += (int) (einstellungsHoehe(m) * k);
        }
        return h;
    }

    private String zahl(NumberSetting n) {
        double v = n.get();
        if (n.getStep() >= 1 && v == Math.rint(v)) return String.valueOf((long) v);
        return String.format(Locale.ROOT, "%.2f", v);
    }

    private String kuerzen(String s, int max) {
        if (this.font.width(s) <= max) return s;
        while (s.length() > 1 && this.font.width(s + "..") > max) s = s.substring(0, s.length() - 1);
        return s + "..";
    }

    private static String schoen(String name) {
        String s = name.toLowerCase(Locale.ROOT);
        if (s.equals("pvp")) return "PvP";
        if (s.equals("hud")) return "HUD";
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /** Weiche, bildratenunabhaengige Annaeherung -- wie ueberall im Client. */
    private static float weich(float ist, float ziel, float tempo, float dt) {
        float f = 1f - (float) Math.exp(-tempo * dt);
        float neu = ist + (ziel - ist) * f;
        return Math.abs(ziel - neu) < 0.005f ? ziel : neu;
    }

    /**
     * Klapp-Pfeil, 5 Pixel gross. Offen zeigt er nach unten, zu nach rechts.
     * Beide Formen werden je nach Klappzustand ueberblendet -- eine echte
     * Drehung ginge nur ueber die Matrix, das Ueberblenden wirkt genauso weich.
     */
    private static void pfeil(GuiGraphicsExtractor ctx, int x, int y, float offen, int farbe) {
        int alpha = (farbe >>> 24) & 0xFF;
        int rgb = farbe & 0x00FFFFFF;
        int aUnten = (int) (alpha * offen);
        int aRechts = (int) (alpha * (1f - offen));
        if (aUnten > 3) {
            int c = (aUnten << 24) | rgb;
            // v: Zeilen 5,3,1 Pixel breit
            ctx.fill(x, y, x + 5, y + 1, c);
            ctx.fill(x + 1, y + 1, x + 4, y + 2, c);
            ctx.fill(x + 2, y + 2, x + 3, y + 3, c);
        }
        if (aRechts > 3) {
            int c = (aRechts << 24) | rgb;
            // >: Spalten 5,3,1 Pixel hoch
            ctx.fill(x + 1, y - 1, x + 2, y + 4, c);
            ctx.fill(x + 2, y, x + 3, y + 3, c);
            ctx.fill(x + 3, y + 1, x + 4, y + 2, c);
        }
    }

    private void roundRect(GuiGraphicsExtractor ctx, int x, int y, int w, int h, int c) {
        if (w <= 0 || h <= 0) return;
        int r = Math.min(2, Math.min(w / 2, h / 2));
        ctx.fill(x, y + r, x + w, y + h - r, c);
        ctx.fill(x + r, y, x + w - r, y + r, c);
        ctx.fill(x + r, y + h - r, x + w - r, y + h, c);
        if (r >= 2) {
            ctx.fill(x + 1, y + 1, x + r, y + r, c);
            ctx.fill(x + w - r, y + 1, x + w - 1, y + r, c);
            ctx.fill(x + 1, y + h - r, x + r, y + h - 1, c);
            ctx.fill(x + w - r, y + h - r, x + w - 1, y + h - 1, c);
        }
    }

    /** Waagerechter Verlauf in Baendern von 6 Pixeln -- guenstig und weich. */
    private static void verlaufBand(GuiGraphicsExtractor ctx, int x, int y, int w, int h,
                                    int von, int bis) {
        if (w <= 0 || h <= 0) return;
        int baender = Math.max(1, (w + 5) / 6);
        for (int b = 0; b < baender; b++) {
            int ax = x + w * b / baender, bx = x + w * (b + 1) / baender;
            if (bx <= ax) continue;
            ctx.fill(ax, y, bx, y + h, VortexStyle.mix(von, bis, (b + 0.5f) / baender));
        }
    }


    /**
     * Knopf, der die Auswahlliste eines Moduls oeffnet ("Select blocks" ...).
     *
     * Hebt sich deutlich von den Einstellungen ab: eigene Flaeche, Pfeil
     * rechts, heller beim Ueberfahren. Es ist der wichtigste Eintrag dieser
     * Module, deshalb steht er ganz oben im Feld.
     */
    private void zeichneAuswahlKnopf(GuiGraphicsExtractor ctx, Module m, String text,
                                     int x, int y, int w, float a) {
        boolean hov = mx >= x && mx < x + w && my >= y && my < y + AUSWAHL_H;
        roundRect(ctx, x, y, w, AUSWAHL_H, VortexStyle.fade(
                hov ? VortexStyle.mix(VortexStyle.CARD, VortexStyle.akzent(0.5f), 0.35f)
                    : VortexStyle.CARD, a));
        ctx.text(this.font, Component.literal(kuerzen(text, w - 20)), x + 6, y + 4,
                VortexStyle.fade(hov ? 0xFFFFFFFF : VortexStyle.TEXT, a), false);
        ctx.text(this.font, Component.literal(">"), x + w - 9, y + 4,
                VortexStyle.fade(VortexStyle.akzent(0.5f), a), false);
        treffer.add(new Treffer(x, y, w, AUSWAHL_H, Art.AUSWAHL, m, null));
    }

}
