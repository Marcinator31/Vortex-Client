package com.example.pvpclient;

import com.example.pvpclient.core.ConfigManager;
import com.example.pvpclient.gui.ClickGui;
import com.example.pvpclient.hud.HudRenderer;
import com.example.pvpclient.module.ModuleManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

/**
 * Client-Einstiegspunkt ("onEnable").
 */
public class PvpClientClient implements ClientModInitializer {

    public static final String MOD_ID = "pvpclient";

    // Ab 1.21.9 ist die Keybind-Kategorie ein Category-Objekt, kein String.
    // Wir erstellen eine eigene Kategorie fuer alle unsere Keybinds.
    private static final KeyBinding.Category CATEGORY =
        KeyBinding.Category.create(Identifier.of(MOD_ID, "main"));

    private static KeyBinding openClickGuiKey;
    private static KeyBinding openHudEditorKey;

    // Flankenerkennung fuer die Freecam-Taste (nur beim Druecken umschalten).
    private static boolean freecamKeyWasDown = false;

    @Override
    public void onInitializeClient() {
        // Module initialisieren (laedt die Registry).
        ModuleManager.INSTANCE.getModules();

        // Gespeicherte Einstellungen laden (Position, Farben, an/aus ...).
        // WICHTIG: nach der Modul-Registrierung, aber vor der syncState-
        // Synchronisation -- damit die geladenen An/Aus-Zustaende korrekt
        // angewendet werden.
        // Erst das zuletzt aktive Preset ermitteln, dann daraus laden.
        ConfigManager.loadActivePreset();
        ConfigManager.load();

        // HUD-Rendering anmelden.
        HudRenderer.register();

        // 3D-Welt-Rendering (Hitboxen) anmelden.
        com.example.pvpclient.hud.HitboxRenderer.register();
        com.example.pvpclient.hud.BlockEspRenderer.register();
        com.example.pvpclient.hud.StashFinder.register();
        com.example.pvpclient.hud.BlockEntityEsp.register();
        com.example.pvpclient.hud.ItemEsp.register();
        com.example.pvpclient.hud.SusChunks.register();
        com.example.pvpclient.hud.TunnelDetector.register();
        com.example.pvpclient.hud.AutoTotem.register();
        com.example.pvpclient.hud.Aimbot.register();
        com.example.pvpclient.hud.AutoHit.register();
        com.example.pvpclient.hud.Fly.register();
        com.example.pvpclient.hud.NoFall.register();
        com.example.pvpclient.hud.WorldScan.register();
        com.example.pvpclient.hud.ChunkBorders.register();
        com.example.pvpclient.hud.ProjectilePath.register();
        com.example.pvpclient.hud.SessionStats.register();
        com.example.pvpclient.hud.WaypointRenderer.register();
        com.example.pvpclient.waypoint.WaypointActions.register();
        com.example.pvpclient.hud.CrystalMacro.register();
        com.example.pvpclient.gui.RestartButton.register();
        com.example.pvpclient.freecam.Freecam.registerSafety();
        com.example.pvpclient.command.ClientCommands.register();

        // Beim Beenden des Spiels alle Einstellungen speichern.
        // Sicherheit: Wenn der Spieler die Welt verlaesst / disconnected, die
        // Freecam (und damit die Kamera-Entity) sauber beenden. Sonst haengt die
        // Entity an der alten Welt und kann beim Wechsel crashen.
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT
            .register((handler, client) -> {
                com.example.pvpclient.freecam.Freecam.disable();
                com.example.pvpclient.hud.StashFinder.reset();
                // Beim Serverwechsel die Totem-Zaehlung leeren -- die Werte
                // gelten nur fuer die Spieler der aktuellen Welt.
                com.example.pvpclient.hud.TotemPops.reset();
            });

        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            // Falls Potato Mode aktiv ist, vorher die Original-Grafikwerte
            // wiederherstellen -- sonst wuerde Minecraft die Potato-Werte als
            // neue Standardwerte in options.txt speichern.
            try {
                for (var module : ModuleManager.INSTANCE.getModules()) {
                    if (module instanceof com.example.pvpclient.module.modules.PotatoModeModule p
                            && p.isEnabled()) {
                        p.restoreOriginals();
                    }
                }
            } catch (Throwable ignored) {
                // Darf das Beenden nie stoeren.
            }
            ConfigManager.save();
        });

        // Keybind: Rechte Umschalttaste oeffnet das ClickGUI (wie viele Clients).
        openClickGuiKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.pvpclient.clickgui", InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_RIGHT_SHIFT, CATEGORY));

        // HINWEIS: Der Account-Switcher ist vorerst deaktiviert.
        //
        // Der Microsoft-Login laesst sich nur mit einer eigenen, bei Azure
        // registrierten Client-ID betreiben, und dafuer braucht man inzwischen
        // ein vollwertiges Azure-Konto. Solange das nicht eingerichtet ist,
        // waere der Knopf nur eine Sackgasse -- deshalb kein Keybind und kein
        // Eintrag im Hauptmenue.
        //
        // Der Code bleibt vollstaendig erhalten (Ordner "account"). Zum
        // Reaktivieren genuegt es, diesen Block und die beiden markierten
        // Stellen weiter unten wieder einzusetzen.

        // Keybind: Rechte Strg-Taste oeffnet den HUD-Editor (Drag & Drop).
        openHudEditorKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.pvpclient.hudeditor", InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_RIGHT_CONTROL, CATEGORY));

        // (deaktiviert) Accounts-Knopf im Hauptmenue -- siehe Hinweis oben.

        // Einmalige Zustands-Synchronisation: Module, die standardmaessig an
        // sind und ihre Wirkung ueber onEnable() entfalten (AppleSkin,
        // ShieldStatus per Reflection), muessen ihren Effekt beim Start einmal
        // aktiv setzen. Wir machen das beim ersten Tick (dann sind alle
        // eingebetteten Mods garantiert geladen). Ein Flag sorgt fuer "nur einmal".
        final boolean[] synced = { false };

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!synced[0]) {
                synced[0] = true;
                for (var module : ModuleManager.INSTANCE.getModules()) {
                    try {
                        module.syncState();
                    } catch (Throwable ignored) {
                        // Ein einzelnes Modul darf den Start nicht stoeren.
                    }
                }
            }
            while (openClickGuiKey.wasPressed()) {
                client.setScreen(new ClickGui());
            }
            while (openHudEditorKey.wasPressed()) {
                client.setScreen(new com.example.pvpclient.gui.HudEditorScreen());
            }

            // --- Freecam: Taste abfragen (Toggle) + Bewegung pro Tick ---
            try {
                com.example.pvpclient.module.modules.FreecamModule fc =
                    com.example.pvpclient.freecam.Freecam.module();
                if (fc != null && fc.isEnabled()) {
                    int keyCode = fc.key.getKeyCode();
                    boolean down = net.minecraft.client.util.InputUtil.isKeyPressed(
                        client.getWindow(), keyCode);
                    // Flankenerkennung: nur beim Druecken umschalten, nicht halten.
                    if (down && !freecamKeyWasDown) {
                        com.example.pvpclient.freecam.Freecam.toggle();
                    }
                    freecamKeyWasDown = down;
                    // Die Bewegung passiert pro Frame im CameraMixin (fluessig),
                    // hier wird nur die Umschalt-Taste geprueft.
                } else {
                    // Modul aus -> Freecam sicher beenden.
                    com.example.pvpclient.freecam.Freecam.disable();
                    freecamKeyWasDown = false;
                }
            } catch (Throwable ignored) {
            }
        });

        System.out.println("[" + MOD_ID + "] Client gestartet.");
    }
}
