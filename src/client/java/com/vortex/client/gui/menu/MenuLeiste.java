package com.vortex.client.gui.menu;

import com.vortex.client.account.AccountScreen;
import com.vortex.client.gui.FriendsScreen;
import com.vortex.client.gui.HomeScreen;
import com.vortex.client.gui.SkinScreen;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;

import java.util.ArrayList;
import java.util.List;

/**
 * Vortex-Leiste im Hauptmenue und im Pausenmenue: Host, Social, Wardrobe,
 * Pictures, Settings, Account -- jeweils ein Knopf, der den passenden
 * Vortex-Bildschirm oeffnet.
 *
 * "Host" gibt es nur im Pausenmenue einer Einzelspielerwelt (vorher gibt
 * es nichts zu hosten). Hostet der Launcher die Welt, steht dort stattdessen
 * "Hosting" ({@link HostingOptionsScreen}).
 *
 * Platz: rechts neben dem Vanilla-Menue, alle Knoepfe zusammen in einem
 * Glas-Dock mit Kopfzeile ({@link MenuDock}, seit 4.30.2). Ist Essential
 * installiert (hat rechts eine eigene Leiste), steht unsere links. Ist das
 * Fenster zu schmal fuer Text, zeigen die Knoepfe nur ihr Symbol.
 */
public final class MenuLeiste {
    private MenuLeiste() {}

    /** Zeilenhoehe, Abstand, Rand zum Fenster, Abstand zum Vanilla-Menue. */
    private static final int ZEILE = 20, ABSTAND = 2, RAND = 8, LUFT = 6;
    /** Breite des Docks: hoechstens, mindestens (mit Text); kompakt nur Symbole. */
    private static final int MAX_BREIT = 120, MIN_BREIT = 92, KOMPAKT = 30, KOMPAKT_ZEILE = 22;
    /** Breite des Vanilla-Menues in der Mitte (Knoepfe sind 200 breit). */
    private static final int MITTE = 204;

    private record Eintrag(String text, String[] symbol, Runnable aktion) {}

    public static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, breite, hoehe) -> {
            boolean pause = screen instanceof PauseScreen;
            if (!pause && !(screen instanceof TitleScreen)) return;
            try {
                anlegen(screen, breite, hoehe, pause);
            } catch (Throwable t) {
                com.vortex.client.core.Errors.report("MenuLeiste", t);
            }
        });
    }

    private static void anlegen(Screen screen, int breite, int hoehe, boolean pause) {
        Minecraft mc = Minecraft.getInstance();
        List<Eintrag> eintraege = new ArrayList<>();
        if (pause && LauncherHosting.aktiv()) {
            // Der Launcher hostet diese Welt (Paper-Server) -- seine Einstellungen hier
            eintraege.add(new Eintrag("Hosting", MenuSymbole.HOST, () -> mc.gui.setScreen(new HostingOptionsScreen(screen))));
        } else if (pause && mc.hasSingleplayerServer()) {
            eintraege.add(new Eintrag("Host", MenuSymbole.HOST, () -> mc.gui.setScreen(new HostScreen(screen))));
        }
        eintraege.add(new Eintrag("Social", MenuSymbole.SOCIAL, () -> mc.gui.setScreen(new FriendsScreen(screen))));
        eintraege.add(new Eintrag("Wardrobe", MenuSymbole.WARDROBE, () -> mc.gui.setScreen(new SkinScreen(screen))));
        eintraege.add(new Eintrag("Cosmetics", MenuSymbole.COSMETICS, () -> mc.gui.setScreen(new com.vortex.client.cosmetics.CosmeticsScreen(screen))));
        eintraege.add(new Eintrag("Pictures", MenuSymbole.PICTURES, () -> mc.gui.setScreen(new PicturesScreen(screen))));
        eintraege.add(new Eintrag("Settings", MenuSymbole.SETTINGS, () -> mc.gui.setScreen(new HomeScreen())));
        eintraege.add(new Eintrag("Account", MenuSymbole.ACCOUNT, () -> mc.gui.setScreen(new AccountScreen())));

        // Wie viel Platz ist neben dem Vanilla-Menue? Text + Symbol, oder nur Symbole?
        int frei = (breite - MITTE) / 2 - RAND - LUFT;
        boolean kompakt = frei < MIN_BREIT;
        int w = kompakt ? KOMPAKT : Math.min(MAX_BREIT, frei);
        int zeile = kompakt ? KOMPAKT_ZEILE : ZEILE;
        int kopf = kompakt ? MenuDock.KOPF_KOMPAKT + 4 : MenuDock.PAD + MenuDock.KOPF + 4;
        int innen = kompakt ? (KOMPAKT - KOMPAKT_ZEILE) / 2 : MenuDock.PAD;
        int hDock = kopf + eintraege.size() * zeile + (eintraege.size() - 1) * ABSTAND + innen;

        boolean links = FabricLoader.getInstance().isModLoaded("essential");
        int x = links ? RAND : breite - w - RAND;
        // Links oben steht im Pausenmenue schon "Restart game" -- darunter bleiben.
        int y = Math.max(links && pause ? 32 : RAND, (hoehe - hDock) / 2);
        MenuDock dock = new MenuDock(x, y, w, hDock, kompakt);

        int yy = y + kopf;
        boolean erster = true;
        for (Eintrag e : eintraege) {
            Screens.getWidgets(screen).add(new MenuKnopf(x + innen, yy, w - 2 * innen, zeile, e.text(), e.symbol(),
                    kompakt, e.aktion(), erster ? dock : null));
            erster = false;
            yy += zeile + ABSTAND;
        }
    }
}
