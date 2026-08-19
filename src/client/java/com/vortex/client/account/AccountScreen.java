package com.vortex.client.account;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

/**
 * Der Account-Switcher-Bildschirm mit echtem Microsoft-Login.
 *
 * "Microsoft-Login" startet den Device Code Flow: der Client holt einen Code,
 * zeigt ihn an und oeffnet die Login-Seite im Browser. Der Nutzer gibt den Code
 * ein und loggt sich bei Microsoft ein; sobald das fertig ist, erscheint der
 * Account in der Liste. Der Login laeuft in einem eigenen Thread, damit das
 * Spiel nicht einfriert.
 *
 * Darunter pro gespeichertem Account "Wechseln" und "Entfernen".
 */
public class AccountScreen extends Screen {

    // Status-Component fuer den laufenden Login (Code, Fehler, Erfolg).
    private volatile String statusLine = "";
    private volatile String codeLine = "";
    private volatile boolean loggingIn = false;

    // Eingabefeld fuer den aus dem Browser kopierten Code (klassischer Weg).
    private net.minecraft.client.gui.components.EditBox codeField;

    public AccountScreen() {
        super(Component.literal("Accounts"));
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int x = cx - 100;
        int y = 44;

        // "Microsoft-Login" -- startet den Device Code Flow.
        this.addRenderableWidget(Button.builder(
            Component.literal(loggingIn ? "Signing in..." : "+ Microsoft sign-in"),
            btn -> startMicrosoftLogin()
        ).bounds(x, y, 200, 20).build());

        y += 24;

        // --- Klassischer Browser-Login (ohne eigene Azure-App) ---
        this.addRenderableWidget(Button.builder(
            Component.literal("1. Open login page"),
            btn -> openLoginPage()
        ).bounds(x, y, 200, 20).build());

        y += 24;

        // Feld zum Einfuegen der Adresse aus dem Browser.
        this.codeField = new net.minecraft.client.gui.components.EditBox(
                this.font, x, y, 200, 20,
                Component.literal("Paste the address"));
        this.codeField.setMaxLength(2000);
        this.codeField.setHint(Component.literal("Paste the address containing code=..."));
        this.addRenderableWidget(this.codeField);

        y += 24;

        this.addRenderableWidget(Button.builder(
            Component.literal("2. Redeem code"),
            btn -> redeemCode()
        ).bounds(x, y, 200, 20).build());

        y += 30;

        // Pro gespeichertem Account: "Wechseln" + "Entfernen".
        for (Account acc : new java.util.ArrayList<>(AccountManager.INSTANCE.getAccounts())) {
            this.addRenderableWidget(Button.builder(
                Component.literal("Switch to: " + acc.username),
                btn -> {
                    AccountManager.INSTANCE.switchTo(acc);
                    this.rebuildWidgets();
                }
            ).bounds(x, y, 150, 20).build());

            this.addRenderableWidget(Button.builder(
                Component.literal("X"),
                btn -> {
                    AccountManager.INSTANCE.remove(acc);
                    this.rebuildWidgets();
                }
            ).bounds(x + 155, y, 45, 20).build());

            y += 24;
        }

        // Schliessen-Button.
        this.addRenderableWidget(Button.builder(
            Component.literal("Close"),
            btn -> this.onClose()
        ).bounds(x, this.height - 30, 200, 20).build());
    }

    /** Startet den Microsoft Device Code Flow in einem Hintergrund-Thread. */
    private static final org.slf4j.Logger LOGGER =
            org.slf4j.LoggerFactory.getLogger("vortexclient-auth");

    /**
     * Macht aus einer Exception eine lesbare Zeile. Wichtig: getMessage() ist oft
     * null (z.B. bei NullPointerException) -- dann stand frueher nur "Fehler: null"
     * da. Hier kommt zusaetzlich immer der Fehlertyp mit, und die Ursache-Kette.
     */
    private static String describe(Throwable e) {
        StringBuilder sb = new StringBuilder();
        Throwable cur = e;
        int depth = 0;
        while (cur != null && depth < 3) {
            if (depth > 0) sb.append(" <- ");
            String msg = cur.getMessage();
            sb.append(cur.getClass().getSimpleName());
            if (msg != null && !msg.isEmpty()) sb.append(": ").append(msg);
            cur = cur.getCause();
            depth++;
        }
        return sb.toString();
    }

    /** Schritt 1: Microsoft-Login-Seite im Browser oeffnen. */
    private void openLoginPage() {
        String url = MicrosoftAuth.getAuthUrl();
        statusLine = "Sign in with your browser, then copy the address.";
        codeLine = "It contains 'code=' and looks blank.";
        try {
            Util.getPlatform().openUri(new java.net.URI(url));
        } catch (Throwable t) {
            LOGGER.error("[vortexclient] Could not open the browser", t);
            statusLine = "Browser did not open \u2014 the address is in latest.log";
            LOGGER.info("[vortexclient] Sign-in address: {}", url);
        }
    }

    /** Schritt 2: eingefuegte Adresse einloesen und Account anlegen. */
    private void redeemCode() {
        if (loggingIn) return;
        String pasted = (codeField != null) ? codeField.getValue() : "";
        if (pasted == null || pasted.trim().isEmpty()) {
            statusLine = "Paste the address from your browser first.";
            return;
        }
        loggingIn = true;
        statusLine = "Redeeming code...";
        codeLine = "";
        this.rebuildWidgets();

        Thread t = new Thread(() -> {
            try {
                Account acc = MicrosoftAuth.loginWithCode(pasted);
                AccountManager.INSTANCE.add(acc);
                statusLine = "Eingeloggt als " + acc.username + "!";
                codeLine = "";
            } catch (Throwable e) {
                LOGGER.error("[vortexclient] Code redemption failed", e);
                String msg = describe(e);
                statusLine = "Error: " + msg;
                // Bei genau diesem Microsoft-Fehler direkt sagen, was zu tun ist --
                // er bedeutet immer dasselbe: die Client-ID ist nicht registriert.
                if (msg.contains("unauthorized_client") || msg.contains("AADSTS700016")) {
                    statusLine = "Client ID not registered (AADSTS700016)";
                    codeLine = "Add your own Azure ID to config/vortexclient-clientid.txt";
                } else {
                    codeLine = "See latest.log for details";
                }
            } finally {
                loggingIn = false;
                net.minecraft.client.Minecraft.getInstance().execute(() -> {
                    if (this.minecraft != null && this.minecraft.gui.screen() == this) {
                        this.rebuildWidgets();
                    }
                });
            }
        }, "vortexclient-ms-code");
        t.setDaemon(true);
        t.start();
    }

    private void startMicrosoftLogin() {
        if (loggingIn) return;
        loggingIn = true;
        statusLine = "Connecting to Microsoft...";
        codeLine = "";
        this.rebuildWidgets();

        Thread t = new Thread(() -> {
            try {
                Account acc = MicrosoftAuth.login(code -> {
                    // Code anzeigen + Browser oeffnen.
                    codeLine = "Code: " + code.userCode;
                    statusLine = "Opening browser... enter this code: " + code.userCode;
                    try {
                        Util.getPlatform().openUri(new java.net.URI(code.verificationUri));
                    } catch (Throwable ignored) {
                        statusLine = "Gehe zu " + code.verificationUri
                                + " and enter the code.";
                    }
                });

                // Erfolg -> Account speichern.
                AccountManager.INSTANCE.add(acc);
                statusLine = "Eingeloggt als " + acc.username + "!";
                codeLine = "";
            } catch (Throwable e) {
                // Vollstaendigen Fehler ins Spiel-Log schreiben (latest.log) --
                // die GUI-Zeile ist kurz, im Log steht die ganze Ursache.
                LOGGER.error("[vortexclient] Microsoft sign-in failed", e);
                statusLine = "Error: " + describe(e);
                codeLine = "See latest.log for details";
            } finally {
                loggingIn = false;
                // GUI im Main-Thread neu aufbauen.
                net.minecraft.client.Minecraft.getInstance().execute(() -> {
                    if (this.minecraft != null && this.minecraft.gui.screen() == this) {
                        this.rebuildWidgets();
                    }
                });
            }
        }, "vortexclient-ms-login");
        t.setDaemon(true);
        t.start();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        super.extractRenderState(ctx, mouseX, mouseY, delta);

        // Aktueller Account-Name oben.
        String current = "Current: " + this.minecraft.getUser().getName();
        ctx.centeredText(this.font, Component.literal(current),
                this.width / 2, 18, 0xFFFFFF00);

        // Status + Code (waehrend/nach dem Login).
        if (!statusLine.isEmpty()) {
            ctx.centeredText(this.font, Component.literal(statusLine),
                    this.width / 2, this.height - 52, 0xFF55FF55);
        }
        if (!codeLine.isEmpty()) {
            ctx.centeredText(this.font, Component.literal(codeLine),
                    this.width / 2, this.height - 64, 0xFFFFFFFF);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
