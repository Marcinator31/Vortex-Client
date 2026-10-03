package com.vortex.client.gui;

import com.vortex.client.community.CommunityApi;
import com.vortex.client.core.ConfigManager;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Die drei Presets -- NUR Mod-Einstellungen.
 *
 * Makros, Waypoints, Freunde, Tastenbelegungen und das Design gelten fuer
 * alle Presets gleich und wechseln nie mit. Pro Platz:
 *
 *   Use      auf dieses Preset umschalten
 *   Name     anklicken zum Umbenennen
 *   Export   Mod-Einstellungen in die Zwischenablage (und als Datei in
 *            config/vortexclient/export)
 *   Import   Mod-Einstellungen aus der Zwischenablage in diesen Platz
 *   Upload   in die Community hochladen: Name, empfohlene Aufloesung,
 *            Beschreibung, oeffentlich oder nur per Link
 *
 * Zum Hochladen braucht es ein Konto auf der Website. Angemeldet wird hier im
 * Spiel; gespeichert wird nur die Sitzung der Website, nie das Passwort.
 */
public class PresetScreen extends Screen {

    private static final int KOPF_H = 30;
    private static final int FUSS_H = 22;
    private static final int KARTE_H = 44;

    private final Screen parent;
    private int winX, winY, winW, winH;
    private int mx, my;
    private long letzteZeit = 0;
    private float oeffnen = 0f;

    /** Klickbare Knoepfe dieses Bildes. */
    private record Knopf(int x, int y, int w, int h, Runnable aktion) {}
    private final List<Knopf> knoepfe = new ArrayList<>();

    private volatile String status = "";
    private volatile boolean arbeitet = false;

    // --- Umbenennen ----------------------------------------------------------
    private int umbenennen = -1;
    private EditBox nameFeld;

    // --- Hochladen (Dialog) --------------------------------------------------
    private int hochladen = -1;           // Platz, oder -1 = kein Dialog
    private boolean oeffentlich = true;
    private EditBox benutzerFeld;
    private EditBox passwortFeld;
    private EditBox titelFeld;
    private EditBox aufloesungFeld;
    private EditBox beschreibungFeld;
    private String merkBenutzer = "";
    private String merkTitel = "";
    private String merkAufloesung = "";
    private String merkBeschreibung = "";

    public PresetScreen(Screen parent) {
        super(Component.literal("Presets"));
        this.parent = parent;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        winW = Math.min(this.width - 20, 440);
        winH = Math.min(this.height - 20, KOPF_H + 3 * (KARTE_H + 6) + 34 + FUSS_H);
        winX = (this.width - winW) / 2;
        winY = (this.height - winH) / 2;

        // Werte ueber ein erneutes init() (Fenstergroesse) retten
        if (benutzerFeld != null) merkBenutzer = benutzerFeld.getValue();
        if (titelFeld != null) merkTitel = titelFeld.getValue();
        if (aufloesungFeld != null) merkAufloesung = aufloesungFeld.getValue();
        if (beschreibungFeld != null) merkBeschreibung = beschreibungFeld.getValue();
        String merkName = nameFeld != null ? nameFeld.getValue() : "";

        // Breite der Dialogfelder steht mit der Fenstergroesse fest -- die
        // Felder werden gleich in der richtigen Breite angelegt.
        int fb = Math.min(winW - 20, 320) - 28;
        nameFeld = feld(24, merkName, 135);
        benutzerFeld = feld(64, merkBenutzer, fb);
        passwortFeld = feld(64, "", fb);
        titelFeld = feld(60, merkTitel, fb);
        aufloesungFeld = feld(20, merkAufloesung, fb);
        beschreibungFeld = feld(200, merkBeschreibung, fb);
        alleFelderVerstecken();
        if (hochladen >= 0) dialogFelderZeigen();
        if (umbenennen >= 0) nameFeld.setVisible(true);
    }

    private EditBox feld(int max, String wert, int breite) {
        EditBox e = new EditBox(this.font, 0, 0, Math.max(40, breite), 12, Component.literal(""));
        e.setBordered(false);
        e.setMaxLength(max);
        e.setValue(wert);
        e.setVisible(false);
        this.addRenderableWidget(e);
        return e;
    }

    private void alleFelderVerstecken() {
        for (EditBox e : new EditBox[]{nameFeld, benutzerFeld, passwortFeld, titelFeld, aufloesungFeld, beschreibungFeld}) {
            if (e != null) e.setVisible(false);
        }
    }

    private void dialogFelderZeigen() {
        boolean an = CommunityApi.benutzer() != null;
        benutzerFeld.setVisible(!an);
        passwortFeld.setVisible(!an);
        titelFeld.setVisible(an);
        aufloesungFeld.setVisible(an);
        beschreibungFeld.setVisible(an);
    }

    // ======================================================================
    // Zeichnen
    // ======================================================================

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        mx = mouseX;
        my = mouseY;
        long jetzt = System.nanoTime();
        float dt = letzteZeit == 0 ? 0.016f : Math.min(0.1f, (jetzt - letzteZeit) / 1e9f);
        letzteZeit = jetzt;
        oeffnen += (1f - oeffnen) * (1f - (float) Math.exp(-12f * dt));
        float a = oeffnen;
        knoepfe.clear();

        ctx.fill(0, 0, this.width, this.height, VortexStyle.fade(VortexStyle.DIM, a));
        VortexStyle.schatten(ctx, winX, winY, winW, winH, a);
        ctx.fill(winX, winY, winX + winW, winY + winH, VortexStyle.fade(VortexStyle.WINDOW, a));
        ctx.fill(winX, winY, winX + winW, winY + KOPF_H, VortexStyle.fade(VortexStyle.BAR, a));
        VortexStyle.akzentLinie(ctx, winX + 3, winY + KOPF_H - 2, winW - 6, a);

        // Kopf
        knopfText(ctx, winX + 6, winY + 7, 16, 16, "<", false, this::onClose, a);
        text(ctx, "Presets", winX + 26, winY + 11, VortexStyle.TEXT, a);
        String wer = CommunityApi.benutzer();
        if (wer != null) {
            int bx = knopf(ctx, winX + winW - 8, winY + 6, "Log out", false, () -> {
                arbeitet = true;
                hintergrund(() -> {
                    CommunityApi.abmelden();
                    status = "Logged out.";
                    Minecraft.getInstance().execute(() -> {
                        if (hochladen >= 0) dialogFelderZeigen();
                    });
                });
            }, a, true);
            String t = "Logged in: " + wer;
            text(ctx, kuerzen(t, bx - winX - 110), bx - 6 - this.font.width(kuerzen(t, bx - winX - 110)),
                    winY + 11, VortexStyle.TEXT_DIM, a);
        }

        // Hinweis
        int y = winY + KOPF_H + 6;
        text(ctx, kuerzen("Presets only hold mod settings. Macros, waypoints, friends and keybinds stay the same.",
                winW - 20), winX + 10, y, VortexStyle.TEXT_DIM, a);
        y += 16;

        // Drei Karten
        for (int i = 0; i < ConfigManager.PRESET_COUNT; i++) {
            karte(ctx, i, winX + 8, y, winW - 16, a);
            y += KARTE_H + 6;
        }

        // Fuss
        int fy = winY + winH - FUSS_H;
        ctx.fill(winX, fy, winX + winW, winY + winH, VortexStyle.fade(VortexStyle.BAR, a));
        String s = arbeitet ? "Working..." : status;
        text(ctx, kuerzen(s, winW - 130), winX + 10, fy + 7, arbeitet ? VortexStyle.akzent(0.5f) : VortexStyle.TEXT, a);
        knopf(ctx, winX + winW - 8, fy + 3, "Community", false,
                () -> Minecraft.getInstance().gui.setScreen(new CommunityScreen(this)), a, true);

        if (hochladen >= 0) {
            // Alles hinter dem Dialog ist gesperrt -- nur seine eigenen Knoepfe zaehlen.
            knoepfe.clear();
            dialog(ctx, a);
        }

        super.extractRenderState(ctx, mouseX, mouseY, delta);
    }

    /**
     * Passwortfeld. Das echte Eingabefeld liegt AUSSERHALB des Bildschirms
     * (es nimmt nur die Tasten entgegen); sichtbar sind allein die Sternchen,
     * die hier gezeichnet werden. So kann das Passwort nie im Klartext
     * erscheinen -- egal in welcher Reihenfolge Minecraft zeichnet.
     */
    private int passwortEingabe(GuiGraphicsExtractor ctx, int x, int y, int w, float a) {
        text(ctx, "Password", x, y, VortexStyle.TEXT_DIM, a);
        ctx.fill(x, y + 10, x + w, y + 24, VortexStyle.fade(VortexStyle.INNER, a));
        boolean fokus = passwortFeld.isFocused();
        ctx.fill(x, y + 23, x + w, y + 24, VortexStyle.fade(fokus ? VortexStyle.akzent(0.5f) : VortexStyle.LINE, a));
        passwortFeld.setX(-4000);
        passwortFeld.setY(y + 13);
        String sterne = "*".repeat(Math.min(40, passwortFeld.getValue().length()));
        boolean blink = fokus && (System.currentTimeMillis() / 500) % 2 == 0;
        text(ctx, sterne + (blink ? "_" : ""), x + 4, y + 14, 0xFFFFFFFF, a);
        knoepfe.add(new Knopf(x, y + 10, w, 14, () -> this.setFocused(passwortFeld)));
        return y + 30;
    }

    private void karte(GuiGraphicsExtractor ctx, int i, int x, int y, int w, float a) {
        boolean aktiv = ConfigManager.getActivePreset() == i;
        boolean hov = hochladen < 0 && mx >= x && mx < x + w && my >= y && my < y + KARTE_H;
        rund(ctx, x, y, w, KARTE_H, VortexStyle.fade(hov ? VortexStyle.HOV : VortexStyle.CARD, a));
        if (aktiv) {
            ctx.fill(x, y + 4, x + 2, y + KARTE_H - 4, VortexStyle.fade(VortexStyle.akzent(0.3f), a));
        }

        // Name (anklickbar zum Umbenennen)
        String name = ConfigManager.presetName(i);
        int nx = x + 10, ny = y + 8;
        if (umbenennen == i) {
            ctx.fill(nx - 3, ny - 3, nx + 140, ny + 11, VortexStyle.fade(VortexStyle.INNER, a));
            nameFeld.setX(nx);
            nameFeld.setY(ny);
            knopf(ctx, nx + 146, ny - 4, "Save", true, () -> namenSpeichern(), a, false);
        } else {
            int nw = this.font.width(name);
            boolean nHov = hochladen < 0 && mx >= nx && mx < nx + nw + 12 && my >= ny - 2 && my < ny + 10;
            text(ctx, name, nx, ny, nHov ? VortexStyle.akzent(0.6f) : 0xFFFFFFFF, a);
            // Unterstrichen beim Ueberfahren: Name ist anklickbar (umbenennen)
            if (nHov) ctx.fill(nx, ny + 9, nx + nw, ny + 10, VortexStyle.fade(VortexStyle.akzent(0.5f), a));
            knoepfe.add(new Knopf(nx, ny - 2, nw + 14, 12, () -> {
                umbenennen = i;
                nameFeld.setValue(ConfigManager.presetName(i).startsWith("Preset ") ? "" : ConfigManager.presetName(i));
                nameFeld.setVisible(true);
                this.setFocused(nameFeld);
            }));
            if (aktiv) {
                int bx = nx + nw + 18;
                rund(ctx, bx, ny - 2, this.font.width("ACTIVE") + 8, 11, VortexStyle.fade(VortexStyle.akzent(0.3f), a * 0.35f));
                text(ctx, "ACTIVE", bx + 4, ny, VortexStyle.akzent(0.6f), a);
            }
        }
        String info = aktiv ? zaehle() : (ConfigManager.presetExists(i) ? "Saved" : "Empty -- starts fresh when selected");
        if (hov && umbenennen != i && my < y + 20) info = "Click the name to rename";
        text(ctx, kuerzen(info, w / 2), nx, y + 26, VortexStyle.TEXT_DIM, a);

        // Beim Umbenennen gibt es hier nur "Save" -- sonst laegen bei schmalem
        // Fenster Export/Import direkt darunter und wuerden statt Save ausgeloest.
        if (umbenennen == i) return;

        // Knoepfe rechts, von rechts nach links
        int bx = x + w - 6;
        int by = y + (KARTE_H - 16) / 2;
        bx = knopf(ctx, bx, by, "Upload", true, () -> dialogOeffnen(i), a, true) - 4;
        bx = knopf(ctx, bx, by, "Import", false, () -> importieren(i), a, true) - 4;
        bx = knopf(ctx, bx, by, "Export", false, () -> exportieren(i), a, true) - 4;
        if (!aktiv) {
            knopf(ctx, bx, by, "Use", true, () -> {
                ConfigManager.switchTo(i);
                status = "Now using " + ConfigManager.presetName(i) + ".";
            }, a, true);
        }
    }

    private String zaehle() {
        int an = 0, alle = 0;
        for (var m : com.vortex.client.module.ModuleManager.INSTANCE.getModules()) {
            if (com.vortex.client.core.CleanModules.versteckt(m)) continue;
            alle++;
            if (m.isEnabled()) an++;
        }
        return an + " of " + alle + " mods on";
    }

    private void dialog(GuiGraphicsExtractor ctx, float a) {
        ctx.fill(winX, winY, winX + winW, winY + winH, VortexStyle.fade(0xA0000000, a));
        int dw = Math.min(winW - 20, 320);
        boolean an = CommunityApi.benutzer() != null;
        int dh = an ? 170 : 132;
        int dx = winX + (winW - dw) / 2, dy = winY + (winH - dh) / 2;
        VortexStyle.schatten(ctx, dx, dy, dw, dh, a);
        rund(ctx, dx, dy, dw, dh, VortexStyle.fade(VortexStyle.CARD, a));
        VortexStyle.akzentLinie(ctx, dx + 4, dy, dw - 8, a);
        int y = dy + 10;
        text(ctx, "Upload \"" + kuerzen(ConfigManager.presetName(hochladen), dw - 90) + "\"", dx + 10, y, 0xFFFFFFFF, a);
        y += 16;

        if (!an) {
            text(ctx, "Log in with your Vortex website account:", dx + 10, y, VortexStyle.TEXT_DIM, a);
            y += 14;
            y = eingabe(ctx, "Name or e-mail", benutzerFeld, dx + 10, y, dw - 20, a);
            y = passwortEingabe(ctx, dx + 10, y, dw - 20, a);
            int bx = knopf(ctx, dx + dw - 10, dy + dh - 22, "Log in", true, this::anmelden, a, true) - 4;
            bx = knopf(ctx, bx, dy + dh - 22, "Cancel", false, this::dialogSchliessen, a, true) - 4;
            knopf(ctx, bx, dy + dh - 22, "Create account", false,
                    () -> net.minecraft.util.Util.getPlatform().openUri(CommunityApi.SITE + "/register.html"), a, true);
        } else {
            y = eingabe(ctx, "Name", titelFeld, dx + 10, y, dw - 20, a);
            y = eingabe(ctx, "Recommended resolution (e.g. 1920x1080)", aufloesungFeld, dx + 10, y, dw - 20, a);
            y = eingabe(ctx, "Description (optional)", beschreibungFeld, dx + 10, y, dw - 20, a);
            // Sichtbarkeit
            String sicht = oeffentlich ? "Public -- listed in the Community" : "Unlisted -- only with the share code";
            knopf(ctx, dx + 10 + this.font.width(sicht) + 16, y - 2, sicht, oeffentlich,
                    () -> oeffentlich = !oeffentlich, a, true);
            int bx = knopf(ctx, dx + dw - 10, dy + dh - 22, "Upload Preset", true, this::hochladenStart, a, true) - 4;
            knopf(ctx, bx, dy + dh - 22, "Cancel", false, this::dialogSchliessen, a, true);
        }
    }

    /** Beschriftung + Eingabefeld; gibt die naechste y-Position zurueck. */
    private int eingabe(GuiGraphicsExtractor ctx, String label, EditBox e, int x, int y, int w, float a) {
        text(ctx, label, x, y, VortexStyle.TEXT_DIM, a);
        ctx.fill(x, y + 10, x + w, y + 24, VortexStyle.fade(VortexStyle.INNER, a));
        ctx.fill(x, y + 23, x + w, y + 24, VortexStyle.fade(e.isFocused() ? VortexStyle.akzent(0.5f) : VortexStyle.LINE, a));
        e.setX(x + 4);
        e.setY(y + 13);
        return y + 30;
    }

    // ======================================================================
    // Aktionen
    // ======================================================================

    private void namenSpeichern() {
        if (umbenennen < 0) return;
        ConfigManager.setPresetName(umbenennen, nameFeld.getValue());
        status = "Renamed.";
        umbenennen = -1;
        nameFeld.setVisible(false);
    }

    private void exportieren(int i) {
        String name = ConfigManager.presetName(i);
        String inhalt = ConfigManager.exportMods(i, name, "");
        if (ConfigManager.modZeilen(inhalt) == 0) {
            status = "This preset is still empty -- select it and change something first.";
            return;
        }
        Minecraft.getInstance().keyboardHandler.setClipboard(inhalt);
        var datei = ConfigManager.exportModsToFileFrom(i, name);
        status = "Copied to clipboard" + (datei != null ? " and saved as export/" + datei.getFileName() : "") + ".";
    }

    private int importFrage = -1;
    private long importFrageZeit = 0;

    private void importieren(int i) {
        String inhalt = Minecraft.getInstance().keyboardHandler.getClipboard();
        if (inhalt == null || ConfigManager.modZeilen(inhalt) == 0) {
            status = "Clipboard holds no preset. Use Export (or Community) first.";
            return;
        }
        // Zweimal klicken: der Platz wird ueberschrieben.
        long jetzt = System.currentTimeMillis();
        if (importFrage != i || jetzt - importFrageZeit > 4000) {
            importFrage = i;
            importFrageZeit = jetzt;
            status = "Click Import again to replace the mod settings of " + ConfigManager.presetName(i) + ".";
            return;
        }
        importFrage = -1;
        boolean ok = ConfigManager.importInto(i, inhalt);
        String n = ConfigManager.meta(inhalt, "name");
        status = ok ? "Imported" + (n.isEmpty() ? "" : " \"" + n + "\"") + " into " + ConfigManager.presetName(i) + " and selected."
                    : "That does not look like a Vortex preset.";
    }

    private void dialogOeffnen(int i) {
        hochladen = i;
        String n = ConfigManager.presetName(i);
        titelFeld.setValue(n);
        var fenster = Minecraft.getInstance().getWindow();
        aufloesungFeld.setValue(fenster.getWidth() + "x" + fenster.getHeight());
        beschreibungFeld.setValue("");
        umbenennen = -1;
        nameFeld.setVisible(false);
        dialogFelderZeigen();
        this.setFocused(CommunityApi.benutzer() == null ? benutzerFeld : titelFeld);
    }

    private void dialogSchliessen() {
        hochladen = -1;
        passwortFeld.setValue("");
        alleFelderVerstecken();
    }

    private void anmelden() {
        String n = benutzerFeld.getValue().trim();
        String pw = passwortFeld.getValue();
        if (n.isEmpty() || pw.isEmpty()) {
            status = "Enter name and password.";
            return;
        }
        arbeitet = true;
        status = "";
        hintergrund(() -> {
            String fehler = CommunityApi.anmelden(n, pw);
            Minecraft.getInstance().execute(() -> {
                passwortFeld.setValue("");
                if (fehler == null) {
                    status = "Logged in as " + CommunityApi.benutzer() + ".";
                    if (hochladen >= 0) {
                        dialogFelderZeigen();
                        this.setFocused(titelFeld);
                    }
                } else {
                    status = fehler;
                }
            });
        });
    }

    private void hochladenStart() {
        int slot = hochladen;
        String titel = titelFeld.getValue().trim();
        if (titel.isEmpty()) {
            status = "Give your preset a name.";
            return;
        }
        String aufl = aufloesungFeld.getValue().trim();
        String beschr = beschreibungFeld.getValue().trim();
        // Aufloesung vorne in die Beschreibung -- so steht sie auch in der
        // Liste (die Website kennt kein eigenes Feld dafuer).
        String beschreibung = (aufl.isEmpty() ? "" : "[" + aufl + "] ") + beschr;
        String inhalt = ConfigManager.exportMods(slot, titel, aufl);
        if (ConfigManager.modZeilen(inhalt) == 0) {
            status = "This preset is still empty -- nothing to upload.";
            return;
        }
        boolean pub = oeffentlich;
        arbeitet = true;
        status = "";
        hintergrund(() -> {
            String[] r = CommunityApi.hochladen(slot, titel, beschreibung, pub, inhalt);
            Minecraft.getInstance().execute(() -> {
                if ("ok".equals(r[0])) {
                    Minecraft.getInstance().keyboardHandler.setClipboard(r[1]);
                    status = "Uploaded! Share code " + r[1] + " copied to clipboard.";
                    dialogSchliessen();
                } else {
                    status = r[1];
                    if (hochladen >= 0) dialogFelderZeigen();
                }
            });
        });
    }

    /** Netzwerk nie auf dem Spiel-Thread -- sonst friert alles ein. */
    private void hintergrund(Runnable r) {
        Thread t = new Thread(() -> {
            try {
                r.run();
            } catch (Throwable pvpErr) {
                com.vortex.client.core.Errors.report("PresetScreen", pvpErr);
                status = "Something went wrong.";
            } finally {
                arbeitet = false;
            }
        }, "vortex-presets");
        t.setDaemon(true);
        t.start();
    }

    // ======================================================================
    // Eingabe
    // ======================================================================

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent click, boolean doubled) {
        if (super.mouseClicked(click, doubled)) return true;
        if (arbeitet) return true;
        for (int k = knoepfe.size() - 1; k >= 0; k--) {
            Knopf b = knoepfe.get(k);
            if (mx >= b.x() && mx < b.x() + b.w() && my >= b.y() && my < b.y() + b.h()) {
                b.aktion().run();
                return true;
            }
        }
        // Klick daneben: Umbenennen uebernehmen
        if (umbenennen >= 0) namenSpeichern();
        return false;
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        int key = event.key();
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER || key == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER) {
            if (umbenennen >= 0) { namenSpeichern(); return true; }
            if (hochladen >= 0 && !arbeitet) {
                if (CommunityApi.benutzer() == null) anmelden(); else hochladenStart();
                return true;
            }
        }
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE && (hochladen >= 0 || umbenennen >= 0)) {
            if (hochladen >= 0) dialogSchliessen();
            umbenennen = -1;
            nameFeld.setVisible(false);
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        ConfigManager.save();
        Minecraft.getInstance().gui.setScreen(parent);
    }

    // ======================================================================
    // Hilfen
    // ======================================================================

    /**
     * Knopf, rechtsbuendig an "rechts" (wenn rechtsbuendig), sonst ab x.
     * Gibt die linke Kante zurueck.
     */
    private int knopf(GuiGraphicsExtractor ctx, int x, int y, String label, boolean betont,
                      Runnable aktion, float a, boolean rechtsbuendig) {
        int w = this.font.width(label) + 14;
        int bx = rechtsbuendig ? x - w : x;
        boolean hov = mx >= bx && mx < bx + w && my >= y && my < y + 16;
        int grund = betont ? VortexStyle.mix(VortexStyle.INNER, VortexStyle.akzent(0.4f), hov ? 0.7f : 0.45f)
                           : (hov ? VortexStyle.HOV : VortexStyle.INNER);
        rund(ctx, bx, y, w, 16, VortexStyle.fade(grund, a));
        ctx.text(this.font, Component.literal(label), bx + 7, y + 4, VortexStyle.fade(0xFFFFFFFF, a), false);
        knoepfe.add(new Knopf(bx, y, w, 16, aktion));
        return bx;
    }

    private void knopfText(GuiGraphicsExtractor ctx, int x, int y, int w, int h, String t, boolean b,
                           Runnable aktion, float a) {
        boolean hov = mx >= x && mx < x + w && my >= y && my < y + h;
        ctx.text(this.font, Component.literal(t), x + 5, y + 4,
                VortexStyle.fade(hov ? VortexStyle.akzent(0.5f) : VortexStyle.TEXT_DIM, a), false);
        knoepfe.add(new Knopf(x, y, w, h, aktion));
    }

    private void text(GuiGraphicsExtractor ctx, String t, int x, int y, int farbe, float a) {
        ctx.text(this.font, Component.literal(t), x, y, VortexStyle.fade(farbe, a), false);
    }

    private String kuerzen(String s, int max) {
        if (s == null) return "";
        if (max <= 8) return "";
        if (this.font.width(s) <= max) return s;
        while (s.length() > 1 && this.font.width(s + "..") > max) s = s.substring(0, s.length() - 1);
        return s + "..";
    }

    private void rund(GuiGraphicsExtractor ctx, int x, int y, int w, int h, int c) {
        if (w <= 0 || h <= 0) return;
        ctx.fill(x, y + 1, x + w, y + h - 1, c);
        ctx.fill(x + 1, y, x + w - 1, y + 1, c);
        ctx.fill(x + 1, y + h - 1, x + w - 1, y + h, c);
    }
}
