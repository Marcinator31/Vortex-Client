package com.vortex.client.gui.menu;

import com.vortex.client.gui.VortexStyle;
import com.vortex.client.util.GameRestarter;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Objects;

/**
 * "Host" in der Menue-Leiste: die Einzelspielerwelt fuer bis zu 4 Freunde
 * oeffnen und die Adresse zum Weitergeben zeigen. Die Logik steckt in
 * {@link WeltHosten}.
 *
 * Ohne e4mc gibt es einen Knopf zum Installieren (danach Neustart) -- die
 * Welt laesst sich trotzdem oeffnen, dann aber nur fuers eigene Netzwerk.
 */
public class HostScreen extends Screen {
    private static final int FENSTER_W = 300;

    private final Screen parent;
    private String zustand = "";       // aendert er sich, werden die Knoepfe neu gebaut
    private String meldung;
    private int fx, fy, fh;

    public HostScreen(Screen parent) {
        super(Component.literal("Host"));
        this.parent = parent;
    }

    private String zustandJetzt() {
        return WeltHosten.offen() + "|" + (WeltHosten.e4mcAdresse() != null) + "|" + WeltHosten.e4mcDa()
                + "|" + WeltHosten.installStatus() + "|" + WeltHosten.neustartNoetig();
    }

    @Override
    protected void init() {
        zustand = zustandJetzt();
        fh = 190;
        fx = (this.width - FENSTER_W) / 2;
        fy = Math.max(8, (this.height - fh) / 2);
        int bx = fx + 16, bw = FENSTER_W - 32, y = fy + fh - 30;

        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
                .bounds(fx + FENSTER_W - 16 - 70, y, 70, 20).build());
        int restW = bw - 74;

        if (!WeltHosten.moeglich()) return;
        if (!WeltHosten.offen()) {
            addRenderableWidget(Button.builder(Component.literal("Open to friends"), b -> {
                meldung = WeltHosten.oeffnen() ? null : "Could not open the world.";
                rebuildWidgets();
            }).bounds(bx, y, restW, 20).build());
        } else {
            String adresse = Objects.requireNonNullElse(WeltHosten.e4mcAdresse(), WeltHosten.lanAdresse());
            addRenderableWidget(Button.builder(Component.literal("Copy address"), b -> {
                if (adresse != null) this.minecraft.keyboardHandler.setClipboard(adresse);
                meldung = "Address copied -- send it to your friends.";
            }).bounds(bx, y, WeltHosten.kannSchliessen() ? restW / 2 - 2 : restW, 20).build());
            if (WeltHosten.kannSchliessen()) {
                addRenderableWidget(Button.builder(Component.literal("Close to friends"), b -> {
                    WeltHosten.schliessen();
                    meldung = null;
                    rebuildWidgets();
                }).bounds(bx + restW / 2 + 2, y, restW / 2 - 2, 20).build());
            }
        }
        if (!WeltHosten.e4mcDa()) {
            boolean fertig = WeltHosten.neustartNoetig();
            addRenderableWidget(Button.builder(Component.literal(fertig ? "Restart game now" : "Install e4mc"), b -> {
                if (WeltHosten.neustartNoetig()) {
                    try { GameRestarter.restart(); } catch (Throwable t) { meldung = "Restart failed -- please restart the game yourself."; }
                } else {
                    WeltHosten.e4mcInstallieren();
                }
            }).bounds(bx, y - 24, bw, 20).build());
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (!zustand.equals(zustandJetzt())) rebuildWidgets();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        g.fill(0, 0, this.width, this.height, VortexStyle.DIM);
        VortexStyle.schatten(g, fx, fy, FENSTER_W, fh, 1f);
        g.fill(fx, fy, fx + FENSTER_W, fy + fh, VortexStyle.WINDOW);
        VortexStyle.akzentLinie(g, fx + 4, fy, FENSTER_W - 8, 1f);

        int x = fx + 16, y = fy + 14;
        g.text(this.font, "Host your world", x, y, VortexStyle.TEXT, true);
        y += 16;

        if (!WeltHosten.moeglich()) {
            y = zeilen(g, "Open a singleplayer world first, then you can host it here.", x, y, VortexStyle.TEXT_DIM);
        } else if (!WeltHosten.offen()) {
            y = zeilen(g, "Open this world so up to " + WeltHosten.MAX_FREUNDE + " friends can join you. "
                    + "It closes when you leave the world.", x, y, VortexStyle.TEXT_DIM);
        } else {
            String e4mc = WeltHosten.e4mcAdresse();
            g.text(this.font, "Address for friends", x, y, VortexStyle.TEXT_DIM, false);
            y += 11;
            String anzeigen = e4mc != null ? e4mc : WeltHosten.e4mcDa() ? "Connecting to e4mc..." : WeltHosten.lanAdresse();
            g.fill(x - 2, y - 3, fx + FENSTER_W - 14, y + 10, VortexStyle.INNER);
            g.text(this.font, anzeigen, x + 2, y, e4mc != null || !WeltHosten.e4mcDa() ? 0xFF7EE2A0 : VortexStyle.TEXT_DIM, false);
            y += 16;
            if (e4mc != null) y = zeilen(g, "Works from anywhere. Same Wi-Fi: " + WeltHosten.lanAdresse(), x, y, VortexStyle.TEXT_DIM);
            else if (!WeltHosten.e4mcDa()) y = zeilen(g, "Only works in your own network. Install e4mc so friends can join from anywhere.", x, y, VortexStyle.TEXT_DIM);
            List<String> spieler = WeltHosten.spieler();
            y += 2;
            y = zeilen(g, "Players " + spieler.size() + " / " + (WeltHosten.MAX_FREUNDE + 1) + ": " + String.join(", ", spieler), x, y, VortexStyle.TEXT);
        }
        String status = WeltHosten.installStatus();
        if (status != null) y = zeilen(g, status, x, y + 2, VortexStyle.TEXT_DIM);
        if (meldung != null) zeilen(g, meldung, x, y + 2, VortexStyle.VIOLETT);

        super.extractRenderState(g, mouseX, mouseY, delta);
    }

    /** Text umbrechen; liefert die naechste freie Zeile. */
    private int zeilen(GuiGraphicsExtractor g, String text, int x, int y, int farbe) {
        for (var zeile : this.font.split(Component.literal(text), FENSTER_W - 32)) {
            g.text(this.font, zeile, x, y, farbe, false);
            y += 10;
        }
        return y;
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(parent);
    }
}
