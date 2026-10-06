package com.vortex.client.musik;

import com.vortex.client.core.ConfigManager;
import com.vortex.client.core.setting.BooleanSetting;
import com.vortex.client.gui.glatt.Glatt;
import com.vortex.client.gui.glatt.Glatt.Schrift;
import com.vortex.client.gui.glatt.Symbole.Symbol;
import com.vortex.client.module.ModuleManager;
import com.vortex.client.module.modules.NowPlayingModule;
import com.vortex.client.module.modules.SpotifyModule;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * "Music" (Startmenue-Kachel, wie Bots): Spotify verbinden, Wiedergabe mit
 * Cover und Steuerung, Spieler in der Naehe und was sie hoeren (Mithoeren per
 * Klick), alle Einstellungen.
 */
public class MusikScreen extends Screen {
    private enum Reiter { NAEHE, EINSTELLUNGEN }

    private record Flaeche(float x, float y, float w, float h, Runnable aktion) {
        boolean in(double mx, double my) { return mx >= x && mx < x + w && my >= y && my < y + h; }
    }

    private static final int TEXT = 0xFFF2F0F8, DIM = 0xFF8D86A6, KARTE = 0xF0181424, KARTE_HOV = 0xF0221C34;
    private static final int FENSTER = 0xF20E0B16, GRUEN = 0xFF1ED760, AKZENT = 0xFF8B5CF6;

    private final Screen parent;
    private final List<Flaeche> flaechen = new ArrayList<>();
    private Reiter reiter = Reiter.NAEHE;
    private EditBox clientId;
    private float scroll;
    private int fx, fy, fw, fh;
    private long offen;
    /** Gekuerzte Texte merken: Vermessen kostet, und es sind jedes Bild dieselben. */
    private final Map<String, String> kurz = new LinkedHashMap<>(64, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, String> e) { return size() > 200; }
    };

    public MusikScreen(Screen parent) {
        super(Component.literal("Music"));
        this.parent = parent;
    }

    private SpotifyModule mod() { return ModuleManager.INSTANCE.get(SpotifyModule.class); }

    @Override
    protected void init() {
        fw = Math.min(this.width - 20, 600);
        fh = Math.min(this.height - 20, 330);
        fx = (this.width - fw) / 2;
        fy = (this.height - fh) / 2;
        if (offen == 0) offen = System.currentTimeMillis();
        MusikDienst.fensterOffen = true;
        String alt = clientId != null ? clientId.getValue() : Spotify.eigeneClientId();
        clientId = new EditBox(this.font, fx + 22, fy + 0, 200, 16, Component.literal("Client ID"));
        clientId.setMaxLength(40);
        clientId.setHint(Component.literal("Own Spotify Client ID (optional)"));
        clientId.setValue(alt);
        clientId.setResponder(v -> { });
        addRenderableWidget(clientId);
        clientId.visible = false;
    }

    private String k(String text, float max, Schrift s) {
        if (text == null) return "";
        String key = text + "\u0000" + (int) max + "\u0000" + s.ordinal();
        return kurz.computeIfAbsent(key, x -> Glatt.kuerzen(text, (int) max, s));
    }

    // ------------------------------------------------------------------
    // Zeichnen
    // ------------------------------------------------------------------

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        flaechen.clear();
        float auf = Math.min(1f, (System.currentTimeMillis() - offen) / 180f);
        auf = 1f - (1f - auf) * (1f - auf);
        g.fill(0, 0, this.width, this.height, Glatt.alpha(0xC8060409, auf));
        var pose = g.pose();
        pose.pushMatrix();
        float sk = 0.96f + 0.04f * auf;
        pose.translate(this.width / 2f, this.height / 2f);
        pose.scale(sk, sk);
        pose.translate(-this.width / 2f, -this.height / 2f);
        try {
            zeichne(g, mx, my, auf);
        } finally {
            pose.popMatrix();
        }
        super.extractRenderState(g, mx, my, delta);
    }

    private void zeichne(GuiGraphicsExtractor g, int mx, int my, float a) {
        Glatt.schatten(g, fx, fy, fw, fh, 10, 12, Glatt.alpha(0x90000000, a));
        Glatt.rund(g, fx, fy, fw, fh, 10, Glatt.alpha(FENSTER, a));
        Glatt.linieW(g, fx + 12, fx + fw - 12, fy + 30, Glatt.alpha(0x30FFFFFF, a));
        Glatt.symbol(g, Symbol.NOTE, fx + 14, fy + 9, 12, Glatt.alpha(GRUEN, a));
        Glatt.text(g, "Music", fx + 31, fy + 11, Glatt.alpha(TEXT, a), Schrift.FETT);
        knopf(g, fx + fw - 64, fy + 7, 52, 16, "Done", mx, my, this::onClose, false, a);

        // Konto-Chip
        String konto = switch (Spotify.status()) {
            case VERBUNDEN -> "Spotify: " + (Spotify.name().isEmpty() ? "connected" : Spotify.name())
                    + (Boolean.TRUE.equals(Spotify.premium()) ? "  ·  Premium" : Boolean.FALSE.equals(Spotify.premium()) ? "  ·  Free" : "");
            case ANMELDEN -> "Waiting for the browser...";
            default -> "Spotify not connected";
        };
        float kw = Glatt.breite(konto, Schrift.NORMAL) + 16;
        float kx = fx + fw - 72 - kw;
        Glatt.rund(g, kx, fy + 7, kw, 16, 8, Glatt.alpha(Spotify.verbunden() ? 0x2A1ED760 : 0x18FFFFFF, a));
        Glatt.text(g, konto, kx + 8, fy + 11, Glatt.alpha(Spotify.verbunden() ? GRUEN : DIM, a), Schrift.NORMAL);

        int lx = fx + 12, ly = fy + 40, lw = Math.min(250, fw / 2 - 18), lh = fh - 66;
        jetztSpielt(g, lx, ly, lw, lh, mx, my, a);

        int rx = lx + lw + 12, rw = fx + fw - 12 - rx, ry = fy + 40;
        // Reiter
        float tx = rx;
        for (Reiter r : Reiter.values()) {
            String name = r == Reiter.NAEHE ? "Nearby players" : "Settings";
            float w = Glatt.breite(name, Schrift.FETT) + 20;
            boolean an = r == reiter, hov = !an && mx >= tx && mx < tx + w && my >= ry && my < ry + 18;
            Glatt.rund(g, tx, ry, w, 18, 9, Glatt.alpha(an ? 0x338B5CF6 : hov ? 0x20FFFFFF : 0x10FFFFFF, a));
            Glatt.text(g, name, tx + 10, ry + 5, Glatt.alpha(an ? TEXT : DIM, a), Schrift.FETT);
            final Reiter z = r;
            flaechen.add(new Flaeche(tx, ry, w, 18, () -> { reiter = z; scroll = 0; }));
            tx += w + 6;
        }
        int iy = ry + 26, ih = fh - 66 - 26;
        if (reiter == Reiter.NAEHE) naehe(g, rx, iy, rw, ih, mx, my, a);
        else einstellungen(g, rx, iy, rw, ih, mx, my, a);

        String meld = MusikDienst.meldung();
        if (meld == null && Spotify.status() == Spotify.Status.FEHLER) meld = Spotify.fehler();
        if (meld == null && Spotify.verbunden()) meld = MusikDienst.apiFehler();
        if (meld != null) Glatt.textMitte(g, k(meld, fw - 40, Schrift.NORMAL), fx + fw / 2f, fy + fh - 17, Glatt.alpha(0xFFFFB86B, a), Schrift.NORMAL);
    }

    /** Linke Karte: Cover, Titel, Fortschritt, Steuerung -- oder "Connect". */
    private void jetztSpielt(GuiGraphicsExtractor g, int x, int y, int w, int h, int mx, int my, float a) {
        Glatt.rund(g, x, y, w, h, 8, Glatt.alpha(0xF0120E1B, a));
        Song s = MusikDienst.eigener();
        // Unter dem Cover: 3 Textzeilen, Fortschritt, Steuerung, (Lautstaerke), Knopf -- das Cover nimmt den Rest
        float unten = Spotify.verbunden() ? 132 : 150;
        float c = Math.max(32, Math.min(Math.min(w - 24, 104), h - 12 - unten));
        float cx = x + (w - c) / 2f, cy = y + 12;
        Identifier id = s == null ? null : Cover.von(s.cover());
        if (id != null) {
            Glatt.schatten(g, cx, cy + 2, c, c, 6, 8, Glatt.alpha(0xA0000000, a));
            g.blit(RenderPipelines.GUI_TEXTURED, id, Math.round(cx), Math.round(cy), 0f, 0f, Math.round(c), Math.round(c), 128, 128, 128, 128, Glatt.alpha(0xFFFFFFFF, a));
        } else {
            Glatt.rund(g, cx, cy, c, c, 8, Glatt.alpha(0xFF1B1726, a));
            Glatt.symbol(g, Symbol.NOTE, cx + c / 2 - 16, cy + c / 2 - 16, 32, Glatt.alpha(s != null ? GRUEN : 0xFF3A3450, a));
        }
        float ty = cy + c + 10;
        if (s != null) {
            Glatt.textMitte(g, k(s.titel(), w - 20, Schrift.FETT), x + w / 2f, ty, Glatt.alpha(TEXT, a), Schrift.FETT);
            Glatt.textMitte(g, k(s.kuenstler(), w - 20, Schrift.NORMAL), x + w / 2f, ty + 12, Glatt.alpha(DIM, a), Schrift.NORMAL);
            String quelle = MusikDienst.ziel() != null ? "Listening along with " + MusikDienst.zielName()
                    : "app".equals(MusikDienst.quelle()) ? "From the Spotify app (not signed in)"
                    : MusikDienst.geraet().isEmpty() ? "Spotify" : "Spotify on " + MusikDienst.geraet();
            Glatt.textMitte(g, k(quelle, w - 20, Schrift.NORMAL), x + w / 2f, ty + 24, Glatt.alpha(MusikDienst.ziel() != null ? GRUEN : 0xFF6F6888, a), Schrift.NORMAL);
        } else {
            Glatt.textMitte(g, Spotify.verbunden() ? "Nothing playing" : "Not connected", x + w / 2f, ty, Glatt.alpha(TEXT, a), Schrift.FETT);
            Glatt.textMitte(g, k(Spotify.verbunden() ? "Play something in Spotify (PC or phone)." : "Connect Spotify to share what you hear.", w - 20, Schrift.NORMAL),
                    x + w / 2f, ty + 12, Glatt.alpha(DIM, a), Schrift.NORMAL);
        }
        float by = ty + 40;
        if (Spotify.verbunden()) {
            // Fortschritt (klicken = springen)
            if (s != null && s.dauer() > 0 && "spotify".equals(MusikDienst.quelle())) {
                float bx = x + 14, bw = w - 28;
                boolean hov = mx >= bx && mx < bx + bw && my >= by - 4 && my < by + 7;
                Glatt.rund(g, bx, by, bw, hov ? 4 : 3, 1.5f, Glatt.alpha(0x30FFFFFF, a));
                Glatt.rund(g, bx, by, Math.max(3, bw * s.anteil()), hov ? 4 : 3, 1.5f, Glatt.alpha(hov ? GRUEN : 0xFFE6E2F2, a));
                Glatt.text(g, Song.zeit(s.jetzt()), bx, by + 7, Glatt.alpha(DIM, a), Schrift.NORMAL);
                Glatt.textRechts(g, Song.zeit(s.dauer()), bx + bw, by + 7, Glatt.alpha(DIM, a), Schrift.NORMAL);
                final Song fs = s;
                flaechen.add(new Flaeche(bx, by - 4, bw, 11, () -> MusikDienst.springen((long) (fs.dauer() * Math.max(0, Math.min(1, (mausX - bx) / bw))))));
            }
            // Steuerung
            float sy = by + 22, mitte = x + w / 2f;
            rundKnopf(g, mitte - 46, sy, 24, Symbol.ZURUECK, mx, my, MusikDienst::vorheriger, false, a);
            rundKnopf(g, mitte - 16, sy - 4, 32, s != null && s.spielt() ? Symbol.PAUSE : Symbol.PLAY, mx, my, MusikDienst::spielenPause, true, a);
            rundKnopf(g, mitte + 22, sy, 24, Symbol.VOR, mx, my, MusikDienst::naechster, false, a);
            // Lautstaerke
            int vol = MusikDienst.lautstaerke();
            if (vol >= 0 && sy + 46 < y + h - 24) {
                float vx = x + 30, vw = w - 60, vy = sy + 40;
                Glatt.symbol(g, Symbol.KOPFHOERER, x + 12, vy - 5, 11, Glatt.alpha(DIM, a));
                Glatt.rund(g, vx, vy, vw, 3, 1.5f, Glatt.alpha(0x30FFFFFF, a));
                Glatt.rund(g, vx, vy, Math.max(3, vw * vol / 100f), 3, 1.5f, Glatt.alpha(0xFFE6E2F2, a));
                Glatt.textRechts(g, vol + "%", x + w - 8, vy - 3, Glatt.alpha(DIM, a), Schrift.NORMAL);
                flaechen.add(new Flaeche(vx, vy - 5, vw, 13, () -> MusikDienst.lautstaerkeSetzen(Math.round(100 * Math.max(0, Math.min(1, (mausX - vx) / vw))))));
            }
            knopf(g, x + w / 2f - 40, y + h - 22, 80, 16, "Disconnect", mx, my, () -> { MusikDienst.stoppeMithoeren(null); Spotify.abmelden(); }, false, a);
            clientId.visible = false;
        } else {
            boolean warte = Spotify.status() == Spotify.Status.ANMELDEN;
            float bw = Math.min(w - 40, 150);
            knopfGruen(g, x + (w - bw) / 2f, by - 4, bw, 20, warte ? "Waiting for browser..." : "Connect Spotify", mx, my, () -> {
                Spotify.setzeEigeneClientId(clientId.getValue());
                Spotify.anmelden();
            }, a);
            // Eigene Client-ID (Spotify erlaubt je App nur 5 Konten)
            float iy = by + 26;
            if (iy + 40 < y + h) {
                clientId.setX(x + 12);
                clientId.setY(Math.round(iy + 12));
                clientId.setWidth(w - 24);
                clientId.visible = true;
                Glatt.text(g, Spotify.STANDARD_CLIENT_ID.isEmpty() ? "Spotify app (Client ID) -- needed:" : "Own Spotify app (optional):",
                        x + 12, iy, Glatt.alpha(DIM, a), Schrift.NORMAL);
                float hy = iy + 34;
                for (String z : new String[]{"developer.spotify.com > Dashboard > Create app,", "Redirect URI: " + Spotify.REDIRECT,
                        "Web API -- then paste the Client ID here."}) {
                    if (hy + 9 > y + h - 4) break;
                    Glatt.text(g, k(z, w - 24, Schrift.NORMAL), x + 12, hy, Glatt.alpha(0xFF6F6888, a), Schrift.NORMAL);
                    hy += 10;
                }
            } else clientId.visible = false;
        }
    }

    private float mausX;

    private void naehe(GuiGraphicsExtractor g, int x, int y, int w, int h, int mx, int my, float a) {
        List<MusikDienst.Fremd> liste = MusikDienst.inDerNaehe();
        if (liste.isEmpty()) {
            Glatt.rund(g, x, y, w, h, 8, Glatt.alpha(0xF0120E1B, a));
            Glatt.symbol(g, Symbol.LEUTE, x + w / 2f - 12, y + h / 2f - 34, 24, Glatt.alpha(0xFF3A3450, a));
            Glatt.textMitte(g, "Nobody nearby is sharing music", x + w / 2f, y + h / 2f - 2, Glatt.alpha(TEXT, a), Schrift.FETT);
            Glatt.textMitte(g, k("Other Vortex players with Spotify show up here.", w - 20, Schrift.NORMAL), x + w / 2f, y + h / 2f + 12, Glatt.alpha(DIM, a), Schrift.NORMAL);
            return;
        }
        int zh = 40;
        float max = Math.max(0, liste.size() * (zh + 6) - 6 - h);
        scroll = Math.max(0, Math.min(scroll, max));
        g.enableScissor(x, y, x + w, y + h);
        UUID ziel = MusikDienst.ziel();
        for (int i = 0; i < liste.size(); i++) {
            MusikDienst.Fremd f = liste.get(i);
            Song s = MusikDienst.songVon(f.uuid());
            if (s == null) continue;
            float zy = y + i * (zh + 6) - scroll;
            if (zy + zh < y || zy > y + h) continue;
            boolean hov = mx >= x && mx < x + w && my >= zy && my < zy + zh && my >= y && my < y + h;
            boolean dabei = f.uuid().equals(ziel);
            Glatt.rund(g, x, zy, w, zh, 7, Glatt.alpha(hov ? KARTE_HOV : KARTE, a));
            if (dabei) Glatt.rahmen(g, x, zy, w, zh, 7, 1, Glatt.alpha(GRUEN, a));
            Identifier id = Cover.von(s.cover());
            if (id != null) g.blit(RenderPipelines.GUI_TEXTURED, id, x + 5, Math.round(zy + 5), 0f, 0f, 30, 30, 128, 128, 128, 128, Glatt.alpha(0xFFFFFFFF, a));
            else { Glatt.rund(g, x + 5, zy + 5, 30, 30, 5, Glatt.alpha(0xFF1B1726, a)); Glatt.symbol(g, Symbol.NOTE, x + 12, zy + 12, 16, Glatt.alpha(GRUEN, a)); }
            boolean schmal = w < 280;
            float bw = schmal ? 48 : 78;
            float tw = w - 44 - bw - 10;
            double d = MusikDienst.abstand(this.minecraft, f.uuid());
            String kopf = f.name() + (d < 1000 ? "  ·  " + Math.round(d) + "m" : "");
            Glatt.text(g, k(kopf, tw, Schrift.FETT), x + 42, zy + 6, Glatt.alpha(TEXT, a), Schrift.FETT);
            Glatt.text(g, k(s.zeile(), tw, Schrift.NORMAL), x + 42, zy + 18, Glatt.alpha(DIM, a), Schrift.NORMAL);
            if (s.dauer() > 0) {
                Glatt.rund(g, x + 42, zy + 31, tw, 2, 1, Glatt.alpha(0x26FFFFFF, a));
                Glatt.rund(g, x + 42, zy + 31, Math.max(2, tw * s.anteil()), 2, 1, Glatt.alpha(0xFFB9B3CC, a));
            }
            if (zy >= y && zy + zh <= y + h) {
                final UUID u = f.uuid();
                if (dabei) knopf(g, x + w - bw - 8, zy + 12, bw, 16, "Stop", mx, my, () -> MusikDienst.mithoerenUmschalten(u), true, a);
                else knopfGruen(g, x + w - bw - 8, zy + 12, bw, 16, schmal ? "Listen" : "Listen along", mx, my, () -> MusikDienst.mithoerenUmschalten(u), a);
            }
        }
        g.disableScissor();
    }

    private void einstellungen(GuiGraphicsExtractor g, int x, int y, int w, int h, int mx, int my, float a) {
        SpotifyModule m = mod();
        NowPlayingModule hud = ModuleManager.INSTANCE.get(NowPlayingModule.class);
        if (m == null) return;
        List<Zeile> z = new ArrayList<>();
        z.add(schalter("Music features", "Everything on this page (Vortex 'Spotify' module).", m.isEnabled(), m::toggle));
        z.add(schalter(m.share, "Share my song", "Other Vortex players see what you hear."));
        z.add(new Zeile("Share with", "Who sees your song.", m.shareWith.get(), false, () -> { m.shareWith.cycle(); ConfigManager.save(); }));
        z.add(schalter(m.aboveHeads, "Songs above heads", "Show what other players hear above them."));
        z.add(schalter(m.ownAboveHead, "My song above my head", "Visible for you in third person (F5)."));
        z.add(new Zeile("Listen along", "Manual: click a player. Nearest: automatically. Needs Premium.", m.listenAlong.get(), m.listenAlong.getIndex() > 0,
                () -> { m.listenAlong.cycle(); ConfigManager.save(); }));
        z.add(new Zeile("Listen range", "Nearest player mode: how close they must be.", Math.round(m.listenRange.get()) + " blocks", false,
                () -> { double v = m.listenRange.get() + 4; m.listenRange.set(v > 48 ? 4 : v); ConfigManager.save(); }));
        z.add(schalter(m.resumeMine, "Resume my music", "After listening along, your own music continues."));
        z.add(schalter(m.desktopApp, "Read the Spotify app", "Without sign-in (Windows): title and artist from the app."));
        if (hud != null) z.add(schalter("Now Playing HUD", "Song card on screen (move it in the HUD editor).", hud.isEnabled(), hud::toggle));

        int zh = 30;
        float max = Math.max(0, z.size() * (zh + 4) - 4 - h);
        scroll = Math.max(0, Math.min(scroll, max));
        g.enableScissor(x, y, x + w, y + h);
        for (int i = 0; i < z.size(); i++) {
            Zeile e = z.get(i);
            float zy = y + i * (zh + 4) - scroll;
            if (zy + zh < y || zy > y + h) continue;
            boolean hov = mx >= x && mx < x + w && my >= zy && my < zy + zh && my >= y && my < y + h;
            Glatt.rund(g, x, zy, w, zh, 6, Glatt.alpha(hov ? KARTE_HOV : KARTE, a));
            float ww = Math.max(46, Glatt.breite(e.wert(), Schrift.FETT) + 16);
            Glatt.text(g, k(e.titel(), w - ww - 26, Schrift.FETT), x + 10, zy + 5, Glatt.alpha(TEXT, a), Schrift.FETT);
            Glatt.text(g, k(e.text(), w - ww - 26, Schrift.NORMAL), x + 10, zy + 16, Glatt.alpha(DIM, a), Schrift.NORMAL);
            float px = x + w - ww - 8, py = zy + 7;
            Glatt.rund(g, px, py, ww, 16, 8, Glatt.alpha(e.an() ? 0x401ED760 : 0x1CFFFFFF, a));
            Glatt.textMitte(g, e.wert(), px + ww / 2f, py + 4, Glatt.alpha(e.an() ? GRUEN : DIM, a), Schrift.FETT);
            if (zy >= y && zy + zh <= y + h) flaechen.add(new Flaeche(x, zy, w, zh, e.aktion()));
        }
        g.disableScissor();
    }

    private record Zeile(String titel, String text, String wert, boolean an, Runnable aktion) {}

    private Zeile schalter(BooleanSetting s, String titel, String text) {
        return new Zeile(titel, text, s.get() ? "On" : "Off", s.get(), () -> { s.toggle(); ConfigManager.save(); });
    }

    private Zeile schalter(String titel, String text, boolean an, Runnable umschalten) {
        return new Zeile(titel, text, an ? "On" : "Off", an, () -> { umschalten.run(); ConfigManager.save(); });
    }

    // ------------------------------------------------------------------
    // Knoepfe
    // ------------------------------------------------------------------

    private void knopf(GuiGraphicsExtractor g, float x, float y, float w, float h, String text, int mx, int my, Runnable aktion, boolean an, float a) {
        boolean hov = mx >= x && mx < x + w && my >= y && my < y + h;
        Glatt.rund(g, x, y, w, h, h / 2f, Glatt.alpha(hov ? 0x40FFFFFF : 0x24FFFFFF, a));
        if (an) Glatt.rahmen(g, x, y, w, h, h / 2f, 1, Glatt.alpha(GRUEN, a));
        Glatt.textMitte(g, text, x + w / 2f, y + (h - 8) / 2f, Glatt.alpha(TEXT, a), Schrift.NORMAL);
        flaechen.add(new Flaeche(x, y, w, h, aktion));
    }

    private void knopfGruen(GuiGraphicsExtractor g, float x, float y, float w, float h, String text, int mx, int my, Runnable aktion, float a) {
        boolean hov = mx >= x && mx < x + w && my >= y && my < y + h;
        Glatt.rund(g, x, y, w, h, h / 2f, Glatt.alpha(hov ? 0xFF3BE477 : GRUEN, a));
        Glatt.textMitte(g, text, x + w / 2f, y + (h - 8) / 2f, Glatt.alpha(0xFF0B1A10, a), Schrift.FETT);
        flaechen.add(new Flaeche(x, y, w, h, aktion));
    }

    private void rundKnopf(GuiGraphicsExtractor g, float x, float y, float d, Symbol sym, int mx, int my, Runnable aktion, boolean gross, float a) {
        boolean hov = mx >= x && mx < x + d && my >= y && my < y + d;
        Glatt.kreis(g, x + d / 2f, y + d / 2f, d, Glatt.alpha(gross ? (hov ? 0xFFFFFFFF : 0xFFE6E2F2) : (hov ? 0x30FFFFFF : 0x00FFFFFF), a));
        float sg = d * (gross ? 0.5f : 0.6f);
        Glatt.symbol(g, sym, x + (d - sg) / 2f, y + (d - sg) / 2f, sg, Glatt.alpha(gross ? 0xFF120E1B : TEXT, a));
        flaechen.add(new Flaeche(x, y, d, d, aktion));
    }

    // ------------------------------------------------------------------
    // Bedienung
    // ------------------------------------------------------------------

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent click, boolean doubled) {
        if (super.mouseClicked(click, doubled)) return true;
        if (click.button() != 0) return false;
        mausX = (float) click.x();
        for (int i = flaechen.size() - 1; i >= 0; i--) {
            if (flaechen.get(i).in(click.x(), click.y())) { flaechen.get(i).aktion().run(); return true; }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        scroll -= (float) vertical * 24f;
        return true;
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void removed() {
        MusikDienst.fensterOffen = false;
        if (clientId != null && !clientId.getValue().equals(Spotify.eigeneClientId()) && !Spotify.verbunden()) {
            Spotify.setzeEigeneClientId(clientId.getValue());
        }
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(parent);
    }
}
