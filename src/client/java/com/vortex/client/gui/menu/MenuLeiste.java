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
 * es nichts zu hosten).
 *
 * Platz: rechts neben dem Vanilla-Menue. Ist Essential installiert (hat
 * rechts eine eigene Leiste), steht unsere links. Ist das Fenster zu schmal
 * fuer Text, zeigen die Knoepfe nur ihr Symbol.
 */
public final class MenuLeiste {
    private MenuLeiste() {}

    private static final int BREIT = 112, HOCH = 22, ABSTAND = 4, RAND = 8, KOMPAKT = 24;
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
        if (pause && mc.hasSingleplayerServer()) {
            eintraege.add(new Eintrag("Host", MenuSymbole.HOST, () -> mc.gui.setScreen(new HostScreen(screen))));
        }
        eintraege.add(new Eintrag("Social", MenuSymbole.SOCIAL, () -> mc.gui.setScreen(new FriendsScreen(screen))));
        eintraege.add(new Eintrag("Wardrobe", MenuSymbole.WARDROBE, () -> mc.gui.setScreen(new SkinScreen(screen))));
        eintraege.add(new Eintrag("Pictures", MenuSymbole.PICTURES, () -> mc.gui.setScreen(new PicturesScreen(screen))));
        eintraege.add(new Eintrag("Settings", MenuSymbole.SETTINGS, () -> mc.gui.setScreen(new HomeScreen())));
        eintraege.add(new Eintrag("Account", MenuSymbole.ACCOUNT, () -> mc.gui.setScreen(new AccountScreen())));

        // Passt neben das Vanilla-Menue Text + Symbol, oder nur das Symbol?
        boolean kompakt = (breite - MITTE) / 2 < BREIT + 2 * RAND;
        int w = kompakt ? KOMPAKT : BREIT;
        boolean links = FabricLoader.getInstance().isModLoaded("essential");
        int x = links ? RAND : breite - w - RAND;
        int gesamt = eintraege.size() * HOCH + (eintraege.size() - 1) * ABSTAND;
        // Links oben steht im Pausenmenue schon "Restart game" -- darunter bleiben.
        int y = Math.max(links && pause ? 32 : RAND, (hoehe - gesamt) / 2);

        for (Eintrag e : eintraege) {
            Screens.getWidgets(screen).add(new MenuKnopf(x, y, w, HOCH, e.text(), e.symbol(), kompakt, e.aktion()));
            y += HOCH + ABSTAND;
        }
    }
}
