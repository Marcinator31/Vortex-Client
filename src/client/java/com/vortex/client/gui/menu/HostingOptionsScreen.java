package com.vortex.client.gui.menu;

import com.vortex.client.gui.VortexStyle;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * "Hosting Options" im Pausenmenue, solange der Launcher die Welt hostet:
 * Adresse fuer Freunde, Spielmodus, Schwierigkeit, PvP, Cheats, Whitelist,
 * Spieler (OP, Kick, Bann) und Neustart. Alles laeuft ueber den Launcher
 * ({@link LauncherHosting}) -- dieselben Aktionen wie dort.
 */
public class HostingOptionsScreen extends Screen {
    private static final int FENSTER_W = 330, MAX_ZEILEN = 6;
    private static final String[] MODI = {"survival", "creative", "adventure", "spectator"};
    private static final String[] SCHWIERIG = {"peaceful", "easy", "normal", "hard"};

    private final Screen parent;
    private String zustand = "";
    private String meldung;
    private int fx, fy, fh, spielerY;

    public HostingOptionsScreen(Screen parent) {
        super(Component.literal("Hosting Options"));
        this.parent = parent;
    }

    private static String gross(String s) {
        return s == null || s.isEmpty() ? "" : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static String naechster(String[] liste, String jetzt) {
        for (int i = 0; i < liste.length; i++) if (liste[i].equals(jetzt)) return liste[(i + 1) % liste.length];
        return liste[0];
    }

    private String zustandJetzt() {
        LauncherHosting.Stand s = LauncherHosting.stand();
        StringBuilder b = new StringBuilder(s.status).append('|').append(s.adresse).append('|').append(s.gamemode).append('|').append(s.difficulty)
                .append('|').append(s.pvp).append(s.cheats).append(s.whitelist).append(s.neustart);
        for (LauncherHosting.Spieler p : s.spieler) b.append('|').append(p.name()).append(p.op());
        return b.toString();
    }

    @Override
    protected void init() {
        zustand = zustandJetzt();
        LauncherHosting.Stand s = LauncherHosting.stand();
        int zeilen = Math.min(MAX_ZEILEN, Math.max(1, s.spieler.size()));
        fh = 172 + zeilen * 22;
        fx = (this.width - FENSTER_W) / 2;
        fy = Math.max(6, (this.height - fh) / 2);
        int x = fx + 14, bw = FENSTER_W - 28;
        boolean an = "running".equals(s.status);

        // Adresse kopieren
        int y = fy + 42;
        addRenderableWidget(Button.builder(Component.literal("Copy"), b -> {
            String a = s.adresse != null ? s.adresse : s.lan;
            if (a != null) {
                this.minecraft.keyboardHandler.setClipboard(a);
                meldung = "Address copied -- send it to your friends.";
            }
        }).bounds(fx + FENSTER_W - 14 - 50, y - 4, 50, 18).build()).active = s.adresse != null || s.lan != null;

        // Einstellungen
        y = fy + 70;
        int halb = (bw - 4) / 2;
        addRenderableWidget(Button.builder(Component.literal("Game mode: " + gross(s.gamemode)),
                b -> LauncherHosting.einstellung("gamemode", naechster(MODI, s.gamemode)))
                .bounds(x, y, halb, 20).tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal("For friends who join. You keep your own game mode."))).build()).active = an;
        addRenderableWidget(Button.builder(Component.literal("Difficulty: " + gross(s.difficulty)),
                b -> LauncherHosting.einstellung("difficulty", naechster(SCHWIERIG, s.difficulty)))
                .bounds(x + halb + 4, y, halb, 20).build()).active = an;
        y += 24;
        int drittel = (bw - 8) / 3;
        addRenderableWidget(Button.builder(Component.literal("PvP: " + (s.pvp ? "On" : "Off")),
                b -> LauncherHosting.einstellung("pvp", !s.pvp)).bounds(x, y, drittel, 20).build()).active = an;
        addRenderableWidget(Button.builder(Component.literal("Cheats: " + (s.cheats ? "On" : "Off")),
                b -> LauncherHosting.einstellung("cheats", !s.cheats)).bounds(x + drittel + 4, y, drittel, 20)
                .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal("Friends become operators and can use commands. You always can."))).build()).active = an;
        addRenderableWidget(Button.builder(Component.literal("Whitelist: " + (s.whitelist ? "On" : "Off")),
                b -> LauncherHosting.einstellung("whitelist", !s.whitelist)).bounds(x + 2 * (drittel + 4), y, bw - 2 * (drittel + 4), 20)
                .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal("Only allowed players can join. Everyone online now is allowed automatically."))).build()).active = an;

        // Spieler
        spielerY = y + 40;
        int py = spielerY;
        List<LauncherHosting.Spieler> liste = s.spieler;
        for (int i = 0; i < Math.min(MAX_ZEILEN, liste.size()); i++) {
            LauncherHosting.Spieler p = liste.get(i);
            if (!p.host()) {
                int bx = fx + FENSTER_W - 14;
                bx -= 40;
                addRenderableWidget(Button.builder(Component.literal("Ban"), b -> {
                    LauncherHosting.spieler(p.name(), "ban");
                    meldung = p.name() + " was banned.";
                }).bounds(bx, py, 40, 18).build());
                bx -= 44;
                addRenderableWidget(Button.builder(Component.literal("Kick"), b -> {
                    LauncherHosting.spieler(p.name(), "kick");
                    meldung = p.name() + " was kicked.";
                }).bounds(bx, py, 40, 18).build());
                bx -= 58;
                addRenderableWidget(Button.builder(Component.literal(p.op() ? "Remove OP" : "Make OP"),
                        b -> LauncherHosting.spieler(p.name(), p.op() ? "deop" : "op")).bounds(bx, py, 54, 18).build());
            }
            py += 22;
        }

        // Unten
        int uy = fy + fh - 28;
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
                .bounds(fx + FENSTER_W - 14 - 70, uy, 70, 20).build());
        if (s.neustart) {
            addRenderableWidget(Button.builder(Component.literal("Restart server"), b -> {
                LauncherHosting.neustart();
                meldung = "Restarting -- join again from Multiplayer in a moment.";
            }).bounds(x, uy, 110, 20).build());
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (!zustand.equals(zustandJetzt())) rebuildWidgets();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        LauncherHosting.Stand s = LauncherHosting.stand();
        g.fill(0, 0, this.width, this.height, VortexStyle.DIM);
        VortexStyle.schatten(g, fx, fy, FENSTER_W, fh, 1f);
        g.fill(fx, fy, fx + FENSTER_W, fy + fh, VortexStyle.WINDOW);
        VortexStyle.akzentLinie(g, fx + 4, fy, FENSTER_W - 8, 1f);

        int x = fx + 14, y = fy + 12;
        boolean an = "running".equals(s.status);
        g.fill(x, y + 2, x + 5, y + 7, an ? 0xFF22C55E : 0xFFF5B74A);
        g.text(this.font, "Hosting Options", x + 10, y, VortexStyle.TEXT, true);
        String sub = s.welt + (s.version.isEmpty() ? "" : "  ·  Minecraft " + s.version) + (an ? "" : "  ·  " + gross(s.status));
        g.text(this.font, sub, fx + FENSTER_W - 14 - this.font.width(sub), y, VortexStyle.TEXT_DIM, false);

        // Adresse
        y = fy + 28;
        g.text(this.font, "Address for friends", x, y, VortexStyle.TEXT_DIM, false);
        y += 14;
        g.fill(x - 2, y - 4, fx + FENSTER_W - 14 - 54, y + 10, VortexStyle.INNER);
        String adresse = s.adresse != null && !"reconnecting".equals(s.netz) ? s.adresse
                : "reconnecting".equals(s.netz) ? "Reconnecting..." : s.lan != null ? s.lan + "  (same Wi-Fi only)" : "Connecting...";
        g.text(this.font, adresse, x + 2, y, s.adresse != null ? 0xFF7EE2A0 : VortexStyle.TEXT_DIM, false);

        // Spieler
        int py = spielerY - 14;
        g.text(this.font, "Players " + s.spieler.size() + " / " + s.maxPlayers, x, py, VortexStyle.TEXT_DIM, false);
        py += 14;
        if (s.spieler.isEmpty()) g.text(this.font, "Nobody is online yet.", x, py + 5, VortexStyle.TEXT_DIM, false);
        for (int i = 0; i < Math.min(MAX_ZEILEN, s.spieler.size()); i++) {
            LauncherHosting.Spieler p = s.spieler.get(i);
            g.fill(x - 2, py - 1, fx + FENSTER_W - 12, py + 19, VortexStyle.INNER);
            g.text(this.font, p.name(), x + 3, py + 5, VortexStyle.TEXT, false);
            int nx = x + 6 + this.font.width(p.name());
            if (p.host()) g.text(this.font, "(you)", nx, py + 5, VortexStyle.TEXT_DIM, false);
            else if (p.op()) g.text(this.font, "OP", nx, py + 5, VortexStyle.VIOLETT, false);
            py += 22;
        }
        if (s.spieler.size() > MAX_ZEILEN) g.text(this.font, "+" + (s.spieler.size() - MAX_ZEILEN) + " more -- see the launcher", x, py, VortexStyle.TEXT_DIM, false);

        int uy = fy + fh - 28;
        String unten = meldung != null ? meldung : s.neustart ? "Some changes need a restart." : null;
        if (unten != null) {
            int maxW = s.neustart ? FENSTER_W - 28 - 70 - 116 : FENSTER_W - 28 - 76;
            int ty = uy + 6;
            for (var zeile : this.font.split(Component.literal(unten), Math.max(60, maxW))) {
                g.text(this.font, zeile, s.neustart ? x + 116 : x, ty, meldung != null ? VortexStyle.VIOLETT : VortexStyle.TEXT_DIM, false);
                ty += 10;
                if (ty > uy + 18) break;
            }
        }

        super.extractRenderState(g, mouseX, mouseY, delta);
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(parent);
    }
}
